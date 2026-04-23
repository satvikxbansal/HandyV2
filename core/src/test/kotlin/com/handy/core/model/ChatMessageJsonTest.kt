package com.handy.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class ChatMessageJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test fun `round trip preserves every field`() {
        val original = ChatMessage(
            id = "11111111-2222-3333-4444-555555555555",
            role = MessageRole.ASSISTANT,
            content = "hi there.",
            timestampEpochMs = 1_700_000_000_000L,
            toolName = "GitHub",
            isStreaming = false,
            searchToolsUsed = listOf("github_search", "fetch_page"),
        )
        val encoded = json.encodeToString(ChatMessage.serializer(), original)
        val decoded = json.decodeFromString(ChatMessage.serializer(), encoded)
        assertThat(decoded).isEqualTo(original)
    }

    @Test fun `role uses lowercase tokens matching macOS`() {
        val msg = ChatMessage(
            id = "abc",
            role = MessageRole.USER,
            content = "hi",
            timestampEpochMs = 0L,
        )
        val encoded = json.encodeToString(ChatMessage.serializer(), msg)
        assertThat(encoded).contains("\"role\":\"user\"")
    }

    @Test fun `decoding tolerates missing optional fields`() {
        // Minimal valid payload: no toolName, no searchToolsUsed, etc.
        val minimal = """
            {
              "id": "abc",
              "role": "system",
              "content": "ok",
              "timestampEpochMs": 42
            }
        """.trimIndent()
        val decoded = json.decodeFromString(ChatMessage.serializer(), minimal)
        assertThat(decoded.role).isEqualTo(MessageRole.SYSTEM)
        assertThat(decoded.isStreaming).isFalse()
        assertThat(decoded.searchToolsUsed).isEmpty()
        assertThat(decoded.toolName).isNull()
    }

    @Test fun `ConversationTurn round trip`() {
        val turn = ConversationTurn(
            userMessage = "what does this email say",
            assistantMessage = "it's a newsletter about kotlin.",
            timestampEpochMs = 123L,
            toolName = "Gmail",
        )
        val encoded = json.encodeToString(ConversationTurn.serializer(), turn)
        val decoded = json.decodeFromString(ConversationTurn.serializer(), encoded)
        assertThat(decoded).isEqualTo(turn)
    }

    @Test fun `ChatMessage_new mints id and timestamp through injected factories`() {
        val msg = ChatMessage.new(
            role = MessageRole.USER,
            content = "hi",
            toolName = "test",
            clock = { 777L },
            uuid = { "deterministic-uuid" },
        )
        assertThat(msg.id).isEqualTo("deterministic-uuid")
        assertThat(msg.timestampEpochMs).isEqualTo(777L)
        assertThat(msg.role).isEqualTo(MessageRole.USER)
    }
}
