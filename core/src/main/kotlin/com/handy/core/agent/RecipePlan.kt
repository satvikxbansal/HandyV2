package com.handy.core.agent

import com.handy.core.action.ActionRisk
import com.handy.core.action.PolicyDecision

data class RecipePlan(
    val recipeId: String,
    val displayName: String,
    val packageName: String?,
    val appLabel: String?,
    val summary: String,
    val steps: List<RecipeStep>,
) {
    init {
        require(recipeId.isNotBlank()) { "RecipePlan.recipeId must not be blank" }
        require(displayName.isNotBlank()) { "RecipePlan.displayName must not be blank" }
        require(steps.isNotEmpty()) { "RecipePlan requires at least one step" }
    }

    val stepCount: Int get() = steps.size
    val hasSensitiveSteps: Boolean get() = steps.any { it.sensitive }

    fun validate(maxSteps: Int = RecipeRunner.MAX_STEPS): RecipePlan {
        require(steps.size <= maxSteps) { "RecipePlan exceeds $maxSteps steps" }
        return this
    }
}

data class RecipeStepPolicyCheck(
    val step: RecipeStep,
    val decision: PolicyDecision,
) {
    val risk: ActionRisk get() = decision.risk
}
