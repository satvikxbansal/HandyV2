package com.handy.core.parsing

/**
 * Assistant-markup parsing for Handy's Claude responses.
 *
 * Ported from the macOS `PointParser.swift` with two extensions:
 *
 *  1. The pixel-coordinate `[POINT:x,y:label]` form is still **accepted**
 *     (so macOS test fixtures round-trip) but is **never generated** in v1
 *     — the model is told to emit semantic forms only. See
 *     `.cursor/rules/10-handy-project-guardrails.mdc` → "Pointing contract".
 *
 *  2. The semantic forms are:
 *
 *         [POINT:role=<role>;text=<exact visible text>]
 *         [POINT:viewId=<resource id suffix>]
 *         [POINT:desc=<contentDescription>]
 *         [POINT:none]
 *
 * The clamp constants (420 for TTS, 110 for overlay) and the
 * sentence-boundary truncation logic are bit-for-bit ports from macOS.
 * Do **not** adjust those numbers without a `DESIGN_NOTES.md` entry.
 */
object AssistantMarkupParser {

    const val MAX_CHARS_TTS: Int = 420
    const val MAX_CHARS_OVERLAY: Int = 110

    /** Any `[POINT:...]` tag — used by [stripPointTags]. */
    private val anyPointTagRegex = Regex("""\[POINT:[^\]]*\]""")

    /**
     * Legacy pixel form. Last match anywhere in the text wins when not
     * end-anchored (mirrors macOS behaviour for trailing punctuation /
     * newlines). Non-public: runtime emits only semantic forms.
     */
    private val pixelPointRegex = Regex(
        """\[POINT:(?:none|(\d+)\s*,\s*(\d+)(?::([^\]:\s][^\]:]*?))?(?::screen(\d+))?)\]""",
    )

    /**
     * Semantic point form: `[POINT:role=<role>;text=<text>]` /
     * `[POINT:viewId=<id>]` / `[POINT:desc=<desc>]` / `[POINT:none]`.
     *
     * The inner body is parsed key=value pairs separated by `;`; whitespace
     * around keys and values is tolerated. Values may contain spaces but
     * not `]`. `[POINT:none]` is treated specially — no body required.
     */
    private val semanticPointRegex = Regex(
        """\[POINT:(?:none|((?:[a-zA-Z]+\s*=\s*[^;\]]+(?:;\s*)?)+))\]""",
    )

    /** [SPOKEN]...[/SPOKEN] extraction. DOTALL so the content may span newlines. */
    private val spokenRegex = Regex(
        pattern = """\[SPOKEN\](.*?)\[/SPOKEN\]""",
        option = RegexOption.DOT_MATCHES_ALL,
    )

    /**
     * Strip every `[POINT:…]` tag from assistant text before rendering it
     * in the chat UI. Collapses the resulting `\n\n\n+` sequences down to
     * `\n\n` and trims leading/trailing whitespace.
     */
    fun stripPointTags(text: String): String {
        var result = anyPointTagRegex.replace(text, "")
        while (result.contains("\n\n\n")) {
            result = result.replace("\n\n\n", "\n\n")
        }
        return result.trim()
    }

    /**
     * Result of parsing a `[POINT:…]` tag.
     *
     * Exactly one of [pixel], [semantic], or [isNone] is non-null / true
     * when the parse succeeded. All three are null/false when there is no
     * pointer in the input.
     */
    data class PointingResult(
        val pixel: PixelPoint? = null,
        val semantic: SemanticPoint? = null,
        val isNone: Boolean = false,
        val cleanedText: String = "",
    ) {
        val hasPointer: Boolean
            get() = pixel != null || semantic != null
    }

    data class PixelPoint(
        val x: Int,
        val y: Int,
        val label: String? = null,
        val screenNumber: Int? = null,
    )

    /** Semantic pointer spec. Exactly one of the identifying fields is non-null. */
    data class SemanticPoint(
        val role: String? = null,
        val text: String? = null,
        val viewId: String? = null,
        val contentDescription: String? = null,
    ) {
        init {
            require(role != null || text != null || viewId != null || contentDescription != null) {
                "SemanticPoint must carry at least one identifier"
            }
        }
    }

