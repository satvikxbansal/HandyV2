package com.handy.core.history

import com.handy.core.model.ChatMessage
import com.handy.core.model.ConversationTurn
import kotlinx.coroutines.flow.Flow

/**
 * Per-tool chat history.
 *
 * v1 implementation in `:android-runtime` is `JsonHistoryStore` — one JSON
 * file per tool-memory key, atomic write via temp-file-then-rename, hard
 * cap enforced at write time. The on-disk shape is kept macOS-compatible
 * (see `core.model.ChatMessage` class-level doc).
 *
 * Do **not** add Room in v1 (see guardrails → "Persistence").
 */
interface ChatHistoryStore {

    /** Observable stream of the current history for [toolName]. */
    fun observe(toolName: String): Flow<List<ChatMessage>>

    /** Snapshot read. Suspends so callers never block the main thread. */
    suspend fun load(toolName: String): List<ChatMessage>

    /** Append one full user/assistant pair, enforcing the per-tool cap. */
    suspend fun appendTurn(toolName: String, turn: ConversationTurn)

    /** Replace the full history for [toolName]. Intended for test fixtures. */
    suspend fun replace(toolName: String, messages: List<ChatMessage>)

    /** Delete one tool's history. */
    suspend fun clear(toolName: String)

    /** Delete every tool's history (user-initiated "reset" from settings). */
    suspend fun clearAll()

    /** All tool-memory keys that currently have on-disk history. */
    suspend fun listTools(): List<String>

    companion object {
        /** The hard cap on ConversationTurns kept per tool. */
        const val MAX_TURNS_PER_TOOL: Int = 100
    }
}
