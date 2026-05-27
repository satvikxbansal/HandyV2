package com.handy.app.agent

import android.content.Context
import com.handy.app.overlay.AgentProgressBubbleState
import com.handy.app.overlay.BuddyFlightDriver
import com.handy.app.overlay.OverlayPresenter
import com.handy.app.screen.ScreenContextBuilder
import com.handy.app.voice.SpeechOutputController
import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionRisk
import com.handy.core.action.ActionPerformer
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PerformResult
import com.handy.core.action.PolicyDecision
import com.handy.core.action.ScrollDirection
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.audit.AuditStore
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipePlanConfirmer
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeIntent
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
import com.handy.core.agent.ResultVerifier
import com.handy.core.agent.UserGoal
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.overlay.CandidateOption
import com.handy.core.overlay.CandidateOptions
import com.handy.core.llm.ToolProvenance
import com.handy.core.screen.IntRect
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.agent.recipes.AndroidRuntimeRecipes
import com.handy.runtime.agent.recipes.AndroidContactsResolver
import com.handy.runtime.audit.RecipeAuditObserver
import com.handy.runtime.intent.AndroidIntentDispatcher
import com.handy.runtime.intent.LaunchableAppIndex
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val presenter: OverlayPresenter,
    private val screenContextBuilder: ScreenContextBuilder,
    private val actionPerformer: ActionPerformer,
    private val policyEngine: ActionPolicyEngine,
    private val intentDispatcher: AndroidIntentDispatcher,
    private val launchableAppIndex: LaunchableAppIndex,
    private val flightDriver: BuddyFlightDriver,
    private val speechOutputController: SpeechOutputController,
    private val auditStore: AuditStore,
    private val resultVerifier: ResultVerifier,
) {
    private val registry = RecipeRegistry(
        RecipeRegistry.defaultRecipes() + AndroidRuntimeRecipes.defaultRecipes(
            findLaunchableApps = launchableAppIndex::find,
            findContacts = AndroidContactsResolver(context)::find,
        ),
    )
    private val _progress = MutableStateFlow(AgentProgressBubbleState.Hidden)
    val progress: StateFlow<AgentProgressBubbleState> = _progress.asStateFlow()

    suspend fun runIfRecipeRequested(
        assistantText: String,
        userText: String,
        initialGrounding: GroundingSnapshot,
        source: TurnSource,
        toolContext: ToolContext,
        provenance: ToolProvenance? = null,
    ): Boolean {
        val goal = UserGoal.fromAssistantText(assistantText)
        if (goal.requestedRecipe == null && goal.requestedIntent == null) return false
        val allowsExecution = UserGoal.allowsRecipeExecution(userText)
        if (!allowsExecution && !goal.isAnswerOnlyCalculatorRequest()) {
            Timber.d(
                "AgentSessionController: ignored recipe directive for guidance-only user intent queryChars=%d",
                userText.length,
            )
            return false
        }

        val proposal = registry.propose(goal, initialGrounding)
        if (!allowsExecution && proposal !is RecipeProposal.Answered) {
            Timber.d(
                "AgentSessionController: ignored non-answer recipe directive for guidance-only user intent queryChars=%d",
                userText.length,
            )
            return false
        }

        when (proposal) {
            is RecipeProposal.Answered -> {
                postRecipeCompletionMessage(proposal.message)
                return true
            }
            is RecipeProposal.Refused -> {
                presenter.setCandidateOptions(proposal.candidateLabels.toCandidateOptions())
                showError("recipe refused: ${proposal.reason}")
                return true
            }
            is RecipeProposal.Proposed -> {
                val plan = proposal.plan
                val preflight = preflight(plan, initialGrounding, provenance)
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

                speechOutputController.stop("panel_dismissed")
                presenter.dismissPanel()
                delay(PANEL_DISMISS_BEFORE_RECIPE_MS)
                runPlan(
                    plan = plan,
                    userText = userText,
                    source = source,
                    toolContext = toolContext,
                    provenance = provenance,
                )
                return true
            }
        }
    }

    private fun preflight(
        plan: RecipePlan,
        grounding: GroundingSnapshot,
        provenance: ToolProvenance?,
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
                    sourceTrust = step.sourceTrust(provenance),
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
        provenance: ToolProvenance?,
    ) {
        suspend fun recipeGrounding(): GroundingSnapshot =
            screenContextBuilder.build(
                userMessage = userText,
                source = source,
                toolContext = toolContext,
                panelSnapshot = null,
                preferFocusedWindow = false,
            )

        val snapshotProvider = RecipeSnapshotProvider { recipeGrounding() }
        val performer = if (plan.recipeId == CHROME_RECIPE_ID) {
            ChromeOmniboxFlightActionPerformer(
                delegate = actionPerformer,
                flightDriver = flightDriver,
                snapshotProvider = ::recipeGrounding,
                provenance = provenance,
            )
        } else {
            actionPerformer
        }
        val auditObserver = RecipeAuditObserver(auditStore)
        val runner = RecipeRunner(
            performer = performer,
            policy = policyEngine,
            intentDispatcher = RecipeIntentDispatcher { action ->
                if (action.isDestructive) {
                    intentDispatcher.dispatchConfirmed(action)
                } else {
                    intentDispatcher.dispatch(action)
                }
            },
            snapshotProvider = snapshotProvider,
            planConfirmer = RecipePlanConfirmer { _, _, _ -> true },
            sensitiveStepConfirmer = RecipeSensitiveStepConfirmer { _, step, _, decision ->
                requestSensitiveStepApproval(plan, step, decision)
            },
            verifier = resultVerifier,
            observer = RecipeRunObserver { event ->
                onRunEvent(event)
                auditObserver.onEvent(event)
            },
            sourceTrustProvider = { step -> step.sourceTrust(provenance) },
        )
        val result = runCatching { runner.run(plan) }
            .onFailure { Timber.w(it, "Agent recipe run failed") }
            .getOrElse { RecipeRunResult.Failed("runner", it.message ?: "runner-failed") }
        if (result is RecipeRunResult.Verified || result is RecipeRunResult.Completed) {
            plan.rideCompletionMessage()?.let(::postRecipeCompletionMessage)
        } else {
            presenter.onError(result.userMessage())
        }
        delay(PROGRESS_FINISH_DISPLAY_MS)
        _progress.value = AgentProgressBubbleState.Hidden
    }

    private fun postRecipeCompletionMessage(message: String) {
        presenter.onStreamingStart()
        presenter.onResponseFinalized(message, message)
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
            is RecipeRunEvent.StepVerified -> {
                _progress.value = AgentProgressBubbleState(
                    visible = true,
                    title = when (event.result) {
                        is com.handy.core.agent.VerificationResult.Verified -> "Step verified"
                        is com.handy.core.agent.VerificationResult.Inconclusive -> "Step observed"
                        is com.handy.core.agent.VerificationResult.Failed -> "Verification failed"
                    },
                    detail = event.step.title,
                )
            }
            is RecipeRunEvent.Finished -> {
                _progress.value = when (val result = event.result) {
                    is RecipeRunResult.Verified -> AgentProgressBubbleState(
                        visible = true,
                        title = "Recipe complete",
                        detail = "${result.completedSteps} steps verified",
                        stepIndex = event.plan.stepCount,
                        stepCount = event.plan.stepCount,
                    )
                    is RecipeRunResult.Completed -> AgentProgressBubbleState(
                        visible = true,
                        title = "Recipe complete",
                        detail = "${result.completedSteps} steps completed",
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

    private fun List<String>.toCandidateOptions(): CandidateOptions? {
        val labels = mapNotNull { it.trim().takeIf(String::isNotBlank) }.take(5)
        if (labels.size < 2) return null
        return CandidateOptions(
            options = labels.mapIndexed { index, label ->
                CandidateOption(
                    id = "recipe_candidate_$index",
                    label = label,
                    role = null,
                    markId = null,
                    viewId = null,
                    bounds = IntRect.ZERO,
                    confidence = 1f,
                    actionable = false,
                )
            },
            visible = true,
        )
    }

    private fun UserGoal.isAnswerOnlyCalculatorRequest(): Boolean {
        val requested = listOfNotNull(requestedIntent, requestedRecipe?.recipeId)
            .map { it.lowercase() }
        return requested.any { it == RecipeIntent.CALCULATE.canonical || it == CALCULATOR_RECIPE_ID }
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
        is RecipeRunResult.Verified -> "recipe complete"
        is RecipeRunResult.Completed -> "recipe complete"
        is RecipeRunResult.Refused -> "policy refused $stepId: $reason"
        is RecipeRunResult.Cancelled -> "recipe cancelled: $reason"
        is RecipeRunResult.Aborted -> "recipe aborted: $reason"
        is RecipeRunResult.Failed -> "recipe failed at $stepId: $reason"
    }

    private fun RecipeStep.sourceTrust(provenance: ToolProvenance?): SourceTrust =
        if (provenance?.isUntrusted == true) {
            SourceTrust.UNTRUSTED_TOOL
        } else {
            policySourceTrust()
        }

    private fun RecipePlan.rideCompletionMessage(): String? =
        rideRecipeAppLabels[recipeId]?.let { app ->
            "Ready to go — tap Confirm in $app when you're ready."
        }

    companion object {
        private const val CHROME_RECIPE_ID = "chrome"
        private const val CALCULATOR_RECIPE_ID = "calculator"
        private val rideRecipeAppLabels = mapOf(
            "uber_ride" to "Uber",
            "ola_ride" to "Ola",
            "rapido_ride" to "Rapido",
        )
        private const val PLAN_CONFIRMATION_TIMEOUT_MS = 12_000L
        private const val STEP_CONFIRMATION_TIMEOUT_MS = 12_000L
        private const val PANEL_DISMISS_BEFORE_RECIPE_MS = 180L
        private const val PROGRESS_FINISH_DISPLAY_MS = 1_400L
        private const val MAX_CONFIRMATION_LABEL = 42
    }
}

private class ChromeOmniboxFlightActionPerformer(
    private val delegate: ActionPerformer,
    private val flightDriver: BuddyFlightDriver,
    private val snapshotProvider: suspend () -> GroundingSnapshot,
    private val provenance: ToolProvenance?,
) : ActionPerformer {

    override val capabilities: Set<ActionCapability>
        get() = delegate.capabilities

    override suspend fun tap(target: TapTarget, sourceTrust: SourceTrust): PerformResult =
        delegate.tap(target, sourceTrust)

    override suspend fun longPress(target: TapTarget, sourceTrust: SourceTrust): PerformResult =
        delegate.longPress(target, sourceTrust)

    override suspend fun scroll(
        direction: ScrollDirection,
        target: TapTarget?,
        sourceTrust: SourceTrust,
    ): PerformResult =
        delegate.scroll(direction, target, sourceTrust)

    override suspend fun typeText(
        target: TapTarget,
        text: String,
        sourceTrust: SourceTrust,
    ): PerformResult {
        val node = target as? TapTarget.AtNode
            ?: return delegate.typeText(target, text, sourceTrust)
        if (!node.isChromeOmniboxTarget()) {
            return delegate.typeText(target, text, sourceTrust)
        }

        val grounding = snapshotProvider().withChromeToolContext(node)
        val landed = flightDriver.flyToAndType(
            spec = AssistantMarkupParser.SemanticPoint(viewId = CHROME_OMNIBOX_VIEW_ID_SUFFIX),
            text = text,
            bubbleLabel = "Typing in Chrome",
            targetLabel = "Chrome address bar",
            fallbackMarks = grounding.panelSnapshot?.marks.orEmpty(),
            groundingSnapshot = grounding,
            provenance = provenance,
            defaultSourceTrust = sourceTrust,
        )
        return if (landed) PerformResult.Ok else PerformResult.Failed("chrome-omnibox-flight-failed")
    }

    private fun TapTarget.AtNode.isChromeOmniboxTarget(): Boolean {
        val viewIdSuffix = viewId?.substringAfterLast('/') ?: return false
        return viewIdSuffix.equals(CHROME_OMNIBOX_VIEW_ID_SUFFIX, ignoreCase = true) &&
            expectedPackage.equals(CHROME_PACKAGE, ignoreCase = true)
    }

    private fun GroundingSnapshot.withChromeToolContext(node: TapTarget.AtNode): GroundingSnapshot {
        val packageName = screenText?.packageName
            ?: node.expectedPackage
            ?: toolContext.packageName
        if (packageName.equals(toolContext.packageName, ignoreCase = true)) return this
        return copy(
            toolContext = toolContext.copy(
                packageName = packageName,
                appLabel = if (packageName.equals(CHROME_PACKAGE, ignoreCase = true)) {
                    "Chrome"
                } else {
                    packageName
                },
                umbrellaSiteLabel = null,
            ),
        )
    }

    private companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
        const val CHROME_OMNIBOX_VIEW_ID_SUFFIX = "url_bar"
    }
}
