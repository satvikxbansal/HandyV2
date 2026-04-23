package com.handy.core.llm

import com.handy.core.model.ChatMessage
import com.handy.core.model.ImagePart
import com.handy.core.screen.ScreenTextSnapshot

/**
 * One streaming request to the LLM.
 *
 * Pure data — `:core` never touches `android.*`. Images are already JPEG
 * bytes; the `screenText` slot carries the optional accessibility-tree
 * snapshot that the `ScreenInputRouter` may attach.
 */
data class LlmRequest(
    val systemPrompt: String,
    val messages: List<ChatMessage>,
    val images: List<ImagePart> = emptyList(),
    val screenText: ScreenTextSnapshot? = null,
    val tools: List<ToolDefinition> = emptyList(),
    val maxTokens: Int = 2048,
    /** Optional provider-specific model override. Falls back to provider default. */
    val modelOverride: String? = null,
)
