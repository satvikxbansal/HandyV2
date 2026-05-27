package com.handy.app.overlay.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.app.design.PrimaryButton
import com.handy.app.design.SecondaryTextButton
import com.handy.core.action.ActionRisk
import com.handy.core.action.ConfirmationLevel
import com.handy.core.overlay.PlanPreview
import com.handy.core.overlay.PlanStep
import com.handy.core.overlay.TapForMeConfirmation
import kotlinx.coroutines.delay

@Composable
fun TapForMeConfirmationSheetV2(
    request: TapForMeConfirmation,
    onDecision: (Boolean, String?) -> Unit,
    modifier: Modifier = Modifier,
    onSheetOpened: () -> Unit = {},
) {
    var completed by remember(request.id) { mutableStateOf(false) }
    var timeoutProgress by remember(request.id) { mutableStateOf(1f) }
    var editableTypingText by remember(request.id) {
        mutableStateOf(request.typingText.orEmpty())
    }
    val isTyping = request.typingText != null
    val planPreview = request.planPreview
    val timeoutMs = timeoutForRisk(request.risk)
    val confirmationLevel = effectiveConfirmationLevel(request)

    fun decide(approved: Boolean) {
        if (completed) return
        completed = true
        onDecision(approved, editableTypingText.takeIf { isTyping })
    }

    LaunchedEffect(request.id, timeoutMs) {
        val started = System.currentTimeMillis()
        while (!completed) {
            val elapsed = System.currentTimeMillis() - started
            timeoutProgress = (1f - elapsed.toFloat() / timeoutMs).coerceIn(0f, 1f)
            if (elapsed >= timeoutMs) break
            delay(100L)
        }
        decide(false)
    }

    LaunchedEffect(request.id) {
        onSheetOpened()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { decide(false) },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        HandyDesignBottomSheetV2(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            DragHandleV2()
            Spacer(Modifier.height(8.dp))
            Header(request = request, isTyping = isTyping, isRecipePlan = planPreview != null)
            if (planPreview != null) {
                Spacer(Modifier.height(16.dp))
                PlanPreviewCard(planPreview)
            }
            if (isTyping) {
                Spacer(Modifier.height(16.dp))
                TypingFieldV2(
                    text = editableTypingText,
                    onChange = { editableTypingText = it },
                )
            }
            Spacer(Modifier.height(16.dp))
            TimeoutBar(timeoutProgress = timeoutProgress, risk = request.risk)
            Spacer(Modifier.height(16.dp))
            ButtonRow(
                confirmationLevel = confirmationLevel,
                risk = request.risk,
                onConfirm = { decide(true) },
                onCancel = { decide(false) },
            )
        }
    }
}

@Composable
fun HandyDesignBottomSheetV2(
    modifier: Modifier = Modifier,
    shape: Shape,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xD1121418))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = shape,
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

@Composable
private fun DragHandleV2() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        )
    }
}

@Composable
private fun Header(
    request: TapForMeConfirmation,
    isTyping: Boolean,
    isRecipePlan: Boolean,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(HandyDesign.Colors.AccentSoft)
                .border(1.dp, HandyDesign.Colors.AccentHairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (isTyping) {
                        R.drawable.ic_keyboard
                    } else {
                        R.drawable.ic_phosphor_hand_pointing_fill
                    },
                ),
                contentDescription = null,
                tint = HandyDesign.Colors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    isRecipePlan -> "Review recipe plan"
                    isTyping -> "Type-for-me confirmation"
                    else -> "Tap-for-me confirmation"
                },
                style = HandyDesignType.TitleSmall,
                color = HandyDesign.Colors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = request.bodyCopy(
                    isTyping = isTyping,
                    appLabel = request.appLabel
                        ?: request.packageName
                        ?: stringResource(R.string.tap_for_me_sheet_this_app),
                ),
                style = HandyDesignType.Body,
                color = HandyDesign.Colors.TextSecondary,
            )
            request.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.tap_for_me_sheet_policy_reason, reason),
                    style = HandyDesignType.Caption,
                    color = HandyDesign.Colors.TextMuted,
                )
            }
        }
    }
}

private fun TapForMeConfirmation.bodyCopy(isTyping: Boolean, appLabel: String): String =
    if (isTyping) {
        "Type into \"$targetLabel\" in $appLabel?"
    } else {
        "Tap \"$targetLabel\" in $appLabel?"
    }

@Composable
private fun PlanPreviewCard(preview: PlanPreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = preview.recipeDisplayName,
            style = HandyDesignType.BodyStrong.copy(fontSize = 14.sp),
            color = HandyDesign.Colors.TextPrimary,
        )
        preview.steps.forEach { step ->
            PlanStepRow(step)
        }
        if (preview.totalStepCount > preview.steps.size) {
            val remaining = preview.totalStepCount - preview.steps.size
            Text(
                text = "+ $remaining more step${if (remaining == 1) "" else "s"}",
                style = HandyDesignType.Caption.copy(fontSize = 12.sp),
                color = HandyDesign.Colors.TextMuted,
                modifier = Modifier.padding(start = 24.dp),
            )
        }
    }
}

