package com.handy.app.widget.design

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.widget.WidgetState
import kotlin.math.PI

@Composable
fun WidgetGlyphV2(
    state: WidgetState,
    pointerRotationRadians: Float,
    pointerScale: Float,
    modifier: Modifier = Modifier,
) {
    val isBlue = state == WidgetState.FLYING || state == WidgetState.POINTING
    val discFill = if (isBlue) HandyDesign.Colors.PointSoft else HandyDesign.Colors.Accent
    val discBorder = when {
        isBlue -> HandyDesign.Colors.PointHairline
        state == WidgetState.TOUCHED -> HandyDesign.Colors.Accent
        else -> HandyDesign.Colors.Accent.copy(alpha = 0.60f)
    }
    val discScale = if (isBlue || state == WidgetState.IDLE) {
        pointerScale.coerceIn(0.90f, 1.20f)
    } else {
        1f
    }
    val pointerRotationDegrees = pointerRotationRadians.toDegrees()
    val trailAlpha by animateFloatAsState(
        targetValue = if (state == WidgetState.FLYING && pointerScale > 1.02f) 1f else 0f,
        animationSpec = tween(120, easing = LinearEasing),
        label = "trail-alpha",
    )

    Box(
        modifier = modifier
            .size(WidgetCanvasSize)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = when (state) {
                    WidgetState.IDLE,
                    WidgetState.TOUCHED,
                    WidgetState.DRAGGING -> "Handy is ready"
                    WidgetState.LISTENING -> "Handy is listening"
                    WidgetState.THINKING -> "Handy is thinking"
                    WidgetState.FLYING -> "Handy is moving to point at a control"
                    WidgetState.POINTING -> "Handy is pointing at a control"
                    WidgetState.ACTING -> "Handy is performing an action"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isBlue) {
            FlyingTrail(
                alpha = trailAlpha,
                rotationDegrees = pointerRotationDegrees,
            )
        }

        Box(
            modifier = Modifier
                .size(WidgetDiscSize)
                .scale(discScale)
                .clip(CircleShape)
                .background(discFill)
                .border(1.dp, discBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DiscGlyph(
                state = state,
                pointerRotationDegrees = pointerRotationDegrees,
            )
        }

        if (state == WidgetState.THINKING) {
            ThinkingArc()
        }

        if (state == WidgetState.ACTING) {
            ActingBadge()
        }
    }
}

@Composable
private fun DiscGlyph(
    state: WidgetState,
    pointerRotationDegrees: Float,
) {
    if (state == WidgetState.LISTENING) {
        ListeningBars()
        return
    }

    val isPointer = state == WidgetState.FLYING || state == WidgetState.POINTING
    val wobble = flyingWobble(state)
    Crossfade(
        targetState = isPointer,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "widget-glyph-v2-pointer-crossfade",
    ) { pointer ->
        if (pointer) {
            PointerHand(
                rotationDegrees = pointerRotationDegrees + 90f + wobble,
            )
        } else {
            PalmHand()
        }
    }
}

@Composable
private fun ListeningBars() {
    val delays = intArrayOf(120, 280, 420, 280, 120)
    val transition = rememberInfiniteTransition(label = "widget-listening")
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        delays.forEachIndexed { index, delayMs ->
            val scaleY by transition.animateFloat(
                initialValue = 0.30f,
                targetValue = 1.00f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(delayMs),
                ),
                label = "lbar-scale$index",
            )
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(14.dp)
                    .graphicsLayer {
                        this.scaleY = scaleY
                    }
                    .background(
                        HandyDesign.Colors.AccentInk,
                        RoundedCornerShape(1.5.dp),
                    ),
            )
        }
    }
}

@Composable
private fun ThinkingArc() {
    val transition = rememberInfiniteTransition(label = "think")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
        ),
        label = "think-rot",
    )

    Canvas(
        modifier = Modifier
            .size(60.dp)
            .graphicsLayer {
                rotationZ = rotation
            },
    ) {
        drawArc(
            color = HandyDesign.Colors.Accent,
            startAngle = 0f,
            sweepAngle = 137f,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            topLeft = Offset.Zero,
            size = this.size,
        )
    }
}

@Composable
private fun FlyingTrail(
    alpha: Float,
    rotationDegrees: Float,
) {
    Box(
        modifier = Modifier
            .size(WidgetDiscSize)
            .graphicsLayer {
                rotationZ = rotationDegrees
                this.alpha = alpha
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-10).dp)
                .size(width = 36.dp, height = 16.dp)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(
                            radius = 0.5.dp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.7f to HandyDesign.Colors.PointTrail.copy(alpha = 0.0f),
                            1f to HandyDesign.Colors.PointTrail,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun PalmHand() {
    Icon(
        painter = painterResource(R.drawable.ic_hand_palm_fill),
        contentDescription = null,
        tint = HandyDesign.Colors.AccentInk,
        modifier = Modifier.size(GlyphSize),
    )
}

@Composable
private fun PointerHand(rotationDegrees: Float) {
    Icon(
        painter = painterResource(R.drawable.ic_phosphor_hand_pointing_bold),
        contentDescription = null,
        tint = HandyDesign.Colors.Point,
        modifier = Modifier
            .size(GlyphSize)
            .rotate(rotationDegrees),
    )
}

@Composable
private fun BoxScope.ActingBadge() {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 4.dp, y = 4.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(HandyDesign.Colors.Surface)
            .border(1.5.dp, HandyDesign.Colors.Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bolt),
            contentDescription = null,
            tint = HandyDesign.Colors.Accent,
            modifier = Modifier.size(10.dp),
        )
    }
}

@Composable
private fun flyingWobble(state: WidgetState): Float {
    if (state != WidgetState.FLYING) return 0f
    val transition = rememberInfiniteTransition(label = "fly-wobble")
    val wobble by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobble",
    )
    return wobble
}

private fun Float.toDegrees(): Float =
    this * 180f / PI.toFloat()

private val WidgetCanvasSize = 64.dp
private val WidgetDiscSize = 48.dp
private val GlyphSize = 29.dp
