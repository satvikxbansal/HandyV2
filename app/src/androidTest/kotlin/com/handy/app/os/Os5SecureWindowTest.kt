package com.handy.app.os

import com.google.common.truth.Truth.assertThat
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.model.ChatMessage
import com.handy.core.model.ConversationTurn
import com.handy.core.model.HandySettings
import com.handy.core.orchestrator.ConversationOrchestrator
import com.handy.core.orchestrator.OrchestrationEvent
import com.handy.core.orchestrator.OrchestrationRequest
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.SECURE_WINDOW_SYSTEM_MESSAGE
import com.handy.core.tool.ToolContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * OS-5 instrumentation counterpart to `ConversationOrchestratorTest` in
 * `:core` — exercises the Android-flavoured types to make sure the wire
 * between `CaptureResult.SecureWindow` and the injected system message
 * holds end-to-end, with no `LlmClient` call.
 */
class Os5SecureWindowTest {

    @Test
    fun secure_window_injects_system_message_and_never_calls_llm() = runBlocking {
        val seenTurns = mutableListOf<ConversationTurn>()
        val store = InMemoryHistoryStore(onAppend = seenTurns::add)
        val llm = object : LlmClient {
            override val modelId: String = "forbidden"
            override fun streamChat(request: LlmRequest): Flow<LlmChunk> {
                throw AssertionError("LlmClient must not be called on SecureWindow")
            }
        }
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            clock = { 100L },
            uuid = { "u-id" },
        )

        val events = mutableListOf<OrchestrationEvent>()
        orchestrator.converse(
            OrchestrationRequest(
                userMessage = "what does this banking screen say",
                toolContext = ToolContext(packageName = "com.example.bank", appLabel = "Bank"),
                settings = HandySettings(),
                fromVoice = false,
                capture = CaptureResult.SecureWindow,
                screenText = null,
                hasBraveKey = false,
                tools = emptyList(),
            ),
        ).collect { events += it }

        val injected = events.filterIsInstance<OrchestrationEvent.SystemMessageInjected>().single()
        assertThat(injected.message.content).isEqualTo(SECURE_WINDOW_SYSTEM_MESSAGE)
        assertThat(events.filterIsInstance<OrchestrationEvent.AssistantTurnFinalized>()).isEmpty()
        assertThat(seenTurns).hasSize(1)
        assertThat(seenTurns.first().assistantMessage).isEqualTo(SECURE_WINDOW_SYSTEM_MESSAGE)
    }

    private class InMemoryHistoryStore(
        private val onAppend: (ConversationTurn) -> Unit,
    ) : ChatHistoryStore {
        private val store = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
        override fun observe(toolName: String): Flow<List<ChatMessage>> =
            store.getOrPut(toolName) { MutableStateFlow(emptyList()) }.asStateFlow()
        override suspend fun load(toolName: String): List<ChatMessage> =
            store[toolName]?.value.orEmpty()
        override suspend fun appendTurn(toolName: String, turn: ConversationTurn) {
            onAppend(turn)
        }
        override suspend fun replace(toolName: String, messages: List<ChatMessage>) {
            store.getOrPut(toolName) { MutableStateFlow(emptyList()) }.value = messages
        }
        override suspend fun clear(toolName: String) { store.remove(toolName) }
        override suspend fun clearAll() { store.clear() }
        override suspend fun listTools(): List<String> = store.keys.toList()
    }
}
