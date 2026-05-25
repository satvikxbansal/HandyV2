package com.handy.app.overlay

import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.BuddyState
import com.handy.core.overlay.CandidateOptions
import com.handy.core.overlay.FlightFsm
import com.handy.core.overlay.OverlayMode
import com.handy.core.overlay.OverlayPanelState
import com.handy.core.overlay.PanelContent
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.overlay.TapForMeConfirmation
import com.handy.core.overlay.TapForMeConfirmationDecision
import com.handy.core.tool.ToolContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import timber.log.Timber

data class ActionDisclosureReviewRequest(val id: Long)

/**
 * Single state owner for the Unified Buddy overlay (widget + panel +
 * lens chrome + bubble taxonomy). Scope §2 / §3.
 *
 * Singleton-scoped: every caller (widget service, chat pipeline, tool
 * runner, action performer in V2) observes the same [state] flow.
 *
 * Cache-at-tap (cursorbuddy recipe #4):
 *  - [onWidgetTap] and [onWidgetLongPressArmed] snapshot the current
 *    tool context + accessibility marks BEFORE the panel takes focus.
 *  - The panel consumes the cached [PanelSnapshot]; it never re-reads
 *    `rootInActiveWindow` mid-panel.
 *
 * Bubble mutual-exclusion (scope §3):
 *  - Transcript (yellow) fades on `BuddyState.THINKING`.
 *  - Response (green) suppresses Navigation (blue).
 *  - Action (teal) suppresses Response.
 *
 * FSM leaf states:
 *  - [FlightFsm.ActionResult], [FlightFsm.Error], and [FlightFsm.Returning]
 *    must either drain to a steady state in the same presenter call or have an
 *    explicit drainer such as [onPointingReturned].
 */
