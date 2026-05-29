package com.handy.core.agent

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.handy.core.action.ScrollDirection
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import org.junit.jupiter.api.Test

class RecipeRegistrySmokeTest {

    @Test fun `canonical utterances route to expected recipe ids`() {
        val registry = RecipeRegistry(canonicalSmokeRecipes())

        canonicalRecipeCases.forEach { case ->
            val proposal = registry.propose(case.goal(), case.grounding())

            assertWithMessage(case.utterance)
                .that(proposal)
                .isInstanceOf(RecipeProposal.Proposed::class.java)
            assertWithMessage(case.utterance)
                .that((proposal as RecipeProposal.Proposed).plan.recipeId)
                .isEqualTo(case.expectedRecipeId)
        }
    }

    @Test fun `summarize screen does not route through recipe registry`() {
        val registry = RecipeRegistry(canonicalSmokeRecipes())

        val proposal = registry.propose(
            UserGoal(text = "summarize this screen"),
            foregroundGrounding(
                packageName = "com.google.android.apps.nexuslauncher",
                appLabel = "Launcher",
            ),
        )

        assertThat(proposal).isInstanceOf(RecipeProposal.Refused::class.java)
    }
}

internal data class CanonicalRecipeCase(
    val utterance: String,
    val foregroundPackage: String,
    val foregroundLabel: String,
    val intent: RecipeIntent,
    val expectedRecipeId: String,
    val args: Map<String, String> = emptyMap(),
) {
    fun goal(recipeId: String = intent.canonical): UserGoal =
        UserGoal(
            text = utterance,
            requestedIntent = intent.canonical,
            requestedRecipe = RecipeInvocation(recipeId = recipeId, args = args),
        )

    fun grounding(): GroundingSnapshot =
        foregroundGrounding(
            packageName = foregroundPackage,
            appLabel = foregroundLabel,
        )
}

