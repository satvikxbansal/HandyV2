package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.action.SettingsTarget
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot

object AndroidSettingsRecipe : AppRecipe {
    override val id: String = "android_settings"
    override val displayName: String = "Open Android setting"
    override val description: String =
        "Open a safe Android Settings deep-link; sensitive Settings targets are denied by policy."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val target = resolveSettingsTarget(invocation, goal, grounding)
            ?: return RecipeProposal.Refused("unsupported-setting")

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Settings",
                summary = "Open ${target.label} settings",
                steps = listOf(
                    RecipeStep(
                        id = "open-settings",
                        title = "Open ${target.label} settings",
                        command = RecipeCommand.NativeAction(
                            AssistantAction.OpenSettings(target.target),
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    private fun resolveSettingsTarget(
        invocation: RecipeInvocation,
        goal: UserGoal,
        grounding: GroundingSnapshot,
    ): SettingsRecipeTarget? {
        val requested = listOfNotNull(
            invocation.arg("target", "setting", "screen"),
            goal.text,
            grounding.windowTitleForRecipe(),
        ).joinToString(" ").normalizeRecipeText()

        return when {
            requested.contains("dark mode") ||
                requested.contains("dark theme") ||
                requested.contains("night mode") -> SettingsRecipeTarget.DARK_MODE
            requested.contains("notification") -> SettingsRecipeTarget.NOTIFICATIONS
            requested.contains("battery") -> SettingsRecipeTarget.BATTERY_OPTIMIZATION
            requested.contains("ringtone") ||
                requested.contains("sound") -> SettingsRecipeTarget.RINGTONE
            requested.contains("do not disturb") ||
                requested.contains("dnd") ||
                requested.contains("silent mode") -> SettingsRecipeTarget.DND
            requested.contains("brightness") -> SettingsRecipeTarget.BRIGHTNESS
            requested.contains("screen timeout") ||
                requested.contains("sleep") ||
                requested.contains("screen off") -> SettingsRecipeTarget.SCREEN_TIMEOUT
            requested.contains("app info") -> SettingsRecipeTarget.APP_INFO
            requested.contains("apps") || requested.contains("application") -> SettingsRecipeTarget.APPS
            requested.contains("accessibility") -> SettingsRecipeTarget.ACCESSIBILITY
            requested.contains("biometric") ||
                requested.contains("fingerprint") ||
                requested.contains("face unlock") -> SettingsRecipeTarget.BIOMETRIC
            requested.contains("security") ||
                requested.contains("screen lock") ||
                requested.contains("lock screen") ||
                requested.contains("password") ||
                requested.contains("pin") -> SettingsRecipeTarget.SECURITY
            requested.contains("wifi") ||
                requested.contains("wi fi") ||
                requested.contains("network") ||
                requested.contains("mobile data") ||
                requested.contains("cellular") ||
                requested.contains("hotspot") ||
                requested.contains("airplane") -> SettingsRecipeTarget.WIFI
            requested.contains("bluetooth") -> SettingsRecipeTarget.BLUETOOTH
            else -> null
        }
    }

    private enum class SettingsRecipeTarget(
        val target: SettingsTarget,
        val label: String,
    ) {
        APP_INFO(SettingsTarget.APP_INFO, "app info"),
        NOTIFICATIONS(SettingsTarget.NOTIFICATIONS, "notifications"),
        BATTERY_OPTIMIZATION(SettingsTarget.BATTERY_OPTIMIZATION, "battery optimization"),
        DARK_MODE(SettingsTarget.DARK_MODE, "dark theme"),
        APPS(SettingsTarget.APPS, "apps"),
        ACCESSIBILITY(SettingsTarget.ACCESSIBILITY, "accessibility"),
        BIOMETRIC(SettingsTarget.BIOMETRIC, "biometric"),
        SECURITY(SettingsTarget.SECURITY, "security"),
        WIFI(SettingsTarget.WIFI, "network"),
        BLUETOOTH(SettingsTarget.BLUETOOTH, "Bluetooth"),
        RINGTONE(SettingsTarget.RINGTONE, "ringtone"),
        DND(SettingsTarget.DND, "do not disturb"),
        BRIGHTNESS(SettingsTarget.BRIGHTNESS, "brightness"),
        SCREEN_TIMEOUT(SettingsTarget.SCREEN_TIMEOUT, "screen timeout"),
    }
}
