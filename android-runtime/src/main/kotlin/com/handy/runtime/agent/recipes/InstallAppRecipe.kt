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

object InstallAppRecipe : AppRecipe {
    override val id: String = ID
    override val displayName: String = "Open Play Store"
    override val description: String =
        "Open a Play Store listing or app search; the user taps Install in Play Store."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val packageHint = invocation.arg("packageHint", "package", "packageName", "id")
            ?.cleanInstallTarget()
        val searchQuery = invocation.arg("searchQuery", "query", "name", "app", "appName")
            ?.cleanInstallTarget()
        val target = packageHint ?: searchQuery
            ?: return RecipeProposal.Refused("missing-app")

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Play Store",
                summary = "Show $target on Play Store",
                steps = listOf(
                    RecipeStep(
                        id = "open-play-store",
                        title = "Show $target on Play Store",
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.InstallApp(
                                packageHint = packageHint,
                                searchQuery = searchQuery,
                            ),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    private fun String.cleanInstallTarget(): String? =
        trim()
            .trim('"', '\'')
            .trim()
            .takeIf { it.isNotBlank() }

    const val ID: String = "install_app"
}
