package com.handy.core.intent

/**
 * Small keyword bank the `ScreenInputRouter` uses to bias toward dispatch
 * when the user's message clearly maps to an [com.handy.core.action.AssistantAction].
 *
 * Referenced from the intent-tool prompt addendum so we don't duplicate
 * the vocabulary in two places.
 */
object IntentKeywordHints {

    /**
     * Flat keyword list. Each entry is a substring-match (case-insensitive).
     * The router keys off presence, not count — mentioning "timer" once is
     * enough to suggest the `dispatch_action` tool.
     */
    val KEYWORDS: List<String> = listOf(
        // timers / alarms
        "timer", "minutes", "alarm", "wake me",
        // app launch
        "open ", "launch ", "start app",
        // calls / messages
        "call ", "dial ", "text ", "message ", "send to ",
        // maps / navigation
        "directions", "navigate", "map of", "near me", "route to",
        // email
        "email ", "compose email", "send email",
        // sharing
        "share ", "forward to ", "send this",
        // web search
        "search for ", "google ", "look up ",
    )

    fun messageLooksActionable(userMessage: String): Boolean {
        val lower = userMessage.lowercase()
        return KEYWORDS.any { lower.contains(it) }
    }
}
