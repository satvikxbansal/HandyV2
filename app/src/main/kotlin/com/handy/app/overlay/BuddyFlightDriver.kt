@file:Suppress("DEPRECATION")

package com.handy.app.overlay

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.handy.app.widget.BezierFlightController
import com.handy.app.widget.LensRenderer
import com.handy.core.agent.CorrectionIntent
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionPerformer
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PerformResult
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.BuddyState
import com.handy.core.overlay.CandidateOption
import com.handy.core.overlay.CandidateOptions
import com.handy.core.overlay.FlightFsm
import com.handy.core.overlay.OverlayMode
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.privacy.ScreenRedactor
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolutionFailureReason
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolvedPointTarget
import com.handy.runtime.accessibility.SemanticPointerResolver
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val policyEngine: ActionPolicyEngine,
    private val actionPerformer: ActionPerformer,
    private val auditStore: AuditStore,
) {

    private var serviceRef: WeakReference<FloatingWidgetOverlayService>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var safetyTimeoutRunnable: Runnable? = null
    private var windowLayoutJob: Job? = null
    private var foldAvoidBounds: List<IntRect> = emptyList()
    private var activeTarget: ActiveFlightTarget? = null
    private var activeViewportSignature: String? = null

    fun attachService(service: FloatingWidgetOverlayService) {
        serviceRef = WeakReference(service)
        observeWindowLayout(service)
    }

    fun detachService(service: FloatingWidgetOverlayService) {
        if (serviceRef?.get() === service) {
            clearStickySafetyTimeout()
            windowLayoutJob?.cancel()
            windowLayoutJob = null
            foldAvoidBounds = emptyList()
            activeTarget = null
            activeViewportSignature = null
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
                flyToBounds(
                    service = flight.service,
                    bounds = resolved.bounds,
                    label = label,
                    resolved = resolved,
                    targetPackage = flight.targetPackage,
                )
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
            presenter.onPreparingPoint(bubbleLabel)
            flyToBounds(
                service = service,
                bounds = IntRect(x - POINT_TARGET_RADIUS, y - POINT_TARGET_RADIUS, x + POINT_TARGET_RADIUS, y + POINT_TARGET_RADIUS),
                label = bubbleLabel,
                targetPackage = null,
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
                val label = target.label ?: "right one"
                presenter.onPreparingPoint(label)
                flyToBounds(
                    service = service,
                    bounds = target.bounds,
                    label = label,
                    targetPackage = target.packageName,
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
        targetPackage: String? = null,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val (widgetW, widgetH) = service.widgetSize().takeIf { it.first > 0 && it.second > 0 }
            ?: run {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
        val (fromX, fromY) = service.currentWindowPosition()
        val (dockX, dockY) = service.currentDockPosition()
        val viewport = currentFlightViewport(service)
        val landing = chooseLandingPosition(service, bounds, widgetW, widgetH, viewport)
        val arrivalAngle = angleFromWidgetToTarget(landing, widgetW, widgetH, bounds)
        activeTarget = ActiveFlightTarget(
            packageName = targetPackage?.takeIf { it.isNotBlank() },
        )
        activeViewportSignature = viewport.signature
        val startedAtMs = SystemClock.uptimeMillis()
        Timber.d(
            "BuddyFlightDriver.flyToBounds: markId=%s confidence=%.2f source=%s from=%d,%d target=%d,%d widget=%dx%d fitScale=%.2f kind=%s angle=%.2f blendStart=%.2f dock=%d,%d bounds=%s safe=%s label=\"%s\"",
            resolved?.markId,
            resolved?.confidence ?: 0f,
            resolved?.source,
            fromX,
            fromY,
            landing.x,
            landing.y,
            widgetW,
            widgetH,
            landing.fitScale,
            landing.kind,
            arrivalAngle,
            POINTER_ROTATION_BLEND_START,
            dockX,
            dockY,
            bounds.logSummary(),
            viewport.safeBounds.logSummary(),
            label?.logSnippet(),
        )

        service.updateBubblePlacementHint(landing.kind)
        service.announceBuddyFlightStart(label)
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
                        scale = scale * landing.fitScale,
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
                        scale = landing.fitScale,
                    )
                    service.announceBuddyFlightArrived(label)
                    presenter.onPointingArrived(label)
                    scheduleStickySafetyTimeout()
                    if (cont.isActive) cont.resume(true)
                }

                // Tap-for-me stays fail-closed in this batch; arrival
                // only owns sticky pointing.

                override fun onPulse(scale: Float) {
                    service.updatePointerPose(scale = scale * landing.fitScale)
                }

                override fun onFade(alpha: Float) {
                    service.updateBuddyAlpha(alpha)
                }

                override fun onReturned() {
                    Timber.d("BuddyFlightDriver.flyToBounds: returned to dock")
                    clearStickySafetyTimeout()
                    activeTarget = null
                    activeViewportSignature = null
                    service.resetPointerPose()
                    presenter.onPointingReturned()
                }

                override fun onFlightCancelled() {
                    Timber.d(
                        "BuddyFlightDriver.flyToBounds: flight cancelled reason=controller_cancel durationMs=%d",
                        SystemClock.uptimeMillis() - startedAtMs,
                    )
                    clearStickySafetyTimeout()
                    activeTarget = null
                    activeViewportSignature = null
                    service.resetPointerPose()
                    if (presenter.state.value.flightFsm != FlightFsm.Docked) {
                        if (presenter.state.value.flightFsm != FlightFsm.Returning) {
                            presenter.onReturningToDock("controller_cancel")
                        }
                        presenter.onPointingReturned()
                    }
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
        cont.invokeOnCancellation {
            cancelIfStaleTarget("coroutine_cancelled")
        }
    }
    /**
     * Cross-cutting: fly to [spec] and, only after policy allows it
     * and the overlay confirmation sheet is accepted, tap the resolved node.
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
                flyToBounds(
                    service = flight.service,
                    bounds = resolved.bounds,
                    label = bubbleLabel,
                    resolved = resolved,
                    targetPackage = flight.targetPackage,
                )
            }
        } finally {
            resolved.node?.let { node -> runCatching { node.recycle() } }
        }
        if (!landed) return false
        val displayLabel = targetLabel?.take(30) ?: "here"
        val tapTarget = buildTapTargetForResolved(spec, resolved, groundingSnapshot)
        val grounding = groundingSnapshot ?: fallbackGroundingFor(flight, tapTarget)
        val policyPackage = tapTarget.expectedPackage
            ?: grounding.toolContext.packageName.takeIf { it.isNotBlank() }
            ?: flight.targetPackage
            ?: "unknown"
        val decision = policyEngine.decide(
            action = AssistantAction.OpenApp(packageHint = policyPackage),
            target = tapTarget,
            grounding = grounding,
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )
        if (!decision.allowed) {
            val reason = decision.reason ?: "policy-denied"
            auditTapForMe(
                tapTarget = tapTarget,
                targetPackage = policyPackage,
                result = AuditResult.Failed(reason),
                confirmationRequired = false,
                userConfirmed = false,
            )
            if (reason !in POINTER_SAFE_DENIAL_REASONS) {
                dismissPointingAfterUserInteraction("policy:$reason")
            }
            return false
        }

        val confirmationLevel = when (decision.confirmation) {
            ConfirmationLevel.NONE -> ConfirmationLevel.NORMAL
            else -> decision.confirmation
        }
        val confirmed = withTimeoutOrNull(TAP_CONFIRMATION_TIMEOUT_MS) {
            presenter.requestTapForMeConfirmation(
                targetLabel = displayLabel,
                appLabel = grounding.toolContext.appLabel,
                packageName = policyPackage,
                confirmationLevel = confirmationLevel,
                risk = decision.risk,
                reason = decision.reason,
            )
        } == true
        if (!confirmed) {
            auditTapForMe(
                tapTarget = tapTarget,
                targetPackage = policyPackage,
                result = AuditResult.Cancelled,
                confirmationRequired = true,
                userConfirmed = false,
            )
            dismissPointingAfterUserInteraction("tap_confirmation_cancelled")
            return false
        }

        presenter.onActionStarted("tapping $displayLabel")
        val result = runCatching {
            actionPerformer.tap(tapTarget)
        }.onFailure { Timber.w(it, "BuddyFlightDriver tap failed") }.getOrNull()
        auditTapForMe(
            tapTarget = tapTarget,
            targetPackage = policyPackage,
            result = result.toAuditResult(),
            confirmationRequired = true,
            userConfirmed = true,
        )
        presenter.onActionFinished()
        return result is PerformResult.Ok
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
        val options = resolved.toCandidateOptions(
            visible = resolved.shouldOfferCandidateOptions(),
        )
        val shouldOfferOptions = resolved.shouldOfferCandidateOptions()
        if (shouldOfferOptions && options?.hasAlternatives == true) {
            Timber.d(
                "BuddyFlightDriver.flyTo: offering candidate options confidence=%.2f failure=%s options=%d",
                resolved.confidence,
                resolved.failureReason,
                options.options.size,
            )
            resolved.node?.let { node -> runCatching { node.recycle() } }
            presenter.onCandidateOptionsAvailable(label, options)
            scheduleStickySafetyTimeout()
            return null
        }
        if (shouldOfferOptions) {
            Timber.d(
                "BuddyFlightDriver.flyTo: middle/ambiguous confidence without alternatives confidence=%.2f failure=%s",
                resolved.confidence,
                resolved.failureReason,
            )
            resolved.node?.let { node -> runCatching { node.recycle() } }
            presenter.onManualTargetFallbackAvailable(label)
            scheduleStickySafetyTimeout()
            return null
        }
        if (resolved.confidence < MIN_CANDIDATE_CONFIDENCE) {
            Timber.d(
                "BuddyFlightDriver.flyTo: refusing low pointer confidence=%.2f failure=%s",
                resolved.confidence,
                resolved.failureReason,
            )
            resolved.node?.let { node -> runCatching { node.recycle() } }
            presenter.onManualTargetFallbackAvailable(label)
            scheduleStickySafetyTimeout()
            return null
        }
        presenter.setCandidateOptions(options?.copy(visible = false))
        return FlightResolution(
            service = service,
            resolved = resolved,
            targetPackage = expectedPackage
                ?: resolved.node?.packageName?.toString()?.takeIf { it.isNotBlank() },
        )
    }

    private data class FlightResolution(
        val service: FloatingWidgetOverlayService,
        val resolved: ResolvedPointTarget,
        val targetPackage: String?,
    )

    suspend fun flyToCandidateOption(candidateId: String): Boolean {
        val service = serviceRef?.get() ?: run {
            Timber.d("BuddyFlightDriver.flyToCandidateOption: no service attached")
            return false
        }
        val state = presenter.state.value
        if (state.buddyState == BuddyState.FLYING) {
            Timber.d("BuddyFlightDriver.flyToCandidateOption: flight already in progress")
            return false
        }
        val options = state.candidateOptions ?: return false
        val candidate = options.options.firstOrNull { it.id == candidateId } ?: return false
        if (candidate.bounds.width <= 0 || candidate.bounds.height <= 0) return false

        Timber.d(
            "BuddyFlightDriver.flyToCandidateOption: id=%s label=\"%s\" confidence=%.2f bounds=%s",
            candidate.id,
            candidate.label.logSnippet(),
            candidate.confidence,
            candidate.bounds.logSummary(),
        )
        clearStickySafetyTimeout()
        return withContext(Dispatchers.Main.immediate) {
            presenter.onCandidateOptionPicked(candidate.id)
            presenter.onPreparingPoint(candidate.label)
            flyToBounds(
                service = service,
                bounds = candidate.bounds,
                label = candidate.label,
                targetPackage = activeTarget?.packageName,
            )
        }
    }

    suspend fun applyCorrectionIntent(intent: CorrectionIntent): Boolean {
        val options = presenter.state.value.candidateOptions ?: return false
        if (!options.hasAlternatives) return false
        val candidate = options.selectFor(intent) ?: return false
        Timber.d(
            "BuddyFlightDriver.applyCorrectionIntent: intent=%s candidate=%s label=\"%s\"",
            intent,
            candidate.id,
            candidate.label.logSnippet(),
        )
        return flyToCandidateOption(candidate.id)
    }

    /** Cancel any in-flight animation (e.g. user tapped widget mid-flight). */
    fun cancel() {
        cancelIfStaleTarget("user_cancel")
    }

    /**
     * Cancel the active target if the screen geometry or foreground
     * package no longer matches the target that was resolved before
     * takeoff.
     */
    fun cancelIfStaleTarget(reason: String, sourcePackage: String? = null): Boolean {
        val state = presenter.state.value
        if (!state.isFlying && state.buddyState != BuddyState.POINTING) return false
        val active = activeTarget
        if (!sourcePackage.isNullOrBlank()) {
            val targetPackage = active?.packageName
            if (targetPackage != null && sourcePackage == targetPackage) return false
            if (sourcePackage == serviceRef?.get()?.packageName) return false
        }
        Timber.d(
            "BuddyFlightDriver: cancelling stale target reason=%s sourcePackage=%s targetPackage=%s",
            reason,
            sourcePackage,
            active?.packageName,
        )
        mainHandler.post {
            clearStickySafetyTimeout()
            val service = serviceRef?.get()
            presenter.onReturningToDock(reason)
            service?.flightControllerInstance()?.cancelAll()
            service?.moveBuddyToDock()
            service?.resetPointerPose()
            activeTarget = null
            activeViewportSignature = null
            if (presenter.state.value.flightFsm != FlightFsm.Docked) {
                presenter.onPointingReturned()
            }
        }
        return true
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
            presenter.onReturningToDock(source?.let { "user_interaction:$it" } ?: "user_interaction")
            service?.flightControllerInstance()?.cancelAll()
            service?.moveBuddyToDock()
            service?.resetPointerPose()
            activeTarget = null
            activeViewportSignature = null
            if (presenter.state.value.flightFsm != FlightFsm.Docked) {
                presenter.onPointingReturned()
            }
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

    private fun observeWindowLayout(service: FloatingWidgetOverlayService) {
        windowLayoutJob?.cancel()
        windowLayoutJob = service.lifecycleScope.launch(Dispatchers.Main.immediate) {
            val trackingContext = windowLayoutTrackingContext(service) ?: return@launch
            val tracker = runCatching { WindowInfoTracker.getOrCreate(trackingContext) }
                .onFailure { Timber.w(it, "BuddyFlightDriver: WindowInfoTracker unavailable") }
                .getOrNull() ?: return@launch
            runCatching {
                tracker.windowLayoutInfo(trackingContext).collectLatest { info ->
                    val next = info.displayFeatures.mapNotNull { feature ->
                        val folding = feature as? FoldingFeature ?: return@mapNotNull null
                        folding.bounds
                            .takeIf { it.width() > 0 && it.height() > 0 }
                            ?.toIntRect()
                    }
                    if (next != foldAvoidBounds) {
                        foldAvoidBounds = next
                        if (activeViewportSignature != null) {
                            cancelIfStaleTarget("fold_changed")
                        }
                    }
                }
            }.onFailure {
                Timber.w(it, "BuddyFlightDriver: fold layout observation failed")
            }
        }
    }

    private fun windowLayoutTrackingContext(service: FloatingWidgetOverlayService): Context? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return service
        val display = service.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display == null) {
            Timber.w("BuddyFlightDriver: fold layout observation skipped; default display unavailable")
            return null
        }
        return runCatching {
            service
                .createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        }.onFailure {
            Timber.w(
                it,
                "BuddyFlightDriver: fold layout observation skipped; overlay window context unavailable",
            )
        }.getOrNull()
    }

    private fun chooseLandingPosition(
        service: FloatingWidgetOverlayService,
        bounds: IntRect,
        widgetW: Int,
        widgetH: Int,
        viewport: FlightViewport,
    ): LandingPosition {
        val density = service.resources.displayMetrics.density
        val chosen = chooseBuddyLandingPosition(
            target = bounds,
            widgetW = widgetW,
            widgetH = widgetH,
            density = density,
            viewport = viewport,
        )
        Timber.d(
            "BuddyFlightDriver.chooseLandingPosition: chosen=%s pos=%d,%d visual=%s scale=%.2f target=%s safe=%s hinges=%s",
            chosen.kind,
            chosen.x,
            chosen.y,
            chosen.visualBounds.logSummary(),
            chosen.fitScale,
            bounds.logSummary(),
            viewport.safeBounds.logSummary(),
            viewport.avoidBounds.joinToString { it.logSummary() },
        )
        return chosen
    }

    private fun currentFlightViewport(service: FloatingWidgetOverlayService): FlightViewport {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = runCatching {
                service.getSystemService(WindowManager::class.java).currentWindowMetrics
            }.getOrNull()
            if (metrics != null) {
                return metrics.toFlightViewport(foldAvoidBounds)
            }
        }
        val screenW = service.resources.displayMetrics.widthPixels
        val screenH = service.resources.displayMetrics.heightPixels
        return FlightViewport(
            bounds = IntRect(0, 0, screenW, screenH),
            insets = FlightInsets(
                top = service.systemBarSize("status_bar_height"),
                bottom = service.systemBarSize("navigation_bar_height"),
            ),
            avoidBounds = foldAvoidBounds,
        )
    }

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

    private fun ResolvedPointTarget.shouldOfferCandidateOptions(): Boolean =
        failureReason == ResolutionFailureReason.AMBIGUOUS ||
            (confidence >= MIN_CANDIDATE_CONFIDENCE && confidence < MIN_FLY_CONFIDENCE)

    private fun ResolvedPointTarget.toCandidateOptions(visible: Boolean): CandidateOptions? {
        val candidates = debugCandidates
            .filter { it.bounds.width > 0 && it.bounds.height > 0 && it.visible && it.enabled }
            .take(MAX_CANDIDATE_OPTIONS)
        if (candidates.isEmpty()) return null

        val baseLabels = candidates.mapIndexed { index, candidate ->
            candidate.baseCandidateLabel(index)
        }
        val duplicateCounts = baseLabels
            .map(::normalizeCandidateText)
            .groupingBy { it }
            .eachCount()
        val seen = mutableMapOf<String, Int>()
        val options = candidates.mapIndexed { index, candidate ->
            val base = baseLabels[index]
            val key = normalizeCandidateText(base)
            val occurrence = (seen[key] ?: 0) + 1
            seen[key] = occurrence
            val label = if ((duplicateCounts[key] ?: 0) > 1) {
                "$base $occurrence"
            } else {
                base
            }
            CandidateOption(
                id = "candidate_$index",
                label = label.take(MAX_CANDIDATE_LABEL_CHARS).trim(),
                role = candidate.role,
                markId = candidate.markId,
                viewId = candidate.viewId,
                bounds = candidate.bounds,
                confidence = (candidate.score / 100f).coerceIn(0f, 1f),
            )
        }
        if (options.isEmpty()) return null
        return CandidateOptions(
            options = options,
            activeCandidateId = options.first().id,
            visible = visible && options.size > 1,
        )
    }

    private fun SemanticPointerResolver.TargetCandidate.baseCandidateLabel(index: Int): String =
        label?.takeIf { it.isNotBlank() }
            ?: viewId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: role?.takeIf { it.isNotBlank() }
            ?: markId?.takeIf { it.isNotBlank() }
            ?: "Target ${index + 1}"

    private fun CandidateOptions.selectFor(intent: CorrectionIntent): CandidateOption? {
        if (options.isEmpty()) return null
        val current = activeIndex.coerceIn(0, options.lastIndex)
        return when (intent) {
            is CorrectionIntent.Other -> selectOther(current, intent.labelHint)
            CorrectionIntent.Next -> options[(current + 1) % options.size]
            CorrectionIntent.Previous -> options[(current - 1 + options.size) % options.size]
            CorrectionIntent.Popup -> options.firstOrNull { it.matchesHint("popup") }
                ?: selectOther(current, null)
        }
    }

    private fun CandidateOptions.selectOther(current: Int, labelHint: String?): CandidateOption? {
        val ordered = options.indices
            .filter { it != current }
            .map { options[it] }
        if (labelHint.isNullOrBlank()) return ordered.firstOrNull()
        return ordered.firstOrNull { it.matchesHint(labelHint) } ?: ordered.firstOrNull()
    }

    private fun CandidateOption.matchesHint(hint: String): Boolean {
        val normalizedHint = normalizeCandidateText(hint)
        if (normalizedHint.isBlank()) return false
        val haystack = normalizeCandidateText(
            listOfNotNull(label, role, markId, viewId).joinToString(" "),
        )
        return haystack.contains(normalizedHint)
    }

    private fun normalizeCandidateText(value: String): String =
        value.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun AssistantMarkupParser.SemanticPoint.logSummary(): String =
        "markId=$markId role=$role text=${text?.logSnippet()} viewId=$viewId desc=${contentDescription?.logSnippet()}"

    private fun IntRect.logSummary(): String =
        "$left,$top-$right,$bottom"

    private fun String.logSnippet(max: Int = 80): String =
        replace('\n', ' ').take(max)

    private fun fallbackGroundingFor(
        flight: FlightResolution,
        target: TapTarget.AtNode,
    ): GroundingSnapshot {
        val packageName = target.expectedPackage ?: flight.targetPackage ?: "unknown"
        return GroundingSnapshot(
            requestId = "tap-for-me-${System.currentTimeMillis()}",
            source = TurnSource.OVERLAY_PANEL,
            toolContext = ToolContext(
                packageName = packageName,
                appLabel = packageName,
            ),
            windowId = target.expectedWindowId,
            capturedAtMs = System.currentTimeMillis(),
        )
    }

    private suspend fun auditTapForMe(
        tapTarget: TapTarget.AtNode,
        targetPackage: String?,
        result: AuditResult,
        confirmationRequired: Boolean,
        userConfirmed: Boolean,
    ) {
        val event = AuditEvent(
            timestampEpochMs = System.currentTimeMillis(),
            requestId = java.util.UUID.randomUUID().toString(),
            provider = "tap-for-me",
            action = AuditAction.Tap,
            targetApp = targetPackage ?: tapTarget.expectedPackage ?: "unknown",
            semanticTarget = tapTarget.auditDescription(),
            confirmationRequired = confirmationRequired,
            userConfirmed = userConfirmed,
            result = result,
            failureReason = (result as? AuditResult.Failed)?.reason,
        )
        runCatching { auditStore.append(event) }
            .onFailure { Timber.w(it, "AuditStore tap-for-me append failed") }
    }

    private fun PerformResult?.toAuditResult(): AuditResult = when (this) {
        PerformResult.Ok -> AuditResult.Dispatched(component = "tap-for-me")
        PerformResult.NotFound -> AuditResult.NotFound
        is PerformResult.Unsupported -> AuditResult.NotPermitted
        is PerformResult.Failed -> AuditResult.Failed(reason)
        null -> AuditResult.Failed("tap failed")
    }

    private fun TapTarget.AtNode.auditDescription(): String = buildString {
        val context = listOfNotNull(role, text, viewId, desc, expectedPackage, snapshotHash)
            .joinToString(" ")
        val passwordContext = context.contains("password", ignoreCase = true) ||
            context.contains("passcode", ignoreCase = true) ||
            Regex("""\bpwd\b""", RegexOption.IGNORE_CASE).containsMatchIn(context)
        appendAuditPart("markId", markId, context)
        appendAuditPart("role", role, context)
        appendAuditPart("text", text, context, isPassword = passwordContext)
        appendAuditPart("viewId", viewId, context)
        appendAuditPart("desc", desc, context, isPassword = passwordContext)
        appendAuditPart("expectedPackage", expectedPackage, context)
        expectedWindowId?.let { append("expectedWindowId=$it;") }
        appendAuditPart("snapshotHash", snapshotHash, context)
    }.trimEnd(';')

    private fun StringBuilder.appendAuditPart(
        name: String,
        value: String?,
        context: String,
        isPassword: Boolean = false,
    ) {
        val redacted = ScreenRedactor.redactText(
            value = value,
            context = context,
            isPassword = isPassword,
            diagnostics = true,
        ) ?: return
        append(name).append('=').append(redacted).append(';')
    }

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
        const val MIN_CANDIDATE_CONFIDENCE: Float = 0.40f
        const val MIN_FLY_CONFIDENCE: Float = 0.70f
        const val MAX_CANDIDATE_OPTIONS: Int = 5
        const val MAX_CANDIDATE_LABEL_CHARS: Int = 28
        const val STICKY_POINTER_TIMEOUT_MS: Long = 30_000L
        const val TAP_CONFIRMATION_TIMEOUT_MS: Long = 8_000L
        val POINTER_SAFE_DENIAL_REASONS: Set<String> = setOf(
            "gate-closed",
            "muted",
            "denylisted",
        )
        const val POINTER_ROTATION_BLEND_START: Float = 0.78f
        const val TWO_PI: Float = (Math.PI * 2.0).toFloat()
    }
}

