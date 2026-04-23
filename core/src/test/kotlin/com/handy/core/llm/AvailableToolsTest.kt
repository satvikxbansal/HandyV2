package com.handy.core.llm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AvailableToolsTest {

    @Test fun `web search disabled hides all search tools`() {
        val tools = availableTools(
            webSearchEnabled = false,
            hasBraveKey = true,
            intentDispatchEnabled = false,
        )
        assertThat(tools).isEmpty()
    }

    @Test fun `web search enabled with brave key offers all three search tools`() {
        val tools = availableTools(
            webSearchEnabled = true,
            hasBraveKey = true,
            intentDispatchEnabled = false,
        )
        assertThat(tools.map { it.name })
            .containsExactly("web_search", "fetch_page", "github_search")
            .inOrder()
    }

    @Test fun `web search enabled without brave key still offers fetch_page and github_search`() {
        val tools = availableTools(
            webSearchEnabled = true,
            hasBraveKey = false,
            intentDispatchEnabled = false,
        )
        // Mirrors V1: no Brave → no `web_search`, but GitHub + fetch
        // still work because they don't need Brave.
        assertThat(tools.map { it.name }).containsExactly("fetch_page", "github_search").inOrder()
    }

    @Test fun `intent dispatch enabled always adds dispatch_action`() {
        val tools = availableTools(
            webSearchEnabled = false,
            hasBraveKey = false,
            intentDispatchEnabled = true,
        )
        assertThat(tools.map { it.name }).containsExactly("dispatch_action")
    }

    @Test fun `intent + web search both enabled produce the full tool list in stable order`() {
        val tools = availableTools(
            webSearchEnabled = true,
            hasBraveKey = true,
            intentDispatchEnabled = true,
        )
        assertThat(tools.map { it.name })
            .containsExactly("web_search", "fetch_page", "github_search", "dispatch_action")
            .inOrder()
    }

    @Test fun `every tool ships a valid json schema string`() {
        val tools = availableTools(
            webSearchEnabled = true,
            hasBraveKey = true,
            intentDispatchEnabled = true,
        )
        tools.forEach { tool ->
            val schema = kotlinx.serialization.json.Json.parseToJsonElement(tool.inputSchemaJson)
            assertThat(schema).isNotNull()
        }
    }
}
