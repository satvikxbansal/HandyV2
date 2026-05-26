package com.handy.runtime.agent.verifiers

import com.handy.core.action.AssistantAction
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.ResultVerifier
import com.handy.core.agent.VerificationResult
import com.handy.core.screen.GroundingSnapshot

class RuntimeResultVerifier : ResultVerifier {
    override val name: String = "RuntimeResultVerifier"

    override fun verifierNameFor(step: RecipeStep): String = verifierFor(step).name

    override suspend fun verify(
        step: RecipeStep,
        snapshotBefore: GroundingSnapshot,
        snapshotAfter: GroundingSnapshot,
    ): VerificationResult {
        val verifier = verifierFor(step)
        val result = verifier.verify(step, snapshotBefore, snapshotAfter)
        return when (result) {
            VerificationResult.Verified,
            is VerificationResult.Failed -> result
            VerificationResult.Inconclusive -> ScreenChangedVerifier.verify(step, snapshotBefore, snapshotAfter)
        }
    }

    private fun verifierFor(step: RecipeStep): ResultVerifier =
        when (val command = step.command) {
            is RecipeCommand.TypeText -> TextFieldFilledVerifier
            is RecipeCommand.NativeAction -> if (command.action is AssistantAction.OpenApp) {
                TapPackageChangedVerifier
            } else {
                IntentLaunchedVerifier
            }
            is RecipeCommand.Tap,
            is RecipeCommand.LongPress,
            is RecipeCommand.Scroll -> ScreenChangedVerifier
        }
}
