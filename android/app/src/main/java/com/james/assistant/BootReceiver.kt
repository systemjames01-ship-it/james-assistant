package com.james.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("james_reminders", Context.MODE_PRIVATE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        for ((key, value) in prefs.all) {
            val parts = (value as? String)?.split("|", limit = 2) ?: continue
            val title = parts.getOrNull(0) ?: continue
            val at = parts.getOrNull(1)?.toLongOrNull() ?: continue
            val id = key.toIntOrNull() ?: continue
            if (at <= now) { prefs.edit().remove(key).apply(); continue }
            val reminderIntent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("title", title)
                putExtra("id", id)
            }
            val pending = PendingIntent.getBroadcast(context, id, reminderIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                if (Build.VERSION.SDK_INT >= 23) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                else alarm.setExact(AlarmManager.RTC_WAKEUP, at, pending)
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }
}
