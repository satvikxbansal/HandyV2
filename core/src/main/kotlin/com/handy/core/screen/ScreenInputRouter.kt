package com.handy.core.screen

/**
 * Decides whether a request should include the screenshot, the flattened
 * accessibility tree, or both.
 *
 * Deliberately deterministic and lightweight — see guardrails →
 * "Screen capture pipeline" and the build plan §6. A future `BrainRouter`
 * may graduate to an on-device classifier; v1 is pure heuristics so every
 * decision is testable.
 *
 * Policy:
 *   1. If the user's message leans visual (color, layout, design,
 *      photo…) and not textual → `VisionOnly`.
 *   2. If the user's message leans textual (summarize, read, translate…)
 *      and not visual → `TextOnly`, but only when the tree quality is
 *      non-trivial; else `VisionOnly`.
 *   3. Otherwise → `Both` (cheap insurance when uncertain).
 */
object ScreenInputRouter {

    sealed class Mode {
        data object VisionOnly : Mode()
        data object TextOnly : Mode()
        data object Both : Mode()
    }

    internal val VISUAL_KEYWORDS: List<String> = listOf(
        "where is", "point at", "point to", "click ", "tap ",
        "show me", "highlight", "color", "layout", "design",
        "image", "photo", "picture", "icon", "diagram",
        "looks like", "appearance", "screenshot",
    )

    internal val TEXTUAL_KEYWORDS: List<String> = listOf(
        "what does", "what is this", "summarize", "summary of", "read ", "read the",
        "email", "article", "message", "translate", "copy this",
        "explain this", "rephrase", "paraphrase", "extract",
    )

    /** Node count below which text-only mode is suppressed as unreliable. */
    private const val MIN_USABLE_TREE_NODES: Int = 6

    fun choose(
        userMessage: String,
        treeQualityScore: Int,
        screenTextPresent: Boolean,
    ): Mode {
        val lower = userMessage.lowercase()
        val looksVisual = VISUAL_KEYWORDS.any { lower.contains(it) }
        val looksTextual = TEXTUAL_KEYWORDS.any { lower.contains(it) }

        // When we do not even have a tree to attach, visual is the only
        // honest answer — callers must still check CaptureResult.
        if (!screenTextPresent) return Mode.VisionOnly

        return when {
            looksVisual && !looksTextual -> Mode.VisionOnly
            looksTextual && !looksVisual && treeQualityScore >= MIN_USABLE_TREE_NODES -> Mode.TextOnly
            looksTextual && !looksVisual -> Mode.VisionOnly
            else -> Mode.Both
        }
    }
}
