package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import com.handy.runtime.intent.LaunchableAppIndex

class OpenAppRecipe(
    private val findLaunchableApps: (String) -> List<LaunchableAppIndex.Entry>,
) : AppRecipe {
    override val id: String = ID
    override val displayName: String = "Open app"
    override val description: String =
        "Open an installed launcher app through LaunchableAppIndex before attempting any UI fallback."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val name = requestedAppName(invocation, goal)
            ?: return RecipeProposal.Refused("missing-app-name")
        val matches = findLaunchableApps(name)
        if (matches.isEmpty()) {
            return RecipeProposal.Refused("app-not-found:$name")
        }
        if (matches.size > 1) {
            return RecipeProposal.Refused(
                "ambiguous-app:${matches.take(3).joinToString { it.appLabel }}",
            )
        }

        val match = matches.single()
        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = match.packageName,
                appLabel = match.appLabel,
                summary = "Open ${match.appLabel}",
                steps = listOf(
                    RecipeStep(
                        id = "open-app",
                        title = "Open ${match.appLabel}",
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.OpenApp(packageHint = match.packageName),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    private fun requestedAppName(invocation: RecipeInvocation, goal: UserGoal): String? =
        invocation.arg("name")
            ?.cleanAppName()
            ?: OPEN_APP_PATTERN.find(goal.text)
                ?.groupValues
                ?.getOrNull(1)
                ?.cleanAppName()

    private fun String.cleanAppName(): String? =
        trim()
            .trim('"', '\'')
            .takeIf { it.isNotBlank() }

    companion object {
        const val ID: String = "open_app"

        private val OPEN_APP_PATTERN = Regex(
            pattern = """\bopen\s+(.+?)(?:\s+app)?\.?\s*$""",
            options = setOf(RegexOption.IGNORE_CASE),
        )
    }
}
