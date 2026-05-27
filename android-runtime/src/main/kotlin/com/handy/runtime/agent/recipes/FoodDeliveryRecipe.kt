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
import com.handy.runtime.intent.LaunchableAppIndex
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class FoodDeliveryRecipe(
    private val findLaunchableApps: (String) -> List<LaunchableAppIndex.Entry> = { emptyList() },
) : AppRecipe {
    override val id: String = "food_delivery"
    override val displayName: String = "Open food delivery"
    override val description: String =
        "Open Swiggy/Zomato search or order tracking. Never places orders, confirms payment, or checks out."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.OPENS_EXTERNAL_UI

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val mode = when (goal.requestedIntent) {
            "food_track_order" -> FoodMode.Track
            else -> FoodMode.Search
        }
        if (mode == FoodMode.Search && goal.hasFoodOrderVeto(invocation)) {
            return RecipeProposal.Refused("i-can-search-but-cant-order")
        }
        if (goal.hasFoodPaymentVeto(invocation)) {
            return RecipeProposal.Refused("food-payment-blocked")
        }

        val app = invocation.arg("app", "service", "provider")
            ?.foodAppOrNull()
            ?: goal.text.foodAppOrNull()
            ?: FoodApp.Swiggy
        val url = when (mode) {
            FoodMode.Search -> {
                val query = invocation.arg("query", "food", "item")
                    ?.cleanFoodValue()
                    ?: goal.text.extractFoodSearchQuery(app)
                    ?: return RecipeProposal.Refused("missing-food-query")
                if (app.isInstalled()) app.deepSearchUrl(query) else app.webSearchUrl(query)
            }
            FoodMode.Track -> if (app.isInstalled()) app.deepTrackUrl else app.webTrackUrl
        }
        val summary = when (mode) {
            FoodMode.Search -> "Open ${app.label} food search"
            FoodMode.Track -> "Open ${app.label} order tracking"
        }

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = app.label,
                summary = summary,
                steps = listOf(
                    RecipeStep(
                        id = when (mode) {
                            FoodMode.Search -> "open-food-search"
                            FoodMode.Track -> "open-food-tracking"
                        },
                        title = summary,
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.OpenUrl(url),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    private fun FoodApp.isInstalled(): Boolean =
        findLaunchableApps(label).any { entry ->
            entry.packageName.equals(packageName, ignoreCase = true) ||
                entry.label.contains(label, ignoreCase = true)
        }
}

private enum class FoodMode {
    Search,
    Track,
}

private enum class FoodApp(
    val label: String,
    val packageName: String,
    val deepTrackUrl: String,
    val webTrackUrl: String,
) {
    Swiggy(
        label = "Swiggy",
        packageName = "in.swiggy.android",
        deepTrackUrl = "swiggy://orders",
        webTrackUrl = "https://www.swiggy.com/my-account/orders",
    ),
    Zomato(
        label = "Zomato",
        packageName = "com.application.zomato",
        deepTrackUrl = "zomato://orders",
        webTrackUrl = "https://www.zomato.com/orders",
    ),
    ;

    fun deepSearchUrl(query: String): String = when (this) {
        Swiggy -> "swiggy://search?query=${query.urlEncode()}"
        Zomato -> "zomato://search?q=${query.urlEncode()}"
    }

    fun webSearchUrl(query: String): String = when (this) {
        Swiggy -> "https://www.swiggy.com/search?query=${query.urlEncode()}"
        Zomato -> "https://www.zomato.com/search?q=${query.urlEncode()}"
    }
}

private fun UserGoal.hasFoodOrderVeto(invocation: RecipeInvocation): Boolean {
    val raw = (listOf(text) + invocation.args.flatMap { (key, value) -> listOf(key, value) })
        .joinToString(" ")
        .lowercase()
    if (Regex("""\border\b""").containsMatchIn(raw) &&
        !Regex("""\b(?:find|search)\b""").containsMatchIn(raw)
    ) {
        return true
    }
    return FOOD_ORDER_BLOCKED_PATTERNS.any { it.containsMatchIn(raw) }
}

private fun UserGoal.hasFoodPaymentVeto(invocation: RecipeInvocation): Boolean {
    val raw = (listOf(text) + invocation.args.flatMap { (key, value) -> listOf(key, value) })
        .joinToString(" ")
        .lowercase()
    return FOOD_PAYMENT_BLOCKED_PATTERNS.any { it.containsMatchIn(raw) }
}

private fun String.foodAppOrNull(): FoodApp? {
    val normalized = lowercase()
    return when {
        normalized.contains("zomato") -> FoodApp.Zomato
        normalized.contains("swiggy") -> FoodApp.Swiggy
        else -> null
    }
}

private fun String.extractFoodSearchQuery(app: FoodApp): String? {
    FOOD_SEARCH_PATTERNS.forEach { pattern ->
        val match = pattern.find(trim()) ?: return@forEach
        return match.groupValues.getOrNull(1)
            ?.replace(app.label, "", ignoreCase = true)
            ?.replace(Regex("""\bon\s*$""", RegexOption.IGNORE_CASE), "")
            ?.cleanFoodValue()
    }
    return null
}

private fun String.cleanFoodValue(): String? =
    trim()
        .trim('"', '\'', '.', ',', ';', ':')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())
        .replace("+", "%20")

private val FOOD_SEARCH_PATTERNS = listOf(
    Regex("""\b(?:find|search(?:\s+for)?)\s+(.+?)(?:\s+on\s+(?:swiggy|zomato))?$""", RegexOption.IGNORE_CASE),
)

private val FOOD_ORDER_BLOCKED_PATTERNS = listOf(
    Regex("""\bplace\s+(?:the\s+)?order\b""", RegexOption.IGNORE_CASE),
    Regex("""\bconfirm\s+order\b""", RegexOption.IGNORE_CASE),
    Regex("""\bcheckout\b""", RegexOption.IGNORE_CASE),
)

private val FOOD_PAYMENT_BLOCKED_PATTERNS = listOf(
    Regex("""\bconfirm\s+payment\b""", RegexOption.IGNORE_CASE),
    Regex("""\bpay\b""", RegexOption.IGNORE_CASE),
    Regex("""\bpayment\b""", RegexOption.IGNORE_CASE),
    Regex("""\bupi\b""", RegexOption.IGNORE_CASE),
    Regex("""\bcard\b""", RegexOption.IGNORE_CASE),
)
