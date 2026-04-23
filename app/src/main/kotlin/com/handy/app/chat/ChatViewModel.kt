package com.handy.app.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmClient
import com.handy.core.model.ChatMessage
import com.handy.core.model.HandySettings
import com.handy.core.model.LoadingVerbs
import com.handy.core.model.MessageRole
import com.handy.core.orchestrator.ConversationOrchestrator
import com.handy.core.orchestrator.OrchestrationEvent
import com.handy.core.orchestrator.OrchestrationRequest
import com.handy.core.tool.ToolContext
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmClient: LlmClient,
    private val historyStore: ChatHistoryStore,
    private val settings: DataStoreSettings,
) : ViewModel() {

    private val orchestrator = ConversationOrchestrator(
        llmClient = llmClient,
        historyStore = historyStore,
    )

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var currentToolContext: ToolContext = DEFAULT_TOOL
    private var sendJob: Job? = null
    private var verbRotationJob: Job? = null

    init {
        viewModelScope.launch {
            historyStore.observe(currentToolContext.historyKey).collectLatest { messages ->
                _state.value = _state.value.copy(messages = messages)
            }
        }
        viewModelScope.launch {
            settings.flow.collectLatest { s ->
                _state.value = _state.value.copy(settings = s)
            }
        }
    }

    fun send(userText: String, fromVoice: Boolean = false) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return
        sendJob?.cancel()

        sendJob = viewModelScope.launch {
            val snapshot = _state.value
            val current = snapshot.settings ?: withContext(Dispatchers.IO) { settings.current() }

            val request = OrchestrationRequest(
                userMessage = trimmed,
                toolContext = currentToolContext,
                settings = current,
                fromVoice = fromVoice,
                capture = null, // Phase 4 hooks capture pipeline here.
                screenText = null,
                hasBraveKey = false,
                tools = emptyList(),
            )

            _state.value = snapshot.copy(
                isStreaming = true,
                streamingDelta = "",
                localOverlay = emptyList(),
                loadingVerb = LoadingVerbs.random(),
                loadingVerbFrozen = false,
                voiceState = if (fromVoice) VoiceUiState.PROCESSING else VoiceUiState.IDLE,
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
                        _state.value = _state.value.copy(
                            isStreaming = false,
                            streamingDelta = "",
                            loadingVerb = "",
                            loadingVerbFrozen = false,
                            voiceState = VoiceUiState.IDLE,
                        )
                    }
                    is OrchestrationEvent.Error -> {
                        stopVerbRotation()
                        val accumulated = _state.value.streamingDelta
                        val overlay = buildErrorOverlay(accumulated, event.message)
                        _state.value = _state.value.copy(
                            isStreaming = false,
                            streamingDelta = "",
                            loadingVerb = "",
                            loadingVerbFrozen = false,
                            errorBanner = event.message,
                            localOverlay = overlay,
                            voiceState = VoiceUiState.IDLE,
                        )
                    }
                    is OrchestrationEvent.UserTurnPersisted,
                    is OrchestrationEvent.SystemMessageInjected,
                    is OrchestrationEvent.ToolCall,
                    is OrchestrationEvent.WebSearchStatus -> {
                        // Phase D hooks tool calls +
                        // search-status overlays.
                    }
                }
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorBanner = null)
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

    private fun buildErrorOverlay(accumulated: String, errorMessage: String): List<ChatMessage> {
        val overlay = mutableListOf<ChatMessage>()
        val failedAssistantBody = accumulated.ifEmpty { "(response failed)" }
        overlay += ChatMessage.new(
            role = MessageRole.ASSISTANT,
            content = failedAssistantBody,
            toolName = currentToolContext.historyKey,
        )
        overlay += ChatMessage.new(
            role = MessageRole.SYSTEM,
            content = "Error: $errorMessage",
            toolName = currentToolContext.historyKey,
        )
        return overlay
    }

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
)
