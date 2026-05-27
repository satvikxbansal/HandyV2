package com.handy.app.accessibility

import com.google.common.truth.Truth.assertThat
import com.handy.app.overlay.OverlayPresenter
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionRisk
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PerformResult
import com.handy.core.action.PolicyDecision
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.action.UiActionKind
import com.handy.core.screen.GroundingSnapshot
import com.handy.runtime.accessibility.LiveScreenGuard
import com.handy.runtime.storage.LearnedAllowlistStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PolicyGuardedActionPerformerSemanticTest {

    @Test fun `tap guard passes semantic ui action and caller source trust to policy`() = runTest {
        val policy = CapturingPolicy()
        val liveScreenGuard = mockk<LiveScreenGuard>()
        coEvery { liveScreenGuard.snapshot() } returns LiveScreenGuard.LiveScreen(
            packageName = "com.google.android.gm",
            windowId = 7,
            rootBoundsHash = "root",
            treeHash = "tree",
        )
        val performer = PolicyGuardedActionPerformer(
            delegate = mockk<SwitchingActionPerformer>(relaxed = true),
            policyEngine = policy,
            liveScreenGuard = liveScreenGuard,
            learnedAllowlistStore = mockk<LearnedAllowlistStore>(relaxed = true),
            presenter = mockk<OverlayPresenter>(relaxed = true),
        )

        val result = performer.tap(
            target = TapTarget.AtNode(
                markId = "m12",
                role = "button",
                text = "Send",
                viewId = "send_button",
                desc = "Send message",
                expectedPackage = "com.google.android.gm",
                expectedWindowId = 7,
                snapshotHash = "root",
            ),
            sourceTrust = SourceTrust.UNTRUSTED_TOOL,
        )

        assertThat(result).isInstanceOf(PerformResult.Failed::class.java)
        val action = policy.action as AssistantAction.UiAction
        assertThat(action.kind).isEqualTo(UiActionKind.TAP)
        assertThat(action.targetLabel).isEqualTo("Send")
        assertThat(action.targetRole).isEqualTo("button")
        assertThat(action.targetMarkId).isEqualTo("m12")
        assertThat(action.targetViewId).isEqualTo("send_button")
        assertThat(action.proposedPackage).isEqualTo("com.google.android.gm")
        assertThat(policy.sourceTrust).isEqualTo(SourceTrust.UNTRUSTED_TOOL)
    }

    private class CapturingPolicy : ActionPolicyEngine {
        lateinit var action: AssistantAction
        lateinit var sourceTrust: SourceTrust

        override fun decide(
            action: AssistantAction,
            target: TapTarget?,
            grounding: GroundingSnapshot,
            sourceTrust: SourceTrust,
        ): PolicyDecision {
            this.action = action
            this.sourceTrust = sourceTrust
            return PolicyDecision(
                allowed = false,
                risk = ActionRisk.HIGH,
                confirmation = ConfirmationLevel.NONE,
                requireFreshSnapshot = false,
                requireNodeActionOnly = false,
                allowGestureFallback = false,
                reason = "captured",
            )
        }
    }
}
