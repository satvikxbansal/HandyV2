package com.handy.app.overlay

import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.app.agent.AgentSessionController
import com.handy.app.chat.ChatConfirmationBroker
import com.handy.app.screen.ScreenContextBuilder
import com.handy.app.voice.SpeechOutputController
import com.handy.app.voice.VoiceController
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.ToolRunner
import com.handy.core.model.ChatMessage
import com.handy.core.model.ConversationTurn
import com.handy.core.model.HandySettings
import com.handy.core.model.MessageRole
import com.handy.core.overlay.OverlayPanelState
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.KeyStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayChatPipelineTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `voice finalized with tts text speaks`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = fixture(
            llmClient = FakeLlmClient(flowOf(
                LlmChunk.Text("[SPOKEN]Tap Search[/SPOKEN]\nDetailed answer"),
                LlmChunk.Done("end"),
            )),
            scope = this,
            requestId = "overlay-voice",
        )

        fixture.pipeline.runTurn("what do I tap", fromVoice = true)
        advanceUntilIdle()

        verify(timeout = 2_000, exactly = 1) {
            fixture.speechOutputController.speakForVoiceTurn("overlay-voice", "Tap Search")
        }
    }

    @Test
    fun `typed finalized with tts-like payload does not speak`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = fixture(
            llmClient = FakeLlmClient(flowOf(
                LlmChunk.Text("[SPOKEN]Tap Search[/SPOKEN]\nDetailed answer"),
                LlmChunk.Done("end"),
            )),
            scope = this,
            requestId = "overlay-typed",
        )

        fixture.pipeline.runTurn("what do I tap", fromVoice = false)
        advanceUntilIdle()
        delay(250)

        verify(exactly = 0) {
            fixture.speechOutputController.speakForVoiceTurn(any(), any())
        }
    }

    @Test
    fun `error event stops speech output`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = fixture(
            llmClient = FakeLlmClient(flowOf(
                LlmChunk.Error(IllegalStateException("boom")),
            )),
            scope = this,
            requestId = "overlay-error",
        )

        fixture.pipeline.runTurn("what do I tap", fromVoice = true)
        advanceUntilIdle()

        verify(timeout = 2_000) { fixture.speechOutputController.stop("turn_error") }
    }

    private fun fixture(
        llmClient: LlmClient,
        scope: CoroutineScope,
        requestId: String,
    ): Fixture {
        val presenter = mockk<OverlayPresenter>()
        every { presenter.state } returns MutableStateFlow(OverlayPanelState())
        every { presenter.setPendingConfirmation(any()) } just runs
        every { presenter.setLoadingVerb(any()) } just runs
        every { presenter.onStreamingStart() } just runs
        every { presenter.onStreamingDelta(any()) } just runs
        every { presenter.onResponseFinalized(any(), any()) } just runs
        every { presenter.onError(any()) } just runs
        every { presenter.dismissPanel() } just runs

        val voiceController = mockk<VoiceController>(relaxed = true)
        val confirmationBroker = ChatConfirmationBroker()
        val bridge = OverlayPanelBridge(
            voiceController = voiceController,
            presenter = presenter,
            confirmationBroker = confirmationBroker,
            appScope = scope,
        )

        val settings = mockk<DataStoreSettings>()
        coEvery { settings.current() } returns HandySettings()

        val keyStore = mockk<KeyStore>()
        every { keyStore.get(any()) } returns null

        val accessibilityStateMonitor = mockk<AccessibilityStateMonitor>()
        every { accessibilityStateMonitor.isEnabled } returns MutableStateFlow(false)

        val screenContextBuilder = mockk<ScreenContextBuilder>()
        coEvery {
            screenContextBuilder.build(any(), any(), any(), any(), any())
        } returns GroundingSnapshot(
            requestId = requestId,
            source = TurnSource.OVERLAY_VOICE,
            toolContext = ToolContext(packageName = "com.handy.android", appLabel = "Handy"),
        )

        val speechOutputController = mockk<SpeechOutputController>()
        every { speechOutputController.speakForVoiceTurn(any(), any()) } just runs
        every { speechOutputController.stop(any()) } just runs

        val agentSessionController = mockk<AgentSessionController>()
        coEvery {
            agentSessionController.runIfRecipeRequested(any(), any(), any(), any(), any(), any())
        } returns false

        val pipeline = OverlayChatPipeline(
            bridge = bridge,
            presenter = presenter,
            llmClient = llmClient,
            historyStore = FakeHistoryStore(),
            toolRunner = mockk<ToolRunner>(relaxed = true),
            settings = settings,
            keyStore = keyStore,
            confirmationBroker = confirmationBroker,
            accessibilityStateMonitor = accessibilityStateMonitor,
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            screenContextBuilder = screenContextBuilder,
            agentSessionController = agentSessionController,
            speechOutputController = speechOutputController,
            appScope = scope,
        )
        return Fixture(
            bridge = bridge,
            pipeline = pipeline,
            speechOutputController = speechOutputController,
        )
    }

    private data class Fixture(
        val bridge: OverlayPanelBridge,
        val pipeline: OverlayChatPipeline,
        val speechOutputController: SpeechOutputController,
    )

    private class FakeLlmClient(
        private val chunks: Flow<LlmChunk>,
    ) : LlmClient {
        override val modelId: String = "fake"
        override fun streamChat(request: LlmRequest): Flow<LlmChunk> = chunks
        override fun streamToolAwareChat(request: LlmRequest, runner: ToolRunner): Flow<LlmChunk> = chunks
    }

    private class FakeHistoryStore : ChatHistoryStore {
        private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())

        override fun observe(toolName: String): Flow<List<ChatMessage>> = messages

        override suspend fun load(toolName: String): List<ChatMessage> = messages.value

        override suspend fun appendTurn(toolName: String, turn: ConversationTurn) {
            messages.value = messages.value + listOf(
                ChatMessage.new(
                    role = MessageRole.USER,
                    content = turn.userMessage,
                    toolName = toolName,
                ),
                ChatMessage.new(
                    role = MessageRole.ASSISTANT,
                    content = turn.assistantMessage,
                    toolName = toolName,
                ),
            )
        }

        override suspend fun replace(toolName: String, messages: List<ChatMessage>) {
            this.messages.value = messages
        }

        override suspend fun clear(toolName: String) {
            messages.value = emptyList()
        }

        override suspend fun clearAll() {
            messages.value = emptyList()
        }

        override suspend fun listTools(): List<String> = emptyList()
    }
}
