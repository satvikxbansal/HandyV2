package com.handy.app.chat

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.app.screen.ScreenContextBuilder
import com.handy.app.voice.SpeechOutputController
import com.handy.app.voice.VoiceController
import com.handy.core.audit.AuditStore
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.foreground.ForegroundAppMonitor
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.InMemoryLlmSessionBudget
import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.ToolRunner
import com.handy.core.model.ChatMessage
import com.handy.core.model.ConversationTurn
import com.handy.core.model.HandySettings
import com.handy.core.model.MessageRole
import com.handy.core.overlay.PanelSnapshot
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelVoiceTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `voice turn finalized with tts text speaks once`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val speech = mockSpeechOutput()
        val viewModel = viewModel(
            llmClient = FakeLlmClient(flowOf(
                LlmChunk.Text("[SPOKEN]Tap Search[/SPOKEN]\nDetailed answer"),
                LlmChunk.Done("end"),
            )),
            speechOutputController = speech,
            requestId = "voice-req",
        )

        try {
            viewModel.send("what do I tap", fromVoice = true)
            advanceUntilIdle()

            verify(timeout = 2_000, exactly = 1) {
                speech.speakForVoiceTurn("voice-req", "Tap Search")
            }
        } finally {
            viewModel.cancelAndDrain()
            advanceUntilIdle()
        }
    }

    @Test
    fun `typed turn finalized with tts-like payload does not speak`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val speech = mockSpeechOutput()
        val viewModel = viewModel(
            llmClient = FakeLlmClient(flowOf(
                LlmChunk.Text("[SPOKEN]Tap Search[/SPOKEN]\nDetailed answer"),
                LlmChunk.Done("end"),
            )),
            speechOutputController = speech,
            requestId = "typed-req",
        )

        try {
            viewModel.send("what do I tap", fromVoice = false)
            advanceUntilIdle()
            delay(250)

            verify(exactly = 0) {
                speech.speakForVoiceTurn(any(), any())
            }
        } finally {
            viewModel.cancelAndDrain()
            advanceUntilIdle()
        }
    }

    @Test
    fun `voice turn error stops speech output`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val speech = mockSpeechOutput()
        val viewModel = viewModel(
            llmClient = FakeLlmClient(flowOf(
                LlmChunk.Error(IllegalStateException("boom")),
            )),
            speechOutputController = speech,
            requestId = "voice-error",
        )

        try {
            viewModel.send("what do I tap", fromVoice = true)
            advanceUntilIdle()

            verify(timeout = 2_000) { speech.stop("turn_error") }
        } finally {
            viewModel.cancelAndDrain()
            advanceUntilIdle()
        }
    }

    @Test
    fun `foreground clear hides stale full chat context`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val foreground = MutableSharedFlow<ForegroundAppSnapshot?>(
            replay = 1,
            extraBufferCapacity = 1,
        )
        foreground.tryEmit(gmail())
        val viewModel = viewModel(
            llmClient = FakeLlmClient(emptyFlow()),
            speechOutputController = mockSpeechOutput(),
            requestId = "tool-clear",
            foregroundFlow = foreground,
            accessibilityEnabled = true,
        )

        try {
            advanceUntilIdle()
            assertThat(viewModel.state.value.currentToolName).isEqualTo("Gmail")
            assertThat(viewModel.state.value.toolDetectionState)
                .isEqualTo(ToolDetectionState.DETECTED)

            foreground.emit(null)
            advanceUntilIdle()

            assertThat(viewModel.state.value.currentToolName).isEqualTo("Handy")
            assertThat(viewModel.state.value.toolDetectionState)
                .isEqualTo(ToolDetectionState.IDLE)
        } finally {
            viewModel.cancelAndDrain()
            advanceUntilIdle()
        }
    }

    @Test
    fun `bound handoff context ignores foreground clears`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val foreground = MutableSharedFlow<ForegroundAppSnapshot?>(
            replay = 1,
            extraBufferCapacity = 1,
        )
        val handoffStore = ChatTargetHandoffStore()
        val handoffId = handoffStore.put(
            PanelSnapshot(
                toolContext = ToolContext(
                    packageName = "com.google.android.gm",
                    appLabel = "Gmail",
                ),
                capturedAtEpochMs = 1L,
            ),
        )
        val viewModel = viewModel(
            llmClient = FakeLlmClient(emptyFlow()),
            speechOutputController = mockSpeechOutput(),
            requestId = "handoff-clear",
            foregroundFlow = foreground,
            accessibilityEnabled = true,
            chatTargetHandoffStore = handoffStore,
        )

        try {
            viewModel.bindTargetHandoff(handoffId)
            foreground.emit(null)
            advanceUntilIdle()

            assertThat(viewModel.state.value.currentToolName).isEqualTo("Gmail")
            assertThat(viewModel.state.value.toolDetectionState)
                .isEqualTo(ToolDetectionState.DETECTED)
        } finally {
            viewModel.cancelAndDrain()
            advanceUntilIdle()
        }
    }

    private fun viewModel(
        llmClient: LlmClient,
        speechOutputController: SpeechOutputController,
        requestId: String,
        foregroundFlow: Flow<ForegroundAppSnapshot?> = emptyFlow(),
        refreshNowSnapshot: ForegroundAppSnapshot? = null,
        accessibilityEnabled: Boolean = false,
        chatTargetHandoffStore: ChatTargetHandoffStore = ChatTargetHandoffStore(),
    ): ChatViewModel {
        val settingsFlow = MutableStateFlow(HandySettings())
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        coEvery { settings.current() } returns HandySettings()

        val voiceController = mockk<VoiceController>(relaxed = true)
        every { voiceController.latestPartial } returns MutableStateFlow("")
        every { voiceController.state } returns MutableStateFlow(VoiceController.State.IDLE)

        val foregroundAppMonitor = mockk<ForegroundAppMonitor>()
        every { foregroundAppMonitor.flow } returns foregroundFlow
        every { foregroundAppMonitor.refreshNow() } returns refreshNowSnapshot

        val keyStore = mockk<KeyStore>()
        every { keyStore.get(any()) } returns null

        val accessibilityStateMonitor = mockk<AccessibilityStateMonitor>()
        every { accessibilityStateMonitor.isEnabled } returns MutableStateFlow(accessibilityEnabled)

        val screenContextBuilder = mockk<ScreenContextBuilder>()
        coEvery {
            screenContextBuilder.build(any(), any(), any(), any(), any(), any())
        } returns GroundingSnapshot(
            requestId = requestId,
            source = TurnSource.FULL_CHAT,
            toolContext = ToolContext(packageName = "com.handy.android", appLabel = "Handy"),
        )

        return ChatViewModel(
            llmClient = llmClient,
            historyStore = FakeHistoryStore(),
            settings = settings,
            voiceController = voiceController,
            foregroundAppMonitor = foregroundAppMonitor,
            toolRunner = mockk<ToolRunner>(relaxed = true),
            keyStore = keyStore,
            confirmationBroker = ChatConfirmationBroker(),
            accessibilityStateMonitor = accessibilityStateMonitor,
            chatTargetHandoffStore = chatTargetHandoffStore,
            screenContextBuilder = screenContextBuilder,
            llmSessionBudget = InMemoryLlmSessionBudget(),
            speechOutputController = speechOutputController,
            auditStore = mockk<AuditStore>(relaxed = true),
        )
    }

    private fun gmail(): ForegroundAppSnapshot = ForegroundAppSnapshot(
        packageName = "com.google.android.gm",
        appLabel = "Gmail",
    )

    private fun mockSpeechOutput(): SpeechOutputController =
        mockk<SpeechOutputController>().also {
            every { it.speakForVoiceTurn(any(), any()) } just runs
            every { it.stop(any()) } just runs
        }

    private suspend fun ChatViewModel.cancelAndDrain() {
        val job = viewModelScope.coroutineContext[Job]
        viewModelScope.cancel()
        job?.join()
    }

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
