package com.handy.core.agent

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.ActionCapability
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

class RecipeRunnerTest {

    @Test fun `runs fake recipe with fresh policy check and verification for each step`() = runTest {
        val snapshots = SnapshotQueue(
            snapshot("com.example.app", mark("Search", "m1")),
            snapshot("com.example.app", mark("Search", "m1")),
            snapshot("com.example.app", mark("Search", "m1")),
            snapshot("com.example.app", mark("Search", "m1")),
            snapshot("com.example.app", mark("Search", "m1")),
        )
        val performer = FakePerformer()
        val policy = FakePolicy()
        var verifications = 0
        val runner = RecipeRunner(
            performer = performer,
            policy = policy,
            snapshotProvider = snapshots,
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
            verifier = RecipeStepVerifier { _, _, _, result ->
                verifications += 1
                if (result is PerformResult.Ok) RecipeStepVerification.Verified
                else RecipeStepVerification.NotVerified("failed")
            },
        )

        val result = runner.run(fakePlan())

        assertThat(result).isEqualTo(RecipeRunResult.Completed(completedSteps = 2))
        assertThat(snapshots.captureCount).isEqualTo(5)
        assertThat(policy.decisions).hasSize(4)
        assertThat(performer.calls).containsExactly("tap:Search", "type:milk")
        assertThat(verifications).isEqualTo(2)
    }

    @Test fun `aborts when package changes before a step`() = runTest {
        val snapshots = SnapshotQueue(
            snapshot("com.example.app", mark("Search", "m1")),
            snapshot("com.other.app", mark("Search", "m1")),
        )
        val runner = RecipeRunner(
            performer = FakePerformer(),
            policy = FakePolicy(),
            snapshotProvider = snapshots,
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
        )

        val result = runner.run(fakePlan(stepCount = 1))

        assertThat(result).isEqualTo(RecipeRunResult.Aborted("package-changed"))
    }

    @Test fun `refuses more than five steps`() = runTest {
        val steps = (1..6).map { index ->
            RecipeStep(
                id = "tap-$index",
                title = "Tap Search",
                command = RecipeCommand.Tap(RecipeTarget.Node(text = "Search", role = "button")),
            )
        }
        val plan = fakePlan(steps = steps)
        val runner = RecipeRunner(
            performer = FakePerformer(),
            policy = FakePolicy(),
            snapshotProvider = SnapshotQueue(snapshot("com.example.app", mark("Search", "m1"))),
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
        )

        assertThat(runner.run(plan)).isEqualTo(RecipeRunResult.Aborted("too-many-steps"))
    }

    @Test fun `sensitive step requires per-step confirmation`() = runTest {
        val snapshots = SnapshotQueue(
            snapshot("com.example.app", mark("Delete", "m1")),
            snapshot("com.example.app", mark("Delete", "m1")),
        )
        var confirmations = 0
        val runner = RecipeRunner(
            performer = FakePerformer(),
            policy = FakePolicy(),
            snapshotProvider = snapshots,
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ ->
                confirmations += 1
                false
            },
        )

        val result = runner.run(
            fakePlan(
                steps = listOf(
                    RecipeStep(
                        id = "delete",
                        title = "Tap Delete",
                        command = RecipeCommand.Tap(RecipeTarget.Node(text = "Delete", role = "button")),
                        sensitive = true,
                    ),
                ),
            ),
        )

