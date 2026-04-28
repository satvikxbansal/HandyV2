package com.handy.app.widget

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.handy.app.theme.HandMarkIcon
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyMotion
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.ListeningWaveformBars
import com.handy.app.theme.PointerHandIcon
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.BuddyState
import kotlin.math.PI

enum class WidgetState { IDLE, TOUCHED, DRAGGING, LISTENING, THINKING, FLYING, POINTING }

/**
 * Hand-mark icon size (absolute). Kept at 32dp so the hand is the
 * dominant glyph inside the 48dp floating widget circle — the amber
 * ring around it reads as a hairline, not a halo.
 */
private val HandIconSize = 32.dp

/**
 * Floating lens — 48dp glass circle, warm amber rim, [HandMark] idle,
 * waveform listening, rotating arc + hand thinking.
 */
@Composable
fun WidgetContent(
    state: WidgetState,
    pointerRotationRadians: Float = 0f,
    pointerScale: Float = 1f,
) {
    HandyTheme(darkTheme = true) {
        val scale by animateFloatAsState(
            targetValue = when (state) {
                WidgetState.TOUCHED, WidgetState.LISTENING -> 1.05f
                else -> 1f
            },
            animationSpec = tween(durationMillis = HandyMotion.DefaultMs),
            label = "widget-scale",
        )
        val borderColor = when (state) {
            WidgetState.IDLE, WidgetState.DRAGGING ->
                HandyColors.Accent.copy(alpha = 0.60f)
            WidgetState.TOUCHED -> HandyColors.Accent
            WidgetState.LISTENING -> HandyColors.GlassBorder
            WidgetState.THINKING -> HandyColors.GlassBorder
            WidgetState.FLYING, WidgetState.POINTING -> HandyColors.BubbleResponse
        }
        val isPointer = state == WidgetState.FLYING || state == WidgetState.POINTING
        Box(
            modifier = Modifier
                .size(HandyDimens.WidgetLensSize)
                .scale(scale),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(HandyColors.GlassTint)
                    .border(HandyDimens.WidgetBorder, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LensSheen()
                if (state == WidgetState.LISTENING) {
                    ListeningWaveformBars(
                        color = HandyColors.Listening,
                        modifier = Modifier.padding(bottom = 2.dp),
                        maxHeight = 14.dp,
                        minHeight = 3.dp,
                    )
                } else {
                    if (state == WidgetState.THINKING) {
                        ThinkingArcRing(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                        )
                    }
                    Crossfade(
                        targetState = isPointer,
                        animationSpec = tween(HandyMotion.DefaultMs),
                        label = "widget-hand-pointer-crossfade",
                    ) { pointer ->
                        if (pointer) {
                            PointerHandIcon(
                                size = HandIconSize,
                                tint = HandyColors.BubbleResponse,
                                modifier = Modifier
                                    .rotate(pointerRotationRadians.toDegrees() + 90f)
                                    .scale(pointerScale.coerceIn(0.90f, 1.18f)),
                            )
                        } else {
                            HandMarkIcon(
                                size = HandIconSize,
                                tint = HandyColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LensSheen() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape),
    ) {
        val w = constraints.maxWidth.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            HandyColors.GlassHighlight.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                        center = Offset(x = w * 0.35f, y = w * 0.15f),
                        radius = w * 0.95f,
                    ),
                ),
        )
    }
}

@Composable
private fun ThinkingArcRing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "think-spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(HandyMotion.RotatingRimMs, easing = LinearEasing),
        ),
        label = "rot",
    )
    Canvas(modifier) {
        rotate(rotation) {
            drawArc(
                color = HandyColors.Accent.copy(alpha = 0.85f),
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * V2 Unified Buddy — [LensRenderer] under optional centre glyph / waveform.
 */
@Composable
fun UnifiedBuddyContent(
    buddyState: BuddyState,
    bubble: BuddyBubble?,
) {
    HandyTheme(darkTheme = true) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(PaddingValues(horizontal = 4.dp, vertical = 4.dp)),
        ) {
            val outer = HandyDimens.WidgetSize + 40.dp
            Box(
                modifier = Modifier.size(outer),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    modifier = Modifier.size(outer),
                    factory = { ctx -> LensRenderer(ctx) },
                    update = { view ->
                        view.saturationTint = tintFor(buddyState)
                        view.lensBaseScale = baseScaleFor(buddyState)
                        view.pulseScale = if (buddyState == BuddyState.POINTING) view.pulseScale else 1.0f
                    },
                )
                when (buddyState) {
                    // V2 Unified Buddy uses the larger hero-sized lens
                    // (112dp outer). The hand inside stays at 32dp —
                    // same absolute size as the floating widget — so
                    // the visual language is consistent.
                    BuddyState.DOCKED, BuddyState.DRAGGING -> HandMarkIcon(
                        size = HandIconSize,
                        tint = HandyColors.Accent,
                    )
                    BuddyState.LISTENING -> ListeningWaveformBars(
                        color = HandyColors.Listening,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    else -> Unit
                }
            }
            if (bubble != null) {
                Spacer(Modifier.width(8.dp))
                WidgetBubbleChip(bubble)
            }
        }
    }
}

@Composable
fun WidgetBubbleChip(bubble: BuddyBubble) {
    val (backgroundColor, textColor, text) = when (bubble) {
        is BuddyBubble.Transcript ->
            Triple(HandyColors.BubbleTranscript, HandyColors.AccentInk, bubble.text)
        is BuddyBubble.Action ->
            Triple(HandyColors.BubbleAction, HandyColors.PageBg, bubble.text)
        is BuddyBubble.Response ->
            Triple(HandyColors.BubbleResponse, HandyColors.PageBg, bubble.text)
        is BuddyBubble.Navigation ->
            Triple(HandyColors.BubbleNavigation, HandyColors.PageBg, bubble.text)
    }
    if (text.isBlank()) return
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 240.dp),
        )
    }
}

private fun tintFor(state: BuddyState): LensRenderer.Tint = when (state) {
    BuddyState.DOCKED, BuddyState.DRAGGING -> LensRenderer.Tint.Amber
    BuddyState.LISTENING -> LensRenderer.Tint.Amber
    BuddyState.THINKING, BuddyState.STREAMING -> LensRenderer.Tint.Amber
    BuddyState.FLYING, BuddyState.POINTING -> LensRenderer.Tint.Green
    BuddyState.ACTING -> LensRenderer.Tint.Teal
    BuddyState.SPEAKING -> LensRenderer.Tint.Green
}

private fun baseScaleFor(state: BuddyState): Float = when (state) {
    BuddyState.THINKING, BuddyState.STREAMING -> 0.78f
    BuddyState.FLYING -> 0.9f
    BuddyState.POINTING -> 0.86f
    else -> 0.82f
}

private fun Float.toDegrees(): Float =
    this * 180f / PI.toFloat()
