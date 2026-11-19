package com.example.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class DailyNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyNotificationScheduler.createNotificationChannel(context)

        val hour = intent.getIntExtra(DailyNotificationScheduler.EXTRA_HOUR, 8)
        val minute = intent.getIntExtra(DailyNotificationScheduler.EXTRA_MINUTE, 0)
        val requestCode = intent.getIntExtra(DailyNotificationScheduler.EXTRA_REQUEST_CODE, 800)

        // Reschedule tomorrow's exact slot silently
        DailyNotificationScheduler.scheduleNextOccurrence(context, hour, minute, requestCode)

        val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!canPost) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.gbook_camera_icon_1795066862092)
        }.getOrNull()

        val title = context.getString(R.string.notif_daily_title)
        val body = context.getString(R.string.notif_daily_body)

        val notification = NotificationCompat.Builder(context, DailyNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_camera)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(body)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }
}
