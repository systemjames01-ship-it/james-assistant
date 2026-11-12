package com.james.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps an explicitly started Jâmes voice/vision session in foreground-service
 * state while the app is temporarily backgrounded. The Activity owns the actual
 * Gemini/audio/camera objects; this service declares the ongoing user-visible
 * microphone/camera use and its persistent notification.
 */
class JamesSessionService : Service() {
    companion object {
        private const val CHANNEL_ID = "james_live_session"
        private const val NOTIFICATION_ID = 731
        private const val ACTION_START = "com.james.assistant.session.START"
        private const val ACTION_CAMERA = "com.james.assistant.session.CAMERA"
        private const val ACTION_STOP = "com.james.assistant.session.STOP"
        private const val EXTRA_CAMERA = "camera_enabled"

        fun start(context: Context) {
            val intent = Intent(context, JamesSessionService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun setCameraEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, JamesSessionService::class.java)
                .setAction(ACTION_CAMERA)
                .putExtra(EXTRA_CAMERA, enabled)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, JamesSessionService::class.java).setAction(ACTION_STOP))
        }
    }

    private var cameraEnabled = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        updateForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAMERA -> cameraEnabled = intent.getBooleanExtra(EXTRA_CAMERA, false)
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> cameraEnabled = false
        }
        updateForeground()
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sessão ao vivo do Jâmes",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indica quando o Jâmes está usando microfone ou câmera após ativação."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            731,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (cameraEnabled) {
            "Microfone e câmera ativos. Toque para abrir os controles."
        } else {
            "Microfone ativo. Toque para abrir os controles."
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jâmes está ativo")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    private fun updateForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (cameraEnabled) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
