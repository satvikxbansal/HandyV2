package com.handy.core.orchestrator

import com.google.common.truth.Truth.assertThat
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.ToolDefinition
import com.handy.core.llm.ToolResult
import com.handy.core.llm.ToolRunner
import com.handy.core.model.ChatMessage
import com.handy.core.model.CloudProvider
import com.handy.core.model.ConversationTurn
import com.handy.core.model.HandySettings
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.IntRect
import com.handy.core.screen.SECURE_WINDOW_SYSTEM_MESSAGE
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.UiNode
import com.handy.core.prompts.PromptCatalog
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

        val deltas = events.filterIsInstance<OrchestrationEvent.StreamingDelta>().map { it.accumulated }
        assertThat(deltas.last()).doesNotContain("[SPOKEN]")
        assertThat(deltas.last()).doesNotContain("[POINT:")

        val finalized = events.filterIsInstance<OrchestrationEvent.AssistantTurnFinalized>().single()
        assertThat(finalized.chatText).contains(spokenBody)
        assertThat(finalized.chatText).contains(detail)
        assertThat(finalized.ttsText).isEqualTo(spokenBody)
        assertThat(finalized.pointing.semantic?.text).isEqualTo("Share")
    }

    @Test fun `quick overlay mode splits spoken without enabling TTS`() = runTest {
        val store = FakeHistoryStore()
        val spokenBody = "tap the search icon at the top."
        val detail = "that opens search for this app."
        val full = "[SPOKEN]$spokenBody[/SPOKEN] $detail [POINT:desc=Search]"
        val llm = ScriptedLlm(
            chunks = listOf(
                LlmChunk.Text(full),
                LlmChunk.Done("end_turn"),
            ),
        )
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            clock = { 3500L },
            uuid = { "u-uid" },
            rng = Random(seed = 0),
        )

        val events = orchestrator.converse(
            OrchestrationRequest(
                userMessage = "how do I search here?",
                toolContext = tool,
                settings = settings,
                fromVoice = false,
                capture = null,
                screenText = null,
                hasBraveKey = false,
                tools = emptyList(),
                quickOverlayResponse = true,
            ),
        ).collectAll()

        val finalized = events.filterIsInstance<OrchestrationEvent.AssistantTurnFinalized>().single()
        assertThat(finalized.chatText).contains(spokenBody)
        assertThat(finalized.chatText).contains(detail)
        assertThat(finalized.overlaySpokenText).isEqualTo(spokenBody)
        assertThat(finalized.ttsText).isNull()
        assertThat(finalized.pointing.semantic?.contentDescription).isEqualTo("Search")
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

    @Test fun `non-empty tools list routes through streamToolAwareChat and the runner gets called`() = runTest {
        val store = FakeHistoryStore()
        val llm = ToolRoutingLlm(
            toolAwareChunks = listOf(
                LlmChunk.ToolCall(id = "t1", name = "web_search", inputJson = "{\"query\":\"kotlin news\"}"),
                LlmChunk.Text("here is what i found. "),
                LlmChunk.Done("end_turn"),
            ),
        )
        val runner = RecordingToolRunner()
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            toolRunner = runner,
            clock = { 6000L },
            uuid = { "u-uid" },
            rng = Random(seed = 0),
        )

        val events = orchestrator.converse(
            OrchestrationRequest(
                userMessage = "latest kotlin release",
                toolContext = tool,
                settings = settings.copy(webSearchEnabled = true),
                fromVoice = false,
                capture = null,
                screenText = null,
                hasBraveKey = true,
                tools = listOf(
                    ToolDefinition(name = "web_search", description = "", inputSchemaJson = "{}"),
                ),
            ),
        ).collectAll()

        assertThat(llm.toolAwareCallCount).isEqualTo(1)
        assertThat(llm.plainCallCount).isEqualTo(0)
        assertThat(runner.beginTurnCount).isEqualTo(1)
        // The LLM client ran the tool call itself in this fake; we only
        // want to assert the orchestrator surfaced a WebSearchStatus
        // event and a finalisation with the tool recorded.
        val status = events.filterIsInstance<OrchestrationEvent.WebSearchStatus>().single()
        assertThat(status.text).isEqualTo("Searching the web...")

        val finalized = events.filterIsInstance<OrchestrationEvent.AssistantTurnFinalized>().single()
        assertThat(finalized.searchToolsUsed).containsExactly("web_search")
    }

    @Test fun `selected cloud provider controls model override on LLM request`() = runTest {
        val store = FakeHistoryStore()
        val llm = CapturingLlm()
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            clock = { 7000L },
            uuid = { "u-uid" },
            rng = Random(seed = 0),
        )

        orchestrator.converse(
            OrchestrationRequest(
                userMessage = "hi",
                toolContext = tool,
                settings = settings.copy(
                    cloudProvider = CloudProvider.GEMINI,
                    claudeModelOverride = "claude-haiku-4-5-20251001",
                    geminiModelOverride = "gemini-test-model",
                ),
                fromVoice = false,
                capture = null,
                screenText = null,
                hasBraveKey = false,
                tools = emptyList(),
            ),
        ).collectAll()

        assertThat(llm.lastRequest?.modelOverride).isEqualTo("gemini-test-model")
    }

    @Test fun `summarize screen mode uses summarize prompt empty tools and skips tool routing`() = runTest {
        val store = FakeHistoryStore()
        val llm = ToolRoutingLlm(
            plainChunks = listOf(
                LlmChunk.Text("this screen shows your inbox and a compose button. [POINT:none]"),
                LlmChunk.Done("end_turn"),
            ),
            toolAwareChunks = listOf(
                LlmChunk.Error(IllegalStateException("tool-aware path should not run")),
            ),
        )
        val runner = RecordingToolRunner()
        val orchestrator = ConversationOrchestrator(
            llmClient = llm,
            historyStore = store,
            toolRunner = runner,
            clock = { 8000L },
            uuid = { "u-uid" },
            rng = Random(seed = 0),
        )

        val events = orchestrator.converse(
            OrchestrationRequest(
                userMessage = "Summarize this screen",
                toolContext = tool,
                settings = settings.copy(webSearchEnabled = true),
                fromVoice = false,
                capture = null,
                screenText = summarizeScreenText(),
                hasBraveKey = true,
                tools = listOf(
                    ToolDefinition(name = "dispatch_action", description = "", inputSchemaJson = "{}"),
                    ToolDefinition(name = "web_search", description = "", inputSchemaJson = "{}"),
                ),
                quickOverlayResponse = true,
            ),
            mode = ConversationMode.SUMMARIZE_SCREEN,
        ).collectAll()

        assertThat(llm.plainCallCount).isEqualTo(1)
        assertThat(llm.toolAwareCallCount).isEqualTo(0)
        assertThat(runner.beginTurnCount).isEqualTo(0)

        val sent = llm.lastPlainRequest ?: error("plain LLM request was not captured")
        assertThat(sent.tools).isEmpty()
        assertThat(sent.systemPrompt).contains(PromptCatalog.SUMMARIZE_SCREEN_PROMPT)
        assertThat(sent.systemPrompt).contains("screen text (from accessibility):")
        assertThat(sent.systemPrompt).doesNotContain("quick overlay response:")
        assertThat(sent.systemPrompt).doesNotContain("agent-mode recipes:")
        assertThat(sent.systemPrompt).doesNotContain("direct actions:")

        val finalized = events.filterIsInstance<OrchestrationEvent.AssistantTurnFinalized>().single()
        assertThat(finalized.chatText).doesNotContain("so we are working")
        assertThat(finalized.pointing.isNone).isTrue()
        assertThat(finalized.searchToolsUsed).isEmpty()
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

        // Non-tool orchestrator tests never take the tool-aware path.
        override fun streamToolAwareChat(
            request: LlmRequest,
            runner: com.handy.core.llm.ToolRunner,
        ): Flow<LlmChunk> = chunks.asFlow()
    }

    private class AssertNeverCalledLlm : LlmClient {
        override val modelId: String = "no-op"
        override fun streamChat(request: LlmRequest): Flow<LlmChunk> {
            throw AssertionError("LLM must not be called on SecureWindow")
        }

        override fun streamToolAwareChat(
            request: LlmRequest,
            runner: com.handy.core.llm.ToolRunner,
        ): Flow<LlmChunk> {
            throw AssertionError("LLM must not be called on SecureWindow")
        }
    }

    private class ThrowingLlm(private val throwable: Throwable) : LlmClient {
        override val modelId: String = "throwing"
        override fun streamChat(request: LlmRequest): Flow<LlmChunk> = flowThatThrows(throwable)

        override fun streamToolAwareChat(
            request: LlmRequest,
            runner: com.handy.core.llm.ToolRunner,
        ): Flow<LlmChunk> = flowThatThrows(throwable)
    }

    private class CapturingLlm : LlmClient {
        override val modelId: String = "capturing"
        var lastRequest: LlmRequest? = null

        override fun streamChat(request: LlmRequest): Flow<LlmChunk> {
            lastRequest = request
            return listOf(LlmChunk.Done("end_turn")).asFlow()
        }

        override fun streamToolAwareChat(request: LlmRequest, runner: ToolRunner): Flow<LlmChunk> {
            lastRequest = request
            return listOf(LlmChunk.Done("end_turn")).asFlow()
        }
    }

    /**
     * Captures which LlmClient entry point was called so the
     * orchestrator test can assert it took the tool-aware branch when
     * tools were present.
     */
    private class ToolRoutingLlm(
        private val plainChunks: List<LlmChunk> = emptyList(),
        private val toolAwareChunks: List<LlmChunk> = emptyList(),
    ) : LlmClient {
        override val modelId: String = "tool-routing"
        var plainCallCount: Int = 0
        var toolAwareCallCount: Int = 0
        var lastPlainRequest: LlmRequest? = null
        var lastToolAwareRequest: LlmRequest? = null

        override fun streamChat(request: LlmRequest): Flow<LlmChunk> {
            plainCallCount++
            lastPlainRequest = request
            return plainChunks.asFlow()
        }

        override fun streamToolAwareChat(
            request: LlmRequest,
            runner: ToolRunner,
        ): Flow<LlmChunk> {
            toolAwareCallCount++
            lastToolAwareRequest = request
            return toolAwareChunks.asFlow()
        }
    }

    private class RecordingToolRunner : ToolRunner {
        val calls: MutableList<Pair<String, String>> = mutableListOf()
        var beginTurnCount: Int = 0

        override fun beginTurn() {
            beginTurnCount++
        }

        override suspend fun run(name: String, inputJson: String): ToolResult {
            calls += name to inputJson
            return ToolResult.Ok("stub-result")
        }
    }

    private fun summarizeScreenText(): ScreenTextSnapshot =
        ScreenTextSnapshot(
            packageName = "com.google.android.gm",
            timestampEpochMs = 0L,
            root = UiNode(
                role = "Root",
                boundsInScreen = IntRect(0, 0, 1080, 2200),
                children = listOf(
                    UiNode(markId = "m1", role = "Text", text = "Primary"),
                    UiNode(markId = "m2", role = "Text", text = "Inbox"),
                    UiNode(markId = "m3", role = "Button", text = "Compose", clickable = true),
                    UiNode(markId = "m4", role = "Text", text = "Receipt from Cafe"),
                    UiNode(markId = "m5", role = "Text", text = "Team standup notes"),
                    UiNode(markId = "m6", role = "Button", text = "Search in mail", clickable = true),
                ),
            ),
        )
}

private fun <T> flowThatThrows(t: Throwable): Flow<T> = kotlinx.coroutines.flow.flow {
    throw t
}

private suspend fun <T> Flow<T>.collectAll(): List<T> {
    val out = mutableListOf<T>()
    collect { out += it }
    return out
}
