package com.handy.app.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyGlassBottomSheet
import com.handy.app.theme.HandyType
import com.handy.core.action.ConfirmationLevel
import com.handy.core.overlay.TapForMeConfirmation
import kotlinx.coroutines.delay

private const val CONFIRMATION_TIMEOUT_MS = 8_000L
private const val HOLD_TO_CONFIRM_MS = 1_000L

@Composable
fun TapForMeConfirmationSheet(
    request: TapForMeConfirmation,
    onDecision: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var completed by remember(request.id) { mutableStateOf(false) }
    var timeoutProgress by remember(request.id) { mutableStateOf(1f) }

    fun decide(approved: Boolean) {
        if (completed) return
        completed = true
        onDecision(approved)
    }

    LaunchedEffect(request.id) {
        val started = System.currentTimeMillis()
        while (!completed) {
            val elapsed = System.currentTimeMillis() - started
            timeoutProgress = (1f - elapsed.toFloat() / CONFIRMATION_TIMEOUT_MS).coerceIn(0f, 1f)
            if (elapsed >= CONFIRMATION_TIMEOUT_MS) break
            delay(100L)
        }
        decide(false)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HandyColors.PageBg.copy(alpha = 0.34f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { decide(false) },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        HandyGlassBottomSheet(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            verticalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(HandyColors.BubbleAction.copy(alpha = 0.18f))
                        .border(0.5.dp, HandyColors.BubbleAction.copy(alpha = 0.36f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pointer_hand),
                        contentDescription = null,
                        tint = HandyColors.BubbleAction,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tap_for_me_sheet_title),
                        style = HandyType.TitleMedium,
                        color = HandyColors.TextPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.tap_for_me_sheet_body,
                            request.targetLabel,
                            request.appLabel ?: request.packageName ?: stringResource(R.string.tap_for_me_sheet_this_app),
                        ),
                        style = HandyType.Caption,
                        color = HandyColors.TextSecondary,
                    )
                    request.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.tap_for_me_sheet_policy_reason, reason),
                            style = HandyType.Overline,
                            color = HandyColors.TextMuted,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(HandyColors.ChipBg),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(timeoutProgress)
                        .height(2.dp)
                        .background(HandyColors.BubbleAction.copy(alpha = 0.70f)),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
            ) {
                SecondaryDecisionButton(
                    text = stringResource(R.string.tap_for_me_sheet_cancel),
                    onClick = { decide(false) },
                    modifier = Modifier.weight(1f),
                )
                if (request.confirmationLevel == ConfirmationLevel.STRONG_HOLD) {
                    HoldToConfirmButton(
                        text = stringResource(R.string.tap_for_me_sheet_hold_confirm),
                        onConfirmed = { decide(true) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PrimaryDecisionButton(
                        text = stringResource(R.string.tap_for_me_sheet_confirm),
                        onClick = { decide(true) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryDecisionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(HandyDimens.RadiusLg))
            .background(HandyColors.BubbleAction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = HandyType.BodyStrong, color = HandyColors.PageBg)
    }
}

@Composable
private fun SecondaryDecisionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(HandyDimens.RadiusLg))
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(HandyDimens.RadiusLg))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = HandyType.BodyStrong, color = HandyColors.TextSecondary)
    }
}

@Composable
private fun HoldToConfirmButton(
    text: String,
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

    LaunchedEffect(holding) {
        if (!holding) {
            rawProgress = 0f
            return@LaunchedEffect
        }
        val started = System.currentTimeMillis()
        while (holding && rawProgress < 1f) {
            rawProgress = ((System.currentTimeMillis() - started).toFloat() / HOLD_TO_CONFIRM_MS)
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
            .height(44.dp)
            .clip(RoundedCornerShape(HandyDimens.RadiusLg))
            .background(HandyColors.BubbleAction.copy(alpha = 0.22f))
            .border(0.5.dp, HandyColors.BubbleAction.copy(alpha = 0.52f), RoundedCornerShape(HandyDimens.RadiusLg))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(false)
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
                .height(44.dp)
                .background(HandyColors.BubbleAction.copy(alpha = 0.72f)),
        )
        Text(
            text = text,
            style = HandyType.BodyStrong,
            color = HandyColors.TextPrimary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
