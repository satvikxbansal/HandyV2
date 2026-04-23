package com.handy.core.llm

import kotlinx.coroutines.flow.Flow

/**
 * Provider-neutral streaming chat completion.
 *
 * v1 wires exactly one implementation: `:android-runtime`'s
 * `ClaudeLlmClient`. v2 can swap in a `GeminiLlmClient` without touching
 * call sites — that is the entire point of this seam.
 *
 * Both `content_block_delta` (Claude) and `generate_content_stream`
 * (Gemini) map cleanly to the [LlmChunk] sealed type.
 */
interface LlmClient {

    /**
     * Stream a chat completion. Emits [LlmChunk.Text] deltas, any
     * [LlmChunk.ToolCall] events, and a terminal [LlmChunk.Done]. If the
     * stream errors, the flow emits [LlmChunk.Error] and then closes.
     */
    fun streamChat(request: LlmRequest): Flow<LlmChunk>

    /** Short identifier for logging and settings display. */
    val modelId: String
}
