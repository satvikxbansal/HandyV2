package com.handy.app.overlay

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.handy.app.R
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.OverlayPanelState
import com.handy.core.overlay.PanelContent
import com.handy.app.theme.HandMarkIcon
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyGlassBottomSheet
import com.handy.app.theme.HandyType

/**
 * Overlay chat panel Compose tree. Glassmorphism per cursorbuddy
 * recipe #8, IME choreography per recipe #5 (DL-027: full-screen overlay
 * + `imePadding()`, no auto-focus), quick-prompt chips per recipe #9.
 */
@Composable
fun OverlayChatPanelContent(
    state: OverlayPanelState,
    callbacks: OverlayPanelCallbacks,
    modifier: Modifier = Modifier,
) {
    if (!state.isPanelVisible) return
    val panel: PanelContent = state.panel
    val appLabel = panel.snapshot?.toolContext?.appLabel

    val focusRequester = remember { FocusRequester() }
    var draft by remember { mutableStateOf(panel.draftInput) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                },
                onClick = callbacks.onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .clickable(
                    indication = null,
                    interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    },
                    onClick = {},
                ),
        ) {
            HandyGlassBottomSheet(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(HandyDimens.StackL),
            ) {
                PanelHeader(
                    greeting = panel.greeting,
                    appLabel = appLabel,
                    onDismiss = callbacks.onDismiss,
                    onExpand = callbacks.onExpand,
                )

                panel.errorBanner?.let { errorBanner ->
                    ErrorChip(message = errorBanner, onDismiss = callbacks.onDismissError)
                }

                panel.pendingConfirmation?.let { pending ->
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
                    val chips = panel.quickPrompts.take(2)
                    if (chips.isNotEmpty()) {
                        QuickPromptsRow(
                            prompts = chips,
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

                state.bubble?.let { bubble -> BubbleFooter(bubble) }
            }
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
    appLabel: String?,
    onDismiss: () -> Unit,
    onExpand: () -> Unit,
) {
    // Spec (`handy-overlay.jsx`): gap 12dp between title block and
    // trailing icons; title row internal gap 10dp between hand and
    // "Handy"; subtitle marginTop 4dp, single line, ellipsis.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackS + 2.dp), // 10dp
            ) {
                HandMarkIcon(size = 24.dp, tint = HandyColors.Accent)
                Text(
                    text = "Handy",
                    style = HandyType.TitleMedium,
                    color = HandyColors.TextPrimary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = greetingWithLabelAccent(greeting, appLabel),
                style = HandyType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BareIconButton(
            iconRes = R.drawable.ic_expand,
            description = "Expand to full chat",
            onClick = onExpand,
        )
        BareIconButton(
            iconRes = R.drawable.ic_close,
            description = "Dismiss",
            onClick = onDismiss,
        )
    }
}

/**
 * Renders [greeting] in muted grey, with any case-insensitive match of
 * [appLabel] re-coloured to [HandyColors.Accent]. Matches the design
 * screenshots where only the app-label word is amber; the surrounding
 * copy ("I see", "On", "Browsing in", punctuation) stays muted.
 *
 * When [appLabel] is blank, missing, or "Handy" (our own app label in
 * launcher foreground), the whole greeting is muted.
 */
internal fun greetingWithLabelAccent(
    greeting: String,
    appLabel: String?,
): AnnotatedString {
    val label = appLabel?.trim().orEmpty()
    val shouldAccent = label.isNotEmpty() && !label.equals("Handy", ignoreCase = true)
    if (!shouldAccent) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = HandyColors.TextSecondary)) {
                append(greeting)
            }
        }
    }
    val index = greeting.indexOf(label, ignoreCase = true)
    if (index < 0) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = HandyColors.TextSecondary)) {
                append(greeting)
            }
        }
    }
    val end = index + label.length
    return buildAnnotatedString {
        if (index > 0) {
            withStyle(SpanStyle(color = HandyColors.TextSecondary)) {
                append(greeting.substring(0, index))
            }
        }
        // Spec (`handy-overlay.jsx`): accented host label renders in
        // Accent with fontWeight 500 (Medium), not the default body
        // weight.
        withStyle(
            SpanStyle(
                color = HandyColors.Accent,
                fontWeight = FontWeight.Medium,
            ),
        ) {
            append(greeting.substring(index, end))
        }
        if (end < greeting.length) {
            withStyle(SpanStyle(color = HandyColors.TextSecondary)) {
                append(greeting.substring(end))
            }
        }
    }
}

