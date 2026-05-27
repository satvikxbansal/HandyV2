@file:Suppress("DEPRECATION")

package com.handy.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.design.HandyDesignType
import com.handy.core.screen.IntRect
import com.handy.core.screen.intersects
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

    data class Candidate(
        val bounds: IntRect,
        val label: String?,
        val confidence: Float,
        val markId: String? = null,
    ) {
        val isRanked: Boolean get() = confidence > 0f
    }

    data class UiState(
        val active: Boolean = false,
        val trigger: Trigger? = null,
        val startedAtEpochMs: Long = 0L,
        val candidates: List<Candidate> = emptyList(),
        val capturedBounds: IntRect? = null,
        val capturedLabel: String? = null,
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
        fun show(state: StateFlow<UiState>, onCancel: (String) -> Unit)
        fun hide()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val isActive: Boolean
        get() = _state.value.active

    fun begin(
        trigger: Trigger,
        candidates: List<Candidate> = emptyList(),
    ): Boolean {
        val current = _state.value
        if (current.active) return false
        val next = UiState(
            active = true,
            trigger = trigger,
            startedAtEpochMs = clock(),
            candidates = candidates,
        )
        _state.value = next
        callbacks.onSelectionStarted(trigger)
        overlayController.show(state) { reason -> cancel(reason) }
        Timber.d(
            "ManualTargetSelector: started trigger=%s candidates=%d",
            trigger,
            candidates.size,
        )
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
        val matchedLabel = current.candidates
            .firstOrNull { it.bounds.intersects(bounds) }
            ?.label
        _state.value = current.copy(
            capturedBounds = bounds,
            capturedLabel = matchedLabel,
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
        private var cancelHost: OverlayComposeHost? = null
        private var cancelView: android.view.View? = null

        override fun show(state: StateFlow<UiState>, onCancel: (String) -> Unit) {
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
            if (view == null) return

            val cancelOverlayHost = OverlayComposeHost(appContext).also { cancelHost = it }
            val cancelOverlayView = cancelOverlayHost.createView {
                val uiState by state.collectAsState()
                if (uiState.active) {
                    ManualCancelBar(onCancel = { onCancel("user_dismissed") })
                }
            }
            val cancelParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = 0
                y = 0
            }
            runCatching { windowManager.addView(cancelOverlayView, cancelParams) }
                .onSuccess { cancelView = cancelOverlayView }
                .onFailure {
                    Timber.e(it, "Manual target cancel overlay attach failed")
                    cancelOverlayHost.release()
                    cancelHost = null
                }
        }

        override fun hide() {
            val overlayView = view
            val overlayHost = host
            val cancelOverlayView = cancelView
            val cancelOverlayHost = cancelHost
            view = null
            host = null
            cancelView = null
            cancelHost = null
            if (overlayView != null) runCatching { windowManager.removeView(overlayView) }
            overlayHost?.release()
            if (cancelOverlayView != null) {
                runCatching { windowManager.removeView(cancelOverlayView) }
            }
            cancelOverlayHost?.release()
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
    HandyDesignTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            )
            TargetHighlights(state)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 18.dp, end = 18.dp),
            ) {
                CoachCardWithHalo(state)
            }
        }
    }
}

@Composable
private fun CoachCardWithHalo(state: ManualTargetSelector.UiState) {
    val toneColor = if (state.captured) {
        HandyDesign.Colors.Accent
    } else {
        HandyDesign.Colors.Point
    }
    Box(modifier = Modifier.wrapContentSize()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(radius = 12.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    toneColor.copy(alpha = 0.40f),
                                    Color.Transparent,
                                ),
                            ),
                            topLeft = Offset(-8.dp.toPx(), -4.dp.toPx()),
                            size = Size(
                                width = size.width + 16.dp.toPx(),
                                height = size.height + 16.dp.toPx(),
                            ),
                            cornerRadius = CornerRadius(26.dp.toPx()),
                        )
                    },
            )
        } else {
            SoftHaloRect(toneColor = toneColor, bleed = 12.dp, alpha = 0.10f, radius = 30.dp)
            SoftHaloRect(toneColor = toneColor, bleed = 10.dp, alpha = 0.18f, radius = 28.dp)
            SoftHaloRect(toneColor = toneColor, bleed = 8.dp, alpha = 0.26f, radius = 26.dp)
        }
        CoachCard(state, toneColor)
    }
}

@Composable
private fun BoxScope.SoftHaloRect(
    toneColor: Color,
    bleed: Dp,
    alpha: Float,
    radius: Dp,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawBehind {
                val bleedPx = bleed.toPx()
                drawRoundRect(
                    color = toneColor.copy(alpha = alpha),
                    topLeft = Offset(-bleedPx, -bleedPx),
                    size = Size(size.width + bleedPx * 2, size.height + bleedPx * 2),
                    cornerRadius = CornerRadius(radius.toPx()),
                )
            },
    )
}

