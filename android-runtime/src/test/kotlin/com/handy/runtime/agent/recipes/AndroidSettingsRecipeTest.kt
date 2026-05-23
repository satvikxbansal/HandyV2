package com.handy.runtime.agent.recipes

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.AssistantAction
import com.handy.core.action.SettingsTarget
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import org.junit.Test

class AndroidSettingsRecipeTest {

    @Test fun `ringtone and sound keywords resolve to ringtone settings`() {
        assertThat(resolve("open ringtone settings")).isEqualTo(SettingsTarget.RINGTONE)
        assertThat(resolve("open sound settings")).isEqualTo(SettingsTarget.RINGTONE)
    }

    @Test fun `do not disturb keywords resolve to dnd settings`() {
        assertThat(resolve("open do not disturb settings")).isEqualTo(SettingsTarget.DND)
        assertThat(resolve("open dnd settings")).isEqualTo(SettingsTarget.DND)
        assertThat(resolve("open silent mode settings")).isEqualTo(SettingsTarget.DND)
    }

    @Test fun `brightness keyword resolves to brightness settings`() {
        assertThat(resolve("open brightness settings")).isEqualTo(SettingsTarget.BRIGHTNESS)
    }

    @Test fun `screen timeout keywords resolve to screen timeout settings`() {
        assertThat(resolve("open screen timeout settings")).isEqualTo(SettingsTarget.SCREEN_TIMEOUT)
        assertThat(resolve("change sleep settings")).isEqualTo(SettingsTarget.SCREEN_TIMEOUT)
        assertThat(resolve("open screen off settings")).isEqualTo(SettingsTarget.SCREEN_TIMEOUT)
    }

    private fun resolve(text: String): SettingsTarget {
        val proposal = AndroidSettingsRecipe.propose(
            goal = UserGoal(
                text = text,
                requestedRecipe = invocation("setting" to text),
            ),
            invocation = invocation("setting" to text),
            grounding = grounding(),
        )
        assertThat(proposal).isInstanceOf(RecipeProposal.Proposed::class.java)
        val step = (proposal as RecipeProposal.Proposed).plan.steps.single()
        val command = step.command
        assertThat(command).isInstanceOf(RecipeCommand.NativeAction::class.java)
        val action = (command as RecipeCommand.NativeAction).action
        assertThat(action).isInstanceOf(AssistantAction.OpenSettings::class.java)
        return (action as AssistantAction.OpenSettings).target
    }

    private fun invocation(vararg args: Pair<String, String>): RecipeInvocation =
        RecipeInvocation("android_settings", args.toMap())

    private fun grounding(): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "android-settings-recipe-test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = "com.android.settings", appLabel = "Settings"),
            screenText = ScreenTextSnapshot(
                packageName = "com.android.settings",
                windowTitle = "Settings",
                timestampEpochMs = 1L,
                root = UiNode(role = "root"),
            ),
        )
}
