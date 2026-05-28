package com.handy.app.agent

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.handy.app.overlay.AgentProgressBubbleState
import com.handy.app.overlay.BuddyFlightDriver
import com.handy.app.overlay.OverlayPresenter
import com.handy.app.screen.ScreenContextBuilder
import com.handy.app.voice.SpeechOutputController
import com.handy.core.action.ActionPerformer
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionRisk
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PolicyDecision
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.action.ScrollDirection
import com.handy.core.agent.ResultVerifier
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeStep
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditStore
import com.handy.core.llm.ToolProvenance
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.intent.AndroidIntentDispatcher
import com.handy.runtime.intent.LaunchableAppIndex
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentSessionControllerTest {

    @Test fun `answer-only calculator recipe can respond to arithmetic question without execution verb`() = runTest {
        val presenter = mockk<OverlayPresenter>()
        every { presenter.onStreamingStart() } just runs
        every { presenter.onResponseFinalized("1035", "1035") } just runs
        val controller = AgentSessionController(
            context = mockk<Context>(relaxed = true),
            presenter = presenter,
            screenContextBuilder = mockk<ScreenContextBuilder>(relaxed = true),
            actionPerformer = mockk<ActionPerformer>(relaxed = true),
            policyEngine = RecordingPolicy(),
            intentDispatcher = mockk<AndroidIntentDispatcher>(relaxed = true),
            launchableAppIndex = mockk<LaunchableAppIndex>(relaxed = true),
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = mockk<SpeechOutputController>(relaxed = true),
            auditStore = InMemoryAuditStore(),
            resultVerifier = ResultVerifier.Default,
        )

        val handled = controller.runIfRecipeRequested(
            assistantText = """[INTENT:calculate]
                |use recipe calculate with args {"expression":"23% of 4500"}
            """.trimMargin(),
            userText = "What's 23% of 4500?",
            initialGrounding = grounding(),
            source = TurnSource.OVERLAY_PANEL,
            toolContext = ToolContext(packageName = "com.example.app", appLabel = "Example"),
        )

        assertThat(handled).isTrue()
        verify { presenter.onResponseFinalized("1035", "1035") }
    }

    @Test fun `recipe step with untrusted provenance is refused before execution`() = runTest {
        val presenter = mockk<OverlayPresenter>()
        every { presenter.onBlockedBubble(any()) } just runs
        every { presenter.onError(any()) } just runs
        val policy = RecordingPolicy()
        val controller = AgentSessionController(
            context = mockk<Context>(relaxed = true),
            presenter = presenter,
            screenContextBuilder = mockk<ScreenContextBuilder>(relaxed = true),
            actionPerformer = mockk<ActionPerformer>(relaxed = true),
            policyEngine = policy,
            intentDispatcher = mockk<AndroidIntentDispatcher>(relaxed = true),
            launchableAppIndex = mockk<LaunchableAppIndex>(relaxed = true),
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = mockk<SpeechOutputController>(relaxed = true),
            auditStore = InMemoryAuditStore(),
            resultVerifier = ResultVerifier.Default,
        )

        val handled = controller.runIfRecipeRequested(
            assistantText = """use recipe tap_visible with args {"label":"Continue","role":"button"}""",
            userText = "tap continue",
            initialGrounding = grounding(),
            source = TurnSource.OVERLAY_PANEL,
            toolContext = ToolContext(packageName = "com.example.app", appLabel = "Example"),
            provenance = ToolProvenance(
                turnId = "turn-a",
                usedUntrustedTools = setOf("fetch_page"),
            ),
        )

        assertThat(handled).isTrue()
        assertThat(policy.sourceTrusts).containsExactly(SourceTrust.UNTRUSTED_TOOL)
        assertThat(controller.progress.value).isEqualTo(
            AgentProgressBubbleState(
                visible = true,
                title = "Recipe stopped",
                detail = "policy refused Tap Continue: tool-suggestion-only",
            ),
        )
    }

    @Test fun `recipe package change shows foreground privacy stop bubble`() = runTest {
        val presenter = mockk<OverlayPresenter>(relaxed = true)
        coEvery {
            presenter.requestTapForMeConfirmation(any(), any(), any(), any(), any(), any(), any())
        } returns true
        val screenContextBuilder = mockk<ScreenContextBuilder>()
        coEvery {
            screenContextBuilder.build(any(), any(), any(), any(), any(), any())
        } returns grounding(packageName = "com.other.app", appLabel = "Other")
        val controller = AgentSessionController(
            context = mockk<Context>(relaxed = true),
            presenter = presenter,
            screenContextBuilder = screenContextBuilder,
            actionPerformer = mockk<ActionPerformer>(relaxed = true),
            policyEngine = RecordingPolicy(),
            intentDispatcher = mockk<AndroidIntentDispatcher>(relaxed = true),
            launchableAppIndex = mockk<LaunchableAppIndex>(relaxed = true),
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = mockk<SpeechOutputController>(relaxed = true),
            auditStore = InMemoryAuditStore(),
            resultVerifier = ResultVerifier.Default,
        )

        val handled = controller.runIfRecipeRequested(
            assistantText = """use recipe tap_visible with args {"label":"Continue","role":"button"}""",
            userText = "tap continue",
            initialGrounding = grounding(),
            source = TurnSource.OVERLAY_PANEL,
            toolContext = ToolContext(packageName = "com.example.app", appLabel = "Example"),
        )

        assertThat(handled).isTrue()
        assertThat(controller.progress.value).isEqualTo(AgentProgressBubbleState.Hidden)
        verify(exactly = 1) { presenter.onForegroundPrivacyStopBubble() }
        verify(exactly = 0) { presenter.onError(any()) }
    }

    @Test fun `plan preview contains recipe metadata and first three visible steps`() {
        val plan = RecipePlan(
            recipeId = "alarm",
            displayName = "Set alarm",
            packageName = "com.google.android.deskclock",
            appLabel = "Clock",
            summary = "Set an alarm for 7 AM",
            steps = (1..5).map { index ->
                RecipeStep(
                    id = "step-$index",
                    title = "Step $index",
                    command = RecipeCommand.Scroll(ScrollDirection.DOWN),
                    sensitive = index == 2,
                )
            },
        )

        val preview = plan.toPlanPreview()

        assertThat(preview.recipeId).isEqualTo("alarm")
        assertThat(preview.recipeDisplayName).isEqualTo("Set alarm")
        assertThat(preview.totalStepCount).isEqualTo(5)
        assertThat(preview.steps.map { it.title }).containsExactly("Step 1", "Step 2", "Step 3").inOrder()
        assertThat(preview.steps.map { it.index }).containsExactly(1, 2, 3).inOrder()
        assertThat(preview.steps.map { it.isSensitive }).containsExactly(false, true, false).inOrder()
    }

    private class RecordingPolicy : ActionPolicyEngine {
        val sourceTrusts = mutableListOf<SourceTrust>()

        override fun decide(
            action: AssistantAction,
            target: TapTarget?,
            grounding: GroundingSnapshot,
            sourceTrust: SourceTrust,
        ): PolicyDecision {
            sourceTrusts += sourceTrust
            return if (sourceTrust == SourceTrust.UNTRUSTED_TOOL) {
                PolicyDecision(
                    allowed = false,
                    risk = ActionRisk.HIGH,
                    confirmation = ConfirmationLevel.NONE,
                    requireFreshSnapshot = false,
                    requireNodeActionOnly = false,
                    allowGestureFallback = false,
                    reason = "tool-suggestion-only",
                )
            } else {
                PolicyDecision(
                    allowed = true,
                    risk = ActionRisk.MEDIUM,
                    confirmation = ConfirmationLevel.NORMAL,
                    requireFreshSnapshot = false,
                    requireNodeActionOnly = false,
                    allowGestureFallback = false,
                    reason = null,
                )
            }
        }
    }

    private fun grounding(
        packageName: String = "com.example.app",
        appLabel: String = "Example",
    ): GroundingSnapshot {
        val toolContext = ToolContext(packageName = packageName, appLabel = appLabel)
        return GroundingSnapshot(
            requestId = "agent-test",
            source = TurnSource.TEST,
            toolContext = toolContext,
            panelSnapshot = PanelSnapshot(
                toolContext = toolContext,
                capturedAtEpochMs = 1L,
                marks = listOf(
                    AccessibilityMark(
                        markId = "m1",
                        role = "button",
                        text = "Continue",
                        bounds = intArrayOf(0, 0, 100, 50),
                        clickable = true,
                    ),
                ),
            ),
        )
    }

    private class InMemoryAuditStore : AuditStore {
        override suspend fun append(event: AuditEvent) = Unit
        override suspend fun recent(limit: Int): List<AuditEvent> = emptyList()
        override fun observe(limit: Int): Flow<List<AuditEvent>> = flowOf(emptyList())
    }
}
