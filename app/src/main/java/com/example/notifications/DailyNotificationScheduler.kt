package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object DailyNotificationScheduler {
    const val CHANNEL_ID = "gbook_daily_reminders"
    const val EXTRA_HOUR = "extra_hour"
    const val EXTRA_MINUTE = "extra_minute"
    const val EXTRA_REQUEST_CODE = "extra_request_code"

    private const val PREFS_NAME = "gbook_prefs"
    private const val KEY_NOTIF_ENABLED = "notif_scheduled_enabled"

    data class DailySlot(val hour: Int, val minute: Int, val requestCode: Int)

    val DAILY_SLOTS = listOf(
        DailySlot(hour = 8, minute = 0, requestCode = 800),
        DailySlot(hour = 13, minute = 0, requestCode = 1300),
        DailySlot(hour = 18, minute = 0, requestCode = 1800)
    )

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "G-Book Camera Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily inspiration to shoot & enhance iPhone-grade photos and videos."
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun markEnabledAndScheduleAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIF_ENABLED, true)
            .apply()
        scheduleAllDailyNotifications(context)
    }

    fun isSchedulingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF_ENABLED, false)
    }

    fun scheduleAllDailyNotifications(context: Context) {
        createNotificationChannel(context)
        DAILY_SLOTS.forEach { slot ->
            scheduleNextOccurrence(context, slot.hour, slot.minute, slot.requestCode)
        }
    }

    fun scheduleNextOccurrence(context: Context, hour: Int, minute: Int, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis + 1000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.timeInMillis,
                pendingIntent
            )
        }
    }
}