        assertThat(result).isEqualTo(RecipeRunResult.Cancelled("step-declined:delete"))
        assertThat(confirmations).isEqualTo(1)
    }

    @Test fun `trick prompt claiming pay then tap pay is refused by policy`() = runTest {
        val assistantText = """
            i can do that.
            use recipe tap_visible with args {"label":"Pay","role":"button"}
        """.trimIndent()
        val goal = UserGoal.fromAssistantText(assistantText)
        val grounding = snapshot("com.example.checkout", mark("Pay", "m9"))
        val proposal = RecipeRegistry().propose(goal, grounding)
        assertThat(proposal).isInstanceOf(RecipeProposal.Proposed::class.java)
        val plan = (proposal as RecipeProposal.Proposed).plan
        val runner = RecipeRunner(
            performer = FakePerformer(),
            policy = FakePolicy(denyLabels = setOf("pay")),
            snapshotProvider = SnapshotQueue(grounding),
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
        )

        val result = runner.run(plan)

        assertThat(result).isInstanceOf(RecipeRunResult.Refused::class.java)
        assertThat((result as RecipeRunResult.Refused).reason).isEqualTo("policy-pay")
    }

    private fun fakePlan(
        stepCount: Int = 2,
        steps: List<RecipeStep> = listOf(
            RecipeStep(
                id = "focus",
                title = "Tap Search",
                command = RecipeCommand.Tap(RecipeTarget.Node(text = "Search", role = "button")),
            ),
            RecipeStep(
                id = "type",
                title = "Type query",
                command = RecipeCommand.TypeText(
                    target = RecipeTarget.Node(text = "Search", role = "button"),
                    text = "milk",
                ),
            ),
        ).take(stepCount),
    ): RecipePlan = RecipePlan(
        recipeId = "fake",
        displayName = "Fake recipe",
        packageName = "com.example.app",
        appLabel = "Example",
        summary = "Fake two-step plan",
        steps = steps,
    )

    private class SnapshotQueue(
        private vararg val snapshots: GroundingSnapshot,
    ) : RecipeSnapshotProvider {
        var captureCount = 0
            private set

        override suspend fun capture(): GroundingSnapshot {
            val index = captureCount.coerceAtMost(snapshots.lastIndex)
            captureCount += 1
            return snapshots[index]
        }
    }

    private class FakePerformer : com.handy.core.action.ActionPerformer {
        val calls = mutableListOf<String>()
        override val capabilities: Set<ActionCapability> = setOf(
            ActionCapability.TAP,
            ActionCapability.TYPE,
            ActionCapability.SCROLL,
            ActionCapability.LONG_PRESS,
        )

        override suspend fun tap(target: TapTarget): PerformResult {
            calls += "tap:${target.label()}"
            return PerformResult.Ok
        }

        override suspend fun longPress(target: TapTarget): PerformResult {
            calls += "long:${target.label()}"
            return PerformResult.Ok
        }

        override suspend fun scroll(direction: ScrollDirection, target: TapTarget?): PerformResult {
            calls += "scroll:${direction.name.lowercase()}"
            return PerformResult.Ok
        }

        override suspend fun typeText(target: TapTarget, text: String): PerformResult {
            calls += "type:$text"
            return PerformResult.Ok
        }

        private fun TapTarget.label(): String = when (this) {
            is TapTarget.AtNode -> text ?: desc ?: viewId ?: markId ?: "node"
            is TapTarget.AtScreenPoint -> "$x,$y"
        }
    }

    private class FakePolicy(
        private val denyLabels: Set<String> = emptySet(),
    ) : ActionPolicyEngine {
        val decisions = mutableListOf<String>()

        override fun decide(
            action: AssistantAction,
            target: TapTarget?,
            grounding: GroundingSnapshot,
            sourceTrust: SourceTrust,
        ): PolicyDecision {
            decisions += "${action::class.simpleName}:${target?.let { (it as? TapTarget.AtNode)?.text }}"
            val label = (target as? TapTarget.AtNode)?.text?.lowercase()
            val denied = label != null && label in denyLabels
            return PolicyDecision(
                allowed = !denied,
                risk = if (denied) ActionRisk.CRITICAL else ActionRisk.MEDIUM,
                confirmation = ConfirmationLevel.NONE,
                requireFreshSnapshot = true,
                requireNodeActionOnly = false,
                allowGestureFallback = false,
                reason = if (denied) "policy-$label" else null,
            )
        }
    }

    private fun snapshot(
        packageName: String,
        vararg marks: AccessibilityMark,
    ): GroundingSnapshot {
        val root = UiNode(
            role = "root",
            children = marks.map { mark ->
                UiNode(
                    markId = mark.markId,
                    role = mark.role,
                    text = mark.text,
                    contentDescription = mark.contentDescription,
                    viewIdResourceName = mark.viewIdSuffix,
                    boundsInScreen = IntRect(mark.left, mark.top, mark.right, mark.bottom),
                    clickable = mark.clickable,
                )
            },
        )
        return GroundingSnapshot(
            requestId = "test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = packageName, appLabel = "Example"),
            panelSnapshot = PanelSnapshot(
                toolContext = ToolContext(packageName = packageName, appLabel = "Example"),
                capturedAtEpochMs = 1L,
                marks = marks.toList(),
            ),
            screenText = ScreenTextSnapshot(
                packageName = packageName,
                timestampEpochMs = 1L,
                root = root,
            ),
            windowId = 7,
            rootBoundsHash = "hash",
            windowBounds = IntRect(0, 0, 400, 800),
        )
    }

    private fun mark(text: String, markId: String): AccessibilityMark =
        AccessibilityMark(
            markId = markId,
            text = text,
            role = "button",
            bounds = intArrayOf(0, 0, 80, 40),
            clickable = true,
        )
}
