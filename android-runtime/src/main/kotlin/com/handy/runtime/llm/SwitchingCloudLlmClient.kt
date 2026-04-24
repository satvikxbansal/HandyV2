package com.handy.runtime.llm

import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.ToolRunner
import com.handy.core.model.CloudProvider
import com.handy.runtime.storage.DataStoreSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber

/**
 * [LlmClient] wrapper that dispatches each request to the cloud
 * provider selected in [com.handy.core.model.HandySettings.cloudProvider].
 *
 * Why a wrapper instead of a Hilt qualifier per-callsite: existing
 * `:core` call sites (e.g. `ConversationOrchestrator`, `ChatViewModel`)
 * use plain `LlmClient`. Adding a qualifier would force those to
 * either know about provider picks (leak) or wrap themselves. The
 * wrapper keeps the seam in one place.
 *
 * Gemini is experimental (scope §5). Falls back to Claude when Gemini
 * isn't configured or Gemini surfaces an auth-style error that the
 * stream cannot recover from.
 */
@Singleton
class SwitchingCloudLlmClient @Inject constructor(
    private val claude: ClaudeLlmClient,
    private val gemini: GeminiCloudLlmClient,
    private val settings: DataStoreSettings,
) : LlmClient {

    override val modelId: String get() = "switching"

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun streamChat(request: LlmRequest): Flow<LlmChunk> =
        pickClient().flatMapConcat { client -> client.streamChat(request) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun streamToolAwareChat(request: LlmRequest, runner: ToolRunner): Flow<LlmChunk> =
        pickClient().flatMapConcat { client -> client.streamToolAwareChat(request, runner) }

    private fun pickClient(): Flow<LlmClient> = flow {
        val current = runCatching { settings.current() }.getOrNull()
        val chosen = when (current?.cloudProvider) {
            CloudProvider.GEMINI -> gemini
            else -> claude
        }
        Timber.d("SwitchingCloudLlmClient: routing to %s", chosen.modelId)
        emit(chosen)
    }
}
