package com.handy.app.overlay

import android.os.Handler
import android.os.Looper
import com.handy.app.widget.BezierFlightController
import com.handy.app.widget.LensRenderer
import com.handy.core.action.ActionPerformer
import com.handy.core.action.TapTarget
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.BuddyState
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.IntRect
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.storage.DataStoreSettings
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
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
) {

    private var serviceRef: WeakReference<FloatingWidgetOverlayService>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun attachService(service: FloatingWidgetOverlayService) {
        serviceRef = WeakReference(service)
    }

    fun detachService(service: FloatingWidgetOverlayService) {
        if (serviceRef?.get() === service) serviceRef = null
    }

    fun isReadyForFlight(): Boolean =
        serviceRef?.get()?.isWidgetReadyForFlight() == true

    /**
     * Resolve [spec] against the live accessibility tree; if that fails,
     * use cached pre-panel marks. Shows the blue navigation bubble with
     * [label] during dwell (scope §3).
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
        val service = serviceRef?.get() ?: run {
            Timber.d("BuddyFlightDriver.flyTo: no service attached")
            return false
        }
        val state = presenter.state.value
        if (state.buddyState == BuddyState.FLYING) {
            Timber.d(
                "BuddyFlightDriver.flyTo: flight already in progress buddy=%s isFlying=%s",
                state.buddyState,
                state.isFlying,
            )
            return false
        }

        Timber.d(
            "BuddyFlightDriver.flyTo: spec=%s fallbackMarks=%d",
            spec.logSummary(),
            fallbackMarks.size,
        )
        val liveResolved = withContext(Dispatchers.Main.immediate) {
            runCatching { pointerResolver.resolve(spec) }.getOrNull()
        }
        val target = if (liveResolved != null) {
            Timber.d("BuddyFlightDriver.flyTo: live resolver hit bounds=%s", liveResolved.bounds.logSummary())
            FlightTarget(bounds = liveResolved.bounds, node = liveResolved.node)
        } else {
            fallbackMarks.resolveCached(spec)?.let { bounds ->
                Timber.d("BuddyFlightDriver.flyTo: cached resolver hit bounds=%s", bounds.logSummary())
                FlightTarget(bounds = bounds, node = null)
            }
        }
        if (target == null) {
            Timber.d("BuddyFlightDriver.flyTo: resolver returned null")
            return false
        }

        return withContext(Dispatchers.Main.immediate) {
            flyToBounds(service, target.bounds, label)
        }.also {
            target.node?.let { node -> runCatching { node.recycle() } }
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

    private suspend fun flyToBounds(
        service: FloatingWidgetOverlayService,
        bounds: IntRect,
        label: String?,
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
        Timber.d(
            "BuddyFlightDriver.flyToBounds: from=%d,%d target=%d,%d kind=%s angle=%.2f blendStart=%.2f dock=%d,%d bounds=%s label=\"%s\"",
            fromX,
            fromY,
            landing.x,
            landing.y,
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
                    Timber.d("BuddyFlightDriver.flyToBounds: arrived label=\"%s\"", label?.logSnippet())
                    service.updatePointerPose(
                        tangentRadians = arrivalAngle,
                        scale = 1.0f,
                    )
                    presenter.onPointingArrived(label)
                    if (cont.isActive) cont.resume(true)
                }

                // tap-for-me escalation is handled by the pipeline via
                // [tapAt]; keeping the arrival callback pure lets the
                // dwell + return flight run regardless of whether we
                // tapped or not.

                override fun onPulse(scale: Float) {
                    service.updatePointerPose(scale = scale)
                }

                override fun onReturned() {
                    Timber.d("BuddyFlightDriver.flyToBounds: returned to dock")
                    service.resetPointerPose()
                    presenter.onPointingReturned()
                }

                override fun onFlightCancelled() {
                    Timber.d("BuddyFlightDriver.flyToBounds: flight cancelled")
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
     * Cross-cutting: fly to [spec], dwell, fly back, and — if the user
     * has `tapForMeEnabled` — also perform a tap on the resolved node.
     * The tap fires mid-dwell so the user sees the teal action bubble
     * before the return flight starts.
     *
     * Returns true when the buddy actually tapped. Scope §4 / recipe #3.
     */
    suspend fun flyToAndTap(
        spec: AssistantMarkupParser.SemanticPoint,
        bubbleLabel: String?,
        targetLabel: String?,
        fallbackMarks: List<AccessibilityMark> = emptyList(),
    ): Boolean {
        val enabled = runCatching { settings.current().tapForMeEnabled }.getOrDefault(false)
        val landed = flyTo(spec, bubbleLabel, fallbackMarks)
        if (!landed) return false
        if (!enabled) return false
        // Short pause so the user sees the buddy land before it taps.
        kotlinx.coroutines.delay(250L)
        val displayLabel = targetLabel?.take(30) ?: "here"
        presenter.onActionStarted("tapping $displayLabel")
        val result = runCatching {
            actionPerformer.tap(
                TapTarget.AtNode(
                    role = spec.role,
                    text = spec.text,
                    viewId = spec.viewId,
                    desc = spec.contentDescription,
                ),
            )
        }.onFailure { Timber.w(it, "BuddyFlightDriver tap failed") }.getOrNull()
        presenter.onActionFinished()
        return result is com.handy.core.action.PerformResult.Ok
    }

    /** Cancel any in-flight animation (e.g. user tapped widget mid-flight). */
    fun cancel() {
        mainHandler.post {
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
        if (presenter.state.value.buddyState != BuddyState.POINTING) return false
        Timber.d("BuddyFlightDriver: dismissing sticky pointer after user interaction source=%s", source)
        mainHandler.post {
            val service = serviceRef?.get()
            service?.flightControllerInstance()?.cancelAll()
            service?.moveBuddyToDock()
            service?.resetPointerPose()
            presenter.onPointingReturned()
        }
        return true
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
        val maxX = (screenW - widgetW).coerceAtLeast(0)
        val maxY = (screenH - widgetH).coerceAtLeast(0)

        fun candidate(kind: String, x: Int, y: Int, priority: Int): LandingCandidate =
            LandingCandidate(
                kind = kind,
                x = x.coerceIn(0, maxX),
                y = y.coerceIn(0, maxY),
                priority = priority,
            )

        fun centeredY(): Int = bounds.centerY - widgetH / 2
        fun centeredX(): Int = bounds.centerX - widgetW / 2

        val avoidBounds = bounds.expand(avoidMargin, screenW, screenH)
        val nearBottom = bounds.centerY >= screenH - edgeBand
        val nearTop = bounds.centerY <= edgeBand
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

    private data class FlightTarget(
        val bounds: IntRect,
        val node: android.view.accessibility.AccessibilityNodeInfo?,
    )

    private fun List<AccessibilityMark>.resolveCached(
        spec: AssistantMarkupParser.SemanticPoint,
    ): IntRect? {
        firstMatchingTextAndRole(spec)?.let { return it.boundsRect() }
        spec.contentDescription?.let { desc ->
            firstOrNull { mark ->
                mark.contentDescription.equals(desc, ignoreCase = true) ||
                    looseContains(mark.contentDescription, desc)
            }
                ?.let { return it.boundsRect() }
        }
        spec.viewId?.let { viewId ->
            firstOrNull { mark ->
                mark.viewIdSuffix.equals(viewId, ignoreCase = true) ||
                    looseContains(mark.viewIdSuffix, viewId)
            }?.let { return it.boundsRect() }
        }
        return fuzzyText(spec, maxDistance = 2)?.boundsRect()
    }

    private fun List<AccessibilityMark>.firstMatchingTextAndRole(
        spec: AssistantMarkupParser.SemanticPoint,
    ): AccessibilityMark? {
        val text = spec.text ?: return null
        val roleHint = spec.role?.lowercase()
        val normalizedText = normalize(text)
        return firstOrNull { mark ->
            mark.text.equals(text, ignoreCase = true) &&
                (roleHint == null || mark.role.lowercase().contains(roleHint))
        } ?: firstOrNull { mark ->
            looseContains(mark.text, normalizedText)
        }
    }

    private fun List<AccessibilityMark>.fuzzyText(
        spec: AssistantMarkupParser.SemanticPoint,
        maxDistance: Int,
    ): AccessibilityMark? {
        val needle = spec.text?.lowercase() ?: return null
        var best: AccessibilityMark? = null
        var bestDistance = Int.MAX_VALUE
        forEach { mark ->
            val candidate = mark.text?.lowercase()
            if (candidate != null) {
                val d = levenshtein(needle, candidate)
                if (d < bestDistance && d <= maxDistance) {
                    best = mark
                    bestDistance = d
                }
            }
        }
        return best
    }

    private fun AccessibilityMark.boundsRect(): IntRect =
        IntRect(left, top, right, bottom)

    private fun AssistantMarkupParser.SemanticPoint.logSummary(): String =
        "role=$role text=${text?.logSnippet()} viewId=$viewId desc=${contentDescription?.logSnippet()}"

    private fun IntRect.logSummary(): String =
        "$left,$top-$right,$bottom"

    private fun String.logSnippet(max: Int = 80): String =
        replace('\n', ' ').take(max)

    private fun normalize(value: String): String =
        value.lowercase()
            .replace('-', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun looseContains(candidate: String?, needle: String): Boolean {
        val a = normalize(candidate.orEmpty())
        val b = normalize(needle)
        return a.isNotBlank() && b.isNotBlank() && (a.contains(b) || b.contains(a))
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost,
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    private companion object {
        const val POINT_TARGET_RADIUS: Int = 20
        const val POINTER_ROTATION_BLEND_START: Float = 0.78f
        const val TWO_PI: Float = (Math.PI * 2.0).toFloat()
    }
}

// Keep the LensRenderer import live for the commented-out direct path.
@Suppress("unused")
private typealias _LensRendererMarker = LensRenderer
