package com.handy.runtime.agent.verifiers

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeRegistry
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.RecipeTarget
import com.handy.core.agent.UserGoal
import com.handy.core.agent.VerificationResult
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import com.handy.runtime.agent.recipes.GmailRecipe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResultVerifierTest {

    @Test fun `tap package changed verifier detects launched app`() = runTest {
        val step = RecipeStep(
            id = "open-app",
            title = "Open Spotify",
            command = RecipeCommand.NativeAction(AssistantAction.OpenApp("com.spotify.music")),
        )

        val result = TapPackageChangedVerifier.verify(
            step = step,
            snapshotBefore = snapshot("com.handy.android"),
            snapshotAfter = snapshot("com.spotify.music"),
        )

        assertThat(result).isEqualTo(VerificationResult.Verified)
    }

    @Test fun `text field filled verifier detects typed text growth`() = runTest {
        val step = RecipeStep(
            id = "type-query",
            title = "Type query",
            command = RecipeCommand.TypeText(
                target = RecipeTarget.Node(viewId = "search", role = "textfield"),
                text = "cotton kurti",
            ),
        )

        val result = TextFieldFilledVerifier.verify(
            step = step,
            snapshotBefore = snapshot(
                packageName = "com.meesho.supply",
                marks = listOf(mark(viewId = "search", text = "", role = "textfield", editable = true)),
            ),
            snapshotAfter = snapshot(
                packageName = "com.meesho.supply",
                marks = listOf(mark(viewId = "search", text = "cotton kurti", role = "textfield", editable = true)),
            ),
        )

        assertThat(result).isEqualTo(VerificationResult.Verified)
    }

    @Test fun `intent launched verifier accepts clock foreground transition`() = runTest {
        val step = RecipeStep(
            id = "set-alarm-intent",
            title = "Set alarm",
            command = RecipeCommand.NativeAction(AssistantAction.SetAlarm(hour = 7, minute = 0)),
        )

        val result = IntentLaunchedVerifier.verify(
            step = step,
            snapshotBefore = snapshot("com.handy.android"),
            snapshotAfter = snapshot("com.google.android.deskclock"),
        )

        assertThat(result).isEqualTo(VerificationResult.Verified)
    }

    @Test fun `screen changed verifier checks root or tree hash`() = runTest {
        val result = ScreenChangedVerifier.verify(
            step = RecipeStep(
                id = "tap",
                title = "Tap Continue",
                command = RecipeCommand.Tap(RecipeTarget.Node(text = "Continue", role = "button")),
            ),
            snapshotBefore = snapshot("com.example", treeHash = "before"),
            snapshotAfter = snapshot("com.example", treeHash = "after"),
        )

        assertThat(result).isEqualTo(VerificationResult.Verified)
    }

    @Test fun `gmail open draft verifies mailto launch and send step keeps strong hold`() = runTest {
        val proposal = RecipeRegistry(listOf(GmailRecipe)).propose(
            goal = UserGoal.fromAssistantText(
                "I can do that.\n[INTENT:draft_gmail]\n" +
                    "use recipe gmail_compose with args {\"to\":\"maya@example.com\",\"body\":\"On my way\",\"sendDesc\":\"Send\"}",
            ),
            grounding = snapshot("com.handy.android"),
        )
        assertThat(proposal).isInstanceOf(RecipeProposal.Proposed::class.java)
        val plan = (proposal as RecipeProposal.Proposed).plan
        val openDraft = plan.steps.first()
        val sendStep = plan.steps.last()

        assertThat(IntentLaunchedVerifier.verify(openDraft, snapshot("com.handy.android"), snapshot("com.google.android.gm")))
            .isEqualTo(VerificationResult.Verified)
        assertThat(sendStep.id).isEqualTo("send")
        assertThat(sendStep.confirmationOverride).isEqualTo(ConfirmationLevel.STRONG_HOLD)
        assertThat(sendStep.sensitive).isTrue()
    }

    private fun snapshot(
        packageName: String,
        marks: List<AccessibilityMark> = emptyList(),
        treeHash: String = "tree",
        rootHash: String = "root",
    ): GroundingSnapshot {
        val toolContext = ToolContext(packageName = packageName, appLabel = packageName)
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
                    scrollable = mark.scrollable,
                    enabled = mark.enabled,
                )
            },
        )
        return GroundingSnapshot(
            requestId = "verifier-test",
            source = TurnSource.TEST,
            toolContext = toolContext,
            panelSnapshot = PanelSnapshot(
                toolContext = toolContext,
                capturedAtEpochMs = 1L,
                marks = marks,
            ),
            screenText = ScreenTextSnapshot(
                packageName = packageName,
                timestampEpochMs = 1L,
                root = root,
            ),
            windowId = 1,
            windowBounds = IntRect(0, 0, 1080, 2400),
            rootBoundsHash = rootHash,
            treeHash = treeHash,
        )
    }

    private fun mark(
        viewId: String,
        text: String?,
        role: String,
        editable: Boolean = false,
    ): AccessibilityMark =
        AccessibilityMark(
            markId = viewId,
            text = text,
            viewIdSuffix = viewId,
            role = role,
            bounds = intArrayOf(0, 0, 200, 100),
            clickable = true,
            editable = editable,
        )
}

