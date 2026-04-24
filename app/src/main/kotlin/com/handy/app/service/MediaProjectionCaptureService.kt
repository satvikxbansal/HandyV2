package com.handy.app.service

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.handy.app.HandyApplication
import com.handy.app.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * API 26–29 MediaProjection fallback for [ScreenCapturePipeline]
 * (OS-3 tier 3).
 *
 * Phase 3 wires the foreground-service shell so the manifest is
 * accurate and the API 34+ / API 30–33 paths compile; a live
 * `MediaProjection` session is threaded through in Phase 4 when the
 * full voice + capture round-trip comes together.
 *
 * MediaProjection lifecycle discipline (OS-3):
 *  - Register a `MediaProjection.Callback`.
 *  - On `onStop()`, release the `VirtualDisplay` + backing `Surface`
 *    synchronously and clear capture state.
 */
@AndroidEntryPoint
class MediaProjectionCaptureService : LifecycleService() {

    private var projection: MediaProjection? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Timber.d("MediaProjection onStop — releasing")
            release()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Timber.e(t, "MediaProjectionCaptureService startForeground failed")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != -1 && resultData != null) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // Kotlin 2.2+ treats `getMediaProjection` as returning
            // `MediaProjection?` (platform type narrowed). `.apply` now
            // requires a safe call on nullable receivers.
            projection = manager.getMediaProjection(resultCode, resultData)?.apply {
                registerCallback(projectionCallback, null)
            }
        } else {
            Timber.w("MediaProjectionCaptureService started without result data — stopping")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, HandyApplication.CHANNEL_CAPTURE)
            .setContentTitle(getString(R.string.capture_service_notification_title))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID: Int = 1002
        const val EXTRA_RESULT_CODE: String = "handy.mp.result_code"
        const val EXTRA_RESULT_DATA: String = "handy.mp.result_data"
    }
}
