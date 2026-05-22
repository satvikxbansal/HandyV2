@file:Suppress("DEPRECATION")

package com.handy.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType
import com.handy.core.screen.IntRect
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manual fallback for sticky pointing. The overlay only instructs and
 * highlights; it never intercepts the target tap.
 */
@Singleton
class ManualTargetSelector(
    private val appPackageName: String,
    private val callbacks: Callbacks,
    private val overlayController: OverlayController,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val capturePulseMs: Long = CAPTURE_PULSE_MS,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        presenter: OverlayPresenter,
        flightDriver: BuddyFlightDriver,
    ) : this(
        appPackageName = context.packageName,
        callbacks = HandyCallbacks(presenter, flightDriver),
        overlayController = WindowOverlayController(context.applicationContext),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        clock = { System.currentTimeMillis() },
        capturePulseMs = CAPTURE_PULSE_MS,
    )

    enum class Trigger { Chip, WidgetLongPress }

    data class UiState(
        val active: Boolean = false,
        val trigger: Trigger? = null,
        val startedAtEpochMs: Long = 0L,
        val capturedBounds: IntRect? = null,
        val captured: Boolean = false,
    )

    interface Callbacks {
        fun onSelectionStarted(trigger: Trigger)
        suspend fun onTargetCaptured(
            node: AccessibilityNodeInfo,
            sourcePackage: String?,
            selectedAtEpochMs: Long,
        ): Boolean
        fun onSelectionFinished(success: Boolean)
        fun onSelectionCancelled(reason: String)
    }

    interface OverlayController {
        fun show(state: StateFlow<UiState>)
        fun hide()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val isActive: Boolean
        get() = _state.value.active

    fun begin(trigger: Trigger): Boolean {
        val current = _state.value
        if (current.active) return false
        val next = UiState(
            active = true,
            trigger = trigger,
            startedAtEpochMs = clock(),
        )
        _state.value = next
        callbacks.onSelectionStarted(trigger)
        overlayController.show(state)
        Timber.d("ManualTargetSelector: started trigger=%s", trigger)
        return true
    }

    fun cancel(reason: String) {
        val current = _state.value
        if (!current.active) return
        _state.value = UiState()
        overlayController.hide()
        callbacks.onSelectionCancelled(reason)
        Timber.d("ManualTargetSelector: cancelled reason=%s", reason)
    }

    fun handleAccessibilityEvent(event: AccessibilityEvent): Boolean {
        if (!_state.value.active) return false
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        val sourcePackage = event.packageName?.toString()
        if (shouldSkipSourcePackage(sourcePackage)) return true
        val source = runCatching { event.source }.getOrNull() ?: return false
        val ownedNode = runCatching { AccessibilityNodeInfo.obtain(source) }
            .onSuccess { runCatching { source.recycle() } }
            .getOrElse { source }
        return captureNode(ownedNode, sourcePackage)
    }

    fun captureNodeForTest(
        node: AccessibilityNodeInfo,
        sourcePackage: String?,
    ): Boolean = captureNode(node, sourcePackage)

    private fun captureNode(
        node: AccessibilityNodeInfo,
        sourcePackage: String?,
    ): Boolean {
        val current = _state.value
        if (!current.active || current.captured) {
            runCatching { node.recycle() }
            return current.active
        }
        if (shouldSkipSourcePackage(sourcePackage)) {
            runCatching { node.recycle() }
            return true
        }
        val bounds = node.boundsAsIntRect()
        if (bounds.width <= 0 || bounds.height <= 0) {
            runCatching { node.recycle() }
            return false
        }
        val capturedAtEpochMs = clock()
        _state.value = current.copy(
            capturedBounds = bounds,
            captured = true,
        )
        scope.launch {
            var success = false
            try {
                delay(capturePulseMs)
                _state.value = UiState()
                overlayController.hide()
                success = callbacks.onTargetCaptured(
                    node = node,
                    sourcePackage = sourcePackage,
                    selectedAtEpochMs = capturedAtEpochMs,
                )
            } catch (t: Throwable) {
                runCatching { node.recycle() }
                Timber.w(t, "ManualTargetSelector: target callback failed")
            } finally {
                callbacks.onSelectionFinished(success)
            }
        }
        Timber.d(
            "ManualTargetSelector: captured pkg=%s bounds=%d,%d-%d,%d",
            sourcePackage,
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
        )
        return true
    }

    private class HandyCallbacks(
        private val presenter: OverlayPresenter,
        private val flightDriver: BuddyFlightDriver,
    ) : Callbacks {
        override fun onSelectionStarted(trigger: Trigger) {
            presenter.onManualTargetSelectionStarted()
        }

        override suspend fun onTargetCaptured(
            node: AccessibilityNodeInfo,
            sourcePackage: String?,
            selectedAtEpochMs: Long,
        ): Boolean =
            flightDriver.resumeWithManualTarget(
                node = node,
                sourcePackage = sourcePackage,
                selectedAtEpochMs = selectedAtEpochMs,
            )

        override fun onSelectionFinished(success: Boolean) {
            if (!success) presenter.onPointingReturned()
        }

        override fun onSelectionCancelled(reason: String) {
            presenter.onPointingReturned()
        }
    }

    private class WindowOverlayController(context: Context) : OverlayController {
        private val appContext = context.applicationContext
        private val windowManager =
            appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        private var host: OverlayComposeHost? = null
        private var view: android.view.View? = null

        override fun show(state: StateFlow<UiState>) {
            if (view != null) return
            val overlayHost = OverlayComposeHost(appContext).also { host = it }
            val overlayView = overlayHost.createView {
                val uiState by state.collectAsState()
                ManualTargetSelectionOverlay(uiState)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            runCatching { windowManager.addView(overlayView, params) }
                .onSuccess { view = overlayView }
                .onFailure {
                    Timber.e(it, "Manual target overlay attach failed")
                    overlayHost.release()
                    host = null
                }
        }

        override fun hide() {
            val overlayView = view
            val overlayHost = host
            view = null
            host = null
            if (overlayView != null) runCatching { windowManager.removeView(overlayView) }
            overlayHost?.release()
        }
    }

    private fun AccessibilityNodeInfo.boundsAsIntRect(): IntRect {
        val rect = Rect().also { getBoundsInScreen(it) }
        return IntRect(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun shouldSkipSourcePackage(sourcePackage: String?): Boolean {
        val normalizedPackage = sourcePackage?.takeIf { it.isNotBlank() } ?: return false
        return normalizedPackage.equals(appPackageName, ignoreCase = true) ||
            SKIPPED_SOURCE_PACKAGES.any { normalizedPackage.equals(it, ignoreCase = true) } ||
            SKIPPED_SOURCE_PACKAGE_PREFIXES.any {
                normalizedPackage.startsWith(it, ignoreCase = true)
            }
    }

    private companion object {
        const val CAPTURE_PULSE_MS: Long = 520L
        val SKIPPED_SOURCE_PACKAGES: Set<String> = setOf(
            "com.android.systemui",
            "android",
            "com.android.launcher3",
        )
        val SKIPPED_SOURCE_PACKAGE_PREFIXES: Set<String> = setOf(
            "com.google.android.inputmethod",
            "com.android.inputmethod",
        )
    }
}

@Composable
private fun ManualTargetSelectionOverlay(state: ManualTargetSelector.UiState) {
    if (!state.active) return
    HandyTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .background(HandyColors.GlassTint, RoundedCornerShape(8.dp))
                    .border(0.5.dp, HandyColors.GlassBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Tap the right one",
                    color = HandyColors.TextPrimary,
                    style = HandyType.Caption,
                    fontWeight = FontWeight.Medium,
                )
            }
            state.capturedBounds?.let { bounds ->
                ManualTargetPulse(bounds)
            }
        }
    }
}

@Composable
private fun ManualTargetPulse(bounds: IntRect) {
    val transition = rememberInfiniteTransition(label = "manual-target-pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "manual-target-pulse-phase",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(bounds.centerX.toFloat(), bounds.centerY.toFloat())
        drawCircle(
            color = HandyColors.Accent.copy(alpha = (1f - phase).coerceIn(0f, 1f)),
            radius = 18.dp.toPx() + 28.dp.toPx() * phase,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = HandyColors.TextPrimary.copy(alpha = 0.70f),
            radius = 8.dp.toPx(),
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}
