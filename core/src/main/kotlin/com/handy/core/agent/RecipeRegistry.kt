package com.handy.core.agent

import com.handy.core.action.ScrollDirection
import com.handy.core.screen.GroundingSnapshot

class RecipeRegistry(
    private val recipes: List<AppRecipe> = defaultRecipes(),
) {
    private val intentRouter = RecipeIntentRouter(recipes)

    fun propose(
        goal: UserGoal,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        intentRouter.routeOrNull(goal, grounding)?.let { (_, recipe) ->
            val invocation = goal.requestedRecipe
                ?.copy(recipeId = recipe.id)
                ?: RecipeInvocation(recipeId = recipe.id, args = emptyMap())
            return recipe.propose(goal, invocation, grounding)
                .withSideEffectNudges(recipe.sideEffectClassification)
        }

        val invocation = goal.requestedRecipe
            ?: return RecipeProposal.Refused("no-recipe-directive")
        val recipe = recipes.firstOrNull {
            it.id.equals(invocation.recipeId, ignoreCase = true)
        } ?: return RecipeProposal.Refused("unknown-recipe:${invocation.recipeId}")
        return recipe.propose(goal, invocation, grounding)
            .withSideEffectNudges(recipe.sideEffectClassification)
    }

    companion object {
        fun defaultRecipes(): List<AppRecipe> = listOf(
            TapVisibleRecipe,
            TypeVisibleRecipe,
            SearchVisibleRecipe,
            ScrollRecipe,
        )
    }
}

private fun RecipeProposal.withSideEffectNudges(
    classification: SideEffectClassification,
): RecipeProposal {
    if (this !is RecipeProposal.Proposed || !classification.requiresStrongHoldNudge()) return this
    val finalStep = plan.steps.lastOrNull() ?: return this
    val nudgedFinalStep = finalStep.copy(
        sensitive = true,
        confirmationOverride = maxOf(
            finalStep.confirmationOverride ?: com.handy.core.action.ConfirmationLevel.NONE,
            com.handy.core.action.ConfirmationLevel.STRONG_HOLD,
        ),
    )
    return RecipeProposal.Proposed(
        plan.copy(steps = plan.steps.dropLast(1) + nudgedFinalStep).validate(),
    )
}

private object TapVisibleRecipe : AppRecipe {
    override val id: String = "tap_visible"
    override val displayName: String = "Tap visible control"
    override val description: String = "Tap one visible non-sensitive control selected by label, mark id, view id, or description."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val target = invocation.toTarget(
            labelKeys = listOf("label", "target", "targetlabel", "text"),
        ) ?: return RecipeProposal.Refused("missing-target")
        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = grounding.toolContext.packageName,
                appLabel = grounding.toolContext.appLabel,
                summary = "Tap ${target.displayLabel()}",
                steps = listOf(
                    RecipeStep(
                        id = "tap",
                        title = "Tap ${target.displayLabel()}",
                        command = RecipeCommand.Tap(target),
                    ),
                ),
            ).validate(),
        )
    }
}

private object TypeVisibleRecipe : AppRecipe {
    override val id: String = "type_visible"
    override val displayName: String = "Type into visible field"
    override val description: String = "Type harmless text into one visible editable field."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val text = invocation.arg("value", "input", "text")
            ?: return RecipeProposal.Refused("missing-text")
        val target = invocation.toTarget(
            labelKeys = listOf("field", "fieldlabel", "target", "targetlabel", "label"),
        ) ?: return RecipeProposal.Refused("missing-field")
        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = grounding.toolContext.packageName,
                appLabel = grounding.toolContext.appLabel,
                summary = "Type into ${target.displayLabel()}",
                steps = listOf(
                    RecipeStep(
                        id = "type",
                        title = "Type into ${target.displayLabel()}",
                        command = RecipeCommand.TypeText(target = target, text = text),
                    ),
                ),
            ).validate(),
        )
    }
}

private object SearchVisibleRecipe : AppRecipe {
    override val id: String = "search_visible"
    override val displayName: String = "Search visible app"
    override val description: String = "Tap a visible search field, type a query, and optionally tap a visible submit control."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val query = invocation.arg("query", "text", "value")
            ?: return RecipeProposal.Refused("missing-query")
        val field = invocation.toTarget(
            labelKeys = listOf("field", "fieldlabel", "searchfield", "target", "targetlabel"),
            defaultRole = "textfield",
        ) ?: RecipeTarget.Node(role = "textfield", text = "Search")

        val steps = mutableListOf(
            RecipeStep(
                id = "focus-field",
                title = "Focus ${field.displayLabel()}",
                command = RecipeCommand.Tap(field),
            ),
            RecipeStep(
                id = "type-query",
                title = "Type search query",
                command = RecipeCommand.TypeText(target = field, text = query),
            ),
        )
        invocation.toTarget(
            labelKeys = listOf("submit", "submitlabel", "button", "buttonlabel"),
            defaultRole = "button",
        )?.let { submit ->
            steps += RecipeStep(
                id = "submit",
                title = "Submit search",
                command = RecipeCommand.Tap(submit),
                sensitive = submit.looksSensitive(),
            )
        }

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = grounding.toolContext.packageName,
                appLabel = grounding.toolContext.appLabel,
                summary = "Search for \"$query\"",
                steps = steps,
            ).validate(),
        )
    }
}

private object ScrollRecipe : AppRecipe {
    override val id: String = "scroll_visible"
    override val displayName: String = "Scroll visible screen"
    override val description: String = "Scroll the current visible app in one direction."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val direction = when (invocation.arg("direction", "dir")?.lowercase()) {
            "up" -> ScrollDirection.UP
            "left" -> ScrollDirection.LEFT
            "right" -> ScrollDirection.RIGHT
            "down", null -> ScrollDirection.DOWN
            else -> return RecipeProposal.Refused("invalid-direction")
        }
        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = grounding.toolContext.packageName,
                appLabel = grounding.toolContext.appLabel,
                summary = "Scroll ${direction.name.lowercase()}",
                steps = listOf(
                    RecipeStep(
                        id = "scroll",
                        title = "Scroll ${direction.name.lowercase()}",
                        command = RecipeCommand.Scroll(direction),
                    ),
                ),
            ).validate(),
        )
    }
}

private fun RecipeInvocation.toTarget(
    labelKeys: List<String>,
    defaultRole: String? = null,
): RecipeTarget.Node? {
    val markId = arg("markid", "markId")
    val viewId = arg("viewid", "viewId", "id")
    val desc = arg("desc", "description", "contentdescription", "contentDescription")
    val label = labelKeys.firstNotNullOfOrNull { arg(it) }
    val role = arg("role") ?: defaultRole
    if (
        markId.isNullOrBlank() &&
        viewId.isNullOrBlank() &&
        desc.isNullOrBlank() &&
        label.isNullOrBlank() &&
        role.isNullOrBlank()
    ) {
        return null
    }
    return RecipeTarget.Node(
        markId = markId,
        role = role,
        text = label,
        viewId = viewId,
        desc = desc,
    )
}
