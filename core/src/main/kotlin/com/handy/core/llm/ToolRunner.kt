package com.handy.core.llm

/**
 * Executes tools that Claude called via `tool_use` content blocks and
 * produces the `tool_result` text that flows back in the next request.
 *
 * This is the pure-Kotlin contract; the `:android-runtime`
 * `HandyToolRunner` is the concrete implementation that knows about
 * Brave, Jina, GitHub, and Android `Intent` dispatch.
 *
 * Design notes:
 *  - The runner is called **outside** the SSE event loop. The
 *    [com.handy.core.llm.LlmClient.streamToolAwareChat] implementation
 *    pauses the active stream when it sees a `ToolCall`, invokes this
 *    runner, then opens a fresh SSE request with the accumulated
 *    `tool_use` + `tool_result` blocks appended to the message list.
 *  - Return `ToolResult.Ok(text)` with text that is **already formatted
 *    for Claude** (see `WebSearchService.formatSearchResults` etc.).
 *  - Return `ToolResult.Failed(message)` to tell Claude the tool failed;
 *    Claude will surface the failure in natural language instead of
 *    silently dropping it.
 */
interface ToolRunner {

    /**
     * Runs the named tool. [inputJson] is Claude's raw input payload as
     * JSON — the runner is responsible for parsing it against the
     * schema that was advertised in [ToolDefinition.inputSchemaJson].
     */
    suspend fun run(name: String, inputJson: String): ToolResult
}

sealed class ToolResult {

    data class Ok(val text: String) : ToolResult()

    data class Failed(val message: String) : ToolResult()
}

/**
 * Suspends until the user answers a destructive-action confirmation.
 *
 * The `:android-runtime` `HandyToolRunner` calls this for every
 * [com.handy.core.intent.IntentResult.NeedsConfirmation] returned by
 * the dispatcher (call, text, share). The UI side owns the bottom
 * sheet / dialog and resolves the suspension — see
 * `:app`'s `ChatConfirmationBroker`.
 *
 * The default binding (no confirmation UI attached) returns `false`
 * so destructive actions fail closed.
 */
fun interface ConfirmationPrompter {

    suspend fun confirm(reason: String): Boolean

    companion object {
        /** Fallback binding — declines every confirmation. */
        val AlwaysDecline: ConfirmationPrompter = ConfirmationPrompter { false }
    }
}
