package com.handy.runtime.agent.verifiers

import com.handy.core.agent.RecipeStep
import com.handy.core.agent.ResultVerifier
import com.handy.core.agent.VerificationResult
import com.handy.core.screen.GroundingSnapshot

object ScreenChangedVerifier : ResultVerifier {
    override val name: String = "ScreenChangedVerifier"

    override suspend fun verify(
        step: RecipeStep,
        snapshotBefore: GroundingSnapshot,
        snapshotAfter: GroundingSnapshot,
    ): VerificationResult =
        if (snapshotAfter.screenChangedFrom(snapshotBefore)) {
            VerificationResult.Verified
        } else {
            VerificationResult.Failed("screen-did-not-change")
        }
}

