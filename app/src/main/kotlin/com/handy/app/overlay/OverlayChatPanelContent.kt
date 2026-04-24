package com.handy.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.OverlayPanelState
import com.handy.core.overlay.PanelContent

/**
 * Overlay chat panel Compose tree. Glassmorphism per cursorbuddy
 * recipe #8, IME choreography per recipe #5, quick-prompt chips per
 * recipe #9, voice auto-submit per recipe #6.
 *
 * The panel is intentionally stateless-per-frame — it reads
 * [OverlayPanelState] and fires [OverlayPanelCallbacks]. The
 * [OverlayPresenter] owns the real state; the widget service owns the
 * WindowManager and focus-flag choreography.
 */
@Composable
fun OverlayChatPanelContent(
    state: OverlayPanelState,
    callbacks: OverlayPanelCallbacks,
    modifier: Modifier = Modifier,
) {
    if (!state.isPanelVisible) return
    val panel: PanelContent = state.panel

    val focusRequester = remember { FocusRequester() }
    var draft by remember { mutableStateOf(panel.draftInput) }

    // NOTE: we deliberately do NOT auto-request focus on the input
    // field when the panel opens. Auto-focusing triggers the IME the
    // moment the user taps the widget, which is surprising and hides
    // the panel content behind the keyboard. The text field is still
    // focusable — a manual tap on "Ask me anything…" shows the IME,
    // at which point `Modifier.imePadding()` on the panel lifts it
    // above the keyboard. (DL-027.)

    // Panel docked to the bottom of a full-screen transparent overlay.
    // `imePadding()` on the panel Column reads `WindowInsets.ime` from
    // the ComposeView — which the full-screen overlay reliably
    // propagates (unlike WRAP_CONTENT overlays which silently drop
    // IME insets on stock Android). Tapping the transparent top area
    // dismisses the panel (modal-sheet semantics). DL-027.
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = callbacks.onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .clickable(
                    // Consume clicks on the panel itself so the
                    // backdrop's onDismiss doesn't fire when the user
                    // interacts with panel content.
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = {},
                )
                .glassSurface(cornerDp = 22)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        PanelHeader(
            greeting = panel.greeting,
            toolLabel = panel.snapshot?.toolContext?.displayLabel,
            onDismiss = callbacks.onDismiss,
            onExpand = callbacks.onExpand,
        )

        val errorBanner = panel.errorBanner
        if (errorBanner != null) {
            ErrorChip(message = errorBanner, onDismiss = callbacks.onDismissError)
        }

        val pending = panel.pendingConfirmation
        if (pending != null) {
            ConfirmationChip(
                reason = pending.reason,
                onConfirm = { callbacks.onConfirm(pending.id, true) },
                onCancel = { callbacks.onConfirm(pending.id, false) },
            )
        }

        if (panel.isListening) {
            ListeningRow(
                partial = panel.partialTranscript,
                onStop = callbacks.onVoiceStop,
            )
        } else if (panel.isStreaming) {
            StreamingRow(
                loadingVerb = panel.loadingVerb.ifBlank { "Thinking…" },
                accumulated = panel.streamingDelta,
            )
        } else {
            InputRow(
                draft = draft,
                onDraftChange = { draft = it },
                onSubmit = {
                    val submit = draft.trim()
                    if (submit.isNotEmpty()) {
                        callbacks.onSend(submit)
                        draft = ""
                    }
                },
                onVoiceStart = callbacks.onVoiceStart,
                focusRequester = focusRequester,
            )
            if (panel.quickPrompts.isNotEmpty()) {
                QuickPromptsRow(
                    prompts = panel.quickPrompts,
                    onPick = { prompt ->
                        callbacks.onSend(prompt)
                        draft = ""
                    },
                )
            }
        }

        val responsePreview = panel.recentResponsePreview
        if (responsePreview.isNotBlank() && !panel.isStreaming && !panel.isListening) {
            ResponsePreview(responsePreview)
        }

        // Mirror the live buddy bubble (yellow/teal/green/blue) into
        // the panel footer so the user sees the same text that's
        // floating next to the docked buddy.
        state.bubble?.let { bubble -> BubbleFooter(bubble) }
        }
    }
}

data class OverlayPanelCallbacks(
    val onDismiss: () -> Unit,
    val onExpand: () -> Unit,
    val onSend: (String) -> Unit,
    val onVoiceStart: () -> Unit,
    val onVoiceStop: () -> Unit,
    val onConfirm: (Long, Boolean) -> Unit,
    val onDismissError: () -> Unit,
)

