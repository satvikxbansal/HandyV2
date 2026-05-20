package com.handy.app.overlay

import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.BuddyState
import com.handy.core.overlay.OverlayMode
import com.handy.core.overlay.OverlayPanelState
import com.handy.core.overlay.PanelContent
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.prompts.QuickPromptCatalog
import com.handy.core.tool.ToolContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

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
 */
@Singleton
class OverlayPresenter @Inject constructor(
    private val foregroundAppMonitor: HandyForegroundAppMonitor,
) {

    private val _state = MutableStateFlow(OverlayPanelState())
    val state: StateFlow<OverlayPanelState> = _state.asStateFlow()

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
        val category = QuickPromptCatalog.categorize(snapshot?.toolContext?.packageName)
        val prompts = QuickPromptCatalog.promptsFor(category)
        val greeting = QuickPromptCatalog.greetingFor(
            snapshot?.toolContext?.appLabel,
            category,
        )
        _state.value = _state.value.copy(
            mode = OverlayMode.ChatPanel,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            panel = PanelContent(
                snapshot = snapshot,
                quickPrompts = prompts,
                greeting = greeting,
            ),
            bubble = null,
        )
        Timber.d(
            "OverlayPresenter: panel open, pkg=%s category=%s marks=%d",
            snapshot?.toolContext?.packageName,
            category,
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
        _state.value = _state.value.copy(
            buddyState = BuddyState.LISTENING,
            isFlying = false,
            bubble = BuddyBubble.Transcript(""),
            panel = _state.value.panel.copy(
                snapshot = snapshot ?: _state.value.panel.snapshot,
                isListening = true,
                partialTranscript = "",
            ),
        )
    }

    fun onWidgetDragStart() {
        _state.value = _state.value.copy(
            buddyState = BuddyState.DRAGGING,
            isFlying = false,
            bubble = null,
        )
    }

    fun onWidgetIdle() {
        _state.value = _state.value.copy(
            mode = OverlayMode.IdleWidget,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            bubble = null,
            panel = PanelContent(),
        )
    }

    fun onWidgetThinking() {
        _state.value = _state.value.copy(
            buddyState = BuddyState.THINKING,
            isFlying = false,
            bubble = null,
        )
    }

    fun dismissPanel() {
        val snapshot = _state.value
        if (snapshot.mode != OverlayMode.ChatPanel) return
        _state.value = snapshot.copy(
            mode = OverlayMode.IdleWidget,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            panel = PanelContent(),
            bubble = snapshot.bubble,
        )
    }

    // ---- voice + chat wiring ------------------------------------------------

    fun updatePartialTranscript(partial: String) {
        val snapshot = _state.value
        if (!snapshot.panel.isListening && snapshot.buddyState != BuddyState.LISTENING) return
        _state.value = snapshot.copy(
            panel = snapshot.panel.copy(partialTranscript = partial),
            bubble = if (partial.isNotBlank()) BuddyBubble.Transcript(partial) else snapshot.bubble,
        )
    }

    fun onPanelVoiceStarted() {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            buddyState = BuddyState.LISTENING,
            panel = snapshot.panel.copy(
                isListening = true,
                partialTranscript = "",
            ),
            bubble = BuddyBubble.Transcript(""),
        )
    }

    fun onVoiceFinalized(transcript: String?) {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            buddyState = if (transcript.isNullOrBlank()) BuddyState.DOCKED else BuddyState.THINKING,
            isFlying = false,
            panel = snapshot.panel.copy(
                isListening = false,
                partialTranscript = transcript.orEmpty(),
                isStreaming = !transcript.isNullOrBlank(),
            ),
            bubble = if (transcript.isNullOrBlank()) null else snapshot.bubble,
        )
    }

    fun onStreamingStart() {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            buddyState = BuddyState.STREAMING,
            isFlying = false,
            panel = snapshot.panel.copy(
                isStreaming = true,
                streamingDelta = "",
            ),
            bubble = null,
        )
    }

    fun onStreamingDelta(accumulated: String) {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            panel = snapshot.panel.copy(streamingDelta = accumulated),
        )
    }

    /**
     * Final assistant text for the response bubble. [overlayClamped]
     * must already be <= 110 chars (guardrails /
     * `AssistantMarkupParser.clampVoiceSpokenForOverlay`).
     */
    fun onResponseFinalized(overlayClamped: String?, chatText: String) {
        val snapshot = _state.value
        val bubble = overlayClamped
            ?.takeIf { it.isNotBlank() }
            ?.let(BuddyBubble::Response)
        _state.value = snapshot.copy(
            buddyState = if (bubble != null) BuddyState.SPEAKING else BuddyState.DOCKED,
            isFlying = false,
            bubble = bubble,
            panel = snapshot.panel.copy(
                isStreaming = false,
                streamingDelta = "",
                recentResponsePreview = chatText.takeTrimmed(180),
                loadingVerb = "",
            ),
        )
    }

    fun setLoadingVerb(verb: String) {
        val snapshot = _state.value
        if (!snapshot.panel.isStreaming && snapshot.buddyState != BuddyState.THINKING) return
        _state.value = snapshot.copy(
            panel = snapshot.panel.copy(loadingVerb = verb),
        )
    }

    fun onError(message: String) {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            bubble = null,
            panel = snapshot.panel.copy(
                isStreaming = false,
                streamingDelta = "",
                loadingVerb = "",
                errorBanner = message,
            ),
        )
    }

    fun dismissError() {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            panel = snapshot.panel.copy(errorBanner = null),
        )
    }

    // ---- buddy flight (populated in Phase 2) --------------------------------

    fun onPreparingPoint(label: String?) {
        _state.value = _state.value.copy(
            mode = OverlayMode.Flying,
            buddyState = BuddyState.PREPARING_POINT,
            isFlying = true,
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::Navigation),
        )
    }

    fun onFlyingStart(label: String?) {
        _state.value = _state.value.copy(
            mode = OverlayMode.Flying,
            buddyState = BuddyState.FLYING,
            isFlying = true,
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::Navigation),
        )
    }

    fun onPointingArrived(label: String?) {
        _state.value = _state.value.copy(
            mode = OverlayMode.Pointing,
            buddyState = BuddyState.POINTING,
            isFlying = true,
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::Navigation)
                ?: _state.value.bubble,
        )
    }

    fun onManualTargetFallbackAvailable(label: String?) {
        _state.value = _state.value.copy(
            mode = OverlayMode.Pointing,
            buddyState = BuddyState.POINTING,
            isFlying = true,
            bubble = label?.takeIf { it.isNotBlank() }?.let(BuddyBubble::Navigation)
                ?: _state.value.bubble,
        )
    }

    fun onManualTargetSelectionStarted() {
        _state.value = _state.value.copy(
            mode = OverlayMode.ManualTargetSelection,
            buddyState = BuddyState.POINTING,
            isFlying = true,
            bubble = null,
        )
    }

    fun onPointingReturned() {
        _state.value = _state.value.copy(
            mode = OverlayMode.IdleWidget,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            bubble = null,
        )
    }

    // ---- action bubble (Phase 3) --------------------------------------------

    fun onActionStarted(label: String) {
        _state.value = _state.value.copy(
            mode = OverlayMode.Acting,
            buddyState = BuddyState.ACTING,
            bubble = BuddyBubble.Action(label),
        )
    }

    fun onActionFinished() {
        _state.value = _state.value.copy(
            mode = OverlayMode.IdleWidget,
            buddyState = BuddyState.DOCKED,
            isFlying = false,
            bubble = null,
        )
    }

    fun setPendingConfirmation(req: PanelContent.PendingConfirmation?) {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            panel = snapshot.panel.copy(pendingConfirmation = req),
        )
    }

    // ---- helpers ------------------------------------------------------------

    fun captureSnapshot(
        marksProvider: () -> List<AccessibilityMark> = { emptyList() },
        clock: () -> Long = { System.currentTimeMillis() },
    ): PanelSnapshot? {
        val fg: ForegroundAppSnapshot? = runCatching { foregroundAppMonitor.refreshNow() }
            .onFailure { Timber.w(it, "OverlayPresenter: foreground refresh failed") }
            .getOrNull()
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
