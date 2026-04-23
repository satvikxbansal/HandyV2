package com.handy.core.model

/**
 * Tool-name → overlay status-line mapping. Ported verbatim from
 * `HandyManager.swift → webSearchStatusText`. Unknown tool names collapse
 * to the generic "Looking things up..." line.
 */
object WebSearchStatusText {

    const val WEB_SEARCH: String = "Searching the web..."
    const val GITHUB_SEARCH: String = "Searching GitHub..."
    const val FETCH_PAGE: String = "Reading page..."
    const val FALLBACK: String = "Looking things up..."

    fun statusFor(toolName: String): String = when (toolName) {
        "web_search" -> WEB_SEARCH
        "github_search" -> GITHUB_SEARCH
        "fetch_page" -> FETCH_PAGE
        else -> FALLBACK
    }
}