@Singleton
class OverlayPresenter @Inject constructor(
    private val foregroundAppMonitor: HandyForegroundAppMonitor,
) {

    private val _state = MutableStateFlow(OverlayPanelState())
    val state: StateFlow<OverlayPanelState> = _state.asStateFlow()
    private val tapConfirmationIds = AtomicLong(1L)
    private val tapConfirmationContinuations =
        mutableMapOf<Long, CancellableContinuation<TapForMeConfirmationDecision>>()
    private val actionDisclosureRequestIds = AtomicLong(1L)
    private val actionDisclosureContinuations =
        mutableMapOf<Long, CancellableContinuation<Boolean>>()
    private val _actionDisclosureReviewRequests =
        MutableSharedFlow<ActionDisclosureReviewRequest>(extraBufferCapacity = 1)
    val actionDisclosureReviewRequests: SharedFlow<ActionDisclosureReviewRequest> =
        _actionDisclosureReviewRequests.asSharedFlow()

    private fun setState(
        event: String,
        target: FlightFsm? = null,
        reducer: (OverlayPanelState) -> OverlayPanelState,
    ) {
        val snapshot = _state.value
        val nextFsm = target ?: snapshot.flightFsm
        if (nextFsm != snapshot.flightFsm) {
            require(isLegalTransition(snapshot.flightFsm, nextFsm)) {
                "Illegal flight FSM transition ${snapshot.flightFsm} -> $nextFsm via $event"
            }
        }
        _state.value = reducer(snapshot).copy(flightFsm = nextFsm)
    }

    private fun forceDocked(
        event: String,
        reducer: (OverlayPanelState) -> OverlayPanelState,
    ) {
        val snapshot = _state.value
        require(snapshot.flightFsm.canResetToDocked()) {
            "Illegal flight FSM transition ${snapshot.flightFsm} -> ${FlightFsm.Docked} via $event"
        }
        _state.value = reducer(snapshot).copy(flightFsm = FlightFsm.Docked)
    }

    // ---- widget-side entry points ------------------------------------------

    /**
     * Called by [FloatingWidgetOverlayService] the instant the widget
     * is tapped. Refreshes the foreground snapshot synchronously so
     * the panel sees the right package (cache-at-tap).
     */
    fun onWidgetTap(
        marksProvider: () -> List<AccessibilityMark> = { emptyList() },
        clock: () -> Long = { System.currentTimeMillis() },
    ) {
        val snapshot = captureSnapshot(marksProvider, clock)
        val greeting = panelGreetingFor(snapshot)
        setState(event = "onWidgetTap") { it.copy(
            mode = OverlayMode.ChatPanel,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            panel = PanelContent(
                snapshot = snapshot,
                greeting = greeting,
            ),
            tapForMeConfirmation = null,
            candidateOptions = null,
            bubble = null,
        ) }
        Timber.d(
            "OverlayPresenter: panel open, pkg=%s label=%s marks=%d",
            snapshot?.toolContext?.packageName,
            snapshot?.toolContext?.displayLabel,
            snapshot?.marks?.size ?: 0,
        )
    }

    /**
     * Called when the 400 ms long-press timer fires and
     * [com.handy.app.voice.VoiceController.start] returned true. Same
     * cache-at-tap semantics — the snapshot is captured before the
     * recognizer emits anything.
     */
    fun onWidgetLongPressArmed(
        marksProvider: () -> List<AccessibilityMark> = { emptyList() },
        clock: () -> Long = { System.currentTimeMillis() },
    ) {
        val snapshot = captureSnapshot(marksProvider, clock)
        setState(
            event = "onWidgetLongPressArmed",
            target = FlightFsm.Listening,
        ) { current -> current.copy(
            buddyState = BuddyState.LISTENING,
            isFlying = false,
            bubble = BuddyBubble.Transcript(""),
            panel = current.panel.copy(
                snapshot = snapshot ?: current.panel.snapshot,
                isListening = true,
                partialTranscript = "",
            ),
        ) }
    }

    fun onWidgetDragStart() {
        setState(event = "onWidgetDragStart") { it.copy(
            buddyState = BuddyState.DRAGGING,
            isFlying = false,
            bubble = null,
        ) }
    }

    fun onWidgetIdle() {
        forceDocked(event = "onWidgetIdle") { it.copy(
            mode = OverlayMode.IdleWidget,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            bubble = null,
            panel = PanelContent(),
            tapForMeConfirmation = null,
            candidateOptions = null,
        ) }
    }

    fun onWidgetThinking() {
        setState(
            event = "onWidgetThinking",
            target = FlightFsm.Thinking,
        ) { it.copy(
            buddyState = BuddyState.THINKING,
            isFlying = false,
            bubble = null,
        ) }
    }

    fun dismissPanel() {
        val snapshot = _state.value
        if (snapshot.mode != OverlayMode.ChatPanel) return
        forceDocked(event = "dismissPanel") { snapshot.copy(
            mode = OverlayMode.IdleWidget,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            panel = PanelContent(),
            tapForMeConfirmation = snapshot.tapForMeConfirmation,
            candidateOptions = null,
            bubble = snapshot.bubble,
        ) }
    }

    // ---- voice + chat wiring ------------------------------------------------

    fun updatePartialTranscript(partial: String) {
        val snapshot = _state.value
        if (!snapshot.panel.isListening && snapshot.buddyState != BuddyState.LISTENING) return
        setState(event = "updatePartialTranscript") { snapshot.copy(
            panel = snapshot.panel.copy(partialTranscript = partial),
            bubble = if (partial.isNotBlank()) BuddyBubble.Transcript(partial) else snapshot.bubble,
        ) }
    }

    fun onPanelVoiceStarted() {
        setState(
            event = "onPanelVoiceStarted",
            target = FlightFsm.Listening,
        ) { snapshot -> snapshot.copy(
            buddyState = BuddyState.LISTENING,
            panel = snapshot.panel.copy(
                isListening = true,
                partialTranscript = "",
            ),
            bubble = BuddyBubble.Transcript(""),
        ) }
    }

    fun onVoiceFinalized(transcript: String?) {
        val target = if (transcript.isNullOrBlank()) FlightFsm.Docked else FlightFsm.Thinking
        setState(
            event = "onVoiceFinalized",
            target = target,
        ) { snapshot -> snapshot.copy(
            buddyState = if (transcript.isNullOrBlank()) BuddyState.DOCKED else BuddyState.THINKING,
            isFlying = false,
            panel = snapshot.panel.copy(
                isListening = false,
                partialTranscript = transcript.orEmpty(),
                isStreaming = !transcript.isNullOrBlank(),
            ),
            bubble = if (transcript.isNullOrBlank()) null else snapshot.bubble,
        ) }
    }

    fun onStreamingStart() {
        setState(
            event = "onStreamingStart",
            target = FlightFsm.Answering,
        ) { snapshot -> snapshot.copy(
            buddyState = BuddyState.STREAMING,
            isFlying = false,
            panel = snapshot.panel.copy(
                isStreaming = true,
                streamingDelta = "",
            ),
            bubble = null,
        ) }
    }

    fun onStreamingDelta(accumulated: String) {
        setState(event = "onStreamingDelta") { snapshot -> snapshot.copy(
            panel = snapshot.panel.copy(streamingDelta = accumulated),
        ) }
    }

    /**
     * Final assistant text for the response bubble. [overlayClamped]
     * must already be <= 110 chars (guardrails /
     * `AssistantMarkupParser.clampVoiceSpokenForOverlay`).
     */
    fun onResponseFinalized(overlayClamped: String?, chatText: String) {
        val bubble = overlayClamped
            ?.takeIf { it.isNotBlank() }
            ?.let(BuddyBubble::Response)
        val target = if (bubble != null) FlightFsm.ActionResult else FlightFsm.Docked
        setState(
            event = "onResponseFinalized",
            target = target,
        ) { snapshot -> snapshot.copy(
            buddyState = if (bubble != null) BuddyState.SPEAKING else BuddyState.DOCKED,
            isFlying = false,
            bubble = bubble,
            panel = snapshot.panel.copy(
                isStreaming = false,
                streamingDelta = "",
                recentResponsePreview = chatText.takeTrimmed(180),
                loadingVerb = "",
            ),
        ) }
    }

    fun setLoadingVerb(verb: String) {
        val snapshot = _state.value
        if (!snapshot.panel.isStreaming && snapshot.buddyState != BuddyState.THINKING) return
        setState(event = "setLoadingVerb") { snapshot.copy(
            panel = snapshot.panel.copy(loadingVerb = verb),
        ) }
    }

    fun onError(message: String) {
        setState(
            event = "onError",
            target = FlightFsm.Error,
        ) { snapshot -> snapshot.copy(
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            bubble = null,
            panel = snapshot.panel.copy(
                isStreaming = false,
                streamingDelta = "",
                loadingVerb = "",
                errorBanner = message,
            ),
        ) }
    }

    fun dismissError() {
        val snapshot = _state.value
        val target = if (snapshot.flightFsm == FlightFsm.Error) FlightFsm.Docked else snapshot.flightFsm
        setState(
            event = "dismissError",
            target = target,
        ) { snapshot.copy(
            panel = snapshot.panel.copy(errorBanner = null),
        ) }
    }

    // ---- buddy flight (populated in Phase 2) --------------------------------

    fun onPreparingPoint(label: String?) {
        setState(
            event = "onPreparingPoint",
            target = FlightFsm.PreparingPoint,
        ) { it.copy(
            mode = OverlayMode.Flying,
            buddyState = BuddyState.PREPARING_POINT,
            isFlying = true,
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::Navigation),
        ) }
    }

    fun onCandidateOptionsAvailable(label: String?, options: CandidateOptions) {
        setState(
            event = "onCandidateOptionsAvailable",
            target = FlightFsm.Pointing,
        ) { current -> current.copy(
            mode = OverlayMode.Pointing,
            buddyState = BuddyState.POINTING,
            isFlying = true,
            candidateOptions = options.copy(visible = options.hasAlternatives),
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::Navigation)
                ?: current.bubble,
        ) }
    }

    fun setCandidateOptions(options: CandidateOptions?) {
        setState(event = "setCandidateOptions") { current ->
            current.copy(candidateOptions = options)
        }
    }

    fun onCandidateOptionPicked(candidateId: String) {
        setState(event = "onCandidateOptionPicked") { current ->
            val options = current.candidateOptions ?: return@setState current
            current.copy(candidateOptions = options.copy(activeCandidateId = candidateId))
        }
    }

    fun onFlyingStart(label: String?) {
        setState(
            event = "onFlyingStart",
            target = FlightFsm.Flying,
        ) { it.copy(
            mode = OverlayMode.Flying,
            buddyState = BuddyState.FLYING,
            isFlying = true,
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::Navigation),
        ) }
    }

    fun onPointingArrived(label: String?) {
        setState(
            event = "onPointingArrived",
            target = FlightFsm.Pointing,
        ) { current -> current.copy(
            mode = OverlayMode.Pointing,
            buddyState = BuddyState.POINTING,
            isFlying = true,
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::Navigation)
                ?: current.bubble,
        ) }
    }

    fun onManualTargetFallbackAvailable(label: String?) {
        setState(
            event = "onManualTargetFallbackAvailable",
            target = FlightFsm.Pointing,
        ) { current -> current.copy(
            mode = OverlayMode.Pointing,
            buddyState = BuddyState.POINTING,
            isFlying = true,
            candidateOptions = null,
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::Navigation)
                ?: current.bubble,
        ) }
    }

    fun onManualTargetSelectionStarted() {
        setState(
            event = "onManualTargetSelectionStarted",
            target = FlightFsm.Pointing,
        ) { it.copy(
            mode = OverlayMode.ManualTargetSelection,
            buddyState = BuddyState.POINTING,
            isFlying = true,
            candidateOptions = null,
            bubble = null,
        ) }
    }

    fun onReturningToDock(reason: String? = null) {
        setState(
            event = "onReturningToDock",
            target = FlightFsm.Returning,
        ) { it.copy(
            mode = OverlayMode.Flying,
            buddyState = BuddyState.CANCELLING,
            isFlying = true,
            bubble = null,
            candidateOptions = null,
            lastFlightCancellationReason = reason ?: it.lastFlightCancellationReason,
        ) }
    }

    fun onPointingReturned() {
        val snapshot = _state.value
        require(
            snapshot.flightFsm == FlightFsm.Returning ||
                snapshot.flightFsm == FlightFsm.Pointing ||
                snapshot.flightFsm == FlightFsm.Flying ||
                snapshot.flightFsm == FlightFsm.PreparingPoint,
        ) {
            "Illegal flight FSM transition ${snapshot.flightFsm} -> ${FlightFsm.Docked} via onPointingReturned"
        }
        _state.value = snapshot.copy(
            mode = OverlayMode.IdleWidget,
            flightFsm = FlightFsm.Docked,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            bubble = null,
            tapForMeConfirmation = null,
            candidateOptions = null,
        )
    }

    // ---- action bubble (Phase 3) --------------------------------------------

    suspend fun requestActionDisclosureReview(): Boolean {
        val id = actionDisclosureRequestIds.getAndIncrement()
        return suspendCancellableCoroutine { cont ->
            actionDisclosureContinuations[id] = cont
            val emitted = _actionDisclosureReviewRequests.tryEmit(
                ActionDisclosureReviewRequest(id = id),
            )
            if (!emitted) {
                actionDisclosureContinuations.remove(id)
                if (cont.isActive) cont.resume(false)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                actionDisclosureContinuations.remove(id)
            }
        }
    }

    fun respondActionDisclosureReview(id: Long, accepted: Boolean) {
        val cont = actionDisclosureContinuations.remove(id) ?: return
        if (cont.isActive) cont.resume(accepted)
    }

    suspend fun requestTapForMeConfirmation(
        targetLabel: String,
        appLabel: String?,
        packageName: String?,
        confirmationLevel: com.handy.core.action.ConfirmationLevel,
        risk: com.handy.core.action.ActionRisk,
        reason: String?,
    ): Boolean =
        requestTapForMeConfirmationDecision(
            targetLabel = targetLabel,
            appLabel = appLabel,
            packageName = packageName,
            confirmationLevel = confirmationLevel,
            risk = risk,
            reason = reason,
            typingText = null,
        ).approved

    suspend fun requestTypeForMeConfirmation(
        targetLabel: String,
        appLabel: String?,
        packageName: String?,
        confirmationLevel: com.handy.core.action.ConfirmationLevel,
        risk: com.handy.core.action.ActionRisk,
        reason: String?,
        typingText: String,
    ): String? =
        requestTapForMeConfirmationDecision(
            targetLabel = targetLabel,
            appLabel = appLabel,
            packageName = packageName,
            confirmationLevel = confirmationLevel,
            risk = risk,
            reason = reason,
            typingText = typingText,
        ).takeIf { it.approved }?.typingText

    private suspend fun requestTapForMeConfirmationDecision(
        targetLabel: String,
        appLabel: String?,
        packageName: String?,
        confirmationLevel: com.handy.core.action.ConfirmationLevel,
        risk: com.handy.core.action.ActionRisk,
        reason: String?,
        typingText: String?,
    ): TapForMeConfirmationDecision {
        val id = tapConfirmationIds.getAndIncrement()
        val request = TapForMeConfirmation(
            id = id,
            targetLabel = targetLabel,
            appLabel = appLabel,
            packageName = packageName,
            confirmationLevel = confirmationLevel,
            risk = risk,
            reason = reason,
            typingText = typingText,
        )
        return suspendCancellableCoroutine { cont ->
            tapConfirmationContinuations[id] = cont
            setState(
                event = "requestTapForMeConfirmation",
                target = FlightFsm.ActionConfirm,
            ) { snapshot -> snapshot.copy(tapForMeConfirmation = request) }
            cont.invokeOnCancellation {
                tapConfirmationContinuations.remove(id)
                clearTapForMeConfirmation(id)
            }
        }
    }

    fun respondTapForMeConfirmation(id: Long, approved: Boolean, typingText: String? = null) {
        val cont = tapConfirmationContinuations.remove(id) ?: return
        clearTapForMeConfirmation(id)
        if (cont.isActive) {
            cont.resume(
                TapForMeConfirmationDecision(
                    approved = approved,
                    typingText = typingText,
                ),
            )
        }
    }

    private fun clearTapForMeConfirmation(id: Long) {
        setState(event = "clearTapForMeConfirmation") { snapshot ->
            if (snapshot.tapForMeConfirmation?.id == id) {
                snapshot.copy(tapForMeConfirmation = null)
            } else {
                snapshot
            }
        }
    }

    fun onActionStarted(label: String) {
        setState(
            event = "onActionStarted",
            target = FlightFsm.Acting,
        ) { it.copy(
            mode = OverlayMode.Acting,
            buddyState = BuddyState.ACTING,
            tapForMeConfirmation = null,
            bubble = BuddyBubble.Action(label),
        ) }
    }

    fun onActionFinished() {
        forceDocked(
            event = "onActionFinished",
        ) { it.copy(
            mode = OverlayMode.IdleWidget,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            bubble = null,
            tapForMeConfirmation = null,
            candidateOptions = null,
        ) }
    }

    fun setPendingConfirmation(req: PanelContent.PendingConfirmation?) {
        val snapshot = _state.value
        val target = when {
            req != null && snapshot.flightFsm != FlightFsm.ActionConfirm -> FlightFsm.ActionConfirm
            req == null && snapshot.flightFsm == FlightFsm.ActionConfirm -> FlightFsm.Docked
            else -> snapshot.flightFsm
        }
        setState(
            event = "setPendingConfirmation",
            target = target,
        ) { snapshot.copy(
            panel = snapshot.panel.copy(pendingConfirmation = req),
        ) }
    }

    // ---- helpers ------------------------------------------------------------

    private fun isLegalTransition(from: FlightFsm, to: FlightFsm): Boolean =
        from == to || when (to) {
            FlightFsm.Docked -> from.canResetToDocked()
            FlightFsm.Listening -> from in setOf(
                FlightFsm.Docked,
                FlightFsm.Pointing,
                FlightFsm.ActionResult,
                FlightFsm.Error,
            )
            FlightFsm.Thinking -> from in setOf(
                FlightFsm.Docked,
                FlightFsm.Listening,
                FlightFsm.ActionResult,
                FlightFsm.Error,
            )
            FlightFsm.Answering -> from in setOf(
                FlightFsm.Docked,
                FlightFsm.Listening,
                FlightFsm.Thinking,
                FlightFsm.ActionConfirm,
                FlightFsm.ActionResult,
                FlightFsm.Error,
            )
            FlightFsm.PreparingPoint -> from in setOf(
                FlightFsm.Docked,
                FlightFsm.Thinking,
                FlightFsm.Answering,
                FlightFsm.Pointing,
                FlightFsm.ActionResult,
            )
            FlightFsm.Flying -> from == FlightFsm.PreparingPoint
            FlightFsm.Pointing -> from in setOf(
                FlightFsm.PreparingPoint,
                FlightFsm.Flying,
                FlightFsm.Pointing,
            )
            FlightFsm.ActionConfirm -> from in setOf(
                FlightFsm.Docked,
                FlightFsm.Thinking,
                FlightFsm.Answering,
                FlightFsm.Pointing,
                FlightFsm.ActionResult,
            )
            FlightFsm.Acting -> from in setOf(
                FlightFsm.Docked,
                FlightFsm.Pointing,
                FlightFsm.ActionConfirm,
                FlightFsm.ActionResult,
            )
            FlightFsm.ActionResult -> from in setOf(
                FlightFsm.Answering,
                FlightFsm.Acting,
                FlightFsm.ActionConfirm,
            )
            FlightFsm.Returning -> from in setOf(
                FlightFsm.PreparingPoint,
                FlightFsm.Flying,
                FlightFsm.Pointing,
                FlightFsm.ActionConfirm,
                FlightFsm.Acting,
                FlightFsm.ActionResult,
            )
            FlightFsm.Error -> from != FlightFsm.Error
        }

    private fun FlightFsm.canResetToDocked(): Boolean =
        this in setOf(
            FlightFsm.Docked,
            FlightFsm.Listening,
            FlightFsm.Thinking,
            FlightFsm.Answering,
            FlightFsm.ActionConfirm,
            FlightFsm.Acting,
            FlightFsm.ActionResult,
            FlightFsm.Returning,
            FlightFsm.Error,
        )

    fun captureSnapshot(
        marksProvider: () -> List<AccessibilityMark> = { emptyList() },
        clock: () -> Long = { System.currentTimeMillis() },
    ): PanelSnapshot? {
        val fg: ForegroundAppSnapshot? = runCatching { foregroundAppMonitor.refreshNow() }
            .onFailure { Timber.w(it, "OverlayPresenter: foreground refresh failed") }
            .getOrNull()
            ?: foregroundAppMonitor.lastKnownSnapshot()
        val context = fg?.let {
            ToolContext(
                packageName = it.packageName,
                appLabel = it.appLabel,
                umbrellaSiteLabel = it.umbrellaSiteLabel,
            )
        } ?: return null
        val marks = runCatching { marksProvider() }.getOrElse {
            Timber.w(it, "OverlayPresenter: marks provider failed")
            emptyList()
        }
        return PanelSnapshot(
            toolContext = context,
            capturedAtEpochMs = clock(),
            marks = marks,
        )
    }

    private fun String.takeTrimmed(n: Int): String {
        val trimmed = trim()
        return if (trimmed.length <= n) trimmed else trimmed.take(n).trimEnd() + "…"
    }
}

