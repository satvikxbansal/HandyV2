package com.handy.runtime.agent.verifiers

import com.handy.core.action.AssistantAction
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.ResultVerifier
import com.handy.core.agent.VerificationResult
import com.handy.core.screen.GroundingSnapshot

object TapPackageChangedVerifier : ResultVerifier {
    override val name: String = "TapPackageChangedVerifier"

    override suspend fun verify(
        step: RecipeStep,
        snapshotBefore: GroundingSnapshot,
        snapshotAfter: GroundingSnapshot,
    ): VerificationResult {
        val action = (step.command as? RecipeCommand.NativeAction)?.action as? AssistantAction.OpenApp
            ?: return VerificationResult.Inconclusive
        val expectedPackage = action.packageHint.takeIf { it.isNotBlank() }
            ?: return VerificationResult.Inconclusive
        val beforePackage = snapshotBefore.foregroundPackageName()
        val afterPackage = snapshotAfter.foregroundPackageName()
        return if (afterPackage.equals(expectedPackage, ignoreCase = true)) {
            VerificationResult.Verified
        } else if (snapshotAfter.looksLikeExpectedApp(expectedPackage)) {
            VerificationResult.Verified
        } else {
            VerificationResult.Failed(
                "expected-package:$expectedPackage actual:${afterPackage ?: "unknown"} before:${beforePackage ?: "unknown"}",
            )
        }
    }
}

private fun GroundingSnapshot.looksLikeExpectedApp(expected: String): Boolean =
    sequenceOf(
        toolContext.appLabel,
        screenText?.windowTitle,
    ).plus(visibleTextValues()).any { value ->
        value?.equals(expected, ignoreCase = true) == true ||
            value?.contains(expected, ignoreCase = true) == true
    }
