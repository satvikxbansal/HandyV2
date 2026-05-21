package com.handy.app.theme

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.handy.app.R
import kotlin.math.roundToInt

private const val GlassSheetFallbackAlpha = 0.92f
private const val GlassSheetBlurredAlpha = 0.78f

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

/** Pointer-hand glyph used while the floating widget is flying/pointing. */
@Composable
fun PointerHandIcon(
    modifier: Modifier = Modifier,
    size: Dp,
    tint: Color,
) {
    Icon(
        painter = painterResource(R.drawable.ic_pointer_hand),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/**
 * Click semantics without Compose's default bounded ripple. Handy's
 * custom circular controls draw their own surface, and the stock ripple
 * reads as a grey square on top of that chrome.
 */
@Composable
fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        enabled = enabled,
        indication = null,
        interactionSource = interactionSource,
        onClick = onClick,
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
 * The panel window itself stays a full-screen input/IME host, so blur
 * is not applied there. On API 31+ the overlay panel service may pass
 * a pre-panel screenshot that this composable crops, clips, and blurs
 * inside only the rounded sheet bounds. When that snapshot is present,
 * the fill relaxes slightly so the blurred backdrop can show through;
 * otherwise it keeps the dense fallback alpha.
 *
 * Stack (bottom → top):
 *   1. Glass fill    — sheet-local high-opacity [HandyColors.GlassTint]
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
    backdropSnapshot: Bitmap? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(HandyDimens.RadiusGlass)
    val backdropBlurActive = backdropSnapshot != null
    val sheetFill = HandyColors.GlassTint.copy(
        alpha = if (backdropBlurActive) {
            GlassSheetBlurredAlpha
        } else {
            GlassSheetFallbackAlpha
        },
    )
    var sheetBounds by remember { mutableStateOf<IntRect?>(null) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Handoff: margin "0 12px 12px" — horizontal 12dp, bottom
            // 12dp, no top margin (the sheet butts against the top of
            // the ComposeView area).
            .padding(horizontal = HandyDimens.StackM)
            .padding(bottom = HandyDimens.StackM)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                sheetBounds = IntRect(
                    left = bounds.left.roundToInt(),
                    top = bounds.top.roundToInt(),
                    right = bounds.right.roundToInt(),
                    bottom = bounds.bottom.roundToInt(),
                )
            }
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
            .border(
                width = 0.5.dp,
                color = HandyColors.GlassBorder,
                shape = shape,
            ),
    ) {
        val bounds = sheetBounds
        if (backdropSnapshot != null && bounds != null) {
            BlurredBackdropSnapshot(
                bitmap = backdropSnapshot,
                bounds = bounds,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    // Layer 1 — high-opacity sheet fill. Keep this local to
                    // the chat overlay so widgets/snackbars retain the shared
                    // GlassTint token.
                    drawRect(sheetFill)

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

                    // Layer 3 — CSS inset highlights from `GlassCard`:
                    // `0 1px 0 highlight inset` plus a subtle inner stroke.
                    drawLine(
                        color = HandyColors.GlassHighlight,
                        start = Offset.Zero,
                        end = Offset(x = size.width, y = 0f),
                        strokeWidth = 0.75.dp.toPx(),
                    )
                    drawRoundRect(
                        color = HandyColors.InnerStroke,
                        cornerRadius = CornerRadius(
                            HandyDimens.RadiusGlass.toPx(),
                            HandyDimens.RadiusGlass.toPx(),
                        ),
                        style = Stroke(width = 0.5.dp.toPx()),
                    )
                },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
        ) {
            content()
        }
    }
}

@Composable
private fun BlurredBackdropSnapshot(
    bitmap: Bitmap,
    bounds: IntRect,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(
        modifier = modifier.blur(
            radius = 12.dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        ),
    ) {
        val srcLeft = bounds.left.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
        val srcTop = bounds.top.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
        val srcRight = bounds.right.coerceIn(srcLeft + 1, bitmap.width.coerceAtLeast(srcLeft + 1))
        val srcBottom = bounds.bottom.coerceIn(srcTop + 1, bitmap.height.coerceAtLeast(srcTop + 1))
        val dstWidth = size.width.roundToInt().coerceAtLeast(1)
        val dstHeight = size.height.roundToInt().coerceAtLeast(1)
        drawImage(
            image = image,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(dstWidth, dstHeight),
            filterQuality = FilterQuality.Medium,
        )
    }
}
