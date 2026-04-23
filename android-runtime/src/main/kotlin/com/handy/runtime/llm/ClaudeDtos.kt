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
