package com.handy.app.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmClient
import com.handy.core.model.ChatMessage
import com.handy.core.model.HandySettings
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

            _state.value = snapshot.copy(isStreaming = true, streamingDelta = "")

            orchestrator.converse(request).collectLatest { event ->
                when (event) {
                    is OrchestrationEvent.LoadingVerb ->
                        _state.value = _state.value.copy(loadingVerb = event.verb)
                    is OrchestrationEvent.StreamingDelta ->
                        _state.value = _state.value.copy(streamingDelta = event.accumulated)
                    is OrchestrationEvent.AssistantTurnFinalized ->
                        _state.value = _state.value.copy(
                            isStreaming = false,
                            streamingDelta = "",
                            loadingVerb = "",
                        )
                    is OrchestrationEvent.Error ->
                        _state.value = _state.value.copy(
                            isStreaming = false,
                            streamingDelta = "",
                            loadingVerb = "",
                            errorBanner = event.message,
                        )
                    is OrchestrationEvent.UserTurnPersisted,
                    is OrchestrationEvent.SystemMessageInjected,
                    is OrchestrationEvent.ToolCall,
                    is OrchestrationEvent.WebSearchStatus -> {
                        // No-op in Phase 3; Phase 4 handles tool calls +
                        // search-status overlays.
                    }
                }
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorBanner = null)
    }

    companion object {
        private val DEFAULT_TOOL = ToolContext(
            packageName = "com.handy.android",
            appLabel = "Handy",
        )
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingDelta: String = "",
    val loadingVerb: String = "",
    val errorBanner: String? = null,
    val settings: HandySettings? = null,
)
