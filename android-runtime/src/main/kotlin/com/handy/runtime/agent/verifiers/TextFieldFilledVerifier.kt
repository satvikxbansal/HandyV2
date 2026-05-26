package com.handy.runtime.agent.verifiers

import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.RecipeTarget
import com.handy.core.agent.ResultVerifier
import com.handy.core.agent.VerificationResult
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.UiNode

object TextFieldFilledVerifier : ResultVerifier {
    override val name: String = "TextFieldFilledVerifier"

    override suspend fun verify(
        step: RecipeStep,
        snapshotBefore: GroundingSnapshot,
        snapshotAfter: GroundingSnapshot,
    ): VerificationResult {
        val command = step.command as? RecipeCommand.TypeText
            ?: return VerificationResult.Inconclusive
        val expectedGrowth = (command.text.length * MIN_TYPED_GROWTH_RATIO).toInt().coerceAtLeast(1)
        val before = snapshotBefore.textFor(command.target).orEmpty()
        val after = snapshotAfter.textFor(command.target).orEmpty()
        if (after.length >= before.length + expectedGrowth) {
            return VerificationResult.Verified
        }

        val typedVisible = snapshotAfter.visibleTextValues().any { value ->
            value.contains(command.text, ignoreCase = true)
        }
        return if (typedVisible) {
            VerificationResult.Verified
        } else {
            VerificationResult.Failed(
                "text-field-not-filled:expected-growth=$expectedGrowth before=${before.length} after=${after.length}",
            )
        }
    }

    private fun GroundingSnapshot.textFor(target: RecipeTarget): String? {
        val node = target as? RecipeTarget.Node ?: return null
        panelSnapshot?.marks.orEmpty()
            .firstOrNull { mark ->
                node.markId?.let { mark.markId.equals(it, ignoreCase = true) } == true ||
                    node.viewId?.let { mark.matchesViewIdOrRole(it, node.role) } == true ||
                    node.desc?.let { mark.contentDescription.equals(it, ignoreCase = true) } == true ||
                    node.text?.let { mark.text.equals(it, ignoreCase = true) } == true
            }
            ?.let { mark -> return mark.text ?: mark.contentDescription }

        return screenText?.root?.firstTextFor(node)
    }

    private fun UiNode.firstTextFor(target: RecipeTarget.Node): String? {
        if (matches(target)) return text ?: contentDescription
        children.forEach { child ->
            child.firstTextFor(target)?.let { return it }
        }
        return null
    }

    private fun UiNode.matches(target: RecipeTarget.Node): Boolean =
        target.markId?.let { markId.equals(it, ignoreCase = true) } == true ||
            target.viewId?.let {
                viewIdResourceName
                    ?.substringAfterLast('/')
                    ?.equals(it.substringAfterLast('/'), ignoreCase = true) == true
            } == true ||
            target.desc?.let { contentDescription.equals(it, ignoreCase = true) } == true ||
            target.text?.let { text.equals(it, ignoreCase = true) } == true ||
            target.role?.let {
                role.replace(" ", "").equals(it.replace(" ", ""), ignoreCase = true)
            } == true

    private const val MIN_TYPED_GROWTH_RATIO: Double = 0.7
}

