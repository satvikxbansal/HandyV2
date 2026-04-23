package com.handy.core.tool

/**
 * The "tool memory" context for one conversation. Handy's per-tool chat
 * history is keyed by the composite of `packageName` + optional
 * `umbrellaSiteLabel`. Within a browser, the umbrella label identifies the
 * site (gmail.com, github.com, …) so switching tabs doesn't bleed one
 * conversation into another.
 */
data class ToolContext(
    val packageName: String,
    val appLabel: String,
    val umbrellaSiteLabel: String? = null,
) {
    /**
     * The key used by the JSON history store. Paths on disk are derived
     * from this via a deterministic sanitisation (no slashes, no colons).
     */
    val historyKey: String
        get() = if (umbrellaSiteLabel != null) "$packageName::$umbrellaSiteLabel" else packageName

    /** Human label shown in the chat top-bar ("Gmail", "GitHub", "Maps"). */
    val displayLabel: String
        get() = umbrellaSiteLabel ?: appLabel
}