internal val canonicalRecipeCases: List<CanonicalRecipeCase> = listOf(
    CanonicalRecipeCase(
        utterance = "open spotify",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.OPEN_APP,
        expectedRecipeId = "open_app",
        args = mapOf("name" to "spotify"),
    ),
    CanonicalRecipeCase(
        utterance = "play jazz on spotify",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.APP_SEARCH,
        expectedRecipeId = "app_search",
        args = mapOf("app" to "spotify", "query" to "jazz"),
    ),
    CanonicalRecipeCase(
        utterance = "install spotify",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.INSTALL_APP,
        expectedRecipeId = "install_app",
        args = mapOf("searchQuery" to "spotify"),
    ),
    CanonicalRecipeCase(
        utterance = "set 7am alarm",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.SET_ALARM,
        expectedRecipeId = "clock_alarm",
        args = mapOf("time" to "7am"),
    ),
    CanonicalRecipeCase(
        utterance = "set 10 minute timer",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.SET_TIMER,
        expectedRecipeId = "set_timer",
        args = mapOf("seconds" to "600"),
    ),
    CanonicalRecipeCase(
        utterance = "search the web for cats",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.WEB_SEARCH,
        expectedRecipeId = "web_search",
        args = mapOf("query" to "cats"),
    ),
    CanonicalRecipeCase(
        utterance = "search chrome for cats",
        foregroundPackage = "com.android.chrome",
        foregroundLabel = "Chrome",
        intent = RecipeIntent.OPEN_CHROME_URL,
        expectedRecipeId = "chrome",
        args = mapOf("query" to "cats"),
    ),
    CanonicalRecipeCase(
        utterance = "turn on dnd",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.OPEN_SETTING,
        expectedRecipeId = "android_settings",
        args = mapOf("setting" to "dnd"),
    ),
    CanonicalRecipeCase(
        utterance = "ringtone settings",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.OPEN_SETTING,
        expectedRecipeId = "android_settings",
        args = mapOf("setting" to "ringtone"),
    ),
    CanonicalRecipeCase(
        utterance = "set brightness",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.OPEN_SETTING,
        expectedRecipeId = "android_settings",
        args = mapOf("setting" to "brightness"),
    ),
    CanonicalRecipeCase(
        utterance = "screen timeout",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.OPEN_SETTING,
        expectedRecipeId = "android_settings",
        args = mapOf("setting" to "screen_timeout"),
    ),
    CanonicalRecipeCase(
        utterance = "reply to john on whatsapp",
        foregroundPackage = "com.whatsapp",
        foregroundLabel = "WhatsApp",
        intent = RecipeIntent.DRAFT_WHATSAPP,
        expectedRecipeId = "whatsapp_reply",
        args = mapOf("recipient" to "john", "message" to "ok"),
    ),
    CanonicalRecipeCase(
        utterance = "draft email to mom",
        foregroundPackage = "com.google.android.gm",
        foregroundLabel = "Gmail",
        intent = RecipeIntent.DRAFT_GMAIL,
        expectedRecipeId = "gmail_compose",
        args = mapOf("to" to "mom", "body" to "hi mom"),
    ),
    CanonicalRecipeCase(
        utterance = "schedule lunch tomorrow",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.CREATE_CALENDAR_EVENT,
        expectedRecipeId = "create_calendar_event",
        args = mapOf("title" to "lunch", "start" to "tomorrow"),
    ),
    CanonicalRecipeCase(
        utterance = "book uber to airport",
        foregroundPackage = "com.ubercab",
        foregroundLabel = "Uber",
        intent = RecipeIntent.BOOK_RIDE,
        expectedRecipeId = "uber_ride",
        args = mapOf("app" to "uber", "destination" to "airport"),
    ),
    CanonicalRecipeCase(
        utterance = "book ola to cubbon park",
        foregroundPackage = "com.olacabs.customer",
        foregroundLabel = "Ola",
        intent = RecipeIntent.BOOK_RIDE,
        expectedRecipeId = "ola_ride",
        args = mapOf("app" to "ola", "destination" to "cubbon park"),
    ),
    CanonicalRecipeCase(
        utterance = "find cheap saree on meesho",
        foregroundPackage = "com.meesho.supply",
        foregroundLabel = "Meesho",
        intent = RecipeIntent.SHOPPING_SEARCH,
        expectedRecipeId = "shopping_search",
        args = mapOf("query" to "cheap saree"),
    ),
    CanonicalRecipeCase(
        utterance = "open the latest podcast of raj shamani on youtube",
        foregroundPackage = "com.google.android.apps.nexuslauncher",
        foregroundLabel = "Launcher",
        intent = RecipeIntent.YOUTUBE_SEARCH,
        expectedRecipeId = "youtube",
        args = mapOf("query" to "raj shamani latest podcast"),
    ),
)

internal fun canonicalSmokeRecipes(): List<AppRecipe> =
    canonicalRecipeCases
        .groupBy(CanonicalRecipeCase::expectedRecipeId)
        .map { (recipeId, cases) ->
            SmokeRecipe(
                id = recipeId,
                utterances = cases.map(CanonicalRecipeCase::utterance).toSet(),
            )
        }

internal fun foregroundGrounding(
    packageName: String,
    appLabel: String,
): GroundingSnapshot =
    GroundingSnapshot(
        requestId = "recipe-registry-smoke-test",
        source = TurnSource.TEST,
        toolContext = ToolContext(packageName = packageName, appLabel = appLabel),
        screenText = ScreenTextSnapshot(
            packageName = packageName,
            windowTitle = appLabel,
            timestampEpochMs = 0L,
            root = UiNode(role = "root", text = appLabel),
        ),
        panelSnapshot = null,
    )

private class SmokeRecipe(
    override val id: String,
    private val utterances: Set<String>,
) : AppRecipe {
    override val displayName: String = id
    override val description: String = "Smoke canary for $id"

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        if (goal.text !in utterances) {
            return RecipeProposal.Refused("utterance-not-in-lane:$id")
        }
        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = grounding.screenText?.packageName ?: grounding.toolContext.packageName,
                appLabel = grounding.toolContext.appLabel,
                summary = id,
                steps = listOf(
                    RecipeStep(
                        id = "canary",
                        title = "Canary",
                        command = RecipeCommand.Scroll(ScrollDirection.DOWN),
                    ),
                ),
            ).validate(),
        )
    }
}
