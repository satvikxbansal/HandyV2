package com.handy.core.llm

/**
 * One event from the LLM stream.
 *
 * Claude's `content_block_delta` (text), `tool_use` / `input_json_delta`
 * (tool call), and `message_stop` (done) all map here. Gemini's
 * `generate_content_stream` fits the same shape.
 */
sealed class LlmChunk {

    /** Incremental text delta. Concatenate to build the final response. */
    data class Text(val delta: String) : LlmChunk()

    /**
     * A structured tool invocation. Input is raw JSON (we do not force a
     * shape here because tools are defined per-use-case). The chat
     * pipeline pauses, dispatches the tool, and re-enters the stream with
     * the result.
     */
    data class ToolCall(val id: String, val name: String, val inputJson: String) : LlmChunk()

    /** Terminal success event. Closes the flow immediately after. */
    data class Done(val stopReason: String) : LlmChunk()

    /** Terminal error. Flow closes after this chunk. */
    data class Error(val throwable: Throwable) : LlmChunk()
}
