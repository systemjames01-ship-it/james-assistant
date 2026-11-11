package com.james.assistant

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Lembrete do Jâmes"
        val id = intent.getIntExtra("id", 1)
        val notification = Notification.Builder(context, "james_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Jâmes")
            .setContentText(title)
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