@Composable
private fun PlanStepRow(step: PlanStep) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val tone = if (step.isSensitive) {
            HandyDesign.Colors.Danger
        } else {
            HandyDesign.Colors.Accent
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = 0.22f))
                .border(0.5.dp, tone.copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step.index.toString(),
                style = HandyDesignType.Overline.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = tone,
            )
        }
        Text(
            text = step.title,
            style = HandyDesignType.Body.copy(fontSize = 13.5.sp),
            color = HandyDesign.Colors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (step.isSensitive) {
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_warning),
                contentDescription = "Sensitive step",
                tint = HandyDesign.Colors.Danger,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun TypingFieldV2(
    text: String,
    onChange: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            textStyle = HandyDesignType.Body.copy(color = HandyDesign.Colors.TextPrimary),
            cursorBrush = SolidColor(HandyDesign.Colors.Accent),
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TimeoutBar(
    timeoutProgress: Float,
    risk: ActionRisk,
) {
    val toneColor = when (risk) {
        ActionRisk.LOW,
        ActionRisk.MEDIUM -> HandyDesign.Colors.Accent
        ActionRisk.HIGH -> HandyDesign.Colors.Honey
        ActionRisk.CRITICAL -> HandyDesign.Colors.Danger
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(timeoutProgress)
                .height(2.dp)
                .background(toneColor.copy(alpha = 0.70f)),
        )
    }
}

@Composable
private fun ButtonRow(
    confirmationLevel: ConfirmationLevel,
    risk: ActionRisk,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryTextButton(
            label = stringResource(R.string.tap_for_me_sheet_cancel),
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        if (confirmationLevel == ConfirmationLevel.STRONG_HOLD) {
            HoldToConfirmButton(
                text = "Hold to continue",
                holdDurationMs = holdDurationForRisk(risk),
                onConfirmed = onConfirm,
                modifier = Modifier.weight(1f),
            )
        } else {
            PrimaryButton(
                label = "Continue",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                showTrailingChevron = false,
            )
        }
    }
}

@Composable
private fun HoldToConfirmButton(
    text: String,
    holdDurationMs: Long,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var holding by remember { mutableStateOf(false) }
    var rawProgress by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "tap-for-me-hold-progress",
    )

    LaunchedEffect(holding, holdDurationMs) {
        if (!holding) {
            rawProgress = 0f
            return@LaunchedEffect
        }
        val started = System.currentTimeMillis()
        while (holding && rawProgress < 1f) {
            rawProgress = ((System.currentTimeMillis() - started).toFloat() / holdDurationMs)
                .coerceIn(0f, 1f)
            if (rawProgress >= 1f) {
                holding = false
                onConfirmed()
                break
            }
            delay(16L)
        }
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(HandyDesign.Dimens.CornerButton))
            .background(HandyDesign.Colors.Accent.copy(alpha = 0.22f))
            .border(
                0.5.dp,
                HandyDesign.Colors.AccentHairline,
                RoundedCornerShape(HandyDesign.Dimens.CornerButton),
            )
            .pointerInput(holdDurationMs) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    holding = true
                    waitForUpOrCancellation()
                    holding = false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(52.dp)
                .background(HandyDesign.Colors.Accent.copy(alpha = 0.70f)),
        )
        Text(
            text = text,
            style = HandyDesignType.BodyStrong,
            color = HandyDesign.Colors.TextPrimary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

internal fun timeoutForRisk(risk: ActionRisk): Long = when (risk) {
    ActionRisk.LOW -> 6_000L
    ActionRisk.MEDIUM -> 8_000L
    ActionRisk.HIGH -> 10_000L
    ActionRisk.CRITICAL -> 12_000L
}

internal fun holdDurationForRisk(risk: ActionRisk): Long = when (risk) {
    ActionRisk.CRITICAL -> 1_500L
    ActionRisk.HIGH -> 1_000L
    ActionRisk.LOW,
    ActionRisk.MEDIUM -> 750L
}

internal fun effectiveConfirmationLevel(request: TapForMeConfirmation): ConfirmationLevel {
    val hasSensitivePreviewStep = request.planPreview?.steps.orEmpty().any { it.isSensitive }
    return if (
        hasSensitivePreviewStep &&
        request.confirmationLevel < ConfirmationLevel.STRONG_HOLD
    ) {
        ConfirmationLevel.STRONG_HOLD
    } else {
        request.confirmationLevel
    }
}
