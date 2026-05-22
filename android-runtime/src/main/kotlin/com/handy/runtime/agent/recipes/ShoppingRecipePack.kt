package com.handy.runtime.agent.recipes

import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.RecipeTarget
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.UiNode

object ShoppingRecipePack {
    fun defaultRecipes(): List<AppRecipe> = listOf(
        ShoppingSearchRecipe,
        ShoppingFindCouponsRecipe,
    )
}

object ShoppingSearchRecipe : AppRecipe {
    override val id: String = "shopping_search"
    override val displayName: String = "Search shopping app"
    override val description: String =
        "Search for products inside Meesho, Amazon, or Flipkart when one of those surfaces is visible."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val surface = grounding.shoppingSurface()
            ?: return RecipeProposal.Refused("unsupported-shopping-surface")
        if (goal.hasShoppingHardVeto(invocation)) {
            return RecipeProposal.Refused("shopping-purchase-blocked")
        }
        val query = invocation.arg("query", "text", "product", "item")
            ?.cleanShoppingValue()
            ?: goal.text.extractShoppingQuery()
            ?: return RecipeProposal.Refused("missing-shopping-query")

        val steps = mutableListOf(
            RecipeStep(
                id = "focus-search",
                title = "Focus ${surface.label} search",
                command = RecipeCommand.Tap(invocation.searchFieldTarget()),
            ),
            RecipeStep(
                id = "type-query",
                title = "Search for $query",
                command = RecipeCommand.TypeText(
                    target = invocation.searchFieldTarget(),
                    text = query,
                ),
            ),
        )

        invocation.submitTarget()?.let { submit ->
            steps += RecipeStep(
                id = "submit-search",
                title = "Submit shopping search",
                command = RecipeCommand.Tap(submit),
            )
        }

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = grounding.packageNameForRecipe(),
                appLabel = surface.label,
                summary = "Search ${surface.label} for $query",
                steps = steps,
            ).validate(),
        )
    }
}

object ShoppingFindCouponsRecipe : AppRecipe {
    override val id: String = "shopping_find_coupons"
    override val displayName: String = "Find shopping coupons"
    override val description: String =
        "Open a visible coupons, offers, or discounts affordance on Meesho, Amazon, or Flipkart."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val surface = grounding.shoppingSurface()
            ?: return RecipeProposal.Refused("unsupported-shopping-surface")
        if (goal.hasShoppingHardVeto(invocation)) {
            return RecipeProposal.Refused("shopping-purchase-blocked")
        }
        val target = invocation.couponTarget()

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = grounding.packageNameForRecipe(),
                appLabel = surface.label,
                summary = "Open coupons or offers on ${surface.label}",
                steps = listOf(
                    RecipeStep(
                        id = "open-coupons",
                        title = "Open ${target.displayLabel()}",
                        command = RecipeCommand.Tap(target),
                    ),
                ),
            ).validate(),
        )
    }
}

private data class ShoppingSurface(val label: String)

private fun GroundingSnapshot.shoppingSurface(): ShoppingSurface? {
    val haystack = listOfNotNull(
        packageNameForRecipe(),
        toolContext.packageName,
        toolContext.appLabel,
        toolContext.umbrellaSiteLabel,
        windowTitleForRecipe(),
    ) + screenText?.root.shoppingLabels()

    val normalized = haystack.joinToString(" ").normalizeRecipeText()
    return when {
        normalized.contains("meesho") -> ShoppingSurface("Meesho")
        normalized.contains("flipkart") -> ShoppingSurface("Flipkart")
        normalized.contains("amazon mshop") && normalized.contains("shopping") ->
            ShoppingSurface("Amazon")
        normalized.contains("amazon") &&
            listOf("amazon in", "amazon com", "amazon shopping").any(normalized::contains) ->
            ShoppingSurface("Amazon")
        else -> null
    }
}

private fun UiNode?.shoppingLabels(limit: Int = 24): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(limit)
    fun walk(node: UiNode) {
        if (out.size >= limit) return
        listOfNotNull(
            node.text,
            node.contentDescription,
            node.viewIdResourceName,
        ).forEach { value ->
            if (out.size < limit && value.isNotBlank()) out += value
        }
        node.children.forEach(::walk)
    }
    walk(this)
    return out
}

private fun UserGoal.hasShoppingHardVeto(invocation: RecipeInvocation): Boolean {
    val raw = (
        listOf(text) +
            invocation.args.flatMap { (key, value) -> listOf(key, value) }
        ).joinToString(" ")
        .lowercase()
    return SHOPPING_HARD_VETO_TERMS.any { raw.contains(it) }
}

private fun RecipeInvocation.searchFieldTarget(): RecipeTarget.Node =
    arg("searchMarkId", "markId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("searchViewId", "viewId")?.let { RecipeTarget.Node(viewId = it, role = "textfield") }
        ?: arg("searchDesc", "desc")?.let { RecipeTarget.Node(desc = it, role = "textfield") }
        ?: arg("field", "fieldLabel", "label")?.let { RecipeTarget.Node(text = it, role = "textfield") }
        ?: RecipeTarget.Node(role = "textfield")

private fun RecipeInvocation.submitTarget(): RecipeTarget.Node? =
    arg("submitMarkId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("submitViewId")?.let { RecipeTarget.Node(viewId = it, role = "button") }
        ?: arg("submitDesc")?.let { RecipeTarget.Node(desc = it, role = "button") }
        ?: arg("submit", "submitLabel", "button", "buttonLabel")
            ?.let { RecipeTarget.Node(text = it, role = "button") }

private fun RecipeInvocation.couponTarget(): RecipeTarget.Node =
    arg("couponMarkId", "markId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("couponViewId", "viewId")?.let { RecipeTarget.Node(viewId = it) }
        ?: arg("couponDesc", "desc")?.let { RecipeTarget.Node(desc = it) }
        ?: arg("target", "label", "text", "offerLabel", "couponLabel")
            ?.let { RecipeTarget.Node(text = it) }
        ?: RecipeTarget.Node(text = "Coupons")

private fun String.cleanShoppingValue(): String? =
    trim()
        .trim('"', '\'')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun String.extractShoppingQuery(): String? {
    val stripped = SHOPPING_SEARCH_PREFIXES.fold(trim()) { acc, regex ->
        regex.replace(acc, "").trim()
    }
    return stripped.cleanShoppingValue()
}

private val SHOPPING_SEARCH_PREFIXES = listOf(
    Regex("""^(please\s+)?(search|find|look\s+up)\s+(for\s+)?""", RegexOption.IGNORE_CASE),
    Regex("""^(please\s+)?(shopping\s+)?search\s+""", RegexOption.IGNORE_CASE),
)

private val SHOPPING_HARD_VETO_TERMS = listOf(
    "add to cart",
    "buy now",
    "checkout",
    "place order",
    "purchase",
    "pay ",
    "payment",
    "saved card",
    "card details",
    "upi",
    "address",
    "apply coupon",
    "खरीद",
    "पेमेंट",
    "भुगतान",
    "कार्ट",
    "ऑर्डर",
)
