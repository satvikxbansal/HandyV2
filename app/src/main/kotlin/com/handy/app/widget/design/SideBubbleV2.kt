package com.handy.app.widget.design

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.core.overlay.BubbleIcon
import com.handy.core.overlay.BubbleTone
import com.handy.core.overlay.BuddyBubble
import kotlin.math.max

@Composable
fun SideBubbleV2(
    bubble: BuddyBubble,
    modifier: Modifier = Modifier,
) {
    if (bubble.label.isBlank()) return
    val tone = bubble.tone.color()
    val haloBleed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 8.dp else 12.dp
    SubcomposeLayout(modifier = modifier) { constraints ->
        val surfacePlaceables = subcompose(SurfaceSlot) {
            BubbleSurface(bubble = bubble, tone = tone)
        }.map { measurable ->
            measurable.measure(constraints)
        }
        val surfaceWidth = surfacePlaceables.maxOfOrNull { it.width } ?: 0
        val surfaceHeight = surfacePlaceables.maxOfOrNull { it.height } ?: 0
        val bleedPx = haloBleed.roundToPx()
        val haloWidth = surfaceWidth + bleedPx * 2
        val haloHeight = surfaceHeight + bleedPx * 2
        val haloPlaceables = subcompose(HaloSlot) {
            BubbleHalo(tone = tone)
        }.map { measurable ->
            measurable.measure(
                Constraints.fixed(
                    width = max(1, haloWidth),
                    height = max(1, haloHeight),
                ),
            )
        }
        layout(surfaceWidth, surfaceHeight) {
            haloPlaceables.forEach { it.placeRelative(-bleedPx, -bleedPx) }
            surfacePlaceables.forEach { it.placeRelative(0, 0) }
        }
    }
}

@Composable
private fun BubbleHalo(tone: Color) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(
            Modifier
                .fillMaxSize()
                .blur(radius = 6.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tone.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                    shape = RoundedCornerShape(26.dp),
                ),
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(tone.copy(alpha = 0.06f), RoundedCornerShape(30.dp)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(2.dp)
                .background(tone.copy(alpha = 0.10f), RoundedCornerShape(28.dp)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .background(tone.copy(alpha = 0.16f), RoundedCornerShape(26.dp)),
        )
    }
}

@Composable
private fun BubbleSurface(
    bubble: BuddyBubble,
    tone: Color,
) {
    val shape = RoundedCornerShape(18.dp)
    val labelFontSize = if (bubble.small) 12.sp else 13.5f.sp
    val prefix = bubble.prefix
    val leading = bubble.leading
    val progress = bubble.progress
    Box(
        modifier = Modifier
            .widthIn(max = if (bubble.small) 240.dp else 280.dp)
            .clip(shape)
            .background(Color(0xD1121418))
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.03f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = shape,
            )
            .padding(
                horizontal = if (bubble.small) 12.dp else 14.dp,
                vertical = if (bubble.small) 8.dp else 10.dp,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (prefix != null) {
                Text(
                    text = prefix.uppercase(),
                    style = HandyDesignType.Overline.copy(
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.14.em,
                    ),
                    color = tone,
                    modifier = Modifier.padding(bottom = 0.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leading != null) {
                    Icon(
                        painter = painterResource(leading.drawableRes()),
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = bubble.label,
                    style = HandyDesignType.Body.copy(
                        fontSize = labelFontSize,
                        lineHeight = labelFontSize * 1.35f,
                        fontWeight = if (bubble.italic) FontWeight.Normal else FontWeight.Medium,
                        fontStyle = if (bubble.italic) FontStyle.Italic else FontStyle.Normal,
                        letterSpacing = 0.em,
                    ),
                    color = HandyDesign.Colors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                ) {
                    val animated by animateFloatAsState(
                        targetValue = progress.coerceIn(0f, 1f),
                        animationSpec = tween(240, easing = LinearOutSlowInEasing),
                        label = "sidebubble-progress",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animated)
                            .background(tone),
                    )
                }
            }
        }
    }
}

private fun BubbleTone.color(): Color = when (this) {
    BubbleTone.ACCENT -> HandyDesign.Colors.Accent
    BubbleTone.MUTED -> HandyDesign.Colors.TextMuted
    BubbleTone.VIOLET -> HandyDesign.Colors.Violet
    BubbleTone.HONEY -> HandyDesign.Colors.Honey
    BubbleTone.POINT -> HandyDesign.Colors.Point
    BubbleTone.ACT -> HandyDesign.Colors.Act
    BubbleTone.DANGER -> HandyDesign.Colors.Danger
}

@DrawableRes
private fun BubbleIcon.drawableRes(): Int = when (this) {
    BubbleIcon.HAND_TAP -> R.drawable.ic_phosphor_hand_pointing_fill
    BubbleIcon.KEYBOARD -> R.drawable.ic_keyboard
    BubbleIcon.RECIPE -> R.drawable.ic_recipe
    BubbleIcon.BACK -> R.drawable.ic_chevron_left
    BubbleIcon.WARNING -> R.drawable.ic_phosphor_warning
    BubbleIcon.CURSOR -> R.drawable.ic_lucide_cursor
    BubbleIcon.GLOBE -> R.drawable.ic_globe
}

private const val SurfaceSlot = "surface"
private const val HaloSlot = "halo"
