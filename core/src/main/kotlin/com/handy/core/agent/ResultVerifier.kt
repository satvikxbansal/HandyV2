package com.handy.core.agent

import com.handy.core.screen.GroundingSnapshot

interface ResultVerifier {
    val name: String
        get() = this::class.simpleName ?: "ResultVerifier"

    fun verifierNameFor(step: RecipeStep): String = name

    suspend fun verify(
        step: RecipeStep,
        snapshotBefore: GroundingSnapshot,
        snapshotAfter: GroundingSnapshot,
    ): VerificationResult

    companion object {
        val Default: ResultVerifier = object : ResultVerifier {
            override val name: String = "NoopResultVerifier"

            override suspend fun verify(
                step: RecipeStep,
                snapshotBefore: GroundingSnapshot,
                snapshotAfter: GroundingSnapshot,
            ): VerificationResult = VerificationResult.Inconclusive
        }
    }
}

sealed class VerificationResult {
    data object Verified : VerificationResult()
    data class Failed(val reason: String) : VerificationResult()
    data object Inconclusive : VerificationResult()
}