@Composable
private fun CoachCard(state: ManualTargetSelector.UiState, toneColor: Color) {
    val candidateCount = state.candidates.count { it.hasDrawableBounds }
    val title = when {
        state.captured -> "Got it — running…"
        candidateCount == 0 -> "Couldn't find a match"
        else -> "Tap the one you mean"
    }
    val subtitle = when {
        state.captured ->
            "Confirming \"${state.capturedLabel ?: "target"}\" tap"
        candidateCount == 0 ->
            "Try saying the button name out loud."
        candidateCount == 1 ->
            "1 possible match. Tap it if it's the one you wanted."
        else -> "$candidateCount of these matched. Pick the one you wanted."
    }
    val counter = if (state.captured || candidateCount == 0) {
        null
    } else {
        "$candidateCount ${if (candidateCount == 1) "match" else "matches"}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xEB121418))
            .border(0.5.dp, toneColor.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(toneColor.copy(alpha = 0.20f))
                .border(0.5.dp, toneColor.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_hand_pointing_bold),
                contentDescription = null,
                tint = toneColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HandyDesignType.Title.copy(fontSize = 14.sp, lineHeight = 17.sp),
                color = HandyDesign.Colors.TextPrimary,
            )
            Text(
                text = subtitle,
                style = HandyDesignType.Caption,
                color = HandyDesign.Colors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (counter != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(toneColor.copy(alpha = 0.20f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(
                    text = counter,
                    style = HandyDesignType.Overline.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.10.em,
                    ),
                    color = toneColor,
                )
            }
        }
    }
}

@Composable
private fun TargetHighlights(state: ManualTargetSelector.UiState) {
    if (state.candidates.isEmpty() && state.capturedBounds == null) return

    val drawableCandidates = state.candidates.filter { it.hasDrawableBounds }
    val visibleCandidates = if (!state.captured) {
        if (drawableCandidates.size > 5) {
            drawableCandidates.sortedByDescending { it.confidence }.take(3)
        } else {
            drawableCandidates.sortedByDescending { it.confidence }
        }
    } else {
        state.capturedBounds?.let { capturedRect ->
            drawableCandidates.filter { it.bounds.intersects(capturedRect) }
        } ?: emptyList()
    }

    visibleCandidates.forEachIndexed { index, candidate ->
        val isCaptured = state.captured &&
            state.capturedBounds?.intersects(candidate.bounds) == true
        CandidateRect(
            candidate = candidate,
            rank = index,
            isCaptured = isCaptured,
        )
    }

    if (state.captured) {
        state.capturedBounds?.let { bounds -> ManualTargetPulse(bounds) }
    }
}

private val ManualTargetSelector.Candidate.hasDrawableBounds: Boolean
    get() = bounds.width > 0 && bounds.height > 0

@Composable
private fun CandidateRect(
    candidate: ManualTargetSelector.Candidate,
    rank: Int,
    isCaptured: Boolean,
) {
    val density = LocalDensity.current
    val xDp = with(density) { candidate.bounds.left.toDp() }
    val yDp = with(density) { candidate.bounds.top.toDp() }
    val wDp = with(density) { (candidate.bounds.right - candidate.bounds.left).toDp() }
    val hDp = with(density) { (candidate.bounds.bottom - candidate.bounds.top).toDp() }
    val toneColor = if (isCaptured) {
        HandyDesign.Colors.Accent
    } else {
        HandyDesign.Colors.Point
    }

    Box(
        modifier = Modifier
            .absoluteOffset(x = xDp, y = yDp)
            .size(width = wDp, height = hDp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val px = 4.dp.toPx()
                    drawRoundRect(
                        color = toneColor.copy(alpha = 0.20f),
                        topLeft = Offset(-px, -px),
                        size = Size(size.width + px * 2, size.height + px * 2),
                        cornerRadius = CornerRadius(14.dp.toPx() + px),
                    )
                }
                .border(2.dp, toneColor, RoundedCornerShape(14.dp)),
        )
        val chipLabel = when {
            isCaptured -> "RUNNING"
            rank == 0 -> "BEST GUESS"
            else -> "MAYBE"
        }
        Box(
            modifier = Modifier
                .absoluteOffset(y = hDp + 6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(toneColor)
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Text(
                text = chipLabel,
                style = HandyDesignType.Overline.copy(
                    fontSize = 9.sp,
                    letterSpacing = 0.10.em,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isCaptured) {
                    HandyDesign.Colors.AccentInk
                } else {
                    Color.White
                },
            )
        }
    }
}

@Composable
private fun ManualCancelBar(onCancel: () -> Unit) {
    HandyDesignTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 36.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xEB121418))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Or tap anywhere outside to dismiss",
                    style = HandyDesignType.Caption.copy(fontSize = 12.sp),
                    color = HandyDesign.Colors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HandyDesign.Colors.SurfaceElevated)
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Cancel",
                        style = HandyDesignType.Body.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = HandyDesign.Colors.TextPrimary,
                    )
                }
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
            color = HandyDesign.Colors.Accent.copy(
                alpha = (1f - phase).coerceIn(0f, 1f),
            ),
            radius = 18.dp.toPx() + 28.dp.toPx() * phase,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
