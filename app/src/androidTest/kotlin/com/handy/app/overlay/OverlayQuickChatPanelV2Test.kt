package com.handy.app.overlay

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.handy.app.overlay.design.OverlayQuickChatPanelV2
import com.handy.core.overlay.OverlayMode
import com.handy.core.overlay.OverlayPanelState
import com.handy.core.overlay.PanelContent
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.tool.ToolContext
import org.junit.Rule
import org.junit.Test

class OverlayQuickChatPanelV2Test {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun quickChatPanelRendersPresenterGreetingInsteadOfGenericContextLine() {
        compose.setContent {
            OverlayQuickChatPanelV2(
                state = OverlayPanelState(
                    mode = OverlayMode.ChatPanel,
                    panel = PanelContent(
                        snapshot = PanelSnapshot(
                            toolContext = ToolContext(
                                packageName = "com.netflix.mediaclient",
                                appLabel = "Netflix",
                            ),
                            capturedAtEpochMs = 0L,
                        ),
                        greeting = "In Netflix. End the scroll. Pick a winner?",
                    ),
                ),
                callbacks = OverlayPanelCallbacks(
                    onDismiss = {},
                    onExpand = {},
                    onSend = {},
                    onVoiceStart = {},
                    onVoiceStop = {},
                    onConfirm = { _, _ -> },
                    onDismissError = {},
                ),
                backdropSnapshot = null,
                isBackdropBlurAvailable = false,
            )
        }

        compose.onNodeWithText("In Netflix. End the scroll. Pick a winner?")
            .assertIsDisplayed()
        compose.onAllNodesWithText("In Netflix. What can I help you with?")
            .assertCountEquals(0)
    }

    @Test
    fun quickChatPanelRendersRefreshPreviewInContextLineWithoutStaleLabel() {
        val previewGreeting = "In YouTube. Summarise or pick what's next?"
        compose.setContent {
            OverlayQuickChatPanelV2(
                state = OverlayPanelState(
                    mode = OverlayMode.ChatPanel,
                    panel = PanelContent(
                        snapshot = PanelSnapshot(
                            toolContext = ToolContext(
                                packageName = "com.google.android.apps.photos",
                                appLabel = "Photos",
                            ),
                            capturedAtEpochMs = 0L,
                        ),
                        greeting = "In Photos. Describe a photo or find one?",
                        contextRefreshInProgress = true,
                        contextRefreshPreviewGreeting = previewGreeting,
                        contextRefreshPreviewLabel = "YouTube",
                    ),
                ),
                callbacks = OverlayPanelCallbacks(
                    onDismiss = {},
                    onExpand = {},
                    onSend = {},
                    onVoiceStart = {},
                    onVoiceStop = {},
                    onConfirm = { _, _ -> },
                    onDismissError = {},
                ),
                backdropSnapshot = null,
                isBackdropBlurAvailable = false,
            )
        }

        compose.onNodeWithContentDescription(previewGreeting)
            .assertIsDisplayed()
        compose.onAllNodesWithText("In Photos. Describe a photo or find one?")
            .assertCountEquals(0)
    }
}