private data class ActiveFlightTarget(
    val packageName: String?,
)

internal data class FlightInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

internal data class FlightViewport(
    val bounds: IntRect,
    val insets: FlightInsets = FlightInsets(),
    val avoidBounds: List<IntRect> = emptyList(),
) {
    val safeBounds: IntRect
        get() = IntRect(
            left = (bounds.left + insets.left).coerceAtMost(bounds.right),
            top = (bounds.top + insets.top).coerceAtMost(bounds.bottom),
            right = (bounds.right - insets.right).coerceAtLeast(bounds.left),
            bottom = (bounds.bottom - insets.bottom).coerceAtLeast(bounds.top),
        ).takeIf { it.width > 0 && it.height > 0 } ?: bounds

    val signature: String
        get() = buildString {
            append(bounds.logSummary()).append('|')
            append(insets.left).append(',')
                .append(insets.top).append(',')
                .append(insets.right).append(',')
                .append(insets.bottom)
            avoidBounds.forEach { append('|').append(it.logSummary()) }
        }
}

internal data class LandingPosition(
    val x: Int,
    val y: Int,
    val kind: String,
    val fitScale: Float = 1f,
    val visualWidth: Int,
    val visualHeight: Int,
) {
    val visualBounds: IntRect get() = IntRect(x, y, x + visualWidth, y + visualHeight)
}

