package com.handy.app.agent

import com.handy.app.overlay.AgentProgressBubbleState
import com.handy.app.overlay.OverlayPresenter
import com.handy.app.screen.ScreenContextBuilder
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionRisk
import com.handy.core.action.ActionPerformer
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PolicyDecision
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipePlanConfirmer
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeRegistry
import com.handy.core.agent.RecipeIntentDispatcher
import com.handy.core.agent.RecipeRunEvent
import com.handy.core.agent.RecipeRunObserver
import com.handy.core.agent.RecipeRunResult
import com.handy.core.agent.RecipeRunner
import com.handy.core.agent.RecipeSensitiveStepConfirmer
import com.handy.core.agent.RecipeSnapshotProvider
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.RecipeStepPolicyCheck
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.agent.recipes.AndroidRuntimeRecipes
import com.handy.runtime.intent.AndroidIntentDispatcher
import com.handy.runtime.intent.LaunchableAppIndex
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@Singleton
class AgentSessionController @Inject constructor(
    private val presenter: OverlayPresenter,
    private val screenContextBuilder: ScreenContextBuilder,
    private val actionPerformer: ActionPerformer,
    private val policyEngine: ActionPolicyEngine,
    private val intentDispatcher: AndroidIntentDispatcher,
    private val launchableAppIndex: LaunchableAppIndex,
) {
    private val registry = RecipeRegistry(
        RecipeRegistry.defaultRecipes() + AndroidRuntimeRecipes.defaultRecipes(launchableAppIndex),
    )
    private val _progress = MutableStateFlow(AgentProgressBubbleState.Hidden)
    val progress: StateFlow<AgentProgressBubbleState> = _progress.asStateFlow()

    suspend fun runIfRecipeRequested(
        assistantText: String,
        userText: String,
        initialGrounding: GroundingSnapshot,
        source: TurnSource,
        toolContext: ToolContext,
    ): Boolean {
        val goal = UserGoal.fromAssistantText(assistantText)
        if (goal.requestedRecipe == null && goal.requestedIntent == null) return false
        if (!UserGoal.allowsRecipeExecution(userText)) {
            Timber.d(
                "AgentSessionController: ignored recipe directive for guidance-only user intent queryChars=%d",
                userText.length,
            )
            return false
        }

        when (val proposal = registry.propose(goal, initialGrounding)) {
            is RecipeProposal.Refused -> {
                showError("recipe refused: ${proposal.reason}")
                return true
            }
            is RecipeProposal.Proposed -> {
                val plan = proposal.plan
                val preflight = preflight(plan, initialGrounding)
                val denied = preflight.firstOrNull { !it.decision.allowed }
                if (denied != null) {
                    showError("policy refused ${denied.step.title}: ${denied.decision.reason ?: "denied"}")
                    return true
                }
                val approved = requestPlanApproval(plan, preflight)
                if (!approved) {
                    _progress.value = AgentProgressBubbleState.Hidden
                    return true
                }

                presenter.dismissPanel()
                delay(PANEL_DISMISS_BEFORE_RECIPE_MS)
                runPlan(
                    plan = plan,
                    userText = userText,
                    source = source,
                    toolContext = toolContext,
                )
                return true
            }
        }
    }

    private fun preflight(
        plan: RecipePlan,
        grounding: GroundingSnapshot,
    ): List<RecipeStepPolicyCheck> {
        var deferredScreen = false
        return plan.steps.map { step ->
            if (deferredScreen && step.requiresResolvedTarget()) {
                return@map RecipeStepPolicyCheck(step, step.deferredInitialDecision())
            }
            val target = step.resolveTarget(grounding)
            val decision = if (target == null && step.requiresResolvedTarget()) {
                deniedDecision("target-not-found")
            } else {
                policyEngine.decide(
                    action = step.policyAction(grounding),
                    target = target,
                    grounding = grounding,
                    sourceTrust = step.policySourceTrust(),
                ).let(step::applyConfirmationOverride)
            }
            if ((step.command as? RecipeCommand.NativeAction)?.allowPackageChangeAfter == true) {
                deferredScreen = true
            }
            RecipeStepPolicyCheck(step, decision)
        }
    }

    private suspend fun requestPlanApproval(
        plan: RecipePlan,
        checks: List<RecipeStepPolicyCheck>,
    ): Boolean {
        val risk = checks.maxByOrNull { it.risk.ordinal }?.risk ?: ActionRisk.MEDIUM
        _progress.value = AgentProgressBubbleState(
            visible = true,
            title = "Review recipe",
            detail = plan.summary,
            stepIndex = 0,
            stepCount = plan.stepCount,
        )
        return withTimeoutOrNull(PLAN_CONFIRMATION_TIMEOUT_MS) {
            presenter.requestTapForMeConfirmation(
                targetLabel = plan.summary.take(MAX_CONFIRMATION_LABEL),
                appLabel = plan.appLabel,
                packageName = plan.packageName,
                confirmationLevel = ConfirmationLevel.NORMAL,
                risk = risk,
                reason = "recipe-plan:${plan.recipeId}",
            )
        } == true
    }

    private suspend fun runPlan(
        plan: RecipePlan,
        userText: String,
        source: TurnSource,
        toolContext: ToolContext,
    ) {
        val runner = RecipeRunner(
            performer = actionPerformer,
            policy = policyEngine,
            intentDispatcher = RecipeIntentDispatcher { action ->
                intentDispatcher.dispatch(action)
            },
            snapshotProvider = RecipeSnapshotProvider {
                screenContextBuilder.build(
                    userMessage = userText,
                    source = source,
                    toolContext = toolContext,
                    panelSnapshot = null,
                    preferFocusedWindow = false,
                )
            },
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, step, _, decision ->
                requestSensitiveStepApproval(plan, step, decision)
            },
            observer = RecipeRunObserver { event -> onRunEvent(event) },
        )
        val result = runCatching { runner.run(plan) }
            .onFailure { Timber.w(it, "Agent recipe run failed") }
            .getOrElse { RecipeRunResult.Failed("runner", it.message ?: "runner-failed") }
        if (result !is RecipeRunResult.Completed) {
            presenter.onError(result.userMessage())
        }
        delay(PROGRESS_FINISH_DISPLAY_MS)
        _progress.value = AgentProgressBubbleState.Hidden
    }

    private suspend fun requestSensitiveStepApproval(
        plan: RecipePlan,
        step: RecipeStep,
        decision: PolicyDecision,
    ): Boolean {
        _progress.value = AgentProgressBubbleState(
            visible = true,
            title = "Confirm step",
            detail = step.title,
            stepIndex = (plan.steps.indexOf(step) + 1).coerceAtLeast(1),
            stepCount = plan.stepCount,
        )
        val level = when (decision.confirmation) {
            ConfirmationLevel.NONE,
            ConfirmationLevel.NORMAL -> ConfirmationLevel.NORMAL
            else -> decision.confirmation
        }
        return withTimeoutOrNull(STEP_CONFIRMATION_TIMEOUT_MS) {
            presenter.requestTapForMeConfirmation(
                targetLabel = step.title.take(MAX_CONFIRMATION_LABEL),
                appLabel = plan.appLabel,
                packageName = plan.packageName,
                confirmationLevel = level,
                risk = decision.risk,
                reason = decision.reason ?: "sensitive-recipe-step",
            )
        } == true
    }

    private fun onRunEvent(event: RecipeRunEvent) {
        when (event) {
            is RecipeRunEvent.Started -> {
                _progress.value = AgentProgressBubbleState(
                    visible = true,
                    title = "Starting recipe",
                    detail = event.plan.summary,
                    stepIndex = 0,
                    stepCount = event.plan.stepCount,
                )
            }
            is RecipeRunEvent.StepStarted -> {
                _progress.value = AgentProgressBubbleState(
                    visible = true,
                    title = "Running recipe",
                    detail = event.step.title,
                    stepIndex = event.stepIndex + 1,
                    stepCount = event.stepCount,
                )
            }
            is RecipeRunEvent.StepCompleted -> {
                _progress.value = AgentProgressBubbleState(
                    visible = true,
                    title = "Step complete",
                    detail = event.step.title,
                    stepIndex = event.completedSteps,
                    stepCount = event.stepCount,
                )
            }
            is RecipeRunEvent.Finished -> {
                _progress.value = when (val result = event.result) {
                    is RecipeRunResult.Completed -> AgentProgressBubbleState(
                        visible = true,
                        title = "Recipe complete",
                        detail = "${result.completedSteps} steps verified",
                        stepIndex = event.plan.stepCount,
                        stepCount = event.plan.stepCount,
                    )
                    else -> AgentProgressBubbleState(
                        visible = true,
                        title = "Recipe stopped",
                        detail = result.userMessage(),
                        stepIndex = 0,
                        stepCount = event.plan.stepCount,
                    )
                }
            }
        }
    }

    private fun showError(message: String) {
        _progress.value = AgentProgressBubbleState(
            visible = true,
            title = "Recipe stopped",
            detail = message,
        )
        presenter.onError(message)
    }

    private fun deniedDecision(reason: String): PolicyDecision =
        PolicyDecision(
            allowed = false,
            risk = ActionRisk.HIGH,
            confirmation = ConfirmationLevel.NONE,
            requireFreshSnapshot = true,
            requireNodeActionOnly = false,
            allowGestureFallback = false,
            reason = reason,
        )

    private fun RecipeRunResult.userMessage(): String = when (this) {
        is RecipeRunResult.Completed -> "recipe complete"
        is RecipeRunResult.Refused -> "policy refused $stepId: $reason"
        is RecipeRunResult.Cancelled -> "recipe cancelled: $reason"
        is RecipeRunResult.Aborted -> "recipe aborted: $reason"
        is RecipeRunResult.Failed -> "recipe failed at $stepId: $reason"
        is RecipeRunResult.VerificationFailed -> "recipe could not verify $stepId: $reason"
    }

    companion object {
        private const val PLAN_CONFIRMATION_TIMEOUT_MS = 12_000L
        private const val STEP_CONFIRMATION_TIMEOUT_MS = 12_000L
        private const val PANEL_DISMISS_BEFORE_RECIPE_MS = 180L
        private const val PROGRESS_FINISH_DISPLAY_MS = 1_400L
        private const val MAX_CONFIRMATION_LABEL = 42
    }
}