private const val FALLBACK_PANEL_GREETING = "What can I help you with?"

internal fun panelGreetingFor(snapshot: PanelSnapshot?): String {
    val context = snapshot?.toolContext ?: return FALLBACK_PANEL_GREETING
    val label = context.displayLabel
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("Handy", ignoreCase = true) }
    specificPanelGreetingFor(context.packageName, label)?.let { return it }
    val category = panelGreetingCategoryFor(context.packageName, context.umbrellaSiteLabel)
    return when (category) {
        PanelGreetingCategory.SETTINGS -> "In Settings. What do you need?"
        PanelGreetingCategory.BROWSER -> label?.let { "Browsing in $it. Need help with this page?" }
            ?: "Browsing the web. Need help with this page?"
        PanelGreetingCategory.EMAIL -> label?.let { "In $it. Want me to read or reply?" }
            ?: "In your inbox. Want me to read or reply?"
        PanelGreetingCategory.MAPS -> label?.let { "In $it. Where to?" }
            ?: "Where to?"
        PanelGreetingCategory.CAMERA -> label?.let { "${it}'s open. Want a photography tip?" }
            ?: "Camera's open. Want a photography tip?"
        PanelGreetingCategory.PHONE -> label?.let { "In $it. Help with this call?" }
            ?: "On a call. Anything I can do?"
        PanelGreetingCategory.SHOPPING -> label?.let { "Shopping in $it. Compare, coupons, or returns?" }
            ?: "Shopping. Compare, coupons, or returns?"
        PanelGreetingCategory.PHOTOS -> label?.let { "In $it. Describe a photo or find one?" }
            ?: "Browsing your photos. Want me to describe one?"
        PanelGreetingCategory.MUSIC -> label?.let { "In $it. Set the mood or queue something?" }
            ?: "Music's on. Set the mood or queue something?"
        PanelGreetingCategory.VIDEO -> label?.let { "In $it. Summarise or pick what's next?" }
            ?: "Watching something. Summarise or pick what's next?"
        PanelGreetingCategory.MESSAGING -> label?.let { "In $it. Draft, summarise, or translate?" }
            ?: "Messaging. Draft, summarise, or translate?"
        PanelGreetingCategory.SOCIAL -> label?.let { "In $it. Summarise the feed or draft a post?" }
            ?: "On social. Summarise the feed or draft a post?"
        PanelGreetingCategory.CALENDAR -> label?.let { "In $it. Find time or summarise a day?" }
            ?: "Planning your day. Find time or summarise?"
        PanelGreetingCategory.NOTES -> label?.let { "In $it. Summarise, expand, or rewrite?" }
            ?: "In your notes. Summarise, expand, or rewrite?"
        // Keep banking neutral on PII-sensitive screens; do not accent the label.
        PanelGreetingCategory.BANKING -> "Banking app open. I'll keep things general."
        PanelGreetingCategory.FOOD -> label?.let { "In $it. Find food or track an order?" }
            ?: "Ordering food. What sounds good?"
        PanelGreetingCategory.RIDE -> label?.let { "In $it. Book a ride or check arrival?" }
            ?: "Hailing a ride. Book or check arrival?"
        PanelGreetingCategory.FILES -> label?.let { "In $it. Find or organise something?" }
            ?: "In Files. Find or organise something?"
        PanelGreetingCategory.DEFAULT -> label?.let { "In $it. What can I help with?" }
            ?: FALLBACK_PANEL_GREETING
    }
}

private fun specificPanelGreetingFor(
    packageName: String?,
    label: String?,
): String? {
    val p = packageName?.lowercase().orEmpty()
    return when {
        label == null -> null
        p.contains("groww") -> "In $label. Bulls, bears, or the bottom line?"
        p.contains("spotify") -> "In $label. Vibes first, skips later?"
        p.contains("netflix") -> "In $label. End the scroll. Pick a winner?"
        else -> null
    }
}