    /**
     * Parse the trailing pointer tag out of [text]. The semantic form takes
     * priority over the legacy pixel form when both match; in practice the
     * model only emits one per response.
     */
    fun parsePoint(text: String): PointingResult {
        val cleaned = stripPointTags(text)

        // Semantic first (v1 contract).
        semanticPointRegex.findAll(text).lastOrNull()?.let { match ->
            val body = match.value
            if (body.replace(" ", "").lowercase() == "[point:none]") {
                return PointingResult(isNone = true, cleanedText = cleaned)
            }
            val inner = match.groupValues[1]
            val pairs = parseKeyValuePairs(inner)
            val semantic = SemanticPoint(
                role = pairs["role"]?.trim(),
                text = pairs["text"]?.trim(),
                viewId = pairs["viewid"]?.trim() ?: pairs["viewId"]?.trim(),
                contentDescription = pairs["desc"]?.trim() ?: pairs["description"]?.trim(),
            ).takeIf { s ->
                s.role != null || s.text != null || s.viewId != null || s.contentDescription != null
            }
            if (semantic != null) {
                return PointingResult(semantic = semantic, cleanedText = cleaned)
            }
        }

        // Legacy pixel form — still parsed for macOS fixture round-trip.
        pixelPointRegex.findAll(text).lastOrNull()?.let { match ->
            val body = match.value
            if (body.replace(" ", "").lowercase() == "[point:none]") {
                return PointingResult(isNone = true, cleanedText = cleaned)
            }
            val x = match.groupValues.getOrNull(1)?.toIntOrNull()
            val y = match.groupValues.getOrNull(2)?.toIntOrNull()
            val label = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            val screen = match.groupValues.getOrNull(4)?.toIntOrNull()
            if (x != null && y != null) {
                return PointingResult(
                    pixel = PixelPoint(x = x, y = y, label = label, screenNumber = screen),
                    cleanedText = cleaned,
                )
            }
        }

        return PointingResult(cleanedText = cleaned)
    }

    /**
     * Extract [SPOKEN]...[/SPOKEN]. Returns (spoken, display) where
     * `spoken` is the TTS-bound inner portion and `display` is the full
     * chat-visible text with the SPOKEN tags stripped.
     *
     * Matches macOS `PointParser.extractSpokenPart` shape: when no tags
     * are found, both returned strings equal the input. When tags are
     * found and the non-tag portion is non-empty, the display is
     * `spoken + "\n\n" + remainder`; when empty, the display equals the
     * spoken portion.
     */
    fun extractSpokenPart(text: String): Pair<String, String> {
        val match = spokenRegex.find(text)
            ?: return text to text

        val spoken = match.groupValues[1].trim()
        val withoutTags = spokenRegex.replace(text, "").trim()
        val display = if (withoutTags.isEmpty()) spoken else "$spoken\n\n$withoutTags"
        return spoken to display
    }

    /** Caps TTS length when the model ignores `[SPOKEN]` discipline or omits tags. */
    fun clampVoiceSpokenForTts(text: String, maxChars: Int = MAX_CHARS_TTS): String {
        val t = text.trim()
        if (t.length <= maxChars) return t
        return truncateAtSentenceBoundary(t, maxChars)
            ?: (t.take(maxChars).trimEnd() + "…")
    }

    /** Shorter cap for the companion-pointer bubble so it stays glanceable. */
    fun clampVoiceSpokenForOverlay(text: String, maxChars: Int = MAX_CHARS_OVERLAY): String {
        val t = text.trim()
        if (t.length <= maxChars) return t
        val clipped = truncateAtSentenceBoundary(t, maxChars)
        if (clipped != null && clipped.length <= maxChars) return clipped
        return t.take(maxChars).trimEnd() + "…"
    }

    /**
     * Substring ending at the last sentence boundary at or before
     * [maxChars]. Returns `null` when there is no usable boundary.
     */
    private fun truncateAtSentenceBoundary(text: String, maxChars: Int): String? {
        if (text.length <= maxChars) return text
        val prefix = text.substring(0, maxChars)
        val delimiters = listOf(". ", "! ", "? ", ".\n", "!\n", "?\n")
        var bestUpper: Int? = null
        for (d in delimiters) {
            var start = 0
            while (true) {
                val idx = prefix.indexOf(d, startIndex = start)
                if (idx < 0) break
                bestUpper = idx + d.length
                start = idx + d.length
            }
        }
        val end = bestUpper ?: return null
        if (end <= 0) return null
        val s = prefix.substring(0, end).trim()
        return s.ifEmpty { null }
    }

    private fun parseKeyValuePairs(body: String): Map<String, String> {
        val out = HashMap<String, String>(4)
        body.split(';').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = pair.substring(0, eq).trim().lowercase()
                val value = pair.substring(eq + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    out[key] = value
                }
            }
        }
        return out
    }
}
