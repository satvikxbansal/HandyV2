package com.handy.app.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.app.screen.ScreenContextBuilder
import com.handy.app.voice.SpeechOutputController
import com.handy.app.voice.VoiceController
import com.handy.core.foreground.ForegroundAppMonitor
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmSessionBudget
import com.handy.core.llm.ToolProvenance
import com.handy.core.llm.ToolRunner
import com.handy.core.llm.availableTools
import com.handy.core.model.ChatMessage
import com.handy.core.model.HandySettings
import com.handy.core.model.LoadingVerbs
import com.handy.core.model.MessageRole
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.orchestrator.ConversationOrchestrator
import com.handy.core.orchestrator.OrchestrationEvent
import com.handy.core.orchestrator.OrchestrationRequest
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.KeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Chat screen state holder.
 *
 * Phase A (parity with V1 [MessageBubbleView], [HandyManager]):
 *  - exposes a rotating loading verb (2.5s cadence, frozen while a tool
 *    is in-flight),
 *  - surfaces error turns as a local overlay (failed assistant bubble +
 *    system-role error bubble) so the user never sees the chat "eat" a
 *    turn silently,
 *  - carries placeholder `currentToolName` / `voiceState` / ``
 *    `pendingTranscript` fields that Phases B and C drive for real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmClient: LlmClient,
    private val historyStore: ChatHistoryStore,
    private val settings: DataStoreSettings,
    private val voiceController: VoiceController,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val toolRunner: ToolRunner,
    private val keyStore: KeyStore,
    private val confirmationBroker: ChatConfirmationBroker,
    private val accessibilityStateMonitor: AccessibilityStateMonitor,
    private val chatTargetHandoffStore: ChatTargetHandoffStore,
    private val screenContextBuilder: ScreenContextBuilder,
    private val llmSessionBudget: LlmSessionBudget,
    private val speechOutputController: SpeechOutputController,
) : ViewModel() {

    private val orchestrator = ConversationOrchestrator(
        llmClient = llmClient,
        historyStore = historyStore,
        toolRunner = toolRunner,
    )

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /**
     * Source of truth for "which tool are we chatting about?". Driven by
     * [ForegroundAppMonitor] and by the in-bar "Change" override. The
     * history subscription below uses `flatMapLatest` over this flow so
     * a tool-change automatically cancels the old `historyStore.observe`
     * and starts a fresh one on the new key.
     */
    private val toolContextFlow = MutableStateFlow(DEFAULT_TOOL)
    private var targetSnapshot: PanelSnapshot? = null
    private var boundTargetHandoffId: String? = null

    private var sendJob: Job? = null
    private var verbRotationJob: Job? = null
    private var showInAppActionCounter: Long = 0L
    private var pendingVoiceTurnRequestId: String? = null

    init {
        _state.value = _state.value.copy(brainReady = computeBrainReady())
        viewModelScope.launch {
            toolContextFlow
                .flatMapLatest { ctx -> historyStore.observe(ctx.historyKey) }
                .collectLatest { messages ->
                    _state.value = _state.value.copy(messages = messages)
                }
        }
        viewModelScope.launch {
            settings.flow.collectLatest { s ->
                _state.value = _state.value.copy(settings = s)
            }
        }
        viewModelScope.launch {
            llmSessionBudget.state.collectLatest { budget ->
                _state.value = _state.value.copy(
                    remainingSessionTokens = budget.remainingTokens,
                    sessionBudgetRunningLow = budget.isRunningLow,
                    sessionBudgetExhausted = budget.isExhausted,
                )
            }
        }
        // Mirror the mic's live partial into the composer so words stream
        // in as they are recognised. Mirrors macOS
        // `HandyManager.startVoiceInput` → `pendingTranscript`
        // (`HandyManager.swift` lines 1078–1081).
        viewModelScope.launch {
            voiceController.latestPartial.collectLatest { partial ->
                if (_state.value.voiceState == VoiceUiState.LISTENING) {
                    _state.value = _state.value.copy(pendingTranscript = partial)
                }
            }
        }
        viewModelScope.launch {
            voiceController.latestNotice.collectLatest { notice ->
                if (_state.value.voiceState == VoiceUiState.LISTENING ||
                    _state.value.voiceState == VoiceUiState.PROCESSING
                ) {
                    _state.value = _state.value.copy(voiceNotice = notice)
                }
            }
        }
        // Foreground-app detection → tool-memory swap. Mirrors macOS
        // `HandyManager.resolveToolNameWithAutoSwitch`
        // (`HandyManager.swift` lines 596–674).
        //
        // Fallback for cold-start: when the chat opens without a
        // preceding widget tap (launcher shortcut, notification,
        // Android-recent-apps), the accessibility event buffer may be
        // empty. Ask the monitor to look at the currently-visible
        // windows. When nothing sticks (launcher, secure window, no
        // accessibility service), the bar stays hidden — matches the
        // user spec "when Handy is opened from the app icon, don't
        // show the detecting-app row".
        viewModelScope.launch {
            foregroundAppMonitor.refreshNow()
        }
        viewModelScope.launch {
            foregroundAppMonitor.flow.collectLatest { snapshot ->
                if (targetSnapshot != null) {
                    Timber.d("ChatViewModel: ignoring foreground swap while target handoff is bound")
                    return@collectLatest
                }
                val ctx = ToolContext(
                    packageName = snapshot.packageName,
                    appLabel = snapshot.appLabel,
                    umbrellaSiteLabel = snapshot.umbrellaSiteLabel,
                )
                Timber.d(
                    "ChatViewModel: tool swap → %s (pkg=%s site=%s)",
                    ctx.displayLabel, ctx.packageName, ctx.umbrellaSiteLabel,
                )
                toolContextFlow.value = ctx
                _state.value = _state.value.copy(
                    currentToolName = ctx.displayLabel,
                    toolDetectionState = ToolDetectionState.DETECTED,
                )
            }
        }
        // Mirror the confirmation broker's pending request into UI
        // state. The chat sheet reads this to know when to render.
        viewModelScope.launch {
            confirmationBroker.pending.collectLatest { pending ->
                _state.value = _state.value.copy(pendingConfirmation = pending)
            }
        }
        // Accessibility gate: the foreground-app monitor and pointer
        // resolver only work when Handy's AccessibilityService is
        // enabled. Surface the live state so the chat can render an
        // amber nudge banner when it's off, and proactively refresh
        // foreground on the false→true edge (the user just came back
        // from Accessibility settings — their prior app is now
        // visible to us through `windows()`).
        viewModelScope.launch {
            var lastSeen: Boolean? = null
            accessibilityStateMonitor.isEnabled.collectLatest { enabled ->
                _state.value = _state.value.copy(accessibilityServiceEnabled = enabled)
                if (enabled && lastSeen == false && targetSnapshot == null) {
                    Timber.d("ChatViewModel: a11y flipped on — refreshing foreground app")
                    foregroundAppMonitor.refreshNow()
                }
                lastSeen = enabled
            }
        }
    }

    fun refreshBrainReady() {
        _state.value = _state.value.copy(brainReady = computeBrainReady())
    }

    fun bindTargetHandoff(id: String?) {
        val normalized = id?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            boundTargetHandoffId = null
            targetSnapshot = null
            return
        }
        if (normalized == boundTargetHandoffId) return
        val snapshot = chatTargetHandoffStore.get(normalized) ?: run {
            boundTargetHandoffId = normalized
            targetSnapshot = null
            return
        }
        boundTargetHandoffId = normalized
        targetSnapshot = snapshot
        val ctx = snapshot.toolContext
        Timber.d(
            "ChatViewModel: bound target handoff → %s (pkg=%s marks=%d)",
            ctx.displayLabel,
            ctx.packageName,
            snapshot.marks.size,
        )
        toolContextFlow.value = ctx
        _state.value = _state.value.copy(
            currentToolName = ctx.displayLabel,
            toolDetectionState = ToolDetectionState.DETECTED,
        )
    }

    /**
     * Starts a push-to-talk session in the chat composer. Mirrors the
     * macOS contract from `HandyManager.startVoiceInput`
     * (`HandyManager.swift` lines 1062–1106): clear pending transcript,
     * flip UI into LISTENING, let the controller emit partials; the mic
     * stays open until [stopVoice] or [cancelVoice] is called.
     *
     * Returns silently and surfaces an error banner when the controller
     * refuses to start (missing RECORD_AUDIO or an already-active
     * session) — the banner copy matches the permission-path rule in
     * `.cursor/rules/10-handy-project-guardrails.mdc` → "Error-message
     * strings".
     */
    fun startVoice() {
        if (_state.value.voiceState != VoiceUiState.IDLE) {
            Timber.d("startVoice: refusing, state=%s", _state.value.voiceState)
            return
        }
        val ok = voiceController.start()
        val recovered = if (!ok && voiceController.state.value == VoiceController.State.LISTENING) {
            Timber.d("startVoice: cancelling stale shared voice session before retry")
            voiceController.cancel()
            voiceController.start()
        } else {
            ok
        }
        if (!recovered) {
            _state.value = _state.value.copy(
                errorBanner = "Microphone permission denied. Tap the widget → Settings → grant microphone access.",
            )
            return
        }
        _state.value = _state.value.copy(
            voiceState = VoiceUiState.LISTENING,
            pendingTranscript = "",
            voiceNotice = "",
            errorBanner = null,
        )
    }

    /**
     * Ends the push-to-talk session and, if the recognizer produced any
     * text, auto-sends it through [send] with `fromVoice = true`. An
     * empty transcript is a silent no-op — no bubble, no banner. Mirrors
     * macOS `HandyManager.stopVoiceInput`
     * (`HandyManager.swift` lines 1108–1132).
     */
    fun stopVoice() {
        if (_state.value.voiceState != VoiceUiState.LISTENING) {
            Timber.d("stopVoice: not listening (state=%s) — ignoring", _state.value.voiceState)
            return
        }
        // Move straight to PROCESSING while we drain the recognizer's
        // buffered final. PROCESSING keeps the amber status dot on and
        // disables the composer so the user can't double-fire.
        _state.value = _state.value.copy(voiceState = VoiceUiState.PROCESSING)

        viewModelScope.launch {
            val transcript = voiceController.stopAndAwaitFinal()
            if (voiceController.consumeLastPointingCorrectionHandled()) {
                _state.value = _state.value.copy(
                    voiceState = VoiceUiState.IDLE,
                    pendingTranscript = "",
                    voiceNotice = "",
                )
                return@launch
            }
            if (voiceController.consumeLastLowConfidenceTranscriptHandled()) {
                _state.value = _state.value.copy(
                    voiceState = VoiceUiState.IDLE,
                    pendingTranscript = "",
                    voiceNotice = "",
                )
                return@launch
            }
            Timber.d("stopVoice: final transcript chars=%d", transcript?.length ?: 0)
            if (transcript.isNullOrBlank()) {
                val error = voiceController.consumeLastError()
                _state.value = _state.value.copy(
                    voiceState = VoiceUiState.IDLE,
                    pendingTranscript = "",
                    voiceNotice = "",
                    errorBanner = error ?: _state.value.errorBanner,
                )
                return@launch
            }
            // Non-empty transcript → auto-send. `send(...)` keeps
            // `voiceState = PROCESSING` until `AssistantTurnFinalized`.
            _state.value = _state.value.copy(pendingTranscript = "", voiceNotice = "")
            send(transcript, fromVoice = true)
        }
    }

    /** Abort an in-flight voice session without sending anything. */
    fun cancelVoice() {
        Timber.d("cancelVoice")
        voiceController.cancel()
        _state.value = _state.value.copy(
            voiceState = VoiceUiState.IDLE,
            pendingTranscript = "",
            voiceNotice = "",
        )
    }

    /**
     * User override for the tool-memory label. Keeps the current
     * [ToolContext.packageName] (we know what app they are in) but
     * replaces the display label; also clears the
     * `umbrellaSiteLabel` so the new key is stable until the user
     * switches apps.
     *
     * Mirrors macOS `HandyManager.setToolName` (`HandyManager.swift`
     * lines 566–570): a no-op when the new name equals the current
     * display label.
     */
    fun setToolName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = toolContextFlow.value
        if (current.displayLabel == trimmed) return
        toolContextFlow.value = current.copy(
            appLabel = trimmed,
            umbrellaSiteLabel = null,
        )
        _state.value = _state.value.copy(
            currentToolName = trimmed,
            toolDetectionState = ToolDetectionState.DETECTED,
        )
    }

    fun send(userText: String, fromVoice: Boolean = false) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return
        sendJob?.cancel()

        sendJob = viewModelScope.launch {
            val snapshot = _state.value
            val current = snapshot.settings ?: withContext(Dispatchers.IO) { settings.current() }

            val hasBraveKey = withContext(Dispatchers.IO) {
                !keyStore.get(KeyStore.KEY_BRAVE).isNullOrBlank()
            }
            val automationEnabled =
                current.accessibilityDisclosureAcknowledged &&
                    accessibilityStateMonitor.isEnabled.value
            // Mirrors V1 `ClaudeAPIService.availableTools`: web tools
            // ride on `webSearchEnabled`; `dispatch_action` stays off
            // in reduced mode so code matches the Play disclosure.
            val tools = availableTools(
                webSearchEnabled = current.webSearchEnabled,
                hasBraveKey = hasBraveKey,
                intentDispatchEnabled = automationEnabled,
            )

            val turnContext = screenContextBuilder.build(
                userMessage = trimmed,
                source = TurnSource.FULL_CHAT,
                toolContext = toolContextFlow.value,
                panelSnapshot = targetSnapshot,
                preferFocusedWindow = targetSnapshot != null,
            )
            Timber.d(
                "ChatViewModel.send: request=%s app=%s screenText=%s captureMode=%s failure=%s",
                turnContext.requestId,
                turnContext.toolContext.packageName,
                turnContext.screenText != null,
                turnContext.captureMode,
                turnContext.failureReason,
            )

            val request = OrchestrationRequest(
                userMessage = trimmed,
                toolContext = turnContext.toolContext,
                settings = current,
                fromVoice = fromVoice,
                capture = turnContext.capture,
                screenText = turnContext.screenText,
                hasBraveKey = hasBraveKey,
                tools = tools,
                agentRecipesEnabled = automationEnabled,
                contextFailureReason = turnContext.failureReason,
                grounding = turnContext,
            )
            pendingVoiceTurnRequestId = if (fromVoice) turnContext.requestId else null

            // Reset the per-turn search-tools buffer before the new stream.
            collectedSearchTools.clear()

            _state.value = snapshot.copy(
                isStreaming = true,
                streamingDelta = "",
                localOverlay = emptyList(),
                loadingVerb = LoadingVerbs.random(),
                loadingVerbFrozen = false,
                voiceState = if (fromVoice) VoiceUiState.PROCESSING else VoiceUiState.IDLE,
                pendingShowInAppAction = null,
            )
            startVerbRotation()

            orchestrator.converse(request).collectLatest { event ->
                when (event) {
                    is OrchestrationEvent.LoadingVerb ->
                        if (!_state.value.loadingVerbFrozen) {
                            _state.value = _state.value.copy(loadingVerb = event.verb)
                        }
                    is OrchestrationEvent.StreamingDelta ->
                        _state.value = _state.value.copy(streamingDelta = event.accumulated)
                    is OrchestrationEvent.AssistantTurnFinalized -> {
                        stopVerbRotation()
                        val voiceTurnId = pendingVoiceTurnRequestId
                        if (voiceTurnId != null && !event.ttsText.isNullOrBlank()) {
                            speechOutputController.speakForVoiceTurn(voiceTurnId, event.ttsText)
                        }
                        pendingVoiceTurnRequestId = null
                        // Stamp the collected tool list onto the just-
                        // persisted assistant message so the italic
                        // "web searched · github searched" caption
                        // renders above the bubble. Mirrors macOS
                        // `HandyManager.sendMessage` lines 963–973.
                        val tagged = event.searchToolsUsed.ifEmpty { collectedSearchTools.toList() }
                        _state.value = _state.value.copy(
                            isStreaming = false,
                            streamingDelta = "",
                            loadingVerb = "",
                            loadingVerbFrozen = false,
                            voiceState = VoiceUiState.IDLE,
                            pendingUserTurn = null,
                            pendingShowInAppAction = buildShowInAppAction(
                                pointing = event.pointing,
                                chatText = event.chatText,
                                snapshotOverride = turnContext.panelSnapshot,
                                groundingSnapshot = turnContext,
                                provenance = event.provenance,
                                userUtterance = trimmed,
                            ),
                        )
                        if (tagged.isNotEmpty()) stampSearchToolsOnLastAssistant(tagged)
                    }
                    is OrchestrationEvent.Error -> {
                        stopVerbRotation()
                        speechOutputController.stop("turn_error")
                        val accumulated = _state.value.streamingDelta
                        // Carry the pendingUserTurn into the overlay so
                        // failed turns still show both sides of the
                        // exchange (user bubble + assistant failure +
                        // system error).
                        val priorUser = _state.value.pendingUserTurn
                        val overlay = buildErrorOverlay(
                            pendingUser = priorUser,
                            accumulated = accumulated,
                            errorMessage = event.message,
                        )
                        _state.value = _state.value.copy(
                            isStreaming = false,
                            streamingDelta = "",
                            loadingVerb = "",
                            loadingVerbFrozen = false,
                            errorBanner = event.message,
                            localOverlay = overlay,
                            voiceState = VoiceUiState.IDLE,
                            pendingUserTurn = null,
                            pendingShowInAppAction = null,
                        )
                        pendingVoiceTurnRequestId = null
                    }
                    is OrchestrationEvent.ToolCall -> {
                        // Log the tool; the matching WebSearchStatus
                        // event carries the user-facing verb.
                        if (event.name !in collectedSearchTools) {
                            collectedSearchTools += event.name
                        }
                    }
                    is OrchestrationEvent.WebSearchStatus -> {
                        // Override + freeze the loading strip so the
                        // random verb rotation doesn't stomp the
                        // tool-specific status string.
                        _state.value = _state.value.copy(
                            loadingVerb = event.text,
                            loadingVerbFrozen = true,
                        )
                    }
                    is OrchestrationEvent.UserTurnPersisted -> {
                        // Render the user bubble immediately so hitting
                        // Send never feels like it vanished while the
                        // assistant is still warming up. The bubble is
                        // cleared on AssistantTurnFinalized — by then
                        // the historyStore has emitted the persisted
                        // pair, so no duplication.
                        _state.value = _state.value.copy(
                            pendingUserTurn = event.message,
                        )
                    }
                    is OrchestrationEvent.SystemMessageInjected -> {
                        stopVerbRotation()
                        _state.value = _state.value.copy(
                            isStreaming = false,
                            streamingDelta = "",
                            loadingVerb = "",
                            loadingVerbFrozen = false,
                            voiceState = VoiceUiState.IDLE,
                            pendingUserTurn = null,
                            pendingShowInAppAction = null,
                        )
                        pendingVoiceTurnRequestId = null
                    }
                }
            }
        }
    }

    private val collectedSearchTools: MutableList<String> = mutableListOf()

    /**
     * Replaces the last assistant message in [ChatUiState.messages] with
     * a copy that carries [tools] in [ChatMessage.searchToolsUsed].
     *
     * The on-disk history (`JsonHistoryStore`) doesn't persist the tool
     * list today — it only stores user+assistant text pairs via
     * `ConversationTurn`. This stamp lives in UI state so the italic
     * caption renders during the session; kill-restart reloads from
     * disk (without the caption) which matches V1 behavior for legacy
     * turns.
     */
    private fun stampSearchToolsOnLastAssistant(tools: List<String>) {
        val currentMessages = _state.value.messages
        val idx = currentMessages.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (idx < 0) return
        val updated = currentMessages.toMutableList()
        updated[idx] = updated[idx].copy(searchToolsUsed = tools)
        _state.value = _state.value.copy(messages = updated.toList())
    }

    fun respondToConfirmation(requestId: Long, approved: Boolean) {
        confirmationBroker.respond(requestId, approved)
    }

    fun consumeShowInAppAction(actionId: Long): FullChatShowInAppAction? {
        val action = _state.value.pendingShowInAppAction
            ?.takeIf { it.id == actionId }
            ?: return null
        _state.value = _state.value.copy(pendingShowInAppAction = null)
        return action
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorBanner = null)
    }

    private fun buildShowInAppAction(
        pointing: AssistantMarkupParser.PointingResult,
        chatText: String,
        snapshotOverride: PanelSnapshot? = null,
        groundingSnapshot: GroundingSnapshot? = null,
        provenance: ToolProvenance? = null,
        userUtterance: String? = null,
    ): FullChatShowInAppAction? {
        val snapshot = snapshotOverride ?: targetSnapshot ?: return null
        if (!pointing.hasPointer) return null
        val targetLabel = pointing.targetLabel()
        return FullChatShowInAppAction(
            id = ++showInAppActionCounter,
            targetLabel = targetLabel,
            bubbleLabel = chatText.takeForBubble().ifBlank { targetLabel },
            pointing = pointing,
            snapshot = snapshot,
            groundingSnapshot = groundingSnapshot,
            provenance = provenance,
            userUtterance = userUtterance,
        )
    }

    private fun AssistantMarkupParser.PointingResult.targetLabel(): String {
        semantic?.let { spec ->
            return spec.text
                ?: spec.contentDescription
                ?: spec.viewId
                ?: spec.markId
                ?: spec.role
                ?: "this"
        }
        pixel?.let { point -> return point.label ?: "this" }
        return "this"
    }

    private fun String.takeForBubble(max: Int = 90): String {
        val cleaned = replace('\n', ' ').trim()
        return if (cleaned.length <= max) cleaned else cleaned.take(max).trimEnd() + "…"
    }

    /**
     * Launches a 2.5 s cadence verb rotation that mirrors
     * `HandyManager.startLoadingAnimation` (`HandyManager.swift` lines
     * 1240–1247). Freezes when [ChatUiState.loadingVerbFrozen] is true —
     * that flag will flip in Phase D when a tool-use status like
     * "Searching the web..." needs to own the strip.
     */
    private fun startVerbRotation() {
        verbRotationJob?.cancel()
        verbRotationJob = viewModelScope.launch {
            while (isActive) {
                delay(VERB_ROTATION_MS)
                val s = _state.value
                if (!s.isStreaming) break
                if (s.loadingVerbFrozen) continue
                _state.value = s.copy(loadingVerb = LoadingVerbs.random())
            }
        }
    }

    private fun stopVerbRotation() {
        verbRotationJob?.cancel()
        verbRotationJob = null
    }

    private fun buildErrorOverlay(
        pendingUser: ChatMessage?,
        accumulated: String,
        errorMessage: String,
    ): List<ChatMessage> {
        val key = toolContextFlow.value.historyKey
        val overlay = mutableListOf<ChatMessage>()
        if (pendingUser != null) overlay += pendingUser
        val failedAssistantBody = accumulated.ifEmpty { "(response failed)" }
        overlay += ChatMessage.new(
            role = MessageRole.ASSISTANT,
            content = failedAssistantBody,
            toolName = key,
        )
        overlay += ChatMessage.new(
            role = MessageRole.SYSTEM,
            content = "Error: $errorMessage",
            toolName = key,
        )
        return overlay
    }

    private fun computeBrainReady(): Boolean =
        !keyStore.get(KeyStore.KEY_ANTHROPIC).isNullOrBlank()

    companion object {
        private val DEFAULT_TOOL = ToolContext(
            packageName = "com.handy.android",
            appLabel = "Handy",
        )
        private const val VERB_ROTATION_MS: Long = 2500L
    }
}