@Composable
private fun BareIconButton(
    @DrawableRes iconRes: Int,
    description: String,
    onClick: () -> Unit,
) {
    // Spec (`handy-overlay.jsx` `BareIconBtn`): 28x28 square hit
    // target, radius 8, transparent bg, 16dp icon, 0.75 opacity.
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(HandyDimens.RadiusSm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = HandyColors.TextSecondary.copy(alpha = 0.75f),
            modifier = Modifier.size(16.dp),
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
    // Spec (`handy-overlay.jsx` `InputRow`): gap 8dp; mic + send =
    // 40dp circles; text field 40dp with RadiusPill, 16dp horizontal
    // padding, placeholder in TextMuted, field border → Accent when
    // focused. Send icon 17dp AccentInk, shadow `0 6 14 -4 accent@88`.
    var fieldFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackS),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(HandyColors.ChipBg, CircleShape)
                .border(0.5.dp, HandyColors.ChipBorder, CircleShape)
                .clickable(onClick = onVoiceStart),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Start voice",
                tint = HandyColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .background(HandyColors.ChipBg, RoundedCornerShape(HandyDimens.RadiusPill))
                .border(
                    0.5.dp,
                    if (fieldFocused) HandyColors.Accent else HandyColors.ChipBorder,
                    RoundedCornerShape(HandyDimens.RadiusPill),
                )
                .padding(horizontal = HandyDimens.Gutter),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) {
                Text(
                    text = "Ask me anything…",
                    style = HandyType.Body,
                    color = HandyColors.TextMuted,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = HandyType.Body.copy(color = HandyColors.TextPrimary),
                cursorBrush = SolidColor(HandyColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { fieldFocused = it.isFocused },
            )
        }

        val sendEnabled = draft.isNotBlank()
        val sendBg = if (sendEnabled) {
            Modifier.background(HandyColors.Accent, CircleShape)
        } else {
            Modifier
                .background(HandyColors.ChipBg, CircleShape)
                .border(0.5.dp, HandyColors.ChipBorder, CircleShape)
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(sendBg)
                .clickable(enabled = sendEnabled, onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = "Send",
                tint = if (sendEnabled) HandyColors.AccentInk else HandyColors.TextMuted,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun QuickPromptsRow(
    prompts: List<String>,
    onPick: (String) -> Unit,
) {
    // Spec (`handy-overlay.jsx` `QuickChip`): padding 7dp vertical /
    // 12dp horizontal, RadiusPill, ChipBg + 0.5dp ChipBorder, 12sp
    // Medium (500) label in TextPrimary. Wraps to next line at 10dp
    // gap; we use horizontalScroll as a pragmatic substitute since
    // the panel is at most 2 chips wide in practice.
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        prompts.forEach { prompt ->
            Box(
                modifier = Modifier
                    .background(
                        HandyColors.ChipBg,
                        RoundedCornerShape(HandyDimens.RadiusPill),
                    )
                    .border(
                        0.5.dp,
                        HandyColors.ChipBorder,
                        RoundedCornerShape(HandyDimens.RadiusPill),
                    )
                    .clickable { onPick(prompt) }
                    .padding(horizontal = HandyDimens.StackM, vertical = 7.dp),
            ) {
                Text(
                    text = prompt,
                    style = HandyType.CaptionSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = HandyColors.TextPrimary,
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
            .background(HandyColors.AccentSoft, RoundedCornerShape(HandyDimens.RadiusXl))
            .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(HandyDimens.RadiusXl))
            .padding(PaddingValues(horizontal = HandyDimens.RowPad, vertical = HandyDimens.StackM)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(HandyColors.Danger.copy(alpha = 0.18f), CircleShape)
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Stop listening",
                tint = HandyColors.Danger,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = partial.ifBlank { "Listening…" },
            style = HandyType.Body,
            color = HandyColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StreamingRow(loadingVerb: String, accumulated: String) {
    Column(verticalArrangement = Arrangement.spacedBy(HandyDimens.StackS)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            CircularProgressIndicator(
                color = HandyColors.Accent,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = loadingVerb,
                style = HandyType.CaptionSmall,
                color = HandyColors.TextSecondary,
            )
        }
        if (accumulated.isNotBlank()) {
            Text(
                text = accumulated,
                style = HandyType.Body,
                color = HandyColors.TextPrimary,
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
                HandyColors.BubbleResponse.copy(alpha = 0.18f),
                RoundedCornerShape(HandyDimens.RadiusXl),
            )
            .border(
                0.5.dp,
                HandyColors.BubbleResponse.copy(alpha = 0.35f),
                RoundedCornerShape(HandyDimens.RadiusXl),
            )
            .padding(horizontal = HandyDimens.RowPad, vertical = HandyDimens.StackM),
    ) {
        Text(
            text = text,
            style = HandyType.CaptionSmall,
            color = HandyColors.TextPrimary,
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
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(HandyDimens.RadiusMd))
            .padding(horizontal = HandyDimens.StackM, vertical = HandyDimens.StackS),
    ) {
        Text(
            text = text,
            style = HandyType.CaptionSmall,
            color = HandyColors.TextPrimary,
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
            .background(HandyColors.Danger.copy(alpha = 0.18f), RoundedCornerShape(HandyDimens.RadiusLg))
            .padding(horizontal = HandyDimens.StackM, vertical = HandyDimens.StackM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        Text(
            text = message,
            style = HandyType.CaptionSmall,
            color = HandyColors.Danger,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(HandyColors.Danger.copy(alpha = 0.2f), CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Dismiss error",
                tint = HandyColors.Danger,
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
            .background(HandyColors.BubbleAction.copy(alpha = 0.18f), RoundedCornerShape(HandyDimens.RadiusXl))
            .border(
                0.5.dp,
                HandyColors.BubbleAction.copy(alpha = 0.35f),
                RoundedCornerShape(HandyDimens.RadiusXl),
            )
            .padding(horizontal = HandyDimens.RowPad, vertical = HandyDimens.StackM),
        verticalArrangement = Arrangement.spacedBy(HandyDimens.StackS),
    ) {
        Text(
            text = reason,
            style = HandyType.Body,
            color = HandyColors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM)) {
            Box(
                modifier = Modifier
                    .background(HandyColors.BubbleAction, RoundedCornerShape(HandyDimens.RadiusMd))
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = HandyDimens.RowPad, vertical = HandyDimens.StackS),
            ) {
                Text(
                    "Continue",
                    style = HandyType.CaptionSmall,
                    color = HandyColors.PageBg,
                )
            }
            Box(
                modifier = Modifier
                    .background(HandyColors.ChipBg, RoundedCornerShape(HandyDimens.RadiusMd))
                    .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(HandyDimens.RadiusMd))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = HandyDimens.RowPad, vertical = HandyDimens.StackS),
            ) {
                Text(
                    "Cancel",
                    style = HandyType.CaptionSmall,
                    color = HandyColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
