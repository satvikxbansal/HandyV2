@file:Suppress("DEPRECATION")

package com.handy.runtime.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import com.handy.core.model.ImagePart
import com.handy.core.screen.CaptureResult
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

/**
 * Three-tier capture pipeline (OS-3).
 *
 *  1. API 34+ → `AccessibilityService.takeScreenshotOfWindow(activeWindowId, …)`.
 *  2. API 30–33 → `AccessibilityService.takeScreenshot(Display.DEFAULT_DISPLAY, …)`.
 *  3. API 26–29 → [MediaProjectionCaptureSource] fallback (owned by the
 *     app's `MediaProjectionCaptureService`).
 *
 * Every path returns a [CaptureResult] — no raw `Bitmap` crosses the
 * orchestrator boundary (OS-5). Secure / unusable buffers are detected
 * by:
 *  - official failure codes from the Accessibility APIs, and
 *  - a cheap "essentially all-black" sampler on the MediaProjection
 *    path (which has no failure code for secure surfaces).
 */
class ScreenCapturePipeline(
    private val accessibilityService: () -> AccessibilityService?,
    private val mediaProjectionSource: MediaProjectionCaptureSource? = null,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val jpegQuality: Int = 80,
) {

    /** Entry point used by the orchestrator. */
    suspend fun capture(activeWindowIdHint: Int? = null): CaptureResult {
        return when {
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> takeByWindow(activeWindowIdHint)
            sdkInt >= Build.VERSION_CODES.R -> takeByAccessibility(activeWindowIdHint)
            else -> takeByMediaProjection(activeWindowIdHint)
        }
    }

    // ---------------- API 34+ (U / 14) ----------------

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun takeByWindow(activeWindowIdHint: Int?): CaptureResult {
        val svc = accessibilityService() ?: return CaptureResult.NotPermitted
        val windowId = activeWindowIdHint ?: run {
            val root = runCatching { svc.rootInActiveWindow }.getOrNull()
                ?: return CaptureResult.Failed("no active window")
            try {
                root.windowId
            } finally {
                runCatching { root.recycle() }
            }
        }
        return suspendCancellableCoroutine { cont ->
            svc.takeScreenshotOfWindow(
                windowId,
                svc.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        if (cont.isActive) {
                            cont.resume(bitmapToResult(hardwareBufferToBitmap(result)))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) {
                            cont.resume(classifyAccessibilityFailure(errorCode))
                        }
                    }
                },
            )
        }
    }

    // ---------------- API 30-33 ----------------

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeByAccessibility(activeWindowIdHint: Int?): CaptureResult {
        val svc = accessibilityService() ?: return CaptureResult.NotPermitted
        val displayId = displayIdForWindow(svc, activeWindowIdHint) ?: Display.DEFAULT_DISPLAY
        return suspendCancellableCoroutine { cont ->
            svc.takeScreenshot(
                displayId,
                svc.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        if (cont.isActive) {
                            cont.resume(bitmapToResult(hardwareBufferToBitmap(result)))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) {
                            cont.resume(classifyAccessibilityFailure(errorCode))
                        }
                    }
                },
            )
        }
    }

    // ---------------- API 26-29 (MediaProjection fallback) ----------------

    private suspend fun takeByMediaProjection(activeWindowIdHint: Int?): CaptureResult {
        val source = mediaProjectionSource ?: return CaptureResult.Unsupported
        if (!source.isReady) return CaptureResult.Unsupported
        val bitmap = source.captureFrame(activeWindowIdHint)
            ?: return CaptureResult.Failed("MediaProjection frame timeout")
        if (BlackFrameDetector.isEssentiallyBlack(bitmap)) {
            bitmap.recycle()
            Timber.d("Capture: MediaProjection frame is essentially black → SecureWindow")
            return CaptureResult.SecureWindow
        }
        return bitmapToResult(bitmap, precomputedBitmap = bitmap)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun displayIdForWindow(
        svc: AccessibilityService,
        activeWindowIdHint: Int?,
    ): Int? {
        val hinted = activeWindowIdHint?.let { targetId ->
            runCatching {
                svc.windows?.firstOrNull { window -> window.id == targetId }?.displayId
            }.getOrNull()
        }
        if (hinted != null) return hinted

        val root = runCatching { svc.rootInActiveWindow }.getOrNull() ?: return null
        return try {
            runCatching { root.window?.displayId }.getOrNull()
        } finally {
            runCatching { root.recycle() }
        }
    }

    // ---------------- classification helpers ----------------

    private fun classifyAccessibilityFailure(errorCode: Int): CaptureResult {
        // ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS — service connection
        // dropped. ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR — typically a
        // secure / restricted surface. ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY
        // — bad display id.
        return when (errorCode) {
            AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> CaptureResult.NotPermitted
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> CaptureResult.SecureWindow
            else -> CaptureResult.Failed("screenshot error $errorCode")
        }
    }

    private fun hardwareBufferToBitmap(result: ScreenshotResult): Bitmap? {
        return try {
            val colorSpace = result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
            val buffer: HardwareBuffer = result.hardwareBuffer
            try {
                Bitmap.wrapHardwareBuffer(buffer, colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                buffer.close()
            }
        } catch (t: Throwable) {
            Timber.w(t, "hardwareBufferToBitmap failed")
            null
        }
    }

    private fun bitmapToResult(
        bitmap: Bitmap?,
        precomputedBitmap: Bitmap? = null,
    ): CaptureResult {
        val src = bitmap ?: precomputedBitmap ?: return CaptureResult.Failed("null bitmap")
        if (BlackFrameDetector.isEssentiallyBlack(src)) {
            src.recycle()
            return CaptureResult.SecureWindow
        }
        val jpeg = ByteArrayOutputStream(64 * 1024).use { out ->
            src.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            out.toByteArray()
        }
        val image = ImagePart(
            jpegBytes = jpeg,
            label = "primary focus (image dimensions: ${src.width}x${src.height} pixels)",
            widthPx = src.width,
            heightPx = src.height,
        )
        src.recycle()
        return CaptureResult.Image(image)
    }
}

/**
 * MediaProjection capture surface — Phase 3 wires this to the
 * `MediaProjectionCaptureService` in `:app` and feeds frames here. The
 * interface stays in `:android-runtime` so `:core` sees none of it.
 *
 * Implementations MUST register a `MediaProjection.Callback`, release the
 * `VirtualDisplay` in `onStop()`, and clear capture state so no further
 * frames are produced (OS-3 MediaProjection lifecycle discipline).
 */
interface MediaProjectionCaptureSource {
    val isReady: Boolean
        get() = true

    suspend fun captureFrame(activeWindowIdHint: Int? = null): Bitmap?
    fun release()
}

internal object BlackFrameDetector {
    /**
     * True when >99% of 16-pixel-stride samples are within 3 of
     * `RGB(0,0,0)` (OS-5 heuristic, ported from the guardrails spec).
     */
    fun isEssentiallyBlack(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return false
        val step = 16
        var total = 0
        var black = 0
        var x = 0
        while (x < w) {
            var y = 0
            while (y < h) {
                val px = bitmap.getPixel(x, y)
                total++
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (r <= 3 && g <= 3 && b <= 3) black++
                y += step
            }
            x += step
        }
        if (total == 0) return false
        return black.toDouble() / total >= 0.99
    }
}