/**
 * Detection state for the tool-name bar.
 *
 * Mirrors macOS `HandyManager.ToolDetectionState` so the bar's render
 * logic round-trips.
 */
enum class ToolDetectionState { IDLE, DETECTING, DETECTED, FAILED }

/**
 * UI-facing voice state. The underlying
 * [com.handy.app.voice.VoiceController] emits a simpler IDLE/LISTENING
 * enum; Phase B maps that flow into this richer shape so the chat UI
 * can distinguish "mic is on" from "LLM is responding".
 */
enum class VoiceUiState { IDLE, LISTENING, PROCESSING, RESPONDING }

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    /**
     * Error-only bubbles that are NOT in the persistent history. Cleared
     * on the next send. Present after [OrchestrationEvent.Error] so the
     * failed turn and the system-level error line render in the list
     * instead of being silently swallowed.
     */
    val localOverlay: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingDelta: String = "",
    val loadingVerb: String = "",
    /**
     * True while a tool call ("Searching the web...") owns the loading
     * strip — freezes the random-verb rotation until the tool completes.
     * Phase D flips this from `OrchestrationEvent.WebSearchStatus`.
     */
    val loadingVerbFrozen: Boolean = false,
    val errorBanner: String? = null,
    val settings: HandySettings? = null,
    val currentToolName: String = "Handy",
    val toolDetectionState: ToolDetectionState = ToolDetectionState.IDLE,
    val voiceState: VoiceUiState = VoiceUiState.IDLE,
    val pendingTranscript: String = "",
    val voiceNotice: String = "",
    /**
     * Non-null while the user is being asked to confirm a destructive
     * `dispatch_action` call. The chat UI renders a bottom sheet /
     * dialog; the ViewModel calls [ChatConfirmationBroker.respond] when
     * the user taps Continue / Cancel.
     */
    val pendingConfirmation: ChatConfirmationBroker.Request? = null,
    /**
     * User message for the turn currently in flight. Rendered eagerly
     * on [OrchestrationEvent.UserTurnPersisted] so hitting Send never
     * feels like the message disappeared. Cleared when the assistant
     * turn is finalised (at which point `historyStore` has emitted the
     * persisted pair) or on error (where it flows into `localOverlay`
     * alongside the failed response).
     */
    val pendingUserTurn: ChatMessage? = null,
    /**
     * Non-null when the latest assistant turn emitted a pointer grounded
     * in the handoff snapshot captured before full chat covered the app.
     */
    val pendingShowInAppAction: FullChatShowInAppAction? = null,
    /**
     * True when Handy's `AccessibilityService` is enabled for our
     * package in Android Settings. Drives the "Enable accessibility to
     * detect the app you're on" nudge banner in [ChatActivity]. Default
     * true so we don't flash the banner during the first frame while
     * the singleton's StateFlow is warming up.
     */
    val accessibilityServiceEnabled: Boolean = true,
    val brainReady: Boolean = false,
    val remainingSessionTokens: Int? = null,
    val sessionBudgetRunningLow: Boolean = false,
    val sessionBudgetExhausted: Boolean = false,
)
