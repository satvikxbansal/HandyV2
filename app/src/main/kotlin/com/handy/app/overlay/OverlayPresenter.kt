package com.handy.app.overlay

import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.action.UiActionKind
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.BubbleTone
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.BuddyState
import com.handy.core.overlay.CandidateOptions
import com.handy.core.overlay.FlightFsm
import com.handy.core.overlay.OverlayMode
import com.handy.core.overlay.OverlayPanelState
import com.handy.core.overlay.PanelContent
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.overlay.PlanPreview
import com.handy.core.overlay.TapForMeConfirmation
import com.handy.core.overlay.TapForMeConfirmationDecision
import com.handy.core.overlay.WebToolProvider
import com.handy.core.speech.SpeechAudioState
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

enum class PanelContextRefreshStartResult {
    Ready,
    Deferred,
    Ignored,
}

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
    private var manualTargetFallbackCandidates: List<ManualTargetSelector.Candidate> = emptyList()

    private fun setState(
        event: String,
        target: FlightFsm? = null,
        reducer: (OverlayPanelState) -> OverlayPanelState,
    ) {
        val snapshot = _state.value
        val nextFsm = target ?: snapshot.flightFsm
        if (nextFsm != snapshot.flightFsm) {
            if (!isLegalTransition(snapshot.flightFsm, nextFsm)) {
                Timber.w(
                    "OverlayPresenter: illegal flight FSM transition %s -> %s via %s; dropped",
                    snapshot.flightFsm,
                    nextFsm,
                    event,
                )
                return
            }
        }
        val next = reducer(snapshot).copy(flightFsm = nextFsm)
        if (!attemptTransition(event, snapshot.buddyState, next.buddyState)) return
        _state.value = next
    }

    private fun forceDocked(
        event: String,
        reducer: (OverlayPanelState) -> OverlayPanelState,
    ) {
        setState(
            event = event,
            target = FlightFsm.Docked,
            reducer = reducer,
        )
    }

    private fun attemptTransition(event: String, from: BuddyState, to: BuddyState): Boolean {
        if (OverlayPresenterFsm.canTransition(from, to)) return true
        Timber.w(
            "OverlayPresenter: illegal BuddyState transition %s -> %s via %s; dropped",
            from,
            to,
            event,
        )
        return false
    }

    private fun clearManualTargetCandidates() {
        manualTargetFallbackCandidates = emptyList()
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
        clearManualTargetCandidates()
        setState(event = "onWidgetTap") { it.copy(
            mode = OverlayMode.ChatPanel,
            buddyState = when (it.buddyState) {
                BuddyState.THINKING,
                BuddyState.STREAMING -> it.buddyState
                else -> BuddyState.DOCKED
            },
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
        clearManualTargetCandidates()
        setState(
            event = "onWidgetLongPressArmed",
            target = FlightFsm.Listening,
        ) { current -> current.copy(
            buddyState = BuddyState.LISTENING,
            isFlying = false,
            bubble = BuddyBubble.transcript(""),
            panel = current.panel.copy(
                snapshot = snapshot ?: current.panel.snapshot,
                isListening = true,
                partialTranscript = "",
                voiceNotice = "",
                lowConfidenceTranscript = null,
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
        clearManualTargetCandidates()
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

    fun onSpeechAudio(audioState: SpeechAudioState) {
        setState(event = "onSpeechAudio($audioState)") { snapshot ->
            val shouldClearSpokenBubble =
                audioState == SpeechAudioState.IDLE &&
                    snapshot.buddyState in setOf(BuddyState.AUDIO_SPEAKING, BuddyState.SPEAKING) &&
                    snapshot.bubble?.isPlainSpokenAnswer() == true
            snapshot.copy(
                audioState = audioState,
                buddyState = snapshot.buddyStateForAudio(audioState),
                bubble = if (shouldClearSpokenBubble) null else snapshot.bubble,
            )
        }
    }

    // ---- voice + chat wiring ------------------------------------------------

    fun updatePartialTranscript(partial: String) {
        val snapshot = _state.value
        if (!snapshot.panel.isListening && snapshot.buddyState != BuddyState.LISTENING) return
        setState(event = "updatePartialTranscript") { snapshot.copy(
            panel = snapshot.panel.copy(partialTranscript = partial),
            bubble = if (partial.isNotBlank()) BuddyBubble.transcript(partial) else snapshot.bubble,
        ) }
    }

    fun updateVoiceNotice(message: String) {
        val snapshot = _state.value
        val voiceDrainActive = snapshot.buddyState == BuddyState.LISTENING ||
            snapshot.buddyState == BuddyState.THINKING
        if (!snapshot.panel.isListening && !voiceDrainActive) return
        val cleaned = message.trim()
        setState(event = "updateVoiceNotice") { snapshot.copy(
            panel = snapshot.panel.copy(voiceNotice = cleaned),
            bubble = if (cleaned.isNotBlank()) actionNoticeBubble(cleaned) else snapshot.bubble,
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
                voiceNotice = "",
                lowConfidenceTranscript = null,
            ),
            bubble = BuddyBubble.transcript(""),
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
                voiceNotice = "",
                isStreaming = !transcript.isNullOrBlank(),
                lowConfidenceTranscript = if (transcript.isNullOrBlank()) {
                    snapshot.panel.lowConfidenceTranscript
                } else {
                    null
                },
            ),
            bubble = if (transcript.isNullOrBlank()) null else snapshot.bubble,
        ) }
    }

    fun onLowConfidenceTranscript(best: String, alternatives: List<String>) {
        val cleanedBest = best.trim()
        val cleanedAlternatives = alternatives
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(cleanedBest, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(2)
        if (cleanedBest.isBlank()) return
        setState(
            event = "onLowConfidenceTranscript",
            target = FlightFsm.Docked,
        ) { snapshot -> snapshot.copy(
            mode = OverlayMode.ChatPanel,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            panel = snapshot.panel.copy(
                isListening = false,
                partialTranscript = cleanedBest,
                voiceNotice = "",
                draftInput = cleanedBest,
                isStreaming = false,
                streamingDelta = "",
                loadingVerb = "",
                errorBanner = null,
                lowConfidenceTranscript = PanelContent.LowConfidenceTranscript(
                    best = cleanedBest,
                    alternatives = cleanedAlternatives,
                ),
            ),
            bubble = null,
        ) }
    }

    fun clearLowConfidenceTranscript() {
        setState(event = "clearLowConfidenceTranscript") { snapshot -> snapshot.copy(
            panel = snapshot.panel.copy(
                draftInput = "",
                lowConfidenceTranscript = null,
            ),
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
                lowConfidenceTranscript = null,
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
    fun onResponseFinalized(overlayClamped: String?, chatText: String, fromVoice: Boolean = false) {
        val bubble = overlayClamped
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { fromVoice }
            ?.let(BuddyBubble::spokenAnswer)
        val target = if (bubble != null) FlightFsm.ActionResult else FlightFsm.Docked
        setState(
            event = "onResponseFinalized",
            target = target,
        ) { snapshot -> snapshot.copy(
            buddyState = if (snapshot.audioState == SpeechAudioState.SPEAKING) {
                BuddyState.AUDIO_SPEAKING
            } else if (bubble != null) {
                BuddyState.SPEAKING
            } else {
                BuddyState.DOCKED
            },
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

    fun onThinkingBubble() {
        setState(event = "onThinkingBubble") { it.copy(
            bubble = BuddyBubble.thinking(),
        ) }
    }

    fun onWebToolBubble(provider: WebToolProvider, providerLabel: String) {
        setState(event = "onWebToolBubble") { it.copy(
            bubble = BuddyBubble.webTool(provider, providerLabel),
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
                voiceNotice = "",
                lowConfidenceTranscript = null,
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
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::navigation),
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
            bubble = if (options.hasAlternatives) {
                BuddyBubble.ambiguous(
                    prefix = "Which one?",
                    label = "${options.options.size} matches for \"${label.orEmpty().ifBlank { "target" }}\"",
                )
            } else {
                label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::navigation)
            }
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
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::navigation),
        ) }
    }

    fun onFlightStartBubble(targetLabel: String) {
        setState(event = "onFlightStartBubble") { it.copy(
            bubble = BuddyBubble.navigation("Going to \"$targetLabel\" →"),
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
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::navigation)
                ?: current.bubble,
        ) }
    }

    fun onPointingArrivedBubble(targetLabel: String) {
        setState(event = "onPointingArrivedBubble") { it.copy(
            bubble = BuddyBubble.navigation("Tap \"$targetLabel\""),
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
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::navigation)
                ?: current.bubble,
        ) }
    }

    fun onManualTargetCandidatesReady(
        label: String?,
        candidates: List<ManualTargetSelector.Candidate>,
    ) {
        val hasLabel = !label.isNullOrBlank()
        manualTargetFallbackCandidates = candidates.toList()
        Timber.d(
            "OverlayPresenter: manual target candidates ready hasLabel=%s count=%d",
            hasLabel,
            candidates.size,
        )
    }

    fun consumeManualTargetCandidates(): List<ManualTargetSelector.Candidate> {
        val candidates = manualTargetFallbackCandidates
        clearManualTargetCandidates()
        return candidates
    }

    fun onManualTargetSelectionStarted() {
        clearManualTargetCandidates()
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
        clearManualTargetCandidates()
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
        clearManualTargetCandidates()
        setState(
            event = "onPointingReturned",
            target = FlightFsm.Docked,
        ) { snapshot -> snapshot.copy(
            mode = OverlayMode.IdleWidget,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            bubble = null,
            tapForMeConfirmation = null,
            candidateOptions = null,
        ) }
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
        planPreview: PlanPreview? = null,
    ): Boolean =
        requestTapForMeConfirmationDecision(
            targetLabel = targetLabel,
            appLabel = appLabel,
            packageName = packageName,
            confirmationLevel = confirmationLevel,
            risk = risk,
            reason = reason,
            typingText = null,
            planPreview = planPreview,
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
            planPreview = null,
        ).takeIf { it.approved }?.typingText

    private suspend fun requestTapForMeConfirmationDecision(
        targetLabel: String,
        appLabel: String?,
        packageName: String?,
        confirmationLevel: com.handy.core.action.ConfirmationLevel,
        risk: com.handy.core.action.ActionRisk,
        reason: String?,
        typingText: String?,
        planPreview: PlanPreview?,
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
            planPreview = planPreview,
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
            bubble = actionNoticeBubble(label),
        ) }
    }

    fun onActionInProgressBubble(
        kind: UiActionKind,
        targetLabel: String,
        progress: Float?,
    ) {
        val label = when (kind) {
            UiActionKind.TAP,
            UiActionKind.LONG_PRESS -> "Tapping \"$targetLabel\"…"
            UiActionKind.TYPE -> "Typing in \"$targetLabel\"…"
            UiActionKind.SCROLL_UP,
            UiActionKind.SCROLL_DOWN,
            UiActionKind.SCROLL_LEFT,
            UiActionKind.SCROLL_RIGHT -> "Scrolling…"
        }
        val bubble = if (kind == UiActionKind.TYPE) {
            BuddyBubble.actingType(label, progress)
        } else {
            BuddyBubble.actingTap(label, progress)
        }
        setState(event = "onActionInProgressBubble") { it.copy(bubble = bubble) }
    }

    fun onRecipeStepBubble(stepIndex: Int, stepCount: Int, label: String) {
        setState(event = "onRecipeStepBubble") { it.copy(
            bubble = BuddyBubble.recipeStep(stepIndex, stepCount, label),
        ) }
    }

    fun onBlockedBubble(reason: String) {
        val label = when (reason) {
            "incognito" -> "Blocked · Incognito mode"
            "secure-window" -> "Blocked · Secure window"
            "tool-suggestion-only" -> "Blocked · Tool-suggested action"
            else -> "Blocked · $reason"
        }
        setState(event = "onBlockedBubble") { it.copy(
            bubble = BuddyBubble.blocked(label),
        ) }
    }

    fun onActionFailedBubble(prefix: String, label: String) {
        setState(event = "onActionFailedBubble") { it.copy(
            bubble = BuddyBubble.failed(prefix, label),
        ) }
    }

    fun onWrongTargetBubble() {
        setState(event = "onWrongTargetBubble") { it.copy(
            bubble = BuddyBubble.wrongTarget(),
        ) }
    }

    fun onAmbiguousTargetBubble(matchCount: Int, targetLabel: String) {
        setState(event = "onAmbiguousTargetBubble") { it.copy(
            bubble = BuddyBubble.ambiguous(
                prefix = "Which one?",
                label = "$matchCount matches for \"$targetLabel\"",
            ),
        ) }
    }

    fun onActionFinished() {
        clearManualTargetCandidates()
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

    fun beginPanelContextRefresh(foreground: ForegroundAppSnapshot): PanelContextRefreshStartResult {
        val snapshot = _state.value
        if (snapshot.mode != OverlayMode.ChatPanel) return PanelContextRefreshStartResult.Ignored
        if (snapshot.hasSamePanelToolContext(foreground)) {
            clearPanelContextRefresh()
            return PanelContextRefreshStartResult.Ignored
        }
        if (!snapshot.canRefreshPanelContextNow()) return PanelContextRefreshStartResult.Deferred

        val previewSnapshot = PanelSnapshot(
            toolContext = foreground.toToolContext(),
            capturedAtEpochMs = System.currentTimeMillis(),
        )
        setState(event = "beginPanelContextRefresh") { current -> current.copy(
            panel = current.panel.copy(
                contextRefreshInProgress = true,
                contextRefreshPreviewGreeting = panelGreetingFor(previewSnapshot),
                contextRefreshPreviewLabel = previewSnapshot.toolContext.displayLabel,
            ),
        ) }
        return PanelContextRefreshStartResult.Ready
    }

    fun applyPanelContextRefresh(
        foreground: ForegroundAppSnapshot,
        marks: List<AccessibilityMark>,
        clock: () -> Long = { System.currentTimeMillis() },
    ): Boolean {
        val snapshot = _state.value
        if (snapshot.mode != OverlayMode.ChatPanel) {
            clearPanelContextRefresh()
            return true
        }
        if (snapshot.hasSamePanelToolContext(foreground)) {
            clearPanelContextRefresh()
            return true
        }
        if (!snapshot.canRefreshPanelContextNow()) {
            clearPanelContextRefresh()
            return false
        }
        val newSnapshot = PanelSnapshot(
            toolContext = foreground.toToolContext(),
            capturedAtEpochMs = clock(),
            marks = marks,
        )
        setState(event = "applyPanelContextRefresh") { current -> current.copy(
            panel = current.panel.copy(
                snapshot = newSnapshot,
                greeting = panelGreetingFor(newSnapshot),
                contextRefreshInProgress = false,
                contextRefreshPreviewGreeting = null,
                contextRefreshPreviewLabel = null,
            ),
        ) }
        Timber.d(
            "OverlayPresenter.applyPanelContextRefresh: label=%s pkg=%s site=%s marks=%d",
            newSnapshot.toolContext.displayLabel,
            newSnapshot.toolContext.packageName,
            newSnapshot.toolContext.umbrellaSiteLabel,
            marks.size,
        )
        return true
    }

    fun clearPanelContextRefresh() {
        val snapshot = _state.value
        if (!snapshot.panel.contextRefreshInProgress &&
            snapshot.panel.contextRefreshPreviewGreeting == null &&
            snapshot.panel.contextRefreshPreviewLabel == null
        ) return
        setState(event = "clearPanelContextRefresh") { current -> current.copy(
            panel = current.panel.copy(
                contextRefreshInProgress = false,
                contextRefreshPreviewGreeting = null,
                contextRefreshPreviewLabel = null,
            ),
        ) }
    }

    // ---- helpers ------------------------------------------------------------

    private fun OverlayPanelState.canRefreshPanelContextNow(): Boolean {
        if (mode != OverlayMode.ChatPanel) return false
        if (audioState != SpeechAudioState.IDLE) return false
        if (buddyState != BuddyState.DOCKED) return false
        if (flightFsm != FlightFsm.Docked) return false
        if (isFlying) return false
        if (tapForMeConfirmation != null) return false
        if (candidateOptions != null) return false
        if (manualTargetFallbackCandidates.isNotEmpty()) return false
        return !panel.isListening &&
            !panel.isStreaming &&
            panel.pendingConfirmation == null &&
            panel.lowConfidenceTranscript == null
    }

    private fun OverlayPanelState.hasSamePanelToolContext(
        foreground: ForegroundAppSnapshot,
    ): Boolean {
        val context = panel.snapshot?.toolContext ?: return false
        return context.packageName == foreground.packageName &&
            context.umbrellaSiteLabel == foreground.umbrellaSiteLabel
    }

    private fun ForegroundAppSnapshot.toToolContext(): ToolContext =
        ToolContext(
            packageName = packageName,
            appLabel = appLabel,
            umbrellaSiteLabel = umbrellaSiteLabel,
        )

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
            FlightFsm.Pointing,
            FlightFsm.Returning,
            FlightFsm.Error,
        )

    private fun OverlayPanelState.buddyStateForAudio(audioState: SpeechAudioState): BuddyState =
        when (audioState) {
            SpeechAudioState.SPEAKING -> {
                if (isFlying || buddyState in audioProtectedBuddyStates) {
                    buddyState
                } else {
                    BuddyState.AUDIO_SPEAKING
                }
            }
            SpeechAudioState.IDLE,
            SpeechAudioState.STOPPING,
            SpeechAudioState.ERROR -> {
                if (buddyState == BuddyState.AUDIO_SPEAKING) BuddyState.DOCKED else buddyState
            }
            SpeechAudioState.PREPARING -> buddyState
        }

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

    private fun actionNoticeBubble(label: String, progress: Float? = null): BuddyBubble {
        val lower = label.lowercase()
        return if ("type" in lower || "typing" in lower) {
            BuddyBubble.actingType(label, progress)
        } else {
            BuddyBubble.actingTap(label, progress)
        }
    }

    private fun BuddyBubble.isPlainSpokenAnswer(): Boolean =
        tone == BubbleTone.ACCENT &&
            prefix == null &&
            leading == null &&
            progress == null &&
            !italic &&
            !small

    private companion object {
        val audioProtectedBuddyStates = setOf(
            BuddyState.PREPARING_POINT,
            BuddyState.FLYING,
            BuddyState.POINTING,
            BuddyState.CANCELLING,
            BuddyState.ACTING,
            BuddyState.DRAGGING,
        )
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
