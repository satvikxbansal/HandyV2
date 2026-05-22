package com.handy.core.model

import com.handy.core.privacy.Sensitive
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * One chat turn, persisted to the JSON history store (`:android-runtime`
 * `JsonHistoryStore`) and rendered by `ChatActivity`.
 *
 * The field names and shape mirror the macOS `ChatMessage` (Handy V1
 * `Handy/Models/ChatMessage.swift`) so histories can round-trip between
 * platforms with a single timestamp translation (see [timestampEpochMs]).
 * Swift's `JSONEncoder` writes `Date` as a seconds-since-reference-date
 * `Double`; on disk we standardise on epoch milliseconds `Long`, and the
 * storage boundary converts if a macOS file is ever imported.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatMessage(
    /** UUID as a lowercase canonical string (matches Swift `UUID().uuidString.lowercased()`). */
    val id: String,
    val role: MessageRole,
    @Sensitive
    val content: String,
    /** Unix epoch milliseconds. See class-level note on macOS compatibility. */
    @EncodeDefault
    val timestampEpochMs: Long,
    val toolName: String? = null,
    @EncodeDefault
    val isStreaming: Boolean = false,
    @EncodeDefault
    val searchToolsUsed: List<String> = emptyList(),
) {
    override fun toString(): String =
        "ChatMessage(id=$id, role=$role, content=[redacted:${content.length} chars], " +
            "timestampEpochMs=$timestampEpochMs, toolName=$toolName, isStreaming=$isStreaming, " +
            "searchToolsUsed=$searchToolsUsed)"

    companion object {
        /**
         * Factory that mints a fresh UUID and timestamp. Keeping this in a
         * companion (rather than a default constructor) leaves
         * deserialisation deterministic — no hidden clock reads.
         */
        fun new(
            role: MessageRole,
            content: String,
            toolName: String? = null,
            isStreaming: Boolean = false,
            searchToolsUsed: List<String> = emptyList(),
            clock: () -> Long = { System.currentTimeMillis() },
            uuid: () -> String = { randomUuid() },
        ): ChatMessage = ChatMessage(
            id = uuid(),
            role = role,
            content = content,
            timestampEpochMs = clock(),
            toolName = toolName,
            isStreaming = isStreaming,
            searchToolsUsed = searchToolsUsed,
        )
    }
}

@Serializable
enum class MessageRole {
    @kotlinx.serialization.SerialName("user") USER,
    @kotlinx.serialization.SerialName("assistant") ASSISTANT,
    @kotlinx.serialization.SerialName("system") SYSTEM,
}

/** The on-disk unit of the per-tool history store. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ConversationTurn(
    @Sensitive
    val userMessage: String,
    @Sensitive
    val assistantMessage: String,
    val timestampEpochMs: Long,
    val toolName: String? = null,
) {
    override fun toString(): String =
        "ConversationTurn(userMessage=[redacted:${userMessage.length} chars], " +
            "assistantMessage=[redacted:${assistantMessage.length} chars], " +
            "timestampEpochMs=$timestampEpochMs, toolName=$toolName)"
}

/**
 * Canonical lowercase UUID string. Pure Kotlin — `java.util.UUID` is on the
 * JVM standard library, not `android.*`, so this stays allowed in `:core`.
 */
internal fun randomUuid(): String = java.util.UUID.randomUUID().toString()
