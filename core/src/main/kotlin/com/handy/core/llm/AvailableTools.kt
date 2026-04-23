package com.handy.core.llm

/**
 * Builds the list of [ToolDefinition]s sent to Claude for a given user
 * turn. Gating mirrors `ClaudeAPIService.availableTools` on macOS:
 *
 *  - `web_search` is offered ONLY when Brave Search has a key. macOS
 *    behaviour: when the user has `webSearchEnabled = true` but no Brave
 *    key, `web_search` is excluded — but `github_search` and
 *    `fetch_page` still ship, because they work without Brave.
 *  - `github_search` and `fetch_page` are offered whenever web search is
 *    enabled in settings, regardless of keys (Jina / GitHub tokens are
 *    optional — they only raise rate limits).
 *  - `dispatch_action` is the Android-only action tool; guarded by
 *    [intentDispatchEnabled] which defaults to true.
 *
 * Keep schemas minimal but accurate — Claude uses them to decide which
 * tool to call. The strings are JSON schema fragments rendered to
 * strings so the `:core` module stays provider-agnostic.
 */
fun availableTools(
    webSearchEnabled: Boolean,
    hasBraveKey: Boolean,
    intentDispatchEnabled: Boolean,
): List<ToolDefinition> {
    val out = mutableListOf<ToolDefinition>()

    if (webSearchEnabled) {
        if (hasBraveKey) {
            out += ToolDefinition(
                name = "web_search",
                description = "Search the public web via Brave Search. Use this for questions about current or real-time information that your training data might not cover — news, product releases, prices, recent events. Returns a ranked list of page titles, URLs, and short snippets.",
                inputSchemaJson = """{"type":"object","properties":{"query":{"type":"string","description":"The search query. Keep it short and specific."}},"required":["query"]}""",
            )
        }
        out += ToolDefinition(
            name = "fetch_page",
            description = "Fetch the plain-text content of a specific URL via Jina Reader. Use when the user references a URL or when a previous web_search result looks promising and you need the full page content. Returns markdown-like plain text, truncated to ~16k characters.",
            inputSchemaJson = """{"type":"object","properties":{"url":{"type":"string","description":"The fully qualified URL to fetch (https://...)."}},"required":["url"]}""",
        )
        out += ToolDefinition(
            name = "github_search",
            description = "Search GitHub for public repositories. Use when the user asks for a library, SDK, or package — returns up to 5 repos ordered by stars.",
            inputSchemaJson = """{"type":"object","properties":{"query":{"type":"string","description":"Free-form search query (same shape as the GitHub search UI)."},"language":{"type":"string","description":"Optional language filter, e.g. \"kotlin\", \"swift\", \"typescript\"."}},"required":["query"]}""",
        )
    }

    if (intentDispatchEnabled) {
        out += ToolDefinition(
            name = "dispatch_action",
            description = DISPATCH_ACTION_DESCRIPTION,
            inputSchemaJson = DISPATCH_ACTION_INPUT_SCHEMA_JSON,
        )
    }

    return out
}

private const val DISPATCH_ACTION_DESCRIPTION =
    "Fire a native Android Intent on the user's behalf. Call this for well-defined one-step requests: set a timer, set an alarm, open an app, open a URL, search Maps, dial a number, compose an email, share text, or run a web-search intent. Destructive actions (call, email, share) always require user confirmation — the tool will return \"needs_confirmation\" in that case; you must ask the user and, once they agree, call dispatch_action again with the same payload."

private val DISPATCH_ACTION_INPUT_SCHEMA_JSON: String = """
{
  "type": "object",
  "properties": {
    "type": {
      "type": "string",
      "enum": [
        "start_timer",
        "set_alarm",
        "open_url",
        "open_app",
        "dial_number",
        "maps_search",
        "compose_email",
        "share_text",
        "web_search"
      ],
      "description": "Which native action to dispatch."
    },
    "seconds": {"type": "integer", "description": "start_timer: duration in seconds."},
    "label": {"type": "string", "description": "start_timer / set_alarm: optional user-visible label."},
    "hour": {"type": "integer", "description": "set_alarm: 0-23."},
    "minute": {"type": "integer", "description": "set_alarm: 0-59."},
    "url": {"type": "string", "description": "open_url: https://… URL."},
    "packageHint": {"type": "string", "description": "open_app: app name or package hint (e.g. \"Spotify\" or \"com.spotify.music\")."},
    "number": {"type": "string", "description": "dial_number: the phone number to dial."},
    "query": {"type": "string", "description": "maps_search / web_search: the search text."},
    "to": {"type": "string", "description": "compose_email: recipient email address."},
    "subject": {"type": "string", "description": "compose_email: email subject."},
    "body": {"type": "string", "description": "compose_email: email body."},
    "text": {"type": "string", "description": "share_text: the text to share."}
  },
  "required": ["type"]
}
""".trim()
