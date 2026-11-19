package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            DailyNotificationScheduler.isSchedulingEnabled(context)
        ) {
            DailyNotificationScheduler.scheduleAllDailyNotifications(context)
        }
    }
}
