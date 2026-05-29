package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.SideEffectClassification
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot

object YouTubeRecipe : AppRecipe {
    override val id: String = "youtube"
    override val displayName: String = "Open YouTube"
    override val description: String =
        "Open YouTube search or a channel lookup through a URL handoff; never like, subscribe, or comment."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.OPENS_EXTERNAL_UI

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        if (goal.hasYouTubeEngagementVeto(invocation)) {
            return RecipeProposal.Refused("youtube-engagement-blocked")
        }

        val channel = invocation.arg("channel", "name")
            ?.cleanYouTubeValue()
            ?: goal.text.extractYouTubeChannel()
        if (channel != null || goal.requestedIntent == "youtube_open_channel") {
            val value = channel ?: return RecipeProposal.Refused("missing-channel")
            return appSearchPlan(
                summary = "Open YouTube channel lookup for \"$value\"",
                title = "Open YouTube channel lookup",
                query = youtubeChannelLookupQuery(value),
            )
        }

        val query = invocation.arg("query", "search", "video", "title")
            ?.cleanYouTubeValue()
            ?: goal.text.extractYouTubeSearch()
            ?: return RecipeProposal.Refused("missing-youtube-query")
        return appSearchPlan(
            summary = "Search YouTube for \"$query\"",
            title = "Search YouTube",
            query = query,
        )
    }

    private fun appSearchPlan(
        summary: String,
        title: String,
        query: String,
    ): RecipeProposal.Proposed =
        RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = YOUTUBE_PACKAGE,
                appLabel = "YouTube",
                summary = summary,
                steps = listOf(
                    RecipeStep(
                        id = "search-youtube",
                        title = title,
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.SearchInApp(
                                packageHint = YOUTUBE_PACKAGE,
                                query = query,
                            ),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
}

private fun UserGoal.hasYouTubeEngagementVeto(invocation: RecipeInvocation): Boolean {
    val raw = (listOf(text) + invocation.args.flatMap { (key, value) -> listOf(key, value) })
        .joinToString(" ")
        .lowercase()
    return YOUTUBE_BLOCKED_PATTERNS.any { it.containsMatchIn(raw) }
}

private fun String.extractYouTubeSearch(): String? {
    YOUTUBE_SEARCH_PATTERNS.forEach { pattern ->
        val match = pattern.find(trim()) ?: return@forEach
        return match.groupValues.getOrNull(1)?.cleanYouTubeValue()
    }
    return null
}

private fun String.extractYouTubeChannel(): String? {
    YOUTUBE_CHANNEL_PATTERNS.forEach { pattern ->
        val match = pattern.find(trim()) ?: return@forEach
        return match.groupValues.getOrNull(1)?.cleanYouTubeValue()
    }
    return null
}

private fun String.cleanYouTubeValue(): String? =
    trim()
        .trim('"', '\'', '.', ',', ';', ':')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun youtubeChannelLookupQuery(channel: String): String {
    val normalized = channel.trim()
    return if (normalized.startsWith("@") && normalized.none(Char::isWhitespace)) {
        normalized
    } else {
        "$normalized channel"
    }
}

private val YOUTUBE_BLOCKED_PATTERNS = listOf(
    Regex("""\blike\s+(?:this|the)\s+video\b""", RegexOption.IGNORE_CASE),
    Regex("""\bsubscribe\b""", RegexOption.IGNORE_CASE),
    Regex("""\bcomment\b""", RegexOption.IGNORE_CASE),
)

private val YOUTUBE_SEARCH_PATTERNS = listOf(
    Regex("""\b(?:play|find|search(?:\s+youtube)?(?:\s+for)?)\s+(.+?)(?:\s+(?:on\s+)?youtube)?$""", RegexOption.IGNORE_CASE),
    Regex("""\byoutube\s+(.+)$""", RegexOption.IGNORE_CASE),
)

private val YOUTUBE_CHANNEL_PATTERNS = listOf(
    Regex("""\bopen\s+(.+?)\s+(?:youtube\s+)?channel$""", RegexOption.IGNORE_CASE),
    Regex("""\bopen\s+(.+?)\s+channel\s+on\s+youtube$""", RegexOption.IGNORE_CASE),
)

private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
