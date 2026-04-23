package com.handy.core.tool

/**
 * Browser URL → stable "umbrella" label for history keying.
 *
 * Ported from macOS `ScreenCaptureService.umbrellaSiteLabel(from:)`. The
 * goal is one chat thread per site, not per page — so
 * `github.com/user/repo` and `github.com/user/repo/issues/42` both
 * resolve to `"GitHub"`.
 *
 * v1 ships a minimal table — Gmail, GitHub, YouTube, X, Reddit, Google
 * Maps, ChatGPT, Claude, Linear, Notion — and a "hostname → Title Case"
 * fallback.
 */
object UmbrellaSiteLabels {

    private val HOSTNAME_MAP: Map<String, String> = mapOf(
        "mail.google.com" to "Gmail",
        "gmail.com" to "Gmail",
        "github.com" to "GitHub",
        "gist.github.com" to "GitHub Gists",
        "youtube.com" to "YouTube",
        "m.youtube.com" to "YouTube",
        "youtu.be" to "YouTube",
        "x.com" to "X",
        "twitter.com" to "X",
        "reddit.com" to "Reddit",
        "old.reddit.com" to "Reddit",
        "google.com" to "Google",
        "www.google.com" to "Google",
        "maps.google.com" to "Google Maps",
        "google.com/maps" to "Google Maps",
        "chat.openai.com" to "ChatGPT",
        "chatgpt.com" to "ChatGPT",
        "claude.ai" to "Claude",
        "linear.app" to "Linear",
        "notion.so" to "Notion",
        "www.notion.so" to "Notion",
        "docs.google.com" to "Google Docs",
        "drive.google.com" to "Google Drive",
        "slack.com" to "Slack",
        "discord.com" to "Discord",
        "stackoverflow.com" to "Stack Overflow",
        "wikipedia.org" to "Wikipedia",
        "en.wikipedia.org" to "Wikipedia",
    )

    /** Normalise a URL to the stable site key used by history files. */
    fun siteKeyFor(url: String): String? {
        val host = hostnameFor(url) ?: return null
        return "host:$host"
    }

    /**
     * Human umbrella label for display + tool-memory keying. Returns null
     * when the URL is malformed; the caller then falls back to the
     * browser app name.
     */
    fun umbrellaLabelFor(url: String): String? {
        val host = hostnameFor(url) ?: return null

        HOSTNAME_MAP[host]?.let { return it }

        val trimmed = host.removePrefix("www.").removePrefix("m.")
        HOSTNAME_MAP[trimmed]?.let { return it }

        // "some-cool-site.com" → "Some Cool Site"
        val bare = trimmed.substringBeforeLast('.')
        if (bare.isBlank()) return null
        return bare
            .split('-', '.', '_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    private fun hostnameFor(url: String): String? {
        val withScheme = if (url.contains("://")) url else "https://$url"
        return try {
            val uri = java.net.URI(withScheme)
            uri.host?.lowercase()
        } catch (t: Throwable) {
            null
        }
    }
}