private data class LandingCandidate(
    val kind: String,
    val x: Int,
    val y: Int,
    val priority: Int,
    val fitScale: Float,
    val visualWidth: Int,
    val visualHeight: Int,
) {
    val visualBounds: IntRect get() = IntRect(x, y, x + visualWidth, y + visualHeight)
}

internal fun chooseBuddyLandingPosition(
    target: IntRect,
    widgetW: Int,
    widgetH: Int,
    density: Float,
    viewport: FlightViewport,
): LandingPosition {
    val safe = viewport.safeBounds
    val regions = splitSafeRegions(safe, viewport.avoidBounds)
        .takeIf { it.isNotEmpty() }
        ?: listOf(safe)
    val gap = (8f * density).roundToInt().coerceAtLeast(1)
    val avoidMargin = (6f * density).roundToInt().coerceAtLeast(1)
    val edgeBand = (96f * density).roundToInt().coerceAtLeast(1)
    // Keep Buddy visibly close to the target while avoiding the tappable
    // bounds. Edge-biased candidates preserve target affinity; overlap with
    // this expanded rect is scored as a hard fallback-only penalty.
    val avoidTarget = target.expandWithin(avoidMargin, safe)
    val preferredBand = when {
        target.centerY >= safe.bottom - edgeBand -> "bottom"
        target.centerY <= safe.top + edgeBand -> "top"
        target.centerX <= safe.left + edgeBand -> "left"
        target.centerX >= safe.right - edgeBand -> "right"
        else -> "middle"
    }

    fun candidatesFor(region: IntRect): List<LandingCandidate> {
        val fitScale = minOf(
            1f,
            region.width.toFloat() / widgetW.coerceAtLeast(1).toFloat(),
            region.height.toFloat() / widgetH.coerceAtLeast(1).toFloat(),
        ).coerceIn(0.1f, 1f)
        val fitW = max(1, (widgetW * fitScale).roundToInt())
        val fitH = max(1, (widgetH * fitScale).roundToInt())
        val minX = region.left
        val minY = region.top
        val maxX = (region.right - fitW).coerceAtLeast(minX)
        val maxY = (region.bottom - fitH).coerceAtLeast(minY)

        fun candidate(kind: String, x: Int, y: Int, priority: Int): LandingCandidate =
            LandingCandidate(
                kind = kind,
                x = x.coerceIn(minX, maxX),
                y = y.coerceIn(minY, maxY),
                priority = priority,
                fitScale = fitScale,
                visualWidth = fitW,
                visualHeight = fitH,
            )

        fun centeredY(): Int = target.centerY - fitH / 2
        fun centeredX(): Int = target.centerX - fitW / 2

        return buildList {
            if (preferredBand == "bottom") {
                add(candidate("bottom-above-center", centeredX(), target.top - fitH - gap, priority = 0))
                add(candidate("bottom-above-left", target.left, target.top - fitH - gap, priority = 3))
                add(candidate("bottom-above-right", target.right - fitW, target.top - fitH - gap, priority = 3))
            }
            if (preferredBand == "top") {
                add(candidate("top-below-center", centeredX(), target.bottom + gap, priority = 0))
                add(candidate("top-below-left", target.left, target.bottom + gap, priority = 3))
                add(candidate("top-below-right", target.right - fitW, target.bottom + gap, priority = 3))
            }
            if (preferredBand == "left") {
                add(candidate("left-side-right", target.right + gap, centeredY(), priority = 1))
            }
            if (preferredBand == "right") {
                add(candidate("right-side-left", target.left - fitW - gap, centeredY(), priority = 1))
            }
            add(candidate("side-right", target.right + gap, centeredY(), priority = 6))
            add(candidate("side-left", target.left - fitW - gap, centeredY(), priority = 6))
            add(candidate("below-center", centeredX(), target.bottom + gap, priority = 8))
            add(candidate("above-center", centeredX(), target.top - fitH - gap, priority = 8))
            add(candidate("corner-bottom-right", target.right + gap, target.bottom + gap, priority = 12))
            add(candidate("corner-bottom-left", target.left - fitW - gap, target.bottom + gap, priority = 12))
            add(candidate("corner-top-right", target.right + gap, target.top - fitH - gap, priority = 12))
            add(candidate("corner-top-left", target.left - fitW - gap, target.top - fitH - gap, priority = 12))
        }
    }

    val candidates = regions.flatMap(::candidatesFor)
    val chosen = candidates.minBy { candidate ->
        candidate.score(
            avoidTarget = avoidTarget,
            hingeBounds = viewport.avoidBounds,
            target = target,
            preferredBand = preferredBand,
        )
    }
    return LandingPosition(
        x = chosen.x,
        y = chosen.y,
        kind = chosen.kind,
        fitScale = chosen.fitScale,
        visualWidth = chosen.visualWidth,
        visualHeight = chosen.visualHeight,
    )
}

