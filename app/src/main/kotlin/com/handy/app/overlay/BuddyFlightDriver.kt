@file:Suppress("DEPRECATION")

package com.handy.app.overlay

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.handy.app.widget.BezierFlightController
import com.handy.app.widget.LensRenderer
import com.handy.core.action.ActionExecutionGate
import com.handy.core.action.ActionPerformer
import com.handy.core.action.TapTarget
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.BuddyState
import com.handy.core.overlay.OverlayMode
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.privacy.ScreenRedactor
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolutionFailureReason
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolvedPointTarget
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.storage.DataStoreSettings
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Singleton orchestrator for Bezier flights. The orchestrator fires a
 * flight request; this driver finds the target via
 * [SemanticPointerResolver] and then drives
 * [FloatingWidgetOverlayService.moveBuddyTo] through the
 * [BezierFlightController]. The presenter is notified at every stage
 * so the bubbles + lens chrome update.
 *
 * Scope §3. Cursorbuddy recipe #1 renders; recipes #3 (node-first
 * tap) is Phase 3.
 */
@Singleton
class BuddyFlightDriver @Inject constructor(
    private val presenter: OverlayPresenter,
    private val pointerResolver: SemanticPointerResolver,
    private val actionPerformer: ActionPerformer,
    private val settings: DataStoreSettings,
    private val auditStore: AuditStore,
) {

    private var serviceRef: WeakReference<FloatingWidgetOverlayService>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var safetyTimeoutRunnable: Runnable? = null

    fun attachService(service: FloatingWidgetOverlayService) {
        serviceRef = WeakReference(service)
    }

    fun detachService(service: FloatingWidgetOverlayService) {
        if (serviceRef?.get() === service) {
            clearStickySafetyTimeout()
            serviceRef = null
        }
    }

    fun isReadyForFlight(): Boolean =
        serviceRef?.get()?.isWidgetReadyForFlight() == true

    /**
     * Resolve [spec] against the live accessibility tree; if that fails,
     * use cached pre-panel marks. Shows the blue navigation bubble with
     * [label] while pointing (scope §3).
     *
     * No-ops when:
     *  - no service attached,
     *  - the resolver returns null,
     *  - a flight is already in progress.
     */
    suspend fun flyTo(
        spec: AssistantMarkupParser.SemanticPoint,
        label: String?,
        fallbackMarks: List<AccessibilityMark> = emptyList(),
    ): Boolean {
        val flight = resolveForFlight(
            spec = spec,
            label = label,
            fallbackMarks = fallbackMarks,
            expectedPackage = null,
            expectedWindowId = null,
        ) ?: return false
        val resolved = flight.resolved
        return try {
            withContext(Dispatchers.Main.immediate) {
                flyToBounds(flight.service, resolved.bounds, label, resolved)
            }
        } finally {
            resolved.node?.let { node -> runCatching { node.recycle() } }
        }
    }

    suspend fun flyToPoint(x: Int, y: Int, bubbleLabel: String?): Boolean {
        val service = serviceRef?.get() ?: run {
            Timber.d("BuddyFlightDriver.flyToPoint: no service attached")
            return false
        }
        val state = presenter.state.value
        if (state.buddyState == BuddyState.FLYING) {
            Timber.d(
                "BuddyFlightDriver.flyToPoint: flight already in progress buddy=%s isFlying=%s",
                state.buddyState,
                state.isFlying,
            )
            return false
        }
        Timber.d("BuddyFlightDriver.flyToPoint: x=%d y=%d label=\"%s\"", x, y, bubbleLabel?.logSnippet())
        return withContext(Dispatchers.Main.immediate) {
            flyToBounds(
                service = service,
                bounds = IntRect(x - POINT_TARGET_RADIUS, y - POINT_TARGET_RADIUS, x + POINT_TARGET_RADIUS, y + POINT_TARGET_RADIUS),
                label = bubbleLabel,
            )
        }
    }

    suspend fun resumeWithManualTarget(
        node: AccessibilityNodeInfo,
        sourcePackage: String?,
        selectedAtEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val target = ManualTargetSnapshot.fromNode(node, sourcePackage)
        var auditResult: AuditResult = AuditResult.Failed("manual-fallback-not-run")
        val landed = runCatching {
            val service = serviceRef?.get() ?: run {
                Timber.d("BuddyFlightDriver.resumeWithManualTarget: no service attached")
                auditResult = AuditResult.NotPermitted
                return@runCatching false
            }
            if (target.bounds.width <= 0 || target.bounds.height <= 0) {
                auditResult = AuditResult.NotFound
                return@runCatching false
            }
            withContext(Dispatchers.Main.immediate) {
                clearStickySafetyTimeout()
                service.flightControllerInstance().cancelAll()
                flyToBounds(
                    service = service,
                    bounds = target.bounds,
                    label = target.label ?: "right one",
                )
            }.also { success ->
                auditResult = if (success) {
                    AuditResult.Dispatched(component = "manual-fallback")
                } else {
                    AuditResult.Cancelled
                }
            }
        }.onFailure { t ->
            auditResult = AuditResult.Failed(t.message ?: "manual-fallback-failed")
            Timber.w(t, "BuddyFlightDriver.resumeWithManualTarget failed")
        }.getOrDefault(false)
        runCatching { node.recycle() }
        auditManualSelection(
            target = target,
            selectedAtEpochMs = selectedAtEpochMs,
            result = auditResult,
        )
        return landed
    }

    private suspend fun flyToBounds(
        service: FloatingWidgetOverlayService,
        bounds: IntRect,
        label: String?,
        resolved: ResolvedPointTarget? = null,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val (widgetW, widgetH) = service.widgetSize().takeIf { it.first > 0 && it.second > 0 }
            ?: run {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
        val (fromX, fromY) = service.currentWindowPosition()
        val (dockX, dockY) = service.currentDockPosition()
        val landing = chooseLandingPosition(service, bounds, widgetW, widgetH)
        val arrivalAngle = angleFromWidgetToTarget(landing, widgetW, widgetH, bounds)
        val startedAtMs = SystemClock.uptimeMillis()
        Timber.d(
            "BuddyFlightDriver.flyToBounds: markId=%s confidence=%.2f source=%s from=%d,%d target=%d,%d widget=%dx%d kind=%s angle=%.2f blendStart=%.2f dock=%d,%d bounds=%s label=\"%s\"",
            resolved?.markId,
            resolved?.confidence ?: 0f,
            resolved?.source,
            fromX,
            fromY,
            landing.x,
            landing.y,
            widgetW,
            widgetH,
            landing.kind,
            arrivalAngle,
            POINTER_ROTATION_BLEND_START,
            dockX,
            dockY,
            bounds.logSummary(),
            label?.logSnippet(),
        )

        service.updateBubblePlacementHint(landing.kind)
        presenter.onFlyingStart(label = label)

        service.flightControllerInstance().flyThere(
            fromX = fromX.toFloat(),
            fromY = fromY.toFloat(),
            toX = landing.x.toFloat(),
            toY = landing.y.toFloat(),
            dockX = dockX.toFloat(),
            dockY = dockY.toFloat(),
            returnToDock = false,
            callback = object : BezierFlightController.Callback {
                override fun onFlightTick(
                    x: Float,
                    y: Float,
                    tangentRadians: Float,
                    scale: Float,
                    progress: Float,
                ) {
                    service.moveBuddyTo(x.toInt(), y.toInt())
                    service.updatePointerPose(
                        tangentRadians = blendPointerAngle(
                            tangentRadians = tangentRadians,
                            arrivalAngle = arrivalAngle,
                            progress = progress,
                        ),
                        scale = scale,
                    )
                }

                override fun onArrived() {
                    Timber.d(
                        "BuddyFlightDriver.flyToBounds: arrived durationMs=%d finalWidget=%d,%d-%d,%d label=\"%s\"",
                        SystemClock.uptimeMillis() - startedAtMs,
                        landing.x,
                        landing.y,
                        landing.x + widgetW,
                        landing.y + widgetH,
                        label?.logSnippet(),
                    )
                    service.updatePointerPose(
                        tangentRadians = arrivalAngle,
                        scale = 1.0f,
                    )
                    presenter.onPointingArrived(label)
                    scheduleStickySafetyTimeout()
                    if (cont.isActive) cont.resume(true)
                }

                // Tap-for-me stays fail-closed in this batch; arrival
                // only owns sticky pointing.

                override fun onPulse(scale: Float) {
                    service.updatePointerPose(scale = scale)
                }

                override fun onReturned() {
                    Timber.d("BuddyFlightDriver.flyToBounds: returned to dock")
                    clearStickySafetyTimeout()
                    service.resetPointerPose()
                    presenter.onPointingReturned()
                }

                override fun onFlightCancelled() {
                    Timber.d(
                        "BuddyFlightDriver.flyToBounds: flight cancelled reason=controller_cancel durationMs=%d",
                        SystemClock.uptimeMillis() - startedAtMs,
                    )
                    clearStickySafetyTimeout()
                    service.resetPointerPose()
                    presenter.onPointingReturned()
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
        cont.invokeOnCancellation {
            service.flightControllerInstance().cancelAll()
        }
    }
    /**
     * Cross-cutting: fly to [spec] and, only after the future action
     * disclosure gate is accepted, perform a tap on the resolved node.
     *
     * Returns true when the buddy actually tapped. Scope §4 / recipe #3.
     */
    suspend fun flyToAndTap(
        spec: AssistantMarkupParser.SemanticPoint,
        bubbleLabel: String?,
        targetLabel: String?,
        fallbackMarks: List<AccessibilityMark> = emptyList(),
        groundingSnapshot: GroundingSnapshot? = null,
    ): Boolean {
        val enabled = runCatching { ActionExecutionGate.gesturesAllowed(settings.current()) }
            .getOrDefault(false)
        val flight = resolveForFlight(
            spec = spec,
            label = bubbleLabel,
            fallbackMarks = fallbackMarks,
            expectedPackage = groundingSnapshot?.toolContext?.packageName,
            expectedWindowId = groundingSnapshot?.windowId,
        ) ?: return false
        val resolved = flight.resolved
        val landed = try {
            withContext(Dispatchers.Main.immediate) {
                flyToBounds(flight.service, resolved.bounds, bubbleLabel, resolved)
            }
        } finally {
            resolved.node?.let { node -> runCatching { node.recycle() } }
        }
        if (!landed) return false
        if (!enabled) return false
        // Short pause so the user sees the buddy land before it taps.
        kotlinx.coroutines.delay(250L)
        val displayLabel = targetLabel?.take(30) ?: "here"
        presenter.onActionStarted("tapping $displayLabel")
        val result = runCatching {
            actionPerformer.tap(
                buildTapTargetForResolved(spec, resolved, groundingSnapshot),
            )
        }.onFailure { Timber.w(it, "BuddyFlightDriver tap failed") }.getOrNull()
        presenter.onActionFinished()
        return result is com.handy.core.action.PerformResult.Ok
    }

    private suspend fun resolveForFlight(
        spec: AssistantMarkupParser.SemanticPoint,
        label: String?,
        fallbackMarks: List<AccessibilityMark>,
        expectedPackage: String?,
        expectedWindowId: Int?,
    ): FlightResolution? {
        val service = serviceRef?.get() ?: run {
            Timber.d("BuddyFlightDriver.flyTo: no service attached")
            return null
        }
        val state = presenter.state.value
        if (state.buddyState == BuddyState.FLYING) {
            Timber.d(
                "BuddyFlightDriver.flyTo: flight already in progress buddy=%s isFlying=%s",
                state.buddyState,
                state.isFlying,
            )
            return null
        }

        Timber.d(
            "BuddyFlightDriver.flyTo: spec=%s fallbackMarks=%d expectedPackage=%s expectedWindowId=%s",
            spec.logSummary(),
            fallbackMarks.size,
            expectedPackage,
            expectedWindowId,
        )
        presenter.onPreparingPoint(label)
        val resolved = withContext(Dispatchers.Main.immediate) {
            runCatching {
                pointerResolver.resolve(
                    spec = spec,
                    fallbackMarks = fallbackMarks,
                    expectedPackage = expectedPackage,
                    expectedWindowId = expectedWindowId,
                )
            }.getOrNull()
        }
        if (resolved == null) {
            Timber.d("BuddyFlightDriver.flyTo: resolver returned null")
            presenter.onManualTargetFallbackAvailable(label)
            scheduleStickySafetyTimeout()
            return null
        }
        Timber.d(
            "BuddyFlightDriver.flyTo: resolved markId=%s source=%s confidence=%.2f failure=%s candidates=%d bounds=%s",
            resolved.markId,
            resolved.source,
            resolved.confidence,
            resolved.failureReason,
            resolved.candidateCount,
            resolved.bounds.logSummary(),
        )
        if (resolved.failureReason == ResolutionFailureReason.AMBIGUOUS ||
            resolved.confidence < MIN_FLY_CONFIDENCE
        ) {
            Timber.d(
                "BuddyFlightDriver.flyTo: refusing low/ambiguous pointer confidence=%.2f failure=%s",
                resolved.confidence,
                resolved.failureReason,
            )
            resolved.node?.let { node -> runCatching { node.recycle() } }
            presenter.onManualTargetFallbackAvailable(label)
            scheduleStickySafetyTimeout()
            return null
        }
        return FlightResolution(service = service, resolved = resolved)
    }

    private data class FlightResolution(
        val service: FloatingWidgetOverlayService,
        val resolved: ResolvedPointTarget,
    )

    /** Cancel any in-flight animation (e.g. user tapped widget mid-flight). */
    fun cancel() {
        mainHandler.post {
            clearStickySafetyTimeout()
            val service = serviceRef?.get()
            service?.flightControllerInstance()?.cancelAll()
            service?.moveBuddyToDock()
            service?.resetPointerPose()
            presenter.onPointingReturned()
        }
    }

    /**
     * The sticky pointer is intentionally persistent after arrival, but
     * should clear once the user acts on the guided app.
     */
    fun dismissPointingAfterUserInteraction(source: String?): Boolean {
        val state = presenter.state.value
        if (state.mode == OverlayMode.ManualTargetSelection) return false
        if (state.buddyState != BuddyState.POINTING) return false
        Timber.d("BuddyFlightDriver: dismissing sticky pointer after user interaction source=%s", source)
        mainHandler.post {
            clearStickySafetyTimeout()
            val service = serviceRef?.get()
            service?.flightControllerInstance()?.cancelAll()
            service?.moveBuddyToDock()
            service?.resetPointerPose()
            presenter.onPointingReturned()
        }
        return true
    }

    private fun scheduleStickySafetyTimeout() {
        clearStickySafetyTimeout()
        safetyTimeoutRunnable = Runnable {
            Timber.d("BuddyFlightDriver: sticky pointer safety timeout")
            dismissPointingAfterUserInteraction("safety_timeout")
        }.also { mainHandler.postDelayed(it, STICKY_POINTER_TIMEOUT_MS) }
    }

    private fun clearStickySafetyTimeout() {
        safetyTimeoutRunnable?.let(mainHandler::removeCallbacks)
        safetyTimeoutRunnable = null
    }

    private data class LandingPosition(
        val x: Int,
        val y: Int,
        val kind: String,
    )

    private data class LandingCandidate(
        val kind: String,
        val x: Int,
        val y: Int,
        val priority: Int,
    )

    private fun chooseLandingPosition(
        service: FloatingWidgetOverlayService,
        bounds: IntRect,
        widgetW: Int,
        widgetH: Int,
    ): LandingPosition {
        val density = service.resources.displayMetrics.density
        // Keep the buddy visibly close to the target while still avoiding
        // overlap with the tappable bounds.
        val gap = (8f * density).toInt()
        val avoidMargin = (6f * density).toInt()
        val edgeBand = (96f * density).toInt()
        val screenW = service.resources.displayMetrics.widthPixels
        val screenH = service.resources.displayMetrics.heightPixels
        val safeTop = service.systemBarSize("status_bar_height")
        val safeBottom = screenH - service.systemBarSize("navigation_bar_height")
        val minX = 0
        val minY = safeTop.coerceAtLeast(0)
        val maxX = (screenW - widgetW).coerceAtLeast(0)
        val maxY = (safeBottom - widgetH).coerceAtLeast(minY)

        fun candidate(kind: String, x: Int, y: Int, priority: Int): LandingCandidate =
            LandingCandidate(
                kind = kind,
                x = x.coerceIn(minX, maxX),
                y = y.coerceIn(minY, maxY),
                priority = priority,
            )

        fun centeredY(): Int = bounds.centerY - widgetH / 2
        fun centeredX(): Int = bounds.centerX - widgetW / 2

        val avoidBounds = bounds.expand(avoidMargin, screenW, safeBottom)
        val nearBottom = bounds.centerY >= safeBottom - edgeBand
        val nearTop = bounds.centerY <= safeTop + edgeBand
        val nearLeft = bounds.centerX <= edgeBand
        val nearRight = bounds.centerX >= screenW - edgeBand
        val preferredBand = when {
            nearBottom -> "bottom"
            nearTop -> "top"
            nearLeft -> "left"
            nearRight -> "right"
            else -> "middle"
        }

        val candidates = buildList {
            if (nearBottom) {
                add(candidate("bottom-above-center", centeredX(), bounds.top - widgetH - gap, priority = 0))
                add(candidate("bottom-above-left", bounds.left, bounds.top - widgetH - gap, priority = 3))
                add(candidate("bottom-above-right", bounds.right - widgetW, bounds.top - widgetH - gap, priority = 3))
            }
            if (nearTop) {
                add(candidate("top-below-center", centeredX(), bounds.bottom + gap, priority = 0))
                add(candidate("top-below-left", bounds.left, bounds.bottom + gap, priority = 3))
                add(candidate("top-below-right", bounds.right - widgetW, bounds.bottom + gap, priority = 3))
            }
            if (nearLeft) {
                add(candidate("left-side-right", bounds.right + gap, centeredY(), priority = 1))
            }
            if (nearRight) {
                add(candidate("right-side-left", bounds.left - widgetW - gap, centeredY(), priority = 1))
            }
            add(candidate("side-right", bounds.right + gap, centeredY(), priority = 6))
            add(candidate("side-left", bounds.left - widgetW - gap, centeredY(), priority = 6))
            add(candidate("below-center", centeredX(), bounds.bottom + gap, priority = 8))
            add(candidate("above-center", centeredX(), bounds.top - widgetH - gap, priority = 8))
            add(candidate("corner-bottom-right", bounds.right + gap, bounds.bottom + gap, priority = 12))
            add(candidate("corner-bottom-left", bounds.left - widgetW - gap, bounds.bottom + gap, priority = 12))
            add(candidate("corner-top-right", bounds.right + gap, bounds.top - widgetH - gap, priority = 12))
            add(candidate("corner-top-left", bounds.left - widgetW - gap, bounds.top - widgetH - gap, priority = 12))
        }
        val chosen = candidates.minBy { candidate ->
            candidate.score(
                avoidBounds = avoidBounds,
                target = bounds,
                widgetW = widgetW,
                widgetH = widgetH,
                preferredBand = preferredBand,
            )
        }
        Timber.d(
            "BuddyFlightDriver.chooseLandingPosition: preferred=%s chosen=%s pos=%d,%d target=%s avoid=%s",
            preferredBand,
            chosen.kind,
            chosen.x,
            chosen.y,
            bounds.logSummary(),
            avoidBounds.logSummary(),
        )
        return LandingPosition(chosen.x, chosen.y, chosen.kind)
    }

    private fun LandingCandidate.score(
        avoidBounds: IntRect,
        target: IntRect,
        widgetW: Int,
        widgetH: Int,
        preferredBand: String,
    ): Int {
        val overlapPenalty = if (overlaps(avoidBounds, widgetW, widgetH)) 1_000_000 else 0
        val centerX = x + widgetW / 2
        val centerY = y + widgetH / 2
        val horizontalAffinity = abs(centerX - target.centerX)
        val verticalAffinity = abs(centerY - target.centerY)
        val bandPenalty = when (preferredBand) {
            "bottom" -> if (kind.startsWith("bottom-above")) 0 else 30_000
            "top" -> if (kind.startsWith("top-below")) 0 else 30_000
            "left" -> if (kind == "left-side-right") 0 else 15_000
            "right" -> if (kind == "right-side-left") 0 else 15_000
            else -> 0
        }
        val driftPenalty = when (preferredBand) {
            "bottom", "top" -> horizontalAffinity * 12 + verticalAffinity
            "left", "right" -> verticalAffinity * 6 + horizontalAffinity
            else -> horizontalAffinity + verticalAffinity
        }
        return overlapPenalty + bandPenalty + priority * 1_000 + driftPenalty
    }

    private fun LandingCandidate.overlaps(bounds: IntRect, widgetW: Int, widgetH: Int): Boolean {
        val right = x + widgetW
        val bottom = y + widgetH
        return x < bounds.right &&
            right > bounds.left &&
            y < bounds.bottom &&
            bottom > bounds.top
    }

    private fun IntRect.expand(margin: Int, screenW: Int, screenH: Int): IntRect =
        IntRect(
            left = (left - margin).coerceAtLeast(0),
            top = (top - margin).coerceAtLeast(0),
            right = (right + margin).coerceAtMost(screenW),
            bottom = (bottom + margin).coerceAtMost(screenH),
        )

    private fun angleFromWidgetToTarget(
        landing: LandingPosition,
        widgetW: Int,
        widgetH: Int,
        bounds: IntRect,
    ): Float {
        val pointerCenterX = landing.x + widgetW / 2f
        val pointerCenterY = landing.y + widgetH / 2f
        return atan2(
            bounds.centerY.toFloat() - pointerCenterY,
            bounds.centerX.toFloat() - pointerCenterX,
        )
    }

    private fun blendPointerAngle(
        tangentRadians: Float,
        arrivalAngle: Float,
        progress: Float,
    ): Float {
        if (progress <= POINTER_ROTATION_BLEND_START) return tangentRadians
        val t = ((progress - POINTER_ROTATION_BLEND_START) / (1f - POINTER_ROTATION_BLEND_START))
            .coerceIn(0f, 1f)
        return lerpAngleShortest(tangentRadians, arrivalAngle, t * t * (3f - 2f * t))
    }

    private fun lerpAngleShortest(start: Float, end: Float, t: Float): Float {
        val delta = ((end - start + PI.toFloat()) % TWO_PI + TWO_PI) % TWO_PI - PI.toFloat()
        return start + delta * t
    }

    private fun AssistantMarkupParser.SemanticPoint.logSummary(): String =
        "markId=$markId role=$role text=${text?.logSnippet()} viewId=$viewId desc=${contentDescription?.logSnippet()}"

    private fun IntRect.logSummary(): String =
        "$left,$top-$right,$bottom"

    private fun String.logSnippet(max: Int = 80): String =
        replace('\n', ' ').take(max)

    private suspend fun auditManualSelection(
        target: ManualTargetSnapshot,
        selectedAtEpochMs: Long,
        result: AuditResult,
    ) {
        val event = AuditEvent(
            timestampEpochMs = selectedAtEpochMs,
            requestId = java.util.UUID.randomUUID().toString(),
            provider = "manual-fallback",
            action = AuditAction.ManualSelect,
            targetApp = target.packageName ?: "unknown",
            semanticTarget = target.auditDescription(),
            confirmationRequired = false,
            userConfirmed = true,
            result = result,
            failureReason = (result as? AuditResult.Failed)?.reason,
        )
        runCatching { auditStore.append(event) }
            .onFailure { Timber.w(it, "AuditStore manual selection append failed") }
    }

    private companion object {
        const val POINT_TARGET_RADIUS: Int = 20
        const val MIN_FLY_CONFIDENCE: Float = 0.68f
        const val STICKY_POINTER_TIMEOUT_MS: Long = 30_000L
        const val POINTER_ROTATION_BLEND_START: Float = 0.78f
        const val TWO_PI: Float = (Math.PI * 2.0).toFloat()
    }
}

private data class ManualTargetSnapshot(
    val bounds: IntRect,
    val packageName: String?,
    val role: String?,
    val text: String?,
    val desc: String?,
    val viewId: String?,
) {
    val label: String?
        get() = text ?: desc ?: viewId?.substringAfterLast('/')

    fun auditDescription(): String = buildString {
        append("manual-fallback;")
        appendPart("role", role)
        appendPart("text", text)
        appendPart("desc", desc)
        appendPart("viewId", viewId?.substringAfterLast('/'))
        append("bounds=")
        append(bounds.left).append(',').append(bounds.top)
            .append('-').append(bounds.right).append(',').append(bounds.bottom)
    }.trimEnd(';')

    private fun StringBuilder.appendPart(name: String, value: String?) {
        value?.takeIf { it.isNotBlank() }?.let {
            append(name).append('=').append(it).append(';')
        }
    }

    companion object {
        fun fromNode(node: AccessibilityNodeInfo, sourcePackage: String?): ManualTargetSnapshot {
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val role = node.className?.toString()?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
            val viewId = node.viewIdResourceName?.takeIf { it.isNotBlank() }
            val context = listOfNotNull(role, viewId, node.contentDescription?.toString())
                .joinToString(" ")
            val isPassword = node.isPassword
            val text = ScreenRedactor.redactText(
                value = node.text?.toString(),
                context = context,
                isPassword = isPassword,
                diagnostics = true,
            )
            val desc = ScreenRedactor.redactText(
                value = node.contentDescription?.toString(),
                context = context,
                isPassword = isPassword,
                diagnostics = true,
            )
            return ManualTargetSnapshot(
                bounds = IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                packageName = sourcePackage
                    ?: node.packageName?.toString()?.takeIf { it.isNotBlank() },
                role = ScreenRedactor.redactText(
                    value = role,
                    context = context,
                    diagnostics = true,
                ),
                text = text,
                desc = desc,
                viewId = ScreenRedactor.redactText(
                    value = viewId,
                    context = context,
                    diagnostics = true,
                ),
            )
        }
    }
}

internal fun buildTapTargetForResolved(
    spec: AssistantMarkupParser.SemanticPoint,
    resolved: ResolvedPointTarget,
    groundingSnapshot: GroundingSnapshot?,
): TapTarget.AtNode =
    TapTarget.AtNode(
        markId = resolved.markId?.takeIf { it.isNotBlank() }
            ?: spec.markId?.takeIf { it.isNotBlank() },
        role = resolved.role?.takeIf { it.isNotBlank() }
            ?: spec.role?.takeIf { it.isNotBlank() },
        text = resolved.text?.takeIf { it.isNotBlank() }
            ?: spec.text?.takeIf { it.isNotBlank() },
        viewId = resolved.viewId?.takeIf { it.isNotBlank() }
            ?: spec.viewId?.takeIf { it.isNotBlank() },
        desc = resolved.desc?.takeIf { it.isNotBlank() }
            ?: spec.contentDescription?.takeIf { it.isNotBlank() },
        expectedPackage = groundingSnapshot?.toolContext?.packageName?.takeIf { it.isNotBlank() },
        expectedWindowId = groundingSnapshot?.windowId,
        snapshotHash = groundingSnapshot?.rootBoundsHash?.takeIf { it.isNotBlank() },
    )

private fun android.content.Context.systemBarSize(name: String): Int {
    val id = resources.getIdentifier(name, "dimen", "android")
    return if (id > 0) runCatching { resources.getDimensionPixelSize(id) }.getOrDefault(0) else 0
}

// Keep the LensRenderer import live for the commented-out direct path.
@Suppress("unused")
private typealias _LensRendererMarker = LensRenderer
