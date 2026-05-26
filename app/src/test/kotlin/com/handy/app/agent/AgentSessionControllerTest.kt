package com.handy.app.agent

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
import com.handy.core.llm.ToolProvenance
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.intent.AndroidIntentDispatcher
import com.handy.runtime.intent.LaunchableAppIndex
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentSessionControllerTest {

    @Test fun `recipe step with untrusted provenance is refused before execution`() = runTest {
        val presenter = mockk<OverlayPresenter>()
        every { presenter.onError(any()) } just runs
        val policy = RecordingPolicy()
        val controller = AgentSessionController(
            presenter = presenter,
            screenContextBuilder = mockk<ScreenContextBuilder>(relaxed = true),
            actionPerformer = mockk<ActionPerformer>(relaxed = true),
            policyEngine = policy,
            intentDispatcher = mockk<AndroidIntentDispatcher>(relaxed = true),
            launchableAppIndex = mockk<LaunchableAppIndex>(relaxed = true),
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = mockk<SpeechOutputController>(relaxed = true),
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

    private fun grounding(): GroundingSnapshot {
        val toolContext = ToolContext(packageName = "com.example.app", appLabel = "Example")
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
}
