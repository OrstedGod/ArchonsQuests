package com.example.archonsquests

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.archonsquests.databinding.ActivityMainBinding
import java.util.Calendar
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import android.view.animation.Animation
import android.widget.FrameLayout
import java.text.SimpleDateFormat
import java.util.*

data class Task(
    val text: String,              // val - текст не меняется
    var isCompleted: Boolean = false,  // var - статус меняется
    var isSelected: Boolean = false,    // var - выделение меняется
    var reminder: Long? = null, // Добавлено
)

class MainActivity : AppCompatActivity() {
    private val tasks = mutableListOf<Task>() // список дел
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var binding: ActivityMainBinding
    private var permissionGranted = false
    private val oswaldBold by lazy { ResourcesCompat.getFont(this, R.font.oswald_bold) }
    private var rotationAnimations = mutableMapOf<View, Animation>()
    private lateinit var viewModel: MainViewModel

    private lateinit var feedbackButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.init(this)

        // подписка на события обновления
        observeViewModelEvents()

        // Провекрка имени пользователя
        handleUserLogin()

        animateElements()
        checkAndRequestNotificationPermission()
        createFeedbackButton()
        setupButtonListeners()
    }

    private fun handleUserLogin() {
        val userName = sharedPreferences.getString("user_name", null)
        if (userName != null) {
            binding.greetingText.text = "Привет, $userName!"
            loadTasks()
        } else {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun setupButtonListeners() {
        binding.addButton.setOnClickListener { addTask() }
        binding.deleteButton.setOnClickListener { deleteSelectedTasks() }
        binding.completeButton.setOnClickListener { completeSelectedTasks() }
        binding.logoutButton.setOnClickListener { logout() }
        binding.feedbackButton.setOnClickListener {}
    }

    private fun observeViewModelEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is MainViewModel.Event.UpdateAvailable -> {
                            showUpdateDialog(event.appUpdateInfo)
                        }

                        is MainViewModel.Event.DownloadProgress -> {
                            showDownloadProgress(event.progress)
                        }

                        is MainViewModel.Event.UpdateDownloaded -> {
                            showInstallReadyDialog()
                        }

                        is MainViewModel.Event.UpdateCompleted -> {
                            showToast("Приложение успешно обновлено!")
                        }

                        is MainViewModel.Event.Error -> {
                            showError(event.message ?: "Произошла неизвестная ошибка")
                        }

                        is MainViewModel.Event.Installing -> {
                            showToast("Установка обновления...")
                        }

                        is MainViewModel.Event.UpdateCancelled -> {
                            showToast("Обновление отменено пользователем")
                        }

                        is MainViewModel.Event.UpdatePending -> {
                            showToast("Обновление ожидает установки")
                        }

                        else -> {
                            // Добавлен лог для отладки, если появляются новые события
                            Log.w("MainActivity", "Необработанное событие: $event")
                        }
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(appUpdateInfo: ru.rustore.sdk.appupdate.model.AppUpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle("🌟 Доступно обновление!")
            .setMessage("Установите новую версию Archon's Quests?")
            .setPositiveButton("Обновить") { _, _ ->
                viewModel.startFlexibleUpdate(appUpdateInfo)
            }
            .setNegativeButton("Позже") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showInstallReadyDialog() {
        AlertDialog.Builder(this)
            .setTitle("✅ Обновление готово!")
            .setMessage("Обновление загружено и готово к установке.")
            .setPositiveButton("Установить сейчас") { _, _ ->
                viewModel.completeUpdateRequested()
            }
            .setNegativeButton("Позже") { dialog, _ ->
                dialog.dismiss()
                showToast("Обновление будет установлено при следующем запуске")
            }
            .show()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Ошибка обновления")
            .setMessage(message)
            .setPositiveButton("Понятно") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showDownloadProgress(progress: Int) {
        // Можно использовать Snackbar или ProgressDialog
        Snackbar.make(
            findViewById(android.R.id.content),
            "Загрузка обновления: $progress%",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun animateElements() {
        binding.greetingText.apply {
            alpha = 0f
            animate().alpha(1f).setDuration(700L).start()
        }

        binding.taskInput.apply {
            alpha = 0f
            animate().alpha(1f).setStartDelay(100).setDuration(600L).start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.greetingText.clearAnimation()
        binding.taskInput.clearAnimation()
        saveTasks()
        for ((view, _) in rotationAnimations) {
            view.clearAnimation()
        }
        rotationAnimations.clear()
    }

    private fun logout() {
        AlertDialog.Builder(this).create().apply {
            setTitle("Подтвердите выход")
            setMessage("Вы действительно хотите выйти из аккаунта?")
            setButton(AlertDialog.BUTTON_POSITIVE, "Да") { _, _ ->
                val editor = sharedPreferences.edit()
                editor.remove("user_name").apply()
                Toast.makeText(this@MainActivity, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            }
            setButton(AlertDialog.BUTTON_NEGATIVE, "Нет") { dialog, _ -> dialog.dismiss() }
            show()
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        RequestPermission()
    ) { granted ->
        permissionGranted = granted
        Toast.makeText(
            this,
            if (granted) "Разрешение на уведомления от Селестии получено!" else "Разрешение на уведомления отклонено Селестией",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Только для Android 13+ требуется разрешение
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                permissionGranted = true
            }
        } else {
            // Для более старых версий разрешение не требуется
            permissionGranted = true
        }
    }

    private fun cancelTaskNotification(task: Task) {
        try {
            val notificationId = task.hashCode()

            // Отменяем уведомление в статус-баре
            NotificationManagerCompat.from(this).cancel(notificationId)

            // Отменяем запланированное уведомление
            WorkManager.getInstance(this)
                .cancelAllWorkByTag("task_notification_${task.hashCode()}")

            Log.d("MainActivity", "Уведомление отменено для: ${task.text}")

        } catch (e: Exception) {
            Log.e("MainActivity", "Ошибка отмены уведомления для: ${task.text}", e)
        }
    }

    // Сохранения задач
    private fun saveTasks() {
        val editor = sharedPreferences.edit()
        val userName = sharedPreferences.getString("user_name", "default_user")
        editor.putInt("${userName}_tasks_count", tasks.size)

        for (i in tasks.indices) {
            val task = tasks[i]
            editor.putString("${userName}_task_text_$i", task.text)
            editor.putBoolean("${userName}_task_is_completed_$i", task.isCompleted)
            editor.putBoolean("${userName}_task_is_selected_$i", task.isSelected)
            editor.putLong("${userName}_task_reminder_$i", task.reminder ?: 0L)
            editor.putInt("task_id_$i", task.hashCode())
        }
        editor.apply()
    }

    private fun addTask() {
        val text = binding.taskInput.text.toString().trim()
        if (text.isEmpty()) {
            binding.taskInput.error = "Великий Сёгун, нельзя добавить пустую задачу!"
            return
        }
        if (text.length > 50) {
            binding.taskInput.error = "Великий Сёгун, у вашей задачи слишком длинное название!"
            return
        }

        val task = Task(
            text,
            isCompleted = false,
            isSelected = false,
            reminder = null
        )

        showSetReminderDialog(task) { selectedTime ->
            task.reminder = selectedTime
            tasks.add(task)
            updateTaskList()
            saveTasks()
            Toast.makeText(this, "Сёгун, ваша задача успешно запланирована!", Toast.LENGTH_SHORT)
                .show()
            scheduleTaskNotification(task, selectedTime)
        }

        // Очищаем EditText сразу после создания задачи и показа диалога
        binding.taskInput.setText("")
        binding.taskInput.error = null // Очищаем ошибку, если была
    }

    private fun formatReminderTime(reminder: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = reminder
        }
        return SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault()).format(calendar.time)
    }

    private fun showSetReminderDialog(task: Task, onTimeSelected: (Long) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Установить напоминание?")
            .setMessage("Хотите установить напоминание для задачи - ${task.text}?")
            .setPositiveButton("Да") { _, _ ->
                showDateTimePicker { selectedTime ->
                    onTimeSelected(selectedTime)
                }
            }
            .setNegativeButton("Нет") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "Сёгун, задачана добавлена без напоминания!",
                    Toast.LENGTH_SHORT
                ).show()
                // Задача добавляется без напоминания
                tasks.add(task)
                updateTaskList()
                saveTasks()
            }
            .setCancelable(false)
            .show()
    }

    private fun showDateTimePicker(onTimeSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        // Функция для повторного показа TimePicker
        fun showTimePicker(year: Int, month: Int, dayOfMonth: Int) {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, hourOfDay, minute, 0)
                }

                val selectedTime = selectedCalendar.timeInMillis

                // Проверяем время
                if (selectedTime <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Выберите время в будущем!", Toast.LENGTH_SHORT).show()
                    // ✅ Повторно показываем TimePicker
                    showTimePicker(year, month, dayOfMonth)
                    return@TimePickerDialog
                }

                // ✅ Всё ок — передаём время
                onTimeSelected(selectedTime)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        // DatePicker с ограничением на прошедшие даты
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            // Проверяем дату
            val selectedCalendar = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (selectedCalendar.timeInMillis < todayCalendar.timeInMillis) {
                Toast.makeText(this, "Нельзя выбрать прошедшую дату!", Toast.LENGTH_SHORT).show()
                return@DatePickerDialog
            }

            // Показываем TimePicker
            showTimePicker(year, month, dayOfMonth)
        }, currentYear, currentMonth, currentDay).apply {
            // Устанавливаем минимальную дату - сегодня
            datePicker.minDate = System.currentTimeMillis() - 1000 // минус 1 сек
        }.show()
    }

    private fun scheduleTaskNotification(task: Task, notificationTime: Long) {
        val currentTime = System.currentTimeMillis()
        val delayMillis = notificationTime - currentTime
        // Проверяем, что время ещё не прошло
        if (delayMillis <= 0) {
            Toast.makeText(this, "Время напоминания уже прошло!", Toast.LENGTH_SHORT).show()
            return
        }
        val data = workDataOf(
            TaskNotificationWorker.TASK_TEXT to task.text,
            TaskNotificationWorker.TASK_ID to task.hashCode()
        )
        val workRequest = OneTimeWorkRequestBuilder<TaskNotificationWorker>()
            .setInputData(data)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag("task_notification_${task.hashCode()}")
            .build()
        WorkManager.getInstance(this)
            .enqueue(workRequest)
    }

    private fun updateTaskList() {
        binding.tContainer.removeAllViews()
        for ((_, task) in tasks.withIndex()) {
            val taskLayout = createTaskView(task)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.task_margin)
            }
            binding.tContainer.addView(taskLayout, layoutParams)
        }
    }

    private fun createTaskView(task: Task): LinearLayout {
        val taskLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = resources.getDrawable(R.drawable.task_background, theme)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(20, 10, 20, 10)
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(69, 69)
            setOnClickListener {
                task.isSelected = !task.isSelected
                when {
                    task.isCompleted -> {
                        setImageResource(R.drawable.ic_electro_active)
                        this.alpha = 0.25f
                        this.isEnabled = false
                    }

                    task.isSelected -> {
                        setImageResource(R.drawable.ic_electro_active)
                    }

                    else -> {
                        setImageResource(R.drawable.ic_electro_off)
                    }
                }
            }
        }

        updateIconState(icon, task)
        taskLayout.addView(icon)

        // Создаём контейнер для текста и времени
        val textAndTimeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // занимает всё оставшееся пространство
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Основной текст задачи
        val textView = TextView(this).apply {
            setTextColor(Color.parseColor("#FFFFFF"))
            text = task.text
            textSize = 22f
            typeface = oswaldBold
            setPadding(20, 0, 16, 0)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END

            if (task.isCompleted) {
                paint.isStrikeThruText = true
                alpha = 0.25f
            }
        }
        textAndTimeContainer.addView(textView)

        // Время напоминания — немного правее текста
        val reminderTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 16f
            typeface = oswaldBold
            gravity = android.view.Gravity.START
            setPadding(10, 0, 0, 0) // небольшой отступ слева
            if (task.isCompleted) {
                paint.isStrikeThruText = true
                alpha = 0.25f
            }
        }

        if (task.reminder != null) {
            reminderTextView.text = "• ${formatReminderTime(task.reminder!!)}"
            reminderTextView.visibility = View.VISIBLE
        } else {
            reminderTextView.visibility = View.GONE
        }

        textAndTimeContainer.addView(reminderTextView)
        taskLayout.addView(textAndTimeContainer)

        return taskLayout
    }

    private fun startRotationAnimation(view: View) {
        val rotation = AnimationUtils.loadAnimation(this, R.anim.slow_rotation)
        rotation.repeatCount = Animation.INFINITE
        view.startAnimation(rotation)
        rotationAnimations[view] = rotation
    }

    private fun updateIconState(icon: ImageView, task: Task) {
        when {
            task.isCompleted -> {
                // Завершённая задача
                icon.setImageResource(R.drawable.ic_electro_active)
                icon.alpha = 0.25f
                icon.isEnabled = false
                icon.clearAnimation()  // Останавливаем анимацию
            }

            task.isSelected -> {
                // Выбранная задача
                icon.setImageResource(R.drawable.ic_electro_active)
                icon.alpha = 1.0f
                icon.isEnabled = true
                startRotationAnimation(icon)
            }

            else -> {
                // Обычная задача
                icon.setImageResource(R.drawable.ic_electro_off)
                icon.alpha = 1.0f
                icon.isEnabled = true
                startRotationAnimation(icon)
            }
        }
    }

    private fun loadTasks() {
        val userName = sharedPreferences.getString("user_name", "default_user")
        val count = sharedPreferences.getInt("${userName}_tasks_count", 0)
        tasks.clear()
        for (i in 0 until count) {
            val text = sharedPreferences.getString("${userName}_task_text_$i", "")
            val isCompleted =
                sharedPreferences.getBoolean("${userName}_task_is_completed_$i", false)
            val isSelected = sharedPreferences.getBoolean("${userName}_task_is_selected_$i", false)
            val reminder = sharedPreferences.getLong("${userName}_task_reminder_$i", 0L)

            if (text != null) {
                val task = Task(text, isCompleted, isSelected).apply {
                    this.reminder = if (reminder > 0L) reminder else null
                }
                tasks.add(task)
            }
        }
        updateTaskList()
    }

    private fun deleteSelectedTasks() {
        val selectedTasks = tasks.filter { it.isSelected || it.isCompleted }
        if (selectedTasks.isNotEmpty()) {
            for (task in selectedTasks) {
                // ✅ Отменяем уведомления
                WorkManager.getInstance(this)
                    .cancelAllWorkByTag("task_notification_${task.hashCode()}")
            }
            tasks.removeAll(selectedTasks)
            updateTaskList()
            saveTasks()
        } else {
            Toast.makeText(this, "Нет выбранных задач для удаления!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun completeSelectedTasks() {
        val selectedTasks = tasks.filter { it.isSelected }
        if (selectedTasks.isEmpty()) {
            Toast.makeText(this, "Нет выбранных задач для завершения!", Toast.LENGTH_SHORT).show()
            return
        }
        for (task in selectedTasks) {
            task.isCompleted = true
            task.isSelected = false
            cancelTaskNotification(task)
            val notificationId = task.hashCode()
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.cancel(notificationId)
        }
        updateTaskList()
        saveTasks()
        Toast.makeText(this, "Задачи завершены!", Toast.LENGTH_SHORT).show()
    }

    private fun createFeedbackButton() {
        feedbackButton = ImageView(this).apply {

            val size = 56.dpToPx()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(0, 0, 16.dpToPx(), 16.dpToPx())
            }

            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Обратная связь"

            setOnClickListener {
                showFeedbackDialog()
            }
        }

        val rootView = findViewById<View>(android.R.id.content) as ViewGroup
        rootView.addView(feedbackButton)
    }

    private fun showFeedbackDialog() {
        AlertDialog.Builder(this)
            .setTitle("Обратная связь")
            .setMessage("Напиши нам на почту!")
            .setPositiveButton("Написать") { _, _ ->
                openGoogleForms()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openGoogleForms() {
        val formUrl = "https://docs.google.com/forms/d/e/your-form-id/viewform" +
                "?usp=pp_url&entry.7024429=${getAppVersion()}" +  // Замени 123456789 на ID поля
                "&entry.941118847=${Build.MODEL}"       // Замени 987654321 на ID поля
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formUrl))
        startActivity(intent)
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "Неизвестная"
        } catch (e: Exception) {
            "Неизвестная"
        }
    }
}