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
     *
     * This path does NOT run tools — tool calls are surfaced to the
     * caller via [LlmChunk.ToolCall] but no `tool_result` is sent back.
     * Use [streamToolAwareChat] when you want the implementation to
     * loop on `stop_reason = tool_use`.
     */
    fun streamChat(request: LlmRequest): Flow<LlmChunk>

    /**
     * Streaming chat with inline tool execution. When the provider's
     * stop reason is `tool_use`, the implementation:
     *  1. Forwards [LlmChunk.Text] deltas and [LlmChunk.ToolCall] events
     *     to the caller in real time.
     *  2. Calls [runner] for each tool, collects [ToolResult] values.
     *  3. Opens a fresh request with the assistant's `tool_use` blocks
     *     and the matching `tool_result` blocks appended, until the
     *     model stops on a non-tool reason or the iteration cap is hit.
     *
     * Ports `ClaudeAPIService.streamResponseWithToolsAsync` from macOS
     * (`ClaudeAPIService.swift` line 634), with the same 5-iteration cap.
     */
    fun streamToolAwareChat(request: LlmRequest, runner: ToolRunner): Flow<LlmChunk>

    /** Short identifier for logging and settings display. */
    val modelId: String
}