/* ----- internal composables ----------------------------------------------- */

@Composable
private fun PanelHeader(
    greeting: String,
    @Suppress("UNUSED_PARAMETER") toolLabel: String?,
    onDismiss: () -> Unit,
    onExpand: () -> Unit,
) {
    // NB: `toolLabel` was previously rendered as a blue 11sp line above
    // the greeting, but `QuickPromptCatalog.greetingFor()` already
    // embeds the app label inside the greeting ("In Photos. What can
    // I help with?"). Rendering both was redundant — the blue label
    // repeated the same word the grey greeting already contained. The
    // parameter stays on the signature so callers don't churn, but is
    // deliberately unused.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Handy",
                color = Color(0xFFF2F4F8),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = greeting,
                color = Color(0xC9D3DDE5),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconSquare(Icons.Outlined.OpenInFull, "Expand to chat", onExpand)
        IconSquare(Icons.Outlined.Close, "Dismiss", onDismiss)
    }
}

@Composable
private fun IconSquare(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(GlassPalette.ChipGradient, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color(0xFFE6EEF5),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun InputRow(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceStart: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(GlassPalette.ChipGradient, CircleShape)
                .clickable(onClick = onVoiceStart),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = "Start voice",
                tint = GlassPalette.AccentBlue,
                modifier = Modifier.size(18.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .background(GlassPalette.DarkInputBg, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) {
                Text(
                    text = "Ask me anything…",
                    color = Color(0xB0C9D3DD),
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFFF2F4F8),
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(GlassPalette.AccentBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        val sendEnabled = draft.isNotBlank()
        val sendBg = if (sendEnabled) {
            Modifier.background(GlassPalette.AccentBlue, CircleShape)
        } else {
            Modifier.background(GlassPalette.ChipGradient, CircleShape)
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(sendBg)
                .clickable(enabled = sendEnabled, onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Send",
                tint = if (sendEnabled) Color.White else Color(0x80C9D3DD),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun QuickPromptsRow(
    prompts: List<String>,
    onPick: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        prompts.forEach { prompt ->
            Box(
                modifier = Modifier
                    .background(GlassPalette.ChipGradient, RoundedCornerShape(14.dp))
                    .clickable { onPick(prompt) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = prompt,
                    color = GlassPalette.AccentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ListeningRow(
    partial: String,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33FBBF24), RoundedCornerShape(18.dp))
            .padding(PaddingValues(horizontal = 12.dp, vertical = 10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(GlassPalette.DangerRed.copy(alpha = 0.18f), CircleShape)
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = "Stop listening",
                tint = GlassPalette.DangerRed,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = partial.ifBlank { "Listening…" },
            color = Color(0xFFF2F4F8),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StreamingRow(loadingVerb: String, accumulated: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                color = GlassPalette.AccentBlue,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = loadingVerb,
                color = Color(0xC9D3DDE5),
                fontSize = 12.sp,
            )
        }
        if (accumulated.isNotBlank()) {
            Text(
                text = accumulated,
                color = Color(0xFFF2F4F8),
                fontSize = 13.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResponsePreview(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                GlassPalette.GreenResponse.copy(alpha = 0.18f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFFF2F4F8),
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BubbleFooter(bubble: BuddyBubble) {
    val (color, text) = when (bubble) {
        is BuddyBubble.Transcript -> GlassPalette.YellowTranscript to bubble.text
        is BuddyBubble.Action -> GlassPalette.Teal to bubble.text
        is BuddyBubble.Response -> GlassPalette.GreenResponse to bubble.text
        is BuddyBubble.Navigation -> GlassPalette.BlueNavigation to bubble.text
    }
    if (text.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFFF2F4F8),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorChip(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassPalette.DangerRed.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            color = GlassPalette.DangerRed,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(GlassPalette.DangerRed.copy(alpha = 0.2f), CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Dismiss error",
                tint = GlassPalette.DangerRed,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ConfirmationChip(
    reason: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassPalette.Teal.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = reason,
            color = Color(0xFFF2F4F8),
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .background(GlassPalette.Teal, RoundedCornerShape(12.dp))
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("Continue", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .background(GlassPalette.ChipGradient, RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("Cancel", color = Color(0xFFC9D3DD), fontSize = 12.sp,
                    fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
