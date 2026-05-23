package com.handy.runtime.agent.recipes

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.SettingsTarget
import com.handy.core.action.SourceTrust
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import com.handy.runtime.action.DefaultActionPolicyEngine
import org.junit.Test

class RuntimeRecipePackTest {

    @Test fun `clock recipe proposes AlarmClock intent for seven am`() {
        val proposal = ClockRecipe.propose(
            goal = goal("Set a 7am alarm", "clock_alarm", "time" to "7am"),
            invocation = invocation("clock_alarm", "time" to "7am"),
            grounding = grounding(),
        )

        val action = proposal.singleNativeAction()
        assertThat(action).isEqualTo(AssistantAction.SetAlarm(hour = 7, minute = 0))
    }

    @Test fun `settings recipe proposes dark mode deep link`() {
        val proposal = AndroidSettingsRecipe.propose(
            goal = goal("Turn on dark mode", "android_settings", "setting" to "dark_mode"),
            invocation = invocation("android_settings", "setting" to "dark_mode"),
            grounding = grounding(packageName = "com.android.settings", windowTitle = "Settings"),
        )

        val action = proposal.singleNativeAction()
        assertThat(action).isEqualTo(AssistantAction.OpenSettings(SettingsTarget.DARK_MODE))
    }

    @Test fun `settings recipe high risk targets are denied by policy`() {
        val proposal = AndroidSettingsRecipe.propose(
            goal = goal("Turn on wifi", "android_settings", "setting" to "wifi"),
            invocation = invocation("android_settings", "setting" to "wifi"),
            grounding = grounding(),
        )
        val action = proposal.singleNativeAction()

        val decision = DefaultActionPolicyEngine().decide(
            action = action,
            target = null,
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_USER,
        )

        assertThat(action).isEqualTo(AssistantAction.OpenSettings(SettingsTarget.WIFI))
        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("settings-too-sensitive")
    }

    @Test fun `maps recipe proposes maps search intent`() {
        val proposal = MapsRecipe.propose(
            goal = goal("Search maps for coffee near me", "maps", "query" to "coffee near me"),
            invocation = invocation("maps", "query" to "coffee near me"),
            grounding = grounding(packageName = "com.google.android.apps.maps", windowTitle = "Maps"),
        )

        val action = proposal.singleNativeAction()
        assertThat(action).isEqualTo(AssistantAction.MapsSearch("coffee near me"))
    }

    @Test fun `maps recipe proposes navigation only when explicitly requested`() {
        val proposal = MapsRecipe.propose(
            goal = goal("Navigate to the airport", "maps", "destination" to "the airport", "mode" to "navigation"),
            invocation = invocation("maps", "destination" to "the airport", "mode" to "navigation"),
            grounding = grounding(),
        )

        val action = proposal.singleNativeAction()
        assertThat(action).isEqualTo(AssistantAction.StartNavigation("the airport"))
    }

    @Test fun `runtime recipe pack includes high value app recipes`() {
        assertThat(AndroidRuntimeRecipes.defaultRecipes().map { it.id })
            .containsAtLeast(
                "open_app",
                "install_app",
                "web_search",
                "set_timer",
                "create_calendar_event",
                "gmail_compose",
                "whatsapp_reply",
                "chrome",
                "shopping_search",
                "shopping_find_coupons",
            )
    }

    @Test fun `gmail recipe opens draft and requires strong hold before send`() {
        val plan = GmailRecipe.propose(
            goal = goal(
                "Email John",
                "gmail_compose",
                "to" to "john@example.com",
                "subject" to "ETA",
                "body" to "on my way",
            ),
            invocation = invocation(
                "gmail_compose",
                "to" to "john@example.com",
                "subject" to "ETA",
                "body" to "on my way",
            ),
            grounding = grounding(),
        ).plan()

        val openDraft = plan.steps.first().command as RecipeCommand.NativeAction
        assertThat(openDraft.action).isInstanceOf(AssistantAction.OpenUrl::class.java)
        assertThat((openDraft.action as AssistantAction.OpenUrl).url)
            .isEqualTo("mailto:john%40example.com?subject=ETA&body=on%20my%20way")
        assertThat(plan.steps.last().id).isEqualTo("send")
        assertThat(plan.steps.last().confirmationOverride).isEqualTo(ConfirmationLevel.STRONG_HOLD)
        assertThat(plan.steps.last().sensitive).isTrue()
    }

    @Test fun `whatsapp recipe searches contact fills draft and requires strong hold before send`() {
        val plan = WhatsAppRecipe.propose(
            goal = goal("Reply to John 'on my way'", "whatsapp_reply"),
            invocation = invocation("whatsapp_reply", "recipient" to "John", "message" to "on my way"),
            grounding = grounding(),
        ).plan()

        assertThat(plan.steps.map { it.id }).containsExactly(
            "open-whatsapp",
            "open-search",
            "search-contact",
            "open-contact",
            "type-message",
            "send",
        ).inOrder()
        assertThat(plan.steps.last().confirmationOverride).isEqualTo(ConfirmationLevel.STRONG_HOLD)
        assertThat(plan.steps.last().sensitive).isTrue()
    }

