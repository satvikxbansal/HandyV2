package com.handy.core.agent

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionPerformer
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionRisk
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PerformResult
import com.handy.core.action.PolicyDecision
import com.handy.core.action.ScrollDirection
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RecipeRunnerVerificationTest {

    @Test fun `verifier failure aborts the recipe and emits step verified`() = runTest {
        val events = mutableListOf<RecipeRunEvent>()
        val runner = RecipeRunner(
            performer = OkPerformer,
            policy = AllowPolicy,
            snapshotProvider = SnapshotQueue(
                snapshot("com.example", "before"),
                snapshot("com.example", "before"),
                snapshot("com.example", "after"),
            ),
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
            verifier = object : ResultVerifier {
                override val name: String = "FailingVerifier"

                override suspend fun verify(
                    step: RecipeStep,
                    snapshotBefore: GroundingSnapshot,
                    snapshotAfter: GroundingSnapshot,
                ): VerificationResult = VerificationResult.Failed("button-did-not-respond")
            },
            observer = RecipeRunObserver { events += it },
        )

        val result = runner.run(
            RecipePlan(
                recipeId = "fake",
                displayName = "Fake",
                packageName = "com.example",
                appLabel = "Example",
                summary = "Tap Continue",
                steps = listOf(
                    RecipeStep(
                        id = "continue",
                        title = "Tap Continue",
                        command = RecipeCommand.Tap(RecipeTarget.Node(text = "Continue", role = "button")),
                    ),
                ),
            ),
        )

        assertThat(result).isEqualTo(
            RecipeRunResult.Failed(stepId = "continue", reason = "button-did-not-respond"),
        )
        assertThat(events.filterIsInstance<RecipeRunEvent.StepVerified>().single().result)
            .isEqualTo(VerificationResult.Failed("button-did-not-respond"))
        assertThat(events.filterIsInstance<RecipeRunEvent.StepCompleted>()).isEmpty()
    }

    private object OkPerformer : ActionPerformer {
        override val capabilities: Set<ActionCapability> = setOf(ActionCapability.TAP)
        override suspend fun tap(target: TapTarget, sourceTrust: SourceTrust): PerformResult = PerformResult.Ok
        override suspend fun longPress(target: TapTarget, sourceTrust: SourceTrust): PerformResult = PerformResult.Ok
        override suspend fun scroll(
            direction: ScrollDirection,
            target: TapTarget?,
            sourceTrust: SourceTrust,
        ): PerformResult = PerformResult.Ok

        override suspend fun typeText(
            target: TapTarget,
            text: String,
            sourceTrust: SourceTrust,
        ): PerformResult = PerformResult.Ok
    }

    private object AllowPolicy : ActionPolicyEngine {
        override fun decide(
            action: AssistantAction,
            target: TapTarget?,
            grounding: GroundingSnapshot,
            sourceTrust: SourceTrust,
        ): PolicyDecision =
            PolicyDecision(
                allowed = true,
                risk = ActionRisk.MEDIUM,
                confirmation = ConfirmationLevel.NONE,
                requireFreshSnapshot = false,
                requireNodeActionOnly = false,
                allowGestureFallback = false,
                reason = null,
            )
    }

    private class SnapshotQueue(
        private vararg val snapshots: GroundingSnapshot,
    ) : RecipeSnapshotProvider {
        private var index = 0
        override suspend fun capture(): GroundingSnapshot =
            snapshots[index.coerceAtMost(snapshots.lastIndex)].also { index += 1 }
    }

    private fun snapshot(
        packageName: String,
        treeHash: String,
    ): GroundingSnapshot {
        val mark = AccessibilityMark(
            markId = "continue",
            text = "Continue",
            role = "button",
            bounds = intArrayOf(0, 0, 100, 50),
            clickable = true,
        )
        val toolContext = ToolContext(packageName = packageName, appLabel = "Example")
        return GroundingSnapshot(
            requestId = "verification-test",
            source = TurnSource.TEST,
            toolContext = toolContext,
            panelSnapshot = PanelSnapshot(
                toolContext = toolContext,
                capturedAtEpochMs = 1L,
                marks = listOf(mark),
            ),
            screenText = ScreenTextSnapshot(
                packageName = packageName,
                timestampEpochMs = 1L,
                root = UiNode(
                    role = "root",
                    children = listOf(
                        UiNode(
                            markId = mark.markId,
                            role = mark.role,
                            text = mark.text,
                            boundsInScreen = IntRect(mark.left, mark.top, mark.right, mark.bottom),
                            clickable = true,
                        ),
                    ),
                ),
            ),
            windowId = 1,
            rootBoundsHash = "root",
            treeHash = treeHash,
        )
    }
}

