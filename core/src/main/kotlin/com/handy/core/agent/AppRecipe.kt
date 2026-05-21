package com.handy.core.agent

import com.handy.core.screen.GroundingSnapshot

interface AppRecipe {
    val id: String
    val displayName: String
    val description: String

    fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal
}

sealed class RecipeProposal {
    data class Proposed(val plan: RecipePlan) : RecipeProposal()
    data class Refused(val reason: String) : RecipeProposal()
}
