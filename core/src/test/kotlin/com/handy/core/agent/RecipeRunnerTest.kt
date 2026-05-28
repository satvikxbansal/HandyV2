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
import com.handy.core.intent.IntentResult
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
            verifier = object : ResultVerifier {
                override val name: String = "CountingVerifier"

                override suspend fun verify(
                    step: RecipeStep,
                    snapshotBefore: GroundingSnapshot,
                    snapshotAfter: GroundingSnapshot,
                ): VerificationResult {
                    verifications += 1
                    return VerificationResult.Verified
                }
            },
        )

        val result = runner.run(fakePlan())

        assertThat(result).isEqualTo(
            RecipeRunResult.Verified(completedSteps = 2, verifiedBy = "CountingVerifier"),
        )
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

    @Test fun `aborts when fresh snapshot has no visible package for expected app`() = runTest {
        val snapshots = SnapshotQueue(
            snapshot("com.example.app", mark("Search", "m1")),
            noVisibleContext("com.example.app"),
        )
        val performer = FakePerformer()
        val runner = RecipeRunner(
            performer = performer,
            policy = FakePolicy(),
            snapshotProvider = snapshots,
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
        )

        val result = runner.run(fakePlan(stepCount = 1))

        assertThat(result).isEqualTo(RecipeRunResult.Aborted("package-changed"))
        assertThat(performer.calls).isEmpty()
    }

    @Test fun `refuses more than six steps`() = runTest {
        val steps = (1..7).map { index ->
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

    @Test fun `runs native action recipe through intent dispatcher`() = runTest {
        val snapshots = SnapshotQueue(
            snapshot("com.handy.android"),
            snapshot("com.handy.android"),
            snapshot("com.google.android.deskclock"),
        )
        val dispatched = mutableListOf<AssistantAction>()
        val runner = RecipeRunner(
            performer = FakePerformer(),
            policy = FakePolicy(),
            intentDispatcher = RecipeIntentDispatcher { action ->
                dispatched += action
                IntentResult.Dispatched("clock")
            },
            snapshotProvider = snapshots,
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
        )

        val result = runner.run(
            fakePlan(
                stepCount = 1,
                packageName = null,
                steps = listOf(
                    RecipeStep(
                        id = "set-alarm",
                        title = "Set alarm",
                        command = RecipeCommand.NativeAction(
                            AssistantAction.SetAlarm(hour = 7, minute = 0),
                        ),
                    ),
                ),
            ),
        )

        assertThat(result).isEqualTo(RecipeRunResult.Completed(completedSteps = 1))
        assertThat(dispatched).containsExactly(AssistantAction.SetAlarm(hour = 7, minute = 0))
    }

    @Test fun `native action no handler fails verification instead of crashing`() = runTest {
        val runner = RecipeRunner(
            performer = FakePerformer(),
            policy = FakePolicy(),
            intentDispatcher = RecipeIntentDispatcher { IntentResult.NoHandler },
            snapshotProvider = SnapshotQueue(
                snapshot("com.handy.android"),
                snapshot("com.handy.android"),
                snapshot("com.handy.android"),
            ),
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
        )

        val result = runner.run(
            fakePlan(
                stepCount = 1,
                packageName = null,
                steps = listOf(
                    RecipeStep(
                        id = "set-alarm",
                        title = "Set alarm",
                        command = RecipeCommand.NativeAction(
                            AssistantAction.SetAlarm(hour = 7, minute = 0),
                        ),
                    ),
                ),
            ),
        )

        assertThat(result).isEqualTo(
            RecipeRunResult.Failed(
                stepId = "set-alarm",
                reason = "intent-no-handler",
            ),
        )
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

    @Test fun `confirmation override upgrades sensitive step to strong hold`() = runTest {
        val snapshots = SnapshotQueue(
            snapshot("com.example.app", mark("Send", "m1")),
            snapshot("com.example.app", mark("Send", "m1")),
        )
        var confirmation: ConfirmationLevel? = null
        val runner = RecipeRunner(
            performer = FakePerformer(),
            policy = FakePolicy(),
            snapshotProvider = snapshots,
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, decision ->
                confirmation = decision.confirmation
                false
            },
        )

        val result = runner.run(
            fakePlan(
                stepCount = 1,
                steps = listOf(
                    RecipeStep(
                        id = "send",
                        title = "Send message?",
                        command = RecipeCommand.Tap(RecipeTarget.Node(text = "Send", role = "button")),
                        sensitive = true,
                        confirmationOverride = ConfirmationLevel.STRONG_HOLD,
                    ),
                ),
            ),
        )

        assertThat(result).isEqualTo(RecipeRunResult.Cancelled("step-declined:send"))
        assertThat(confirmation).isEqualTo(ConfirmationLevel.STRONG_HOLD)
    }

    @Test fun `native app entry step can defer targets until after package switch`() = runTest {
        val snapshots = SnapshotQueue(
            snapshot("com.handy.android"),
            snapshot("com.handy.android"),
            snapshot("com.whatsapp", mark("Search", "m1")),
            snapshot("com.whatsapp", mark("Search", "m1")),
            snapshot("com.whatsapp", mark("Search", "m1")),
        )
        val performer = FakePerformer()
        val runner = RecipeRunner(
            performer = performer,
            policy = FakePolicy(),
            intentDispatcher = RecipeIntentDispatcher { IntentResult.Dispatched("whatsapp") },
            snapshotProvider = snapshots,
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
        )

        val result = runner.run(
            fakePlan(
                packageName = "com.whatsapp",
                steps = listOf(
                    RecipeStep(
                        id = "open-whatsapp",
                        title = "Open WhatsApp",
                        command = RecipeCommand.NativeAction(
                            AssistantAction.OpenApp("WhatsApp"),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                    RecipeStep(
                        id = "tap-search",
                        title = "Tap Search",
                        command = RecipeCommand.Tap(RecipeTarget.Node(text = "Search", role = "button")),
                    ),
                ),
            ),
        )

        assertThat(result).isEqualTo(RecipeRunResult.Completed(completedSteps = 2))
        assertThat(performer.calls).containsExactly("tap:Search")
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

    @Test fun `passes policy gesture fallback decision to performer target`() = runTest {
        val snapshots = SnapshotQueue(
            snapshot("com.example.app", mark("Continue", "m1")),
            snapshot("com.example.app", mark("Continue", "m1")),
            snapshot("com.example.app", mark("Continue", "m1")),
        )
        val performer = FakePerformer()
        val runner = RecipeRunner(
            performer = performer,
            policy = FakePolicy(allowGestureFallback = true),
            snapshotProvider = snapshots,
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
        )

        val result = runner.run(
            fakePlan(
                stepCount = 1,
                steps = listOf(
                    RecipeStep(
                        id = "continue",
                        title = "Tap Continue",
                        command = RecipeCommand.Tap(RecipeTarget.Node(text = "Continue", role = "button")),
                    ),
                ),
            ),
        )

        assertThat(result).isEqualTo(RecipeRunResult.Completed(completedSteps = 1))
        assertThat((performer.targets.single() as TapTarget.AtNode).allowGestureFallback).isTrue()
    }

    private fun fakePlan(
        stepCount: Int = 2,
        packageName: String? = "com.example.app",
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
        packageName = packageName,
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
        val targets = mutableListOf<TapTarget>()
        override val capabilities: Set<ActionCapability> = setOf(
            ActionCapability.TAP,
            ActionCapability.TYPE,
            ActionCapability.SCROLL,
            ActionCapability.LONG_PRESS,
        )

        override suspend fun tap(target: TapTarget, sourceTrust: SourceTrust): PerformResult {
            targets += target
            calls += "tap:${target.label()}"
            return PerformResult.Ok
        }

        override suspend fun longPress(target: TapTarget, sourceTrust: SourceTrust): PerformResult {
            targets += target
            calls += "long:${target.label()}"
            return PerformResult.Ok
        }

        override suspend fun scroll(
            direction: ScrollDirection,
            target: TapTarget?,
            sourceTrust: SourceTrust,
        ): PerformResult {
            target?.let { targets += it }
            calls += "scroll:${direction.name.lowercase()}"
            return PerformResult.Ok
        }

        override suspend fun typeText(
            target: TapTarget,
            text: String,
            sourceTrust: SourceTrust,
        ): PerformResult {
            targets += target
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
        private val allowGestureFallback: Boolean = false,
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
                allowGestureFallback = allowGestureFallback,
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

    private fun noVisibleContext(packageName: String): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = packageName, appLabel = "Example"),
            windowBounds = IntRect(0, 0, 400, 800),
        )

    private fun mark(text: String, markId: String): AccessibilityMark =
        AccessibilityMark(
            markId = markId,
            text = text,
            role = "button",
            bounds = intArrayOf(0, 0, 80, 40),
            clickable = true,
        )
}
