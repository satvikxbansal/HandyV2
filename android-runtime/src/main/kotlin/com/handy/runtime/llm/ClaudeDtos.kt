package com.handy.runtime.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire-format DTOs for the Anthropic Messages API (SSE streaming).
 *
 * These types only exist inside `:android-runtime`; `:core` stays
 * provider-neutral.
 */

@Serializable
internal data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
    val stream: Boolean = true,
    val tools: List<ClaudeTool>? = null,
)

@Serializable
internal data class ClaudeMessage(
    val role: String,
    val content: List<ClaudeContentPart>,
)

/**
 * Anthropic content-block union. Claude accepts `text`, `image`,
 * `tool_use`, and `tool_result` blocks; everything goes through the
 * same `content: List<…>` slot on a [ClaudeMessage].
 */
@Serializable
internal sealed class ClaudeContentPart {

    @Serializable
    @SerialName("text")
    internal data class Text(val text: String) : ClaudeContentPart()

    @Serializable
    @SerialName("image")
    internal data class Image(val source: Source) : ClaudeContentPart() {
        @Serializable
        internal data class Source(
            val type: String = "base64",
            @SerialName("media_type") val mediaType: String = "image/jpeg",
            val data: String,
        )
    }

    /**
     * An assistant-emitted tool invocation replayed back on the next
     * request so Claude sees its own prior `tool_use` when it resumes.
     * [input] is the full JSON object the model produced — we deserialise
     * and re-serialise via [JsonElement] to avoid lossy round-trip.
     */
    @Serializable
    @SerialName("tool_use")
    internal data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonElement,
    ) : ClaudeContentPart()

    /**
     * The user-side follow-up that carries a tool's result back to
     * Claude. [content] is a free-form text block; [isError] is true when
     * the tool raised — Claude handles failure modes more gracefully
     * when we flag them explicitly.
     */
    @Serializable
    @SerialName("tool_result")
    internal data class ToolResult(
        @SerialName("tool_use_id") val toolUseId: String,
        val content: String,
        @SerialName("is_error") val isError: Boolean = false,
    ) : ClaudeContentPart()
}

@Serializable
internal data class ClaudeTool(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonElement,
)

// ------- streaming events ----------

@Serializable
internal data class ClaudeStreamEvent(
    val type: String,
    val index: Int? = null,
    @SerialName("content_block") val contentBlock: ClaudeContentBlock? = null,
    val delta: ClaudeDelta? = null,
    val message: ClaudeStreamMessage? = null,
)

@Serializable
internal data class ClaudeContentBlock(
    val type: String,
    val id: String? = null,
    val name: String? = null,
    val text: String? = null,
)

@Serializable
internal data class ClaudeDelta(
    val type: String? = null,
    val text: String? = null,
    @SerialName("partial_json") val partialJson: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
internal data class ClaudeStreamMessage(
    val id: String? = null,
    val role: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
)
