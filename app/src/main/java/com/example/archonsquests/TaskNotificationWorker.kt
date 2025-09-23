package com.example.archonsquests

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TaskNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "TaskNotificationWorker"

    override suspend fun doWork(): Result {
        return try {
            val taskText = inputData.getString(TASK_TEXT) ?: "Задача"
            val taskId = inputData.getInt(TASK_ID, 0)

            Log.d(TAG, "Показ уведомления для задачи: $taskText")

            // ✅ Проверяем, существует ли задача и не завершена
            if (!isTaskActive(taskId)) {
                Log.d(TAG, "Задача $taskId не существует или завершена — уведомление не показывается")
                return Result.success()
            }

            showNotification(taskText, taskId)
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка показа уведомления", e)
            Result.failure()
        }
    }

    private fun showNotification(taskText: String, taskId: Int) {
        createNotificationChannel()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Добавляем данные для идентификации задачи
            putExtra("notification_task_id", taskId)
            putExtra("notification_task_text", taskText)
        }

         // ✅ Создаём PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)  // Твоя иконка
            .setContentTitle("Донесение для Сёгуна!")
            .setContentText("Великий Сёгун! Кудзё Сара сообщает, что пора выполнить задачу: $taskText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // автоматически закрыть уведомление
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS or NotificationCompat.DEFAULT_SOUND)
            .setVibrate(longArrayOf(0, 150, 75, 150, 75, 200, 100, 200))  // Вибрация
            .setLights(Color.BLUE, 500, 500) // световое уведомление
            .build()

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        try {
            notificationManager.notify(taskId, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Нет разрешения на уведомления", e)
        }
    }

    private fun isTaskActive(taskHashCode: Int): Boolean {
        val sharedPreferences = applicationContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userName = sharedPreferences.getString("user_name", "default_user") ?: "default_user"
        val count = sharedPreferences.getInt("${userName}_tasks_count", 0)

        for (i in 0 until count) {
            val taskText = sharedPreferences.getString("${userName}_task_text_$i", "")
            val isCompleted = sharedPreferences.getBoolean("${userName}_task_is_completed_$i", false)
            val isSelected = sharedPreferences.getBoolean("${userName}_task_is_selected_$i", false)
            val reminder = sharedPreferences.getLong("${userName}_task_reminder_$i", 0L)

            if (taskText != null && !isCompleted) {
                // Воссоздаём временную задачу для вычисления хэша
                val tempTask = Task(
                    text = taskText,
                    isCompleted = isCompleted,
                    isSelected = isSelected,
                    reminder = if (reminder > 0L) reminder else null
                )
                if (tempTask.hashCode() == taskHashCode) {
                    return true // Задача существует и не завершена
                }
            }
        }

        return false // Задача не найдена или завершена
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Напоминания о задачах",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Напоминания о ваших задачах в Archon's Quests"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 75, 150, 75, 200, 100, 200)
            }

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }



    companion object {
        const val TASK_TEXT = "task_text"
        const val TASK_ID = "task_id"
        const val NOTIFICATION_CHANNEL_ID = "task_reminders"
    }
}