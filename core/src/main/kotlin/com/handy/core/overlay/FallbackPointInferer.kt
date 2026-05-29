package com.handy.core.overlay

import com.handy.core.parsing.AssistantMarkupParser

/**
 * Last-resort pointer recovery for the overlay path.
 *
 * The model is still expected to emit [POINT:...] tags. This inferer only
 * recovers guidance-mode misses by selecting an existing cached markId from
 * the same snapshot shown to the model. It never fabricates coordinates and
 * never authorizes an action.
 */
object FallbackPointInferer {

    fun infer(
        userText: String,
        assistantText: String,
        marks: List<AccessibilityMark>,
    ): AssistantMarkupParser.SemanticPoint? {
        val stableMarks = marks.withStableMarkIds()
        if (stableMarks.isEmpty()) return null

        val userNorm = normalize(userText)
        val assistantNorm = normalize(AssistantMarkupParser.stripDisplayMarkup(assistantText))
        if (!hasPointingIntent(userNorm, assistantNorm)) return null

        val haystackTokens = tokens("$userNorm $assistantNorm").toSet()
        val scored = stableMarks
            .asSequence()
            .filter { it.canBeFallbackTarget() }
            .mapNotNull { mark ->
                val score = score(mark, userNorm, assistantNorm, haystackTokens)
                if (score > 0) ScoredMark(mark, score) else null
            }
            .sortedByDescending { it.score }
            .toList()

        val best = scored.firstOrNull() ?: return null
        if (best.score < MIN_SCORE) return null
        val runnerUp = scored.drop(1).firstOrNull()
        if (runnerUp != null && best.score - runnerUp.score < AMBIGUITY_MARGIN) return null
        return best.mark.toSemanticPoint()
    }

    private fun score(
        mark: AccessibilityMark,
        userNorm: String,
        assistantNorm: String,
        haystackTokens: Set<String>,
    ): Int {
        val label = mark.preferredLabel()?.takeIf { it.isNotBlank() }
        val labelNorm = label?.let(::normalize).orEmpty()
        val labelTokens = tokens(labelNorm)
        var score = 0

        if (labelNorm.length >= 3) {
            if (userNorm.contains(labelNorm)) score += 95
            if (assistantNorm.contains(labelNorm)) score += 85

            val overlap = labelTokens.count { it in haystackTokens }
            if (overlap > 0) {
                score += overlap * TOKEN_OVERLAP_SCORE
                if (overlap == labelTokens.size) score += ALL_TOKENS_BONUS
            }
            if (labelTokens.size == 1 && labelTokens.firstOrNull() in haystackTokens) {
                score -= SINGLE_TOKEN_PENALTY
            }
        }

        if (mentionsMenu(userNorm, assistantNorm) && mark.isTopLeftMenuCandidate()) {
            score = maxOf(score, TOP_LEFT_MENU_SCORE)
        }

        val firstToken = labelTokens.firstOrNull()
        if (firstToken != null && firstToken in ACTION_TOKENS && firstToken in haystackTokens) {
            score += ACTION_TOKEN_BONUS
        }
        if (mark.clickable || mark.editable) score += ACTIONABLE_BONUS
        if (mark.semanticRole() != null) score += ROLE_BONUS
        return score
    }

    private fun hasPointingIntent(userNorm: String, assistantNorm: String): Boolean {
        if (QUESTION_NAVIGATION_PATTERNS.any { it.containsMatchIn(userNorm) }) return true
        if (EXECUTABLE_REQUEST_PATTERNS.any { it.containsMatchIn(userNorm) }) return false
        if (
            USER_GUIDANCE_HINTS.any { it.containsMatchIn(userNorm) } &&
            ASSISTANT_NAVIGATION_PATTERNS.any { it.containsMatchIn(assistantNorm) }
        ) return true
        return false
    }

    private fun mentionsMenu(userNorm: String, assistantNorm: String): Boolean {
        val text = "$userNorm $assistantNorm"
        return MENU_TOKENS.any { token -> text.contains(token) }
    }

    private fun AccessibilityMark.canBeFallbackTarget(): Boolean =
        !isPassword &&
            enabled &&
            right > left &&
            bottom > top &&
            !markId.isNullOrBlank() &&
            (clickable || editable || scrollable || !preferredLabel().isNullOrBlank())

    private fun AccessibilityMark.isTopLeftMenuCandidate(): Boolean {
        if (left > TOP_LEFT_MAX_X || top > TOP_LEFT_MAX_Y) return false
        val label = normalize(preferredLabel().orEmpty())
        return clickable && MENU_TOKENS.any { label.contains(it) }
    }

    private fun AccessibilityMark.preferredLabel(): String? =
        text ?: contentDescription ?: viewIdSuffix

    private fun AccessibilityMark.toSemanticPoint(): AssistantMarkupParser.SemanticPoint =
        AssistantMarkupParser.SemanticPoint(
            markId = markId,
            role = semanticRole(),
            text = text,
            contentDescription = contentDescription,
            viewId = viewIdSuffix,
        )

    private fun AccessibilityMark.semanticRole(): String? {
        val lower = role.lowercase()
        return when {
            lower.contains("button") -> "button"
            lower.contains("edit") -> "textfield"
            lower.contains("checkbox") -> "checkbox"
            lower.contains("switch") -> "switch"
            lower.contains("tab") -> "tab"
            lower.contains("menu") -> "menuitem"
            else -> null
        }
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace('-', ' ')
            .replace('_', ' ')
            .replace(Regex("""[^a-z0-9\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun tokens(value: String): List<String> =
        normalize(value)
            .split(' ')
            .filter { token -> token.length >= 3 && token !in STOP_WORDS }

    private data class ScoredMark(
        val mark: AccessibilityMark,
        val score: Int,
    )

    private val QUESTION_NAVIGATION_PATTERNS = listOf(
        Regex("""\bhow\s+(do|can|should)\s+i\b"""),
        Regex("""\bwhere\s+(do|can|should)\s+i\b"""),
        Regex("""\bwhere\s+is\b"""),
        Regex("""\bwhich\s+(button|field|menu|option|setting)\b"""),
        Regex("""\bshow\s+me\b"""),
        Regex("""\bwhat\s+can\s+i\s+do\s+here\b"""),
        Regex("""\bwhat\s+should\s+i\s+tap\b"""),
    )

    private val ASSISTANT_NAVIGATION_PATTERNS = listOf(
        Regex("""\b(tap|press|select|choose|open|use)\s+(the|that|this)\b"""),
        Regex("""\byou(?:'| )?ll\s+want\s+to\s+(tap|press|select|choose|open|use)\b"""),
        Regex("""\blook\s+for\s+(the|a)\b"""),
    )

    private val USER_GUIDANCE_HINTS = listOf(
        Regex("""\b(can\s?t|cannot|help|find|show|locate|guide)\b"""),
        Regex("""\b(point|tap|press|choose|select)\b"""),
    )

    private val EXECUTABLE_REQUEST_PATTERNS = listOf(
        Regex("""\b(set|start)\s+(an?\s+)?(alarm|timer)\b"""),
        Regex("""\b(open|launch|install|call|text|send|draft|share|create|schedule|book|navigate|play)\b"""),
    )

    private val MENU_TOKENS = setOf(
        "menu",
        "drawer",
        "hamburger",
        "navigation",
        "three line",
        "three horizontal",
        "top left",
    )

    private val ACTION_TOKENS = setOf(
        "add",
        "open",
        "edit",
        "send",
        "save",
        "create",
        "compose",
        "search",
        "start",
        "continue",
        "next",
        "settings",
    )

    private val STOP_WORDS = setOf(
        "the",
        "this",
        "that",
        "you",
        "your",
        "with",
        "into",
        "for",
        "and",
        "new",
        "here",
        "there",
        "what",
        "where",
        "should",
        "want",
        "need",
    )

    private const val TOKEN_OVERLAP_SCORE = 18
    private const val ALL_TOKENS_BONUS = 25
    private const val SINGLE_TOKEN_PENALTY = 20
    private const val ACTION_TOKEN_BONUS = 10
    private const val ACTIONABLE_BONUS = 8
    private const val ROLE_BONUS = 4
    private const val TOP_LEFT_MENU_SCORE = 72
    private const val TOP_LEFT_MAX_X = 180
    private const val TOP_LEFT_MAX_Y = 360
    private const val MIN_SCORE = 60
    private const val AMBIGUITY_MARGIN = 8
}
