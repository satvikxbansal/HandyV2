package com.handy.core.orchestrator

import com.google.common.truth.Truth.assertThat
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.model.ChatMessage
import com.handy.core.model.ConversationTurn
import com.handy.core.model.HandySettings
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.SECURE_WINDOW_SYSTEM_MESSAGE
import com.handy.core.tool.ToolContext
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ConversationOrchestratorTest {

    private val tool = ToolContext(
        packageName = "com.google.android.gm",
        appLabel = "Gmail",
    )

    private val settings = HandySettings()

    @Test fun `secure window skips the LLM and injects the canonical system message`() = runTest {
        val recordedTurns = mutableListOf<ConversationTurn>()
        val store = FakeHistoryStore(turnsRecorder = { recordedTurns += it })
        val llm = AssertNeverCalledLlm()
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            clock = { 1000L },
            uuid = { "u-uid" },
            rng = Random(seed = 0),
        )

        val flow = orchestrator.converse(
            OrchestrationRequest(
                userMessage = "what does this account page say?",
                toolContext = tool,
                settings = settings,
                fromVoice = false,
                capture = CaptureResult.SecureWindow,
                screenText = null,
                hasBraveKey = false,
                tools = emptyList(),
            ),
        )

        val events = flow.collectAll()

        // Loading verb fires once, then user turn persisted, then system message injected.
        assertThat(events.filterIsInstance<OrchestrationEvent.LoadingVerb>()).hasSize(1)
        assertThat(events.filterIsInstance<OrchestrationEvent.UserTurnPersisted>()).hasSize(1)
        val injected = events.filterIsInstance<OrchestrationEvent.SystemMessageInjected>().single()
        assertThat(injected.message.content).isEqualTo(SECURE_WINDOW_SYSTEM_MESSAGE)
        assertThat(events.filterIsInstance<OrchestrationEvent.AssistantTurnFinalized>()).isEmpty()

        assertThat(recordedTurns).hasSize(1)
        assertThat(recordedTurns.first().assistantMessage).isEqualTo(SECURE_WINDOW_SYSTEM_MESSAGE)
    }

    @Test fun `text-mode stream emits deltas, finalizes, and persists the turn`() = runTest {
        val recordedTurns = mutableListOf<ConversationTurn>()
        val store = FakeHistoryStore(turnsRecorder = { recordedTurns += it })
        val llm = ScriptedLlm(
            chunks = listOf(
                LlmChunk.Text("hello "),
                LlmChunk.Text("there."),
                LlmChunk.Done("end_turn"),
            ),
        )
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            clock = { 2000L },
            uuid = { "u-uid" },
            rng = Random(seed = 0),
        )

        val events = orchestrator.converse(
            OrchestrationRequest(
                userMessage = "hi",
                toolContext = tool,
                settings = settings,
                fromVoice = false,
                capture = null,
                screenText = null,
                hasBraveKey = false,
                tools = emptyList(),
            ),
        ).collectAll()

        val deltas = events.filterIsInstance<OrchestrationEvent.StreamingDelta>().map { it.accumulated }
        assertThat(deltas.last()).contains("hello there.")

        val finalized = events.filterIsInstance<OrchestrationEvent.AssistantTurnFinalized>().single()
        assertThat(finalized.chatText).contains("hello there.")
        assertThat(finalized.ttsText).isNull()
        assertThat(finalized.pointing.hasPointer).isFalse()

        assertThat(recordedTurns).hasSize(1)
    }

    @Test fun `voice mode splits spoken and display and clamps TTS`() = runTest {
        val store = FakeHistoryStore()
        val spokenBody = "tap share, then export."
        val detail = "you can also pick png or pdf in the export panel."
        val full = "[SPOKEN]$spokenBody[/SPOKEN] $detail [POINT:role=button;text=Share]"
        val llm = ScriptedLlm(
            chunks = listOf(
                LlmChunk.Text(full),
                LlmChunk.Done("end_turn"),
            ),
        )
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            clock = { 3000L },
            uuid = { "u-uid" },
            rng = Random(seed = 0),
        )

        val events = orchestrator.converse(
            OrchestrationRequest(
                userMessage = "how do I export in figma",
                toolContext = tool,
                settings = settings,
                fromVoice = true,
                capture = null,
                screenText = null,
                hasBraveKey = false,
                tools = emptyList(),
            ),
        ).collectAll()

        val finalized = events.filterIsInstance<OrchestrationEvent.AssistantTurnFinalized>().single()
        assertThat(finalized.chatText).contains(spokenBody)
        assertThat(finalized.chatText).contains(detail)
        assertThat(finalized.ttsText).isEqualTo(spokenBody)
        assertThat(finalized.pointing.semantic?.text).isEqualTo("Share")
    }

    @Test fun `tool call events propagate as web search status`() = runTest {
        val store = FakeHistoryStore()
        val llm = ScriptedLlm(
            chunks = listOf(
                LlmChunk.ToolCall(id = "t1", name = "web_search", inputJson = "{\"q\":\"kotlin\"}"),
                LlmChunk.Text("according to the kotlin blog..."),
                LlmChunk.Done("end_turn"),
            ),
        )
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            clock = { 4000L },
            uuid = { "u-uid" },
            rng = Random(seed = 0),
        )

        val events = orchestrator.converse(
            OrchestrationRequest(
                userMessage = "latest kotlin features",
                toolContext = tool,
                settings = settings.copy(webSearchEnabled = true),
                fromVoice = false,
                capture = null,
                screenText = null,
                hasBraveKey = true,
                tools = emptyList(),
            ),
        ).collectAll()

        val status = events.filterIsInstance<OrchestrationEvent.WebSearchStatus>().single()
        assertThat(status.text).isEqualTo("Searching the web...")

        val finalized = events.filterIsInstance<OrchestrationEvent.AssistantTurnFinalized>().single()
        assertThat(finalized.searchToolsUsed).containsExactly("web_search")
    }

    @Test fun `llm errors surface as Error event, not a throw`() = runTest {
        val store = FakeHistoryStore()
        val llm = ThrowingLlm(IllegalStateException("kaboom"))
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            clock = { 5000L },
            uuid = { "u-uid" },
            rng = Random(seed = 0),
        )

        val events = orchestrator.converse(
            OrchestrationRequest(
                userMessage = "hi",
                toolContext = tool,
                settings = settings,
                fromVoice = false,
                capture = null,
                screenText = null,
                hasBraveKey = false,
                tools = emptyList(),
            ),
        ).collectAll()

        val errors = events.filterIsInstance<OrchestrationEvent.Error>()
        assertThat(errors).hasSize(1)
        assertThat(errors.first().message).contains("kaboom")
    }

    // --------------------------- test doubles ---------------------------

    private class FakeHistoryStore(
        private val turnsRecorder: (ConversationTurn) -> Unit = {},
    ) : ChatHistoryStore {
        private val storage = mutableMapOf<String, MutableList<ChatMessage>>()
        private val stateFlow = MutableStateFlow<List<ChatMessage>>(emptyList())

        override fun observe(toolName: String): Flow<List<ChatMessage>> = stateFlow
        override suspend fun load(toolName: String): List<ChatMessage> =
            storage[toolName].orEmpty()

        override suspend fun appendTurn(toolName: String, turn: ConversationTurn) {
            turnsRecorder(turn)
            storage.getOrPut(toolName) { mutableListOf() }
        }

        override suspend fun replace(toolName: String, messages: List<ChatMessage>) {
            storage[toolName] = messages.toMutableList()
        }

        override suspend fun clear(toolName: String) { storage.remove(toolName) }
        override suspend fun clearAll() { storage.clear() }
        override suspend fun listTools(): List<String> = storage.keys.toList()
    }

    private class ScriptedLlm(private val chunks: List<LlmChunk>) : LlmClient {
        override val modelId: String = "scripted"
        override fun streamChat(request: LlmRequest): Flow<LlmChunk> = chunks.asFlow()
    }

    private class AssertNeverCalledLlm : LlmClient {
        override val modelId: String = "no-op"
        override fun streamChat(request: LlmRequest): Flow<LlmChunk> {
            throw AssertionError("LLM must not be called on SecureWindow")
        }
    }

    private class ThrowingLlm(private val throwable: Throwable) : LlmClient {
        override val modelId: String = "throwing"
        override fun streamChat(request: LlmRequest): Flow<LlmChunk> = flowThatThrows(throwable)
    }
}

private fun <T> flowThatThrows(t: Throwable): Flow<T> = kotlinx.coroutines.flow.flow {
    throw t
}

private suspend fun <T> Flow<T>.collectAll(): List<T> {
    val out = mutableListOf<T>()
    collect { out += it }
    return out
}