private fun LandingCandidate.score(
    avoidTarget: IntRect,
    hingeBounds: List<IntRect>,
    target: IntRect,
    preferredBand: String,
): Int {
    val overlapPenalty = if (visualBounds.overlaps(avoidTarget)) 1_000_000 else 0
    val hingePenalty = if (hingeBounds.any { visualBounds.overlaps(it) }) 2_000_000 else 0
    val centerX = x + visualWidth / 2
    val centerY = y + visualHeight / 2
    val horizontalAffinity = abs(centerX - target.centerX)
    val verticalAffinity = abs(centerY - target.centerY)
    val centerDistance = hypot(
        (centerX - target.centerX).toDouble(),
        (centerY - target.centerY).toDouble(),
    ).roundToInt()
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
        else -> centerDistance
    }
    val shrinkPenalty = ((1f - fitScale) * 10_000f).roundToInt()
    return overlapPenalty + hingePenalty + bandPenalty + shrinkPenalty + priority * 1_000 + driftPenalty
}

private fun splitSafeRegions(safe: IntRect, avoidBounds: List<IntRect>): List<IntRect> =
    avoidBounds.fold(listOf(safe)) { regions, avoid ->
        regions.flatMap { region -> region.splitAround(avoid) }
    }.filter { it.width > 0 && it.height > 0 }

