package com.example.archonsquests

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.rustore.sdk.appupdate.errors.RuStoreInstallException
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.RuStoreAppUpdateManager
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateInfo
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability
import ru.rustore.sdk.appupdate.model.AppUpdateType
import ru.rustore.sdk.core.exception.RuStoreNotInstalledException
import ru.rustore.sdk.core.exception.RuStoreOutdatedException
import ru.rustore.sdk.core.exception.RuStoreUserUnauthorizedException

class MainViewModel : ViewModel() {
    private lateinit var ruStoreAppUpdateManager: RuStoreAppUpdateManager
    private var isInitialized = false
    private var pendingUpdateInfo: AppUpdateInfo? = null

    // Кэширование раз в 4 часа
    private var lastUpdateCheck: Long = 0
    private val updateCheckInterval = 4 * 60 * 60 * 1000L // 4 часа

    // Автоматическая очистка при 100 запросах
    private var updateCheckCounter: Int = 0
    private val maxUpdateChecks = 100

    private val TAG = "MainViewModel"

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 10)
    val events: SharedFlow<Event> get() = _events

    private val installStateUpdateListener = InstallStateUpdateListener { installState ->
        viewModelScope.launch(Dispatchers.Main) {
            if (!isActive) return@launch

            when (installState.installStatus) {
                InstallStatus.DOWNLOADED -> {
                    _events.emit(Event.UpdateDownloaded)
                }
                InstallStatus.DOWNLOADING -> {
                    val progress = calculateProgress(
                        installState.bytesDownloaded,
                        installState.totalBytesToDownload
                    )
                    _events.emit(Event.DownloadProgress(progress))
                }
                InstallStatus.INSTALLING -> {
                    _events.emit(Event.Installing)
                }
                InstallStatus.FAILED -> {
                    Log.e(TAG, "Ошибка установки")
                    _events.emit(Event.Error("Ошибка установки"))
                }
                InstallStatus.PENDING -> {
                    _events.emit(Event.UpdatePending)
                }
                else -> {
                    Log.w(TAG, "Неизвестный статус: ${installState.installStatus}")
                }
            }
        }
    }

    fun init(context: Context) {
        if (isInitialized) return

        try {
            ruStoreAppUpdateManager = RuStoreAppUpdateManagerFactory.create(context)
            isInitialized = true
            checkForUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации RuStore: ${e.message}", e)
            viewModelScope.launch {
                _events.emit(Event.Error("Ошибка инициализации обновлений"))
            }
        }
    }

    fun checkForUpdates() {
        if (!isInitialized || !shouldCheckForUpdates()) return

        lastUpdateCheck = System.currentTimeMillis()
        updateCheckCounter++

        if (updateCheckCounter >= maxUpdateChecks) {
            resetCounters()
            Log.d(TAG, "Сброшены счётчики проверок ($maxUpdateChecks)")
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                ruStoreAppUpdateManager.getAppUpdateInfo()
                    .addOnSuccessListener { appUpdateInfo ->
                        launch(Dispatchers.Main) {
                            if (isActive) {
                                handleUpdateInfo(appUpdateInfo)
                            }
                        }
                    }
                    .addOnFailureListener { throwable ->
                        launch(Dispatchers.Main) {
                            if (isActive) {
                                Log.e(TAG, "Ошибка проверки обновлений", throwable)
                                handleRuStoreException(throwable)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Критическая ошибка в checkForUpdates", e)
            }
        }
    }

    private suspend fun handleUpdateInfo(appUpdateInfo: AppUpdateInfo) {
        if (appUpdateInfo.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE) {
            try {
                pendingUpdateInfo = appUpdateInfo
                ruStoreAppUpdateManager.registerListener(installStateUpdateListener)
                _events.emit(Event.UpdateAvailable)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки обновления", e)
                viewModelScope.launch {
                    _events.emit(Event.Error("Ошибка обработки обновления"))
                }
            }
        } else {
            Log.d(TAG, "Нет доступных обновлений")
        }
    }

    fun startFlexibleUpdate() {
        val updateInfo = pendingUpdateInfo ?: run {
            viewModelScope.launch {
                _events.emit(Event.Error("Информация об обновлении утеряна"))
            }
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            try {
                val options = AppUpdateOptions.Builder()
                    .appUpdateType(AppUpdateType.FLEXIBLE)
                    .build()
                ruStoreAppUpdateManager.startUpdateFlow(updateInfo, options)
                    .addOnSuccessListener { resultCode ->
                        when (resultCode) {
                            Activity.RESULT_OK -> {
                                Log.d(TAG, "Обновление начато")
                            }
                            Activity.RESULT_CANCELED -> {
                                Log.w(TAG, "Пользователь отменил обновление")
                                viewModelScope.launch {
                                    _events.emit(Event.UpdateCancelled)
                                }
                            }
                        }
                    }
                    .addOnFailureListener { throwable ->
                        Log.e(TAG, "startUpdateFlow error", throwable)
                        handleRuStoreException(throwable)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Критическая ошибка startFlexibleUpdate", e)
                _events.emit(Event.Error("Критическая ошибка: ${e.message}"))
            }
        }
    }

    fun completeUpdateRequested() {
        if (!isInitialized) {
            viewModelScope.launch {
                _events.emit(Event.Error("Система обновлений не инициализирована"))
            }
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            try {
                ruStoreAppUpdateManager.completeUpdate(
                    AppUpdateOptions.Builder()
                        .appUpdateType(AppUpdateType.FLEXIBLE)
                        .build()
                ).addOnSuccessListener {
                    viewModelScope.launch {
                        _events.emit(Event.UpdateCompleted)
                    }
                }.addOnFailureListener { throwable ->
                    Log.e(TAG, "Ошибка завершения обновления", throwable)
                    handleRuStoreException(throwable)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Критическая ошибка completeUpdate", e)
                viewModelScope.launch {
                    _events.emit(Event.Error("Критическая ошибка: ${e.message}"))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (isInitialized) {
                ruStoreAppUpdateManager.unregisterListener(installStateUpdateListener)
            }
            resetCounters()
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка отключения слушателя", e)
        }
    }

    private fun shouldCheckForUpdates(): Boolean {
        val now = System.currentTimeMillis()

        if (now < lastUpdateCheck) {
            lastUpdateCheck = 0
            return true
        }

        val timePassed = now - lastUpdateCheck
        return timePassed > updateCheckInterval
    }

    private fun resetCounters() {
        lastUpdateCheck = 0
        updateCheckCounter = 0
        pendingUpdateInfo = null
        Log.d(TAG, "Счётчики проверок обновлений сброшены")
    }

    private fun calculateProgress(bytesDownloaded: Long, totalBytes: Long): Int {
        return if (totalBytes > 0) {
            ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else 0
    }

    private fun handleRuStoreException(throwable: Throwable) {
        viewModelScope.launch {
            when (throwable) {
                is RuStoreNotInstalledException -> {
                    _events.emit(Event.Error("RuStore не установлен на устройстве"))
                }
                is RuStoreOutdatedException -> {
                    _events.emit(Event.Error("Обновите RuStore до последней версии"))
                }
                is RuStoreUserUnauthorizedException -> {
                    _events.emit(Event.Error("Войдите в аккаунт RuStore"))
                }
                is RuStoreInstallException -> {
                    _events.emit(Event.Error("Ошибка установки: ${throwable.message}"))
                }
                else -> {
                    _events.emit(Event.Error("Ошибка обновления: ${throwable.message}"))
                }
            }
        }
    }

    sealed class Event {
        object UpdateAvailable : Event()
        object UpdateDownloaded : Event()
        object UpdateCompleted : Event()
        object UpdateCancelled : Event()
        object UpdatePending : Event()
        object Installing : Event()
        data class DownloadProgress(val progress: Int) : Event()
        data class Error(val message: String) : Event()
    }
}