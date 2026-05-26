package com.handy.app.overlay.design

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.app.design.HandyWordmark
import com.handy.app.overlay.OverlayPanelCallbacks
import com.handy.app.overlay.PanelGreetingCategory
import com.handy.app.overlay.panelGreetingCategoryFor
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.OverlayPanelState

@Suppress("UNUSED_PARAMETER")
@Composable
fun OverlayQuickChatPanelV2(
    state: OverlayPanelState,
    callbacks: OverlayPanelCallbacks,
    backdropSnapshot: Bitmap?,
    isBackdropBlurAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    var lastVisibleState by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (state.isPanelVisible) {
            lastVisibleState = state
        }
    }
    val renderState = if (state.isPanelVisible) state else lastVisibleState
    if (!state.isPanelVisible && !lastVisibleState.isPanelVisible) return

    val panelVisible = state.isPanelVisible
    var panelEntered by remember { mutableStateOf(false) }
    LaunchedEffect(panelVisible) {
        panelEntered = panelVisible
    }
    val animatedOffset by animateDpAsState(
        targetValue = if (panelVisible && panelEntered) 0.dp else 400.dp,
        animationSpec = if (panelVisible) {
            spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            spring(
                dampingRatio = 1f,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "panel-offset",
    )

    val panel = renderState.panel
    val toolContext = panel.snapshot?.toolContext
    val context = LocalContext.current
    val view = LocalView.current
    val currentAppDisplayName = remember(toolContext?.packageName, toolContext?.appLabel) {
        resolveCurrentAppDisplayName(
            context = context,
            packageName = toolContext?.packageName,
            fallbackLabel = toolContext?.appLabel,
        )
    }
    val category = panelGreetingCategoryFor(
        packageName = toolContext?.packageName,
        siteLabel = toolContext?.umbrellaSiteLabel,
    )
    val focusRequester = remember { FocusRequester() }
    var draft by remember(panel.draftInput) { mutableStateOf(panel.draftInput) }
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val sheetInteractionSource = remember { MutableInteractionSource() }
    val sheetShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
    val panelSurface = if (isBackdropBlurAvailable) {
        HandyDesign.Colors.PanelSurface
    } else {
        HandyDesign.Colors.PanelSurface.copy(alpha = 1f)
    }
    val errorBanner = panel.errorBanner
    val pendingConfirmation = panel.pendingConfirmation
    val lowConfidenceTranscript = panel.lowConfidenceTranscript
    val showResponsePreview = panel.recentResponsePreview.isNotBlank() &&
        !panel.isStreaming &&
        !panel.isListening
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    LaunchedEffect(panelVisible, currentAppDisplayName) {
        if (panelVisible) {
            view.announceForAccessibility(
                "Handy quick chat opened in ${currentAppDisplayName ?: "this app"}",
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = scrimInteractionSource,
                    onClick = callbacks.onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .offset(y = animatedOffset),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxPanelHeight),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = sheetInteractionSource,
                            onClick = {},
                        )
                        .clip(sheetShape)
                        .border(
                            width = 0.5.dp,
                            color = HandyDesign.Colors.AccentHairline,
                            shape = sheetShape,
                        ),
                ) {
                    if (backdropSnapshot != null) {
                        PanelBackdrop(
                            snapshot = backdropSnapshot,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(panelSurface),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
                    ) {
                        DragHandleV2()
                        PanelHeaderV2(
                            onExpand = callbacks.onExpand,
                            onDismiss = callbacks.onDismiss,
                        )
                        ContextLineV2(
                            greeting = panel.greeting,
                            accentLabel = toolContext?.displayLabel,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Spacer(Modifier.height(18.dp))
                        when {
                            errorBanner != null -> ErrorChipV2(
                                message = errorBanner,
                                onDismiss = callbacks.onDismissError,
                            )
                            pendingConfirmation != null -> ConfirmationChipV2(
                                reason = pendingConfirmation.reason,
                                onConfirm = { callbacks.onConfirm(pendingConfirmation.id, true) },
                                onCancel = { callbacks.onConfirm(pendingConfirmation.id, false) },
                            )
                            panel.isListening -> ListeningRowV2(
                                partial = panel.partialTranscript,
                                notice = panel.voiceNotice,
                                onStop = callbacks.onVoiceStop,
                            )
                            lowConfidenceTranscript != null -> Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LowConfidenceTranscriptChipV2(
                                    best = lowConfidenceTranscript.best,
                                    alternatives = lowConfidenceTranscript.alternatives,
                                    onPick = callbacks.onSend,
                                )
                                InputRowV2(
                                    draft = draft,
                                    category = category,
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
                            }
                            panel.isStreaming -> StreamingRowV2(
                                loadingVerb = panel.loadingVerb.ifBlank { "Thinking…" },
                                accumulated = panel.streamingDelta,
                            )
                            else -> InputRowV2(
                                draft = draft,
                                category = category,
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
                        }
                        if (showResponsePreview) {
                            Spacer(Modifier.height(12.dp))
                            ResponsePreviewV2(panel.recentResponsePreview)
                        }
                        renderState.bubble?.let { bubble ->
                            Spacer(Modifier.height(12.dp))
                            BubbleFooterV2(bubble)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LowConfidenceTranscriptChipV2(
    best: String,
    alternatives: List<String>,
    onPick: (String) -> Unit,
) {
    val choices = remember(best, alternatives) {
        (listOf(best) + alternatives)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(3)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.HoneySoft)
            .border(
                0.5.dp,
                HandyDesign.Colors.HoneyHair,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "I heard '$best'. Did you mean:",
            style = HandyDesignType.Body,
            color = HandyDesign.Colors.TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { choice ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HandyDesign.Colors.SurfaceGlass)
                        .border(
                            0.5.dp,
                            HandyDesign.Colors.HoneyHair,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            role = Role.Button,
                            onClick = { onPick(choice) },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = choice,
                        style = HandyDesignType.Caption.copy(
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = HandyDesign.Colors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DragHandleV2() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Drag to resize panel"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.20f)),
        )
    }
}

@Composable
private fun PanelHeaderV2(
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HandyWordmark(size = 18, markSize = 22)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BareIconButtonV2(
                iconRes = R.drawable.ic_expand,
                description = "Open full chat",
                onClick = onExpand,
            )
            BareIconButtonV2(
                iconRes = R.drawable.ic_close,
                description = "Dismiss",
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun ContextLineV2(
    greeting: String,
    accentLabel: String?,
    modifier: Modifier = Modifier,
) {
    val text = remember(greeting, accentLabel) {
        greetingWithLabelAccent(greeting, accentLabel)
    }
    Text(
        text = text,
        style = HandyDesignType.Body.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = HandyDesign.Colors.TextSecondary,
        ),
        color = HandyDesign.Colors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        modifier = modifier.fillMaxWidth(),
    )
}

internal fun greetingWithLabelAccent(
    greeting: String,
    label: String?,
): AnnotatedString {
    val text = greeting.ifBlank { "What can I help you with?" }
    val trimmedLabel = label
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("Handy", ignoreCase = true) }
        ?: return AnnotatedString(text)
    val labelStart = text.indexOf(trimmedLabel, ignoreCase = true)
    if (labelStart < 0) return AnnotatedString(text)
    val labelEnd = labelStart + trimmedLabel.length

    return buildAnnotatedString {
        append(text.substring(0, labelStart))
        withStyle(
            SpanStyle(
                color = HandyDesign.Colors.Accent,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            append(text.substring(labelStart, labelEnd))
        }
        append(text.substring(labelEnd))
    }
}

private fun resolveCurrentAppDisplayName(
    context: Context,
    packageName: String?,
    fallbackLabel: String?,
): String? {
    val p = packageName?.trim().takeIf { !it.isNullOrBlank() } ?: return null
    val packageManager = context.packageManager
    val label = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(p, 0),
        ).toString()
    }.recoverCatching { error ->
        if (error is PackageManager.NameNotFoundException) {
            fallbackLabel.orEmpty()
        } else {
            throw error
        }
    }.getOrNull()
    return label?.trim()?.takeIf { it.isNotBlank() && !it.equals("Handy", ignoreCase = true) }
}

@Composable
private fun BareIconButtonV2(
    @DrawableRes iconRes: Int,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = HandyDesign.Colors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun InputRowV2(
    draft: String,
    category: PanelGreetingCategory,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceStart: () -> Unit,
    focusRequester: FocusRequester,
) {
    var fieldFocused by remember { mutableStateOf(false) }
    val composerShape = RoundedCornerShape(30.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(composerShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (fieldFocused) {
                    HandyDesign.Colors.AccentHairline
                } else {
                    Color.White.copy(alpha = 0.16f)
                },
                composerShape,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ComposerIconButtonV2(
            iconRes = R.drawable.ic_phosphor_mic,
            description = "Start voice",
            iconTint = HandyDesign.Colors.TextPrimary,
            iconSize = 20.dp,
            onClick = onVoiceStart,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) {
                Text(
                    text = contextualPlaceholderV2(category),
                    style = HandyDesignType.Body.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    ),
                    color = HandyDesign.Colors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = HandyDesignType.Body.copy(
                    color = HandyDesign.Colors.TextPrimary,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                ),
                cursorBrush = SolidColor(HandyDesign.Colors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { fieldFocused = it.isFocused },
            )
        }
        ComposerIconButtonV2(
            iconRes = R.drawable.ic_phosphor_send,
            description = "Send",
            iconTint = HandyDesign.Colors.Accent,
            iconSize = 18.dp,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun ComposerIconButtonV2(
    @DrawableRes iconRes: Int,
    description: String,
    iconTint: Color,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun contextualPlaceholderV2(category: PanelGreetingCategory): String = when (category) {
    PanelGreetingCategory.PHOTOS -> "What's in this photo?"
    PanelGreetingCategory.MAPS -> "What's near here?"
    else -> "Ask Handy anything…"
}

@Composable
private fun ListeningRowV2(
    partial: String,
    notice: String,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.AccentSoft)
            .border(0.5.dp, HandyDesign.Colors.AccentHairline, RoundedCornerShape(14.dp))
            .padding(PaddingValues(horizontal = 14.dp, vertical = 12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(HandyDesign.Colors.DangerSoft)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onStop,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_mic),
                contentDescription = "Stop listening",
                tint = HandyDesign.Colors.Danger,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = partial.ifBlank { "Listening…" },
                style = HandyDesignType.Body,
                color = HandyDesign.Colors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (notice.isNotBlank()) {
                Text(
                    text = notice,
                    style = HandyDesignType.Caption,
                    color = HandyDesign.Colors.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StreamingRowV2(
    loadingVerb: String,
    accumulated: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                color = HandyDesign.Colors.Accent,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = loadingVerb,
                style = HandyDesignType.Caption,
                color = HandyDesign.Colors.TextSecondary,
            )
        }
        if (accumulated.isNotBlank()) {
            Text(
                text = accumulated,
                style = HandyDesignType.Body,
                color = HandyDesign.Colors.TextPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConfirmationChipV2(
    reason: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.Act.copy(alpha = 0.18f))
            .border(
                0.5.dp,
                HandyDesign.Colors.Act.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = reason,
            style = HandyDesignType.Body,
            color = HandyDesign.Colors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(HandyDesign.Colors.Act)
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Continue",
                    style = HandyDesignType.Caption,
                    color = HandyDesign.Colors.PageBg,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(HandyDesign.Colors.SurfaceGlass)
                    .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Cancel",
                    style = HandyDesignType.Caption,
                    color = HandyDesign.Colors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ErrorChipV2(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.DangerSoft)
            .border(
                0.5.dp,
                HandyDesign.Colors.Danger.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = HandyDesignType.Caption,
            color = HandyDesign.Colors.Danger,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(HandyDesign.Colors.DangerSoft)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Dismiss error",
                tint = HandyDesign.Colors.Danger,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ResponsePreviewV2(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.SurfaceGlass)
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = HandyDesignType.Caption.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            color = HandyDesign.Colors.TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BubbleFooterV2(bubble: BuddyBubble) {
    val (color, text) = when (bubble) {
        is BuddyBubble.Transcript -> HandyDesign.Colors.Honey to bubble.text
        is BuddyBubble.Action -> HandyDesign.Colors.Act to bubble.text
        is BuddyBubble.Response -> HandyDesign.Colors.Accent to bubble.text
        is BuddyBubble.Navigation -> HandyDesign.Colors.Point to bubble.text
    }
    if (text.isBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.18f))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = HandyDesignType.Caption.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            color = HandyDesign.Colors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
