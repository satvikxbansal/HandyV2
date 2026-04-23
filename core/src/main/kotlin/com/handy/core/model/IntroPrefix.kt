package com.handy.core.model

/**
 * First-turn-only greeting prefix. Ported from
 * `HandyManager.swift → sendMessage` where the prefix is produced inline:
 *
 * ```swift
 * let introPrefix = messages.count <= 1
 *     ? "so we are working with \(toolName), let me help you with your query. "
 *     : ""
 * ```
 *
 * Android mirrors the rule exactly: prepend this prefix iff the
 * conversation for the current tool has at most one message (the user's
 * first turn). The result is prepended to the streamed assistant content
 * by the orchestrator before rendering.
 */
object IntroPrefix {

    /**
     * Returns the prefix when this is the first user turn for [toolName],
     * or an empty string otherwise.
     *
     * @param existingMessageCount number of messages already persisted for
     *   this tool (excluding the user message about to be sent).
     */
    fun forTurn(toolName: String, existingMessageCount: Int): String =
        if (existingMessageCount <= 1) {
            "so we are working with $toolName, let me help you with your query. "
        } else {
            ""
        }
}