    @Test fun `whatsapp recipe uses wa link when phone is provided`() {
        val plan = WhatsAppRecipe.propose(
            goal = goal("WhatsApp John", "whatsapp_reply"),
            invocation = invocation(
                "whatsapp_reply",
                "recipient" to "John",
                "message" to "on my way",
                "phone" to "+1 555 123 4567",
            ),
            grounding = grounding(),
        ).plan()

        val openChat = plan.steps.first().command as RecipeCommand.NativeAction
        assertThat((openChat.action as AssistantAction.OpenUrl).url)
            .isEqualTo("https://wa.me/15551234567?text=on%20my%20way")
        assertThat(plan.steps.last().confirmationOverride).isEqualTo(ConfirmationLevel.STRONG_HOLD)
    }

    @Test fun `chrome recipe opens url through native intent`() {
        val action = ChromeRecipe.propose(
            goal = goal("Open example.com", "chrome", "url" to "example.com"),
            invocation = invocation("chrome", "url" to "example.com"),
            grounding = grounding(packageName = "com.android.chrome", windowTitle = "Chrome"),
        ).singleNativeAction()

        assertThat(action).isEqualTo(AssistantAction.OpenUrl("https://example.com"))
    }

    @Test fun `chrome recipe refuses summary so fetch page can be used`() {
        val proposal = ChromeRecipe.propose(
            goal = goal("Summarize this page", "chrome", "mode" to "summarize"),
            invocation = invocation("chrome", "mode" to "summarize"),
            grounding = grounding(packageName = "com.android.chrome", windowTitle = "Chrome"),
        )

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("use-fetch-page-for-summary"))
    }

    @Test fun `shopping search proposes scoped product search`() {
        val plan = ShoppingSearchRecipe.propose(
            goal = goal("Search for cotton kurti", "shopping_search", "query" to "cotton kurti"),
            invocation = invocation("shopping_search", "query" to "cotton kurti"),
            grounding = grounding(packageName = "com.meesho.supply", windowTitle = "Meesho"),
        ).plan()

        assertThat(plan.appLabel).isEqualTo("Meesho")
        assertThat(plan.steps.map { it.id }).containsExactly("focus-search", "type-query").inOrder()
        val type = plan.steps.last().command as RecipeCommand.TypeText
        assertThat(type.text).isEqualTo("cotton kurti")
    }

    @Test fun `shopping recipes refuse unsupported apps`() {
        val proposal = ShoppingSearchRecipe.propose(
            goal = goal("Search for cotton kurti", "shopping_search", "query" to "cotton kurti"),
            invocation = invocation("shopping_search", "query" to "cotton kurti"),
            grounding = grounding(packageName = "com.instagram.android", windowTitle = "Instagram"),
        )

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("unsupported-shopping-surface"))
    }

    @Test fun `shopping recipes block purchase and payment requests`() {
        val proposal = ShoppingSearchRecipe.propose(
            goal = goal("Buy now", "shopping_search", "query" to "cotton kurti"),
            invocation = invocation("shopping_search", "query" to "cotton kurti"),
            grounding = grounding(packageName = "com.flipkart.android", windowTitle = "Flipkart"),
        )

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("shopping-purchase-blocked"))
    }

    @Test fun `shopping coupon recipe taps visible offers affordance only`() {
        val plan = ShoppingFindCouponsRecipe.propose(
            goal = goal("Find coupons", "shopping_find_coupons", "label" to "Offers"),
            invocation = invocation("shopping_find_coupons", "label" to "Offers"),
            grounding = grounding(
                packageName = "com.amazon.mShop.android.shopping",
                windowTitle = "Amazon",
            ),
        ).plan()

        assertThat(plan.appLabel).isEqualTo("Amazon")
        assertThat(plan.steps.map { it.id }).containsExactly("open-coupons")
        assertThat(plan.steps.single().command).isInstanceOf(RecipeCommand.Tap::class.java)
    }

    private fun goal(
        text: String,
        recipeId: String,
        vararg args: Pair<String, String>,
    ): UserGoal = UserGoal(
        text = text,
        requestedRecipe = invocation(recipeId, *args),
    )

    private fun invocation(
        recipeId: String,
        vararg args: Pair<String, String>,
    ): RecipeInvocation = RecipeInvocation(recipeId, args.toMap())

    private fun RecipeProposal.singleNativeAction(): AssistantAction {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        val step = (this as RecipeProposal.Proposed).plan.steps.single()
        val command = step.command
        assertThat(command).isInstanceOf(RecipeCommand.NativeAction::class.java)
        return (command as RecipeCommand.NativeAction).action
    }

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun grounding(
        packageName: String = "com.handy.android",
        windowTitle: String? = null,
    ): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "recipe-test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = packageName, appLabel = packageName),
            screenText = ScreenTextSnapshot(
                packageName = packageName,
                windowTitle = windowTitle,
                timestampEpochMs = 1L,
                root = UiNode(role = "root"),
            ),
        )
}