private fun IntRect.splitAround(avoid: IntRect): List<IntRect> {
    if (!overlaps(avoid)) return listOf(this)
    val pieces = buildList {
        if (avoid.left > left) add(IntRect(left, top, avoid.left.coerceAtMost(right), bottom))
        if (avoid.right < right) add(IntRect(avoid.right.coerceAtLeast(left), top, right, bottom))
        if (avoid.top > top) add(IntRect(left, top, right, avoid.top.coerceAtMost(bottom)))
        if (avoid.bottom < bottom) add(IntRect(left, avoid.bottom.coerceAtLeast(top), right, bottom))
    }.filter { it.width > 0 && it.height > 0 }
    return pieces.ifEmpty { listOf(this) }
}

private fun IntRect.expandWithin(margin: Int, bounds: IntRect): IntRect =
    IntRect(
        left = (left - margin).coerceAtLeast(bounds.left),
        top = (top - margin).coerceAtLeast(bounds.top),
        right = (right + margin).coerceAtMost(bounds.right),
        bottom = (bottom + margin).coerceAtMost(bounds.bottom),
    )

private fun IntRect.overlaps(other: IntRect): Boolean =
    left < other.right &&
        right > other.left &&
        top < other.bottom &&
        bottom > other.top

private fun IntRect.logSummary(): String =
    "$left,$top-$right,$bottom"

private fun Rect.toIntRect(): IntRect = IntRect(left, top, right, bottom)

private fun WindowMetrics.toFlightViewport(avoidBounds: List<IntRect>): FlightViewport {
    val insetTypes = WindowInsets.Type.systemBars() or
        WindowInsets.Type.displayCutout() or
        WindowInsets.Type.ime()
    val safeInsets = windowInsets.getInsets(insetTypes)
    return FlightViewport(
        bounds = bounds.toIntRect(),
        insets = FlightInsets(
            left = safeInsets.left,
            top = safeInsets.top,
            right = safeInsets.right,
            bottom = safeInsets.bottom,
        ),
        avoidBounds = avoidBounds,
    )
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
        resolverConfidence = resolved.confidence,
    )

private fun android.content.Context.systemBarSize(name: String): Int {
    val id = resources.getIdentifier(name, "dimen", "android")
    return if (id > 0) runCatching { resources.getDimensionPixelSize(id) }.getOrDefault(0) else 0
}

// Keep the LensRenderer import live for the commented-out direct path.
@Suppress("unused")
private typealias _LensRendererMarker = LensRenderer
