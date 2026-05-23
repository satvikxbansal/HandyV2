package com.handy.core.agent

class RecipeIntentRouter(
    recipes: List<AppRecipe>,
) {
    private val recipesById: Map<String, AppRecipe> =
        recipes.associateBy { it.id.lowercase() }

    fun routeOrNull(goal: UserGoal): Pair<RecipeIntent, AppRecipe>? {
        val intent = RecipeIntent.fromCanonical(goal.requestedIntent)
            ?: RecipeIntent.fromCanonical(goal.requestedRecipe?.recipeId)
            ?: return null
        val recipeId = recipeIdFor(intent)
        val recipe = recipesById[recipeId.lowercase()] ?: return null
        return intent to recipe
    }

    companion object {
        val INTENT_TO_RECIPE_ID: Map<RecipeIntent, String> = mapOf(
            RecipeIntent.OPEN_APP to "open_app",
            RecipeIntent.SET_ALARM to "clock_alarm",
            RecipeIntent.SET_TIMER to "set_timer",
            RecipeIntent.WEB_SEARCH to WEB_SEARCH_RECIPE_ID,
            RecipeIntent.INSTALL_APP to "install_app",
            RecipeIntent.OPEN_SETTING to "android_settings",
            RecipeIntent.CREATE_CALENDAR_EVENT to "create_calendar_event",
            RecipeIntent.DRAFT_GMAIL to "gmail_compose",
            RecipeIntent.DRAFT_WHATSAPP to "whatsapp_reply",
            RecipeIntent.OPEN_CHROME_URL to "chrome",
            RecipeIntent.SHOPPING_SEARCH to "shopping_search",
            RecipeIntent.SHOPPING_FIND_COUPONS to "shopping_find_coupons",
            RecipeIntent.BOOK_RIDE to "book_ride",
        )

        fun recipeIdFor(intent: RecipeIntent): String =
            INTENT_TO_RECIPE_ID.getValue(intent)
    }
}

private const val WEB_SEARCH_RECIPE_ID = "web_search"
