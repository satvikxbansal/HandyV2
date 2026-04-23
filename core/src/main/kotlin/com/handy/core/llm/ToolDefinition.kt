package com.handy.core.llm

/**
 * Declarative tool description shipped to the LLM.
 *
 * The input schema is raw JSON Schema; `:core` keeps it as a string so
 * we don't couple the interface to a specific schema library.
 *
 * v1 tools:
 *  - `dispatch_action` (always available when intent dispatch is on).
 *  - `web_search` / `fetch_page` / `github_search` (only when
 *    `AppSettings.webSearchEnabled` is true AND the relevant API key is
 *    configured).
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)
