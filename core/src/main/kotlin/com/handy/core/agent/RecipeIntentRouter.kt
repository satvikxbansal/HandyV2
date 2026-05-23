package com.handy.core.agent

import com.handy.core.screen.GroundingSnapshot

class RecipeIntentRouter(
    recipes: List<AppRecipe>,
) {
    private val recipesById: Map<String, AppRecipe> =
        recipes.associateBy { it.id.lowercase() }

    fun routeOrNull(
        goal: UserGoal,
        grounding: GroundingSnapshot? = null,
    ): Pair<RecipeIntent, AppRecipe>? {
        val intent = RecipeIntent.fromCanonical(goal.requestedIntent)
            ?: RecipeIntent.fromCanonical(goal.requestedRecipe?.recipeId)
            ?: return null
        val recipeId = recipeIdFor(intent, goal, grounding)
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
            RecipeIntent.BOOK_RIDE to UBER_RIDE_RECIPE_ID,
        )

        fun recipeIdFor(intent: RecipeIntent): String =
            INTENT_TO_RECIPE_ID.getValue(intent)

        private fun recipeIdFor(
            intent: RecipeIntent,
            goal: UserGoal,
            grounding: GroundingSnapshot?,
        ): String =
            if (intent == RecipeIntent.BOOK_RIDE) {
                rideRecipeIdFor(goal, grounding)
            } else {
                recipeIdFor(intent)
            }

        private fun rideRecipeIdFor(
            goal: UserGoal,
            grounding: GroundingSnapshot?,
        ): String {
            val requested = listOfNotNull(
                goal.requestedRecipe?.arg("app", "service", "provider", "rideApp"),
                goal.text,
            ).joinToString(" ").lowercase()
            val foregroundPackage = grounding.foregroundPackage()
            return when {
                requested.contains("rapido") ||
                    foregroundPackage.equals(RAPIDO_PACKAGE, ignoreCase = true) -> RAPIDO_RIDE_RECIPE_ID
                requested.contains("ola") ||
                    foregroundPackage.equals(OLA_PACKAGE, ignoreCase = true) -> OLA_RIDE_RECIPE_ID
                requested.contains("uber") ||
                    foregroundPackage.equals(UBER_PACKAGE, ignoreCase = true) -> UBER_RIDE_RECIPE_ID
                else -> UBER_RIDE_RECIPE_ID
            }
        }
    }
}

private const val WEB_SEARCH_RECIPE_ID = "web_search"
private const val UBER_RIDE_RECIPE_ID = "uber_ride"
private const val OLA_RIDE_RECIPE_ID = "ola_ride"
private const val RAPIDO_RIDE_RECIPE_ID = "rapido_ride"
private const val UBER_PACKAGE = "com.ubercab"
private const val OLA_PACKAGE = "com.olacabs.customer"
private const val RAPIDO_PACKAGE = "com.rapido.passenger"

private fun GroundingSnapshot?.foregroundPackage(): String? =
    this?.screenText?.packageName
        ?: this?.toolContext?.packageName?.takeIf { it.isNotBlank() }
