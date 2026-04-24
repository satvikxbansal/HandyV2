package com.handy.core.brain

import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.LocalAvailability
import com.handy.core.llm.LocalGenAiClient
import com.handy.core.llm.LocalGenAiRequest
import com.handy.core.llm.LocalGenAiResult
import com.handy.core.llm.LocalTask
import com.handy.core.llm.ToolRunner
import com.handy.core.model.CloudProvider
import com.handy.core.model.HandySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BrainRouterTest {

    private class FakeLlm(override val modelId: String) : LlmClient {
        override fun streamChat(request: LlmRequest): Flow<LlmChunk> = flowOf(LlmChunk.Done("stop"))
        override fun streamToolAwareChat(request: LlmRequest, runner: ToolRunner): Flow<LlmChunk> =
            flowOf(LlmChunk.Done("stop"))
    }

    private class FakeLocal(
        override val modelId: String,
        private val avail: LocalAvailability,
        private val supported: Set<LocalTask>,
    ) : LocalGenAiClient {
        override suspend fun isAvailable(): LocalAvailability = avail
        override suspend fun supports(task: LocalTask): Boolean = task in supported
        override suspend fun run(request: LocalGenAiRequest): LocalGenAiResult =
            LocalGenAiResult.Ok("${request.task}:${request.input}")
    }

    private val claude = FakeLlm("claude-test")
    private val gemini = FakeLlm("gemini-test")

    @Test
    fun routesToClaudeByDefault() {
        val router = BrainRouter(claude, gemini, null)
        val decision = router.route(
            task = AssistantTask(requiresCloud = true),
            settings = HandySettings(),
        )
        val use = assertIs<BrainDecision.UseCloud>(decision)
        assertEquals("claude-test", use.client.modelId)
    }

    @Test
    fun routesToGeminiWhenUserSelected() {
        val router = BrainRouter(claude, gemini, null)
        val decision = router.route(
            task = AssistantTask(requiresCloud = true),
            settings = HandySettings(cloudProvider = CloudProvider.GEMINI),
        )
        val use = assertIs<BrainDecision.UseCloud>(decision)
        assertEquals("gemini-test", use.client.modelId)
    }

    @Test
    fun privacyMode_routesSupportedTaskLocalFirst() {
        val local = FakeLocal("nano", LocalAvailability.Available, setOf(LocalTask.SUMMARIZE_TEXT))
        val router = BrainRouter(claude, gemini, local)
        val decision = router.route(
            task = AssistantTask(
                requiresCloud = false,
                localTask = LocalTask.SUMMARIZE_TEXT,
            ),
            settings = HandySettings(
                preferLocalWhenPossible = true,
                localAiEnabled = true,
            ),
        )
        val use = assertIs<BrainDecision.UseLocal>(decision)
        assertEquals(LocalTask.SUMMARIZE_TEXT, use.localTask)
    }

    @Test
    fun privacyMode_fallsBackToCloudWhenCloudRequired() {
        val local = FakeLocal("nano", LocalAvailability.Available, setOf(LocalTask.SUMMARIZE_TEXT))
        val router = BrainRouter(claude, gemini, local)
        val decision = router.route(
            task = AssistantTask(requiresCloud = true, localTask = LocalTask.SUMMARIZE_TEXT),
            settings = HandySettings(
                preferLocalWhenPossible = true,
                localAiEnabled = true,
            ),
        )
        assertIs<BrainDecision.UseCloud>(decision)
    }

    @Test
    fun canRunLocally_gatedByAvailability() = runTest {
        val local = FakeLocal("nano", LocalAvailability.Unsupported, setOf(LocalTask.SUMMARIZE_TEXT))
        val router = BrainRouter(claude, gemini, local)
        val settings = HandySettings(localAiEnabled = true)
        assertTrue(!router.canRunLocally(LocalTask.SUMMARIZE_TEXT, settings))
    }

    @Test
    fun failsGracefully_whenSelectedCloudMissing() {
        // User picked Gemini but Gemini isn't configured — router does
        // NOT silently fall back; it surfaces a FailGracefully so the
        // UI can prompt for the missing key.
        val router = BrainRouter(
            claude = claude,
            geminiCloud = null,
            localGenAi = null,
        )
        val decision = router.route(
            task = AssistantTask(requiresCloud = true),
            settings = HandySettings(cloudProvider = CloudProvider.GEMINI),
        )
        assertIs<BrainDecision.FailGracefully>(decision)
    }
}
