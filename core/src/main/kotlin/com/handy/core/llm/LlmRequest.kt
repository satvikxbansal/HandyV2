package com.handy.core.llm

import com.handy.core.model.ChatMessage
import com.handy.core.model.ImagePart
import com.handy.core.privacy.Sensitive
import com.handy.core.screen.ScreenTextSnapshot

/**
 * One streaming request to the LLM.
 *
 * Pure data — `:core` never touches `android.*`. Images are already JPEG
 * bytes; the `screenText` slot carries the optional accessibility-tree
 * snapshot that the `ScreenInputRouter` may attach.
 */
data class LlmRequest(
    @Sensitive
    val systemPrompt: String,
    @Sensitive
    val messages: List<ChatMessage>,
    val images: List<ImagePart> = emptyList(),
    @Sensitive
    val screenText: ScreenTextSnapshot? = null,
    val tools: List<ToolDefinition> = emptyList(),
    val maxTokens: Int = 2048,
    /** Optional provider-specific model override. Falls back to provider default. */
    val modelOverride: String? = null,
) {
    override fun toString(): String =
        "LlmRequest(systemPrompt=[redacted:${systemPrompt.length} chars], " +
            "messages=${messages.size}, images=${images.size}, screenText=${screenText != null}, " +
            "tools=${tools.map { it.name }}, maxTokens=$maxTokens, modelOverride=$modelOverride)"
}
