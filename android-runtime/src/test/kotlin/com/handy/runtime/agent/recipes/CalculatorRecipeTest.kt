package com.handy.runtime.agent.recipes

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.AssistantAction
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
import org.junit.Test

class CalculatorRecipeTest {

    @Test fun `percent of expression answers locally`() {
        val proposal = CalculatorRecipe.propose(
            goal = UserGoal(text = "What's 23% of 4500?"),
            invocation = invocation("expression" to "23% of 4500"),
            grounding = grounding(),
        )

        assertThat(proposal).isEqualTo(RecipeProposal.Answered("1035"))
    }

    @Test fun `safe parser supports arithmetic precedence and parentheses`() {
        assertThat(evaluateCalculatorExpression("compute 12*47")).isEqualTo(564.0)
        assertThat(evaluateCalculatorExpression("calculate (12 + 3) * 4")).isEqualTo(60.0)
        assertThat(evaluateCalculatorExpression("20 % 6")).isEqualTo(2.0)
    }

    @Test fun `safe parser rejects unsupported functions and division by zero`() {
        assertThat(evaluateCalculatorExpression("sqrt(9)")).isNull()
        assertThat(evaluateCalculatorExpression("1 / 0")).isNull()
        assertThat(evaluateCalculatorExpression("2 ** 8")).isNull()
    }

    @Test fun `open calculator proposes calculator app intent`() {
        val plan = CalculatorRecipe.propose(
            goal = UserGoal(text = "open calculator"),
            invocation = invocation("mode" to "open"),
            grounding = grounding(),
        ).plan()

        val command = plan.steps.single().command as RecipeCommand.NativeAction
        assertThat(command.action).isEqualTo(AssistantAction.OpenCalculator)
    }

    private fun invocation(
        vararg args: Pair<String, String>,
    ): RecipeInvocation =
        RecipeInvocation(recipeId = CalculatorRecipe.id, args = args.toMap())

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun grounding(): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "calculator-recipe-test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = "com.handy.android", appLabel = "Handy"),
            screenText = ScreenTextSnapshot(
                packageName = "com.handy.android",
                windowTitle = null,
                timestampEpochMs = 1L,
                root = UiNode(role = "root"),
            ),
        )
}
