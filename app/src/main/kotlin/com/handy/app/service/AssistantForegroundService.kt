package com.handy.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.chat.ChatActivity
import com.handy.app.overlay.FloatingWidgetOverlayService
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.LifecycleService
import timber.log.Timber

/**
 * Handy's always-on foreground anchor (guardrails OS-1).
 *
 * Responsibilities:
 *  - Keep the app alive in the background so the overlay widget stays
 *    attached to `WindowManager` across app switches.
 *  - Own the microphone while voice capture is active (hence
 *    `foregroundServiceType = specialUse | microphone` in the manifest).
 *  - Start [FloatingWidgetOverlayService] when foregrounded.
 *
 * Notification channel is created at `HandyApplication.onCreate`
 * (OS-1: never lazily).
 */
@AndroidEntryPoint
class AssistantForegroundService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Timber.e(t, "AssistantForegroundService startForeground failed")
            stopSelf()
            return START_NOT_STICKY
        }

        // Launch the widget overlay iff the user has granted SAW.
        // The overlay service guards itself on Settings.canDrawOverlays.
        val widgetIntent = Intent(this, FloatingWidgetOverlayService::class.java)
        ContextCompat.startForegroundService(this, widgetIntent)

        return START_STICKY
    }

    override fun onDestroy() {
        stopService(Intent(this, FloatingWidgetOverlayService::class.java))
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, HandyApplication.CHANNEL_ASSISTANT)
            .setContentTitle(getString(R.string.assistant_service_notification_title))
            .setContentText(getString(R.string.assistant_service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID: Int = 1001

        fun start(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantForegroundService::class.java))
        }
    }
}
