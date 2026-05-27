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
import java.util.Locale

object AndroidRuntimeRecipes {
    fun defaultRecipes(launchableApps: LaunchableAppIndex): List<AppRecipe> =
        defaultRecipes(launchableApps::find)

    fun defaultRecipes(
        findLaunchableApps: (String) -> List<LaunchableAppIndex.Entry> = { emptyList() },
        findContacts: (String) -> ContactLookupResult = { ContactLookupResult.Matches(emptyList()) },
    ): List<AppRecipe> = listOf(
        OpenAppRecipe(findLaunchableApps),
        InstallAppRecipe,
        ClockRecipe,
        TimerRecipe,
        CalendarEventRecipe,
        CalendarEventRecipeV2,
        WebSearchRecipe,
        AndroidSettingsRecipe,
        MapsRecipe,
        GmailRecipe,
        WhatsAppRecipe,
        ChromeRecipe,
        YouTubeRecipe,
        NotesRecipe,
        ContactsRecipe(findContacts),
        FilesRecipe,
        PhotosRecipe,
        CalculatorRecipe,
        FoodDeliveryRecipe(findLaunchableApps),
    ) + ShoppingRecipePack.defaultRecipes() + RideHailingRecipePack.defaultRecipes()
}

object ClockRecipe : AppRecipe {
    override val id: String = "clock_alarm"
    override val displayName: String = "Set an alarm"
    override val description: String =
        "Set a Clock alarm via the Android AlarmClock intent before attempting any UI fallback."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.OPENS_EXTERNAL_UI

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val alarmTime = parseAlarmTime(invocation, goal)
            ?: return RecipeProposal.Refused("missing-alarm-time")
        val label = invocation.arg("label", "message", "name")

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Clock",
                summary = "Set alarm for ${alarmTime.displayLabel()}",
                steps = listOf(
                    RecipeStep(
                        id = "set-alarm-intent",
                        title = "Set alarm for ${alarmTime.displayLabel()}",
                        command = RecipeCommand.NativeAction(
                            AssistantAction.SetAlarm(
                                hour = alarmTime.hour,
                                minute = alarmTime.minute,
                                label = label,
                            ),
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    private fun parseAlarmTime(invocation: RecipeInvocation, goal: UserGoal): ClockTime? {
        val hour = invocation.arg("hour")?.toIntOrNull()
        val minute = invocation.arg("minute", "minutes")?.toIntOrNull() ?: 0
        val period = invocation.arg("period", "meridiem", "ampm")
        if (hour != null) {
            return ClockTime.from(hour, minute, period)
        }
        return parseClockTime(invocation.arg("time", "at").orEmpty())
            ?: parseClockTime(goal.text)
    }
}

internal data class ClockTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23) { "hour must be 0-23" }
        require(minute in 0..59) { "minute must be 0-59" }
    }

    fun displayLabel(): String {
        val suffix = if (hour < 12) "AM" else "PM"
        val displayHour = when (val value = hour % 12) {
            0 -> 12
            else -> value
        }
        return "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
    }

    companion object {
        fun from(hour: Int, minute: Int, period: String? = null): ClockTime? {
            if (minute !in 0..59) return null
            val normalizedPeriod = period.normalizeRecipeText()
            val resolvedHour = when {
                normalizedPeriod in setOf("am", "a m") && hour in 1..12 -> hour % 12
                normalizedPeriod in setOf("pm", "p m") && hour in 1..12 -> (hour % 12) + 12
                period.isNullOrBlank() && hour in 0..23 -> hour
                else -> return null
            }
            return ClockTime(hour = resolvedHour, minute = minute)
        }
    }
}

internal fun parseClockTime(text: String): ClockTime? {
    if (text.isBlank()) return null
    TWELVE_HOUR_TIME.find(text)?.let { match ->
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        val period = match.groupValues[3]
        return ClockTime.from(hour, minute, period)
    }
    TWENTY_FOUR_HOUR_TIME.find(text)?.let { match ->
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return ClockTime.from(hour, minute)
    }
    return null
}

internal fun String?.normalizeRecipeText(): String =
    this
        ?.lowercase(Locale.US)
        ?.replace(Regex("""[^a-z0-9]+"""), " ")
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        .orEmpty()

internal fun GroundingSnapshot.packageNameForRecipe(): String =
    screenText?.packageName ?: toolContext.packageName

internal fun GroundingSnapshot.windowTitleForRecipe(): String =
    screenText?.windowTitle.orEmpty()

private val TWELVE_HOUR_TIME = Regex(
    pattern = """\b(1[0-2]|0?[1-9])(?:[:.]([0-5]\d))?\s*(a\.?m\.?|p\.?m\.?)\b""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val TWENTY_FOUR_HOUR_TIME = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""")
