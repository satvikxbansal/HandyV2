package com.handy.app.agent

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
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeIntentDispatcher
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipePlanConfirmer
import com.handy.core.agent.RecipeRunResult
import com.handy.core.agent.RecipeRunner
import com.handy.core.agent.RecipeSensitiveStepConfirmer
import com.handy.core.agent.RecipeSnapshotProvider
import com.handy.core.agent.RecipeStep
import com.handy.core.intent.IntentResult
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RecipeNativeActionExecutionTest {

    @Test fun native_action_recipe_dispatches_without_tap_performer() {
        runBlocking {
            val dispatched = mutableListOf<AssistantAction>()
            val runner = RecipeRunner(
                performer = NoopPerformer,
                policy = AllowPolicy,
                intentDispatcher = RecipeIntentDispatcher { action ->
                    dispatched += action
                    IntentResult.Dispatched("clock")
                },
                snapshotProvider = SnapshotQueue(
                    grounding("com.handy.android"),
                    grounding("com.handy.android"),
                    grounding("com.google.android.deskclock"),
                ),
                planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
                sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, _, _, _ -> true },
            )

            val result = runner.run(
                RecipePlan(
                    recipeId = "clock_alarm",
                    displayName = "Set an alarm",
                    packageName = null,
                    appLabel = "Clock",
                    summary = "Set alarm for 7:00 AM",
                    steps = listOf(
                        RecipeStep(
                            id = "set-alarm-intent",
                            title = "Set alarm for 7:00 AM",
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
    }

    private class SnapshotQueue(
        private vararg val snapshots: GroundingSnapshot,
    ) : RecipeSnapshotProvider {
        private var index = 0
        override suspend fun capture(): GroundingSnapshot {
            val current = snapshots[index.coerceAtMost(snapshots.lastIndex)]
            index += 1
            return current
        }
    }

    private object NoopPerformer : com.handy.core.action.ActionPerformer {
        override val capabilities: Set<ActionCapability> = emptySet()
        override suspend fun tap(target: TapTarget): PerformResult = PerformResult.Unsupported("unused")
        override suspend fun longPress(target: TapTarget): PerformResult = PerformResult.Unsupported("unused")
        override suspend fun scroll(direction: ScrollDirection, target: TapTarget?): PerformResult =
            PerformResult.Unsupported("unused")
        override suspend fun typeText(target: TapTarget, text: String): PerformResult =
            PerformResult.Unsupported("unused")
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
                risk = ActionRisk.LOW,
                confirmation = ConfirmationLevel.NONE,
                requireFreshSnapshot = false,
                requireNodeActionOnly = false,
                allowGestureFallback = false,
                reason = null,
            )
    }

    private fun grounding(packageName: String): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "android-test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = packageName, appLabel = packageName),
        )
}
