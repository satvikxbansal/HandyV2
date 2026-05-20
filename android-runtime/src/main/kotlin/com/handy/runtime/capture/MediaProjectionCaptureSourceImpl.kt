@file:Suppress("DEPRECATION")

package com.handy.runtime.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * One-shot MediaProjection frame source for API 26-29.
 *
 * The app-owned foreground service supplies the live [MediaProjection]
 * after user consent. This class owns the transient ImageReader and
 * VirtualDisplay for each frame and tears both down synchronously after
 * success, timeout, cancellation, or projection stop.
 */
@Singleton
class MediaProjectionCaptureSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaProjectionCaptureSource {

    private val projectionLock = Any()
    private val captureMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var releasingProjection: Boolean = false

    override val isReady: Boolean
        get() = projection != null

    fun setProjection(mediaProjection: MediaProjection) {
        clearProjection(stopProjection = false)
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Timber.d("MediaProjectionCaptureSource: projection stopped")
                clearProjection(stopProjection = false)
            }
        }
        synchronized(projectionLock) {
            projection = mediaProjection
            projectionCallback = callback
        }
        mediaProjection.registerCallback(callback, mainHandler)
    }

    override suspend fun captureFrame(activeWindowIdHint: Int?): Bitmap? {
        val currentProjection = projection ?: return null
        return captureMutex.withLock {
            withContext(Dispatchers.Default) {
                runCatching {
                    withTimeoutOrNull(FRAME_TIMEOUT_MS) {
                        awaitFrame(currentProjection, activeWindowIdHint)
                    }
                }.onFailure { t ->
                    Timber.w(t, "MediaProjectionCaptureSource: capture failed")
                }.getOrNull()
            }
        }
    }

    override fun release() {
        clearProjection(stopProjection = true)
    }

    private suspend fun awaitFrame(
        mediaProjection: MediaProjection,
        activeWindowIdHint: Int?,
    ): Bitmap? = suspendCancellableCoroutine { cont ->
        val spec = captureSpec()
        val imageReader = ImageReader.newInstance(
            spec.width,
            spec.height,
            PixelFormat.RGBA_8888,
            MAX_IMAGES,
        )
        val handlerThread = HandlerThread("HandyMediaProjectionFrame").apply { start() }
        val handler = Handler(handlerThread.looper)
        var virtualDisplay: VirtualDisplay? = null
        val completed = AtomicBoolean(false)
        val cleaned = AtomicBoolean(false)

        fun cleanup() {
            if (!cleaned.compareAndSet(false, true)) return
            runCatching { imageReader.setOnImageAvailableListener(null, null) }
            runCatching { virtualDisplay?.release() }
            runCatching { imageReader.close() }
            runCatching { handlerThread.quitSafely() }
        }

        cont.invokeOnCancellation { cleanup() }

        imageReader.setOnImageAvailableListener(
            { reader ->
                if (!completed.compareAndSet(false, true)) return@setOnImageAvailableListener
                val bitmap = runCatching {
                    reader.acquireLatestImage()?.use(::imageToBitmap)
                }.onFailure { t ->
                    Timber.w(t, "MediaProjectionCaptureSource: image conversion failed")
                }.getOrNull()
                cleanup()
                if (cont.isActive) cont.resume(bitmap)
            },
            handler,
        )

        virtualDisplay = runCatching {
            mediaProjection.createVirtualDisplay(
                "HandyCapture-${activeWindowIdHint ?: "display"}",
                spec.width,
                spec.height,
                spec.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler,
            )
        }.onFailure { t ->
            Timber.w(t, "MediaProjectionCaptureSource: createVirtualDisplay failed")
            if (completed.compareAndSet(false, true)) {
                cleanup()
                if (cont.isActive) cont.resume(null)
            }
        }.getOrNull()
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride.coerceAtLeast(1)
        val rowStride = plane.rowStride
        val paddedWidth = (rowStride / pixelStride).coerceAtLeast(image.width)
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        if (paddedWidth == image.width) return padded

        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun captureSpec(): CaptureSpec {
        val resources = context.resources
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager?.currentWindowMetrics?.bounds
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                return CaptureSpec(
                    width = bounds.width(),
                    height = bounds.height(),
                    densityDpi = resources.displayMetrics.densityDpi,
                )
            }
        }

        if (windowManager != null) {
            runCatching { windowManager.defaultDisplay.getRealMetrics(metrics) }
        }
        val fallback = if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            metrics
        } else {
            resources.displayMetrics
        }
        return CaptureSpec(
            width = fallback.widthPixels.coerceAtLeast(1),
            height = fallback.heightPixels.coerceAtLeast(1),
            densityDpi = fallback.densityDpi.takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT,
        )
    }

    private fun clearProjection(stopProjection: Boolean) {
        val currentProjection: MediaProjection?
        val currentCallback: MediaProjection.Callback?
        synchronized(projectionLock) {
            if (releasingProjection) return
            releasingProjection = true
            currentProjection = projection
            currentCallback = projectionCallback
            projection = null
            projectionCallback = null
        }
        runCatching {
            if (currentProjection != null && currentCallback != null) {
                currentProjection.unregisterCallback(currentCallback)
            }
        }
        if (stopProjection) {
            runCatching { currentProjection?.stop() }
        }
        synchronized(projectionLock) {
            releasingProjection = false
        }
    }

    private data class CaptureSpec(
        val width: Int,
        val height: Int,
        val densityDpi: Int,
    )

    private companion object {
        const val FRAME_TIMEOUT_MS = 1_000L
        const val MAX_IMAGES = 2
    }
}
