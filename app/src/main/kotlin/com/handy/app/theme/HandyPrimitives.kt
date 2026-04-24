package com.handy.app.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.handy.app.R

/**
 * Branded hand glyph — matches `ic_hand_mark.xml` / design handoff.
 */
@Composable
fun HandMarkIcon(
    modifier: Modifier = Modifier,
    size: Dp,
    tint: Color,
) {
    Icon(
        painter = painterResource(R.drawable.ic_hand_mark),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/**
 * Five vertical bars — listening state on the floating lens (prototype
 * `Waveform`, 0.9s period, 100ms stagger).
 */
@Composable
fun ListeningWaveformBars(
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 18.dp,
    minHeight: Dp = 4.dp,
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(5) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = HandyMotion.ListeningPulseMs,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(
                        index * 100,
                    ),
                ),
                label = "bar-$index",
            )
            val h = minHeight + (maxHeight - minHeight) * phase
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/**
 * Bottom-sheet glass chrome — canonical recipe from the design handoff
 * (`handy-android-design system/.../handy-primitives.jsx` `GlassCard`
 * and `README.md` § Shadow recipe + Backdrop blur fallback).
 *
 * Under the `10-handy-project-guardrails.mdc` §V2-forbidden rule we do
 * NOT apply `RenderEffect.createBlurEffect` or `FLAG_BLUR_BEHIND` on
 * the overlay window. The handoff's explicit fallback for that case
 * is a denser `GlassTint` (82% alpha) with no blur — that's what
 * [HandyColors.GlassTint] now encodes, so the sheet gets the right
 * look without the forbidden APIs.
 *
 * Stack (bottom → top):
 *   1. Glass fill    — [HandyColors.GlassTint]
 *   2. Top sheen     — radial gradient `GlassHighlight → transparent`,
 *                      centred at 30% from the left / 0% from the top,
 *                      opacity 0.6, per handoff spec.
 *   3. Inset top hairline — 0.5dp [HandyColors.GlassHighlight] on the
 *                          first row of pixels (matches CSS
 *                          `box-shadow: 0 1px 0 0 highlight inset`).
 *   4. 0.5dp border  — [HandyColors.GlassBorder] (warm peach hairline
 *                      — this IS the "amber outline" visible in the
 *                      design screenshots; do **not** swap it for a
 *                      higher-alpha accent stroke).
 *   5. Drop shadow   — `0 24 60 rgba(0,0,0,0.55)` ambient +
 *                      `0 6 16 rgba(0,0,0,0.40)` contact.
 */
@Composable
fun HandyGlassBottomSheet(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(HandyDimens.StackM),
    contentPadding: Dp = HandyDimens.CardPad,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(HandyDimens.RadiusGlass)
    val neutralGlassBase = Color(0xE05F5D63)
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Handoff: margin "0 12px 12px" — horizontal 12dp, bottom
            // 12dp, no top margin (the sheet butts against the top of
            // the ComposeView area).
            .padding(horizontal = HandyDimens.StackM)
            .padding(bottom = HandyDimens.StackM)
            // Broad soft falloff + slight contact shadow at the bottom.
            .shadow(
                elevation = 28.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.46f),
                ambientColor = Color.Black.copy(alpha = 0.30f),
            )
            .shadow(
                elevation = 8.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.30f),
                ambientColor = Color.Black.copy(alpha = 0.18f),
            )
            .clip(shape)
            .drawBehind {
                // Layer 1 — canonical glass fill (already dense via
                // token alpha 0.82 — handoff fallback clause).
                drawRect(HandyColors.GlassTint)
                // Layer 1b — neutral gray wash to suppress warm/orange cast
                // when rendered above colorful app content.
                drawRect(neutralGlassBase)

                // Layer 2 — top-centred specular sheen. Matches CSS
                // `radial-gradient(120% 60% at 30% 0%, highlight 0%,
                //  transparent 45%)` at opacity 0.6.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HandyColors.GlassHighlight.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(x = size.width * 0.30f, y = 0f),
                        radius = size.width * 1.20f,
                    ),
                )
            }
            .border(
                width = 0.5.dp,
                color = HandyColors.GlassBorder,
                shape = shape,
            )
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
    ) {
        content()
    }
}
