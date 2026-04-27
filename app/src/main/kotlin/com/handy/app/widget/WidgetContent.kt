package com.handy.app.widget

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.BuddyState

enum class WidgetState { IDLE, TOUCHED, DRAGGING, LISTENING, THINKING }

/**
 * Hand-mark icon size (absolute). The current handoff renders the
 * floating disc at 60dp and keeps the palm at roughly 44% of that
 * diameter, matching `handy-widget.jsx`.
 */
private val HandIconSize = 26.dp
private val FloatingDiscSize = 60.dp
private val FloatingTouchTarget = 100.dp

/**
 * Floating lens — 60dp glass circle inside a 100dp halo/touch target,
 * warm amber rim, [HandMark] idle, waveform + halos while listening,
 * rotating sweep rim with counter-rotated content while thinking.
 */
@Composable
fun WidgetContent(state: WidgetState) {
    HandyTheme(darkTheme = true) {
        val infinite = rememberInfiniteTransition(label = "widget")
        val rotation by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(HandyMotion.IdlePulseMs, easing = LinearEasing),
            ),
            label = "rim-rotation",
        )
        val scale by animateFloatAsState(
            targetValue = when (state) {
                WidgetState.TOUCHED, WidgetState.LISTENING -> 1.05f
                else -> 1f
            },
            animationSpec = tween(durationMillis = HandyMotion.DefaultMs),
            label = "widget-scale",
        )
        Box(
            modifier = Modifier
                .size(FloatingTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            if (state == WidgetState.LISTENING) {
                ListeningHalo(delayMillis = 0)
                ListeningHalo(delayMillis = 400, inset = 10.dp)
            } else if (state == WidgetState.TOUCHED) {
                HoverGlow()
            }

            Box(
                modifier = Modifier
                    .size(FloatingDiscSize)
                    .clip(CircleShape)
                    .then(
                        if (state == WidgetState.THINKING) {
                            Modifier
                                .rotate(rotation)
                                .background(
                                    Brush.sweepGradient(
                                        0.00f to HandyColors.Accent,
                                        0.25f to HandyColors.Accent,
                                        0.55f to HandyColors.Accent.copy(alpha = 0f),
                                        1.00f to HandyColors.Accent.copy(alpha = 0f),
                                    ),
                                    CircleShape,
                                )
                                .padding(1.5.dp)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (state == WidgetState.THINKING) {
                                Modifier.rotate(-rotation)
                            } else {
                                Modifier
                            },
                        )
                        .scale(scale)
                        .clip(CircleShape)
                        .background(HandyColors.GlassTint)
                        .border(
                            width = if (state == WidgetState.THINKING) 0.dp else HandyDimens.WidgetBorder,
                            color = when (state) {
                                WidgetState.IDLE, WidgetState.DRAGGING ->
                                    HandyColors.Accent.copy(alpha = 0.60f)
                                WidgetState.TOUCHED -> HandyColors.Accent
                                else -> HandyColors.GlassBorder
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    LensSheen()
                    when (state) {
                        WidgetState.LISTENING -> ListeningWaveformBars(
                            color = HandyColors.Listening,
                            modifier = Modifier.padding(bottom = 2.dp),
                            maxHeight = 18.dp,
                            minHeight = 4.dp,
                        )
                        else -> HandMarkIcon(
                            size = HandIconSize,
                            tint = HandyColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningHalo(delayMillis: Int, inset: androidx.compose.ui.unit.Dp = 0.dp) {
    val transition = rememberInfiniteTransition(label = "listening-halo-$delayMillis")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = HandyMotion.IdlePulseMs,
                delayMillis = delayMillis,
                easing = LinearEasing,
            ),
        ),
        label = "halo",
    )
    Box(
        modifier = Modifier
            .padding(inset)
            .fillMaxSize()
            .scale(0.9f + t * 0.5f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        HandyColors.Listening.copy(alpha = 0.4f * (1f - t)),
                        Color.Transparent,
                    )
                ),
            ),
    )
}

@Composable
private fun HoverGlow() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        HandyColors.Accent.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
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
                BubbleChip(bubble)
            }
        }
    }
}

@Composable
private fun BubbleChip(bubble: BuddyBubble) {
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
            .background(backgroundColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthSafe(260.dp),
        )
    }
}

private fun Modifier.widthSafe(max: androidx.compose.ui.unit.Dp): Modifier =
    this.then(Modifier.width(max))

private fun tintFor(state: BuddyState): LensRenderer.Tint = when (state) {
    BuddyState.DOCKED, BuddyState.DRAGGING -> LensRenderer.Tint.Amber
    BuddyState.LISTENING -> LensRenderer.Tint.Amber
    BuddyState.THINKING, BuddyState.STREAMING -> LensRenderer.Tint.Amber
    BuddyState.FLYING, BuddyState.POINTING -> LensRenderer.Tint.Blue
    BuddyState.ACTING -> LensRenderer.Tint.Teal
    BuddyState.SPEAKING -> LensRenderer.Tint.Green
}

private fun baseScaleFor(state: BuddyState): Float = when (state) {
    BuddyState.THINKING, BuddyState.STREAMING -> 0.78f
    BuddyState.FLYING -> 0.9f
    BuddyState.POINTING -> 0.86f
    else -> 0.82f
}
