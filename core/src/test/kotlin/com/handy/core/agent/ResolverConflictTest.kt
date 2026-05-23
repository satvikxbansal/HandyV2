package com.handy.core.agent

import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

class ResolverConflictTest {

    @Test fun `canonical recipe lanes refuse each others utterances`() {
        val recipes = canonicalSmokeRecipes()

        recipes.forEach { recipeA ->
            val recipeACases = canonicalRecipeCases.filter { it.expectedRecipeId == recipeA.id }
            recipes
                .filterNot { recipeB -> recipeB.id == recipeA.id }
                .forEach { recipeB ->
                    recipeACases.forEach { case ->
                        val proposal = recipeB.propose(
                            goal = case.goal(recipeId = recipeB.id),
                            invocation = RecipeInvocation(recipeId = recipeB.id, args = case.args),
                            grounding = case.grounding(),
                        )

                        assertWithMessage("${case.utterance}: ${recipeA.id} overlapped ${recipeB.id}")
                            .that(proposal)
                            .isInstanceOf(RecipeProposal.Refused::class.java)
                    }
                }
        }
    }
}
