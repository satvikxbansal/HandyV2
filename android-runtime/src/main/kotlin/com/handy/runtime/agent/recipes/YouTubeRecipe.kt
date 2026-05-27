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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
            return openUrlPlan(
                summary = "Open YouTube channel lookup for \"$value\"",
                title = "Open YouTube channel lookup",
                url = youtubeChannelLookupUrl(value),
            )
        }

        val query = invocation.arg("query", "search", "video", "title")
            ?.cleanYouTubeValue()
            ?: goal.text.extractYouTubeSearch()
            ?: return RecipeProposal.Refused("missing-youtube-query")
        return openUrlPlan(
            summary = "Search YouTube for \"$query\"",
            title = "Search YouTube",
            url = youtubeSearchUrl(query),
        )
    }

    private fun openUrlPlan(
        summary: String,
        title: String,
        url: String,
    ): RecipeProposal.Proposed =
        RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "YouTube",
                summary = summary,
                steps = listOf(
                    RecipeStep(
                        id = "open-youtube-url",
                        title = title,
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.OpenUrl(url),
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

private fun youtubeSearchUrl(query: String): String =
    "https://www.youtube.com/results?search_query=${query.urlEncode()}"

private fun youtubeChannelLookupUrl(channel: String): String {
    val normalized = channel.trim()
    return if (normalized.startsWith("@") && normalized.none(Char::isWhitespace)) {
        "https://www.youtube.com/${normalized.urlEncode()}"
    } else {
        youtubeSearchUrl("$normalized channel")
    }
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())
        .replace("+", "%20")

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
