package com.handy.app.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.BuddyState

enum class WidgetState { IDLE, TOUCHED, DRAGGING, LISTENING, THINKING }

/**
 * V1-compatible entry point. Kept so existing call sites in
 * [com.handy.app.overlay.FloatingWidgetOverlayService] compile.
 *
 * New V2 render ([UnifiedBuddyContent]) lives below and is driven by
 * the richer [BuddyState] + [BuddyBubble] from the presenter.
 */
@Composable
fun WidgetContent(state: WidgetState) {
    HandyTheme(darkTheme = true) {
        val scaleTarget = if (state == WidgetState.TOUCHED || state == WidgetState.LISTENING) 1.06f else 1f
        val scale by animateFloatAsState(targetValue = scaleTarget, label = "widget-scale")

        val outlineColor by animateColorAsState(
            targetValue = when (state) {
                WidgetState.IDLE, WidgetState.DRAGGING -> HandyColors.Amber
                WidgetState.TOUCHED -> HandyColors.TextPrimary
                WidgetState.LISTENING -> HandyColors.Accent
                WidgetState.THINKING -> HandyColors.Accent
            },
            label = "widget-outline",
        )

        val fillColor by animateColorAsState(
            targetValue = when (state) {
                WidgetState.LISTENING -> HandyColors.AccentMuted
                else -> HandyColors.Surface
            },
            label = "widget-fill",
        )

        Box(
            modifier = Modifier
                .size(HandyDimens.WidgetSize)
                .scale(scale)
                .background(fillColor, CircleShape)
                .border(HandyDimens.WidgetBorder, outlineColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (state) {
                WidgetState.LISTENING -> Icons.Outlined.GraphicEq
                WidgetState.THINKING -> Icons.Outlined.MoreHoriz
                else -> Icons.Outlined.PanTool
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when (state) {
                    WidgetState.LISTENING -> HandyColors.Accent
                    else -> outlineColor
                },
            )
        }
    }
}

/**
 * V2 Unified Buddy render — Glass Lens + four-color bubble beside it.
 * Uses [AndroidView] to host the custom [LensRenderer] (cursorbuddy
 * recipe #1); bubbles are pure Compose.
 *
 * Scope §3 mutual-exclusion is enforced upstream by
 * [com.handy.app.overlay.OverlayPresenter] — this composable trusts
 * that [bubble] carries exactly one color at a time.
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
            // Lens — always present.
            Box(
                modifier = Modifier.size(HandyDimens.WidgetSize + 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    modifier = Modifier.size(HandyDimens.WidgetSize + 12.dp),
                    factory = { ctx -> LensRenderer(ctx) },
                    update = { view ->
                        view.saturationTint = tintFor(buddyState)
                        view.lensBaseScale = baseScaleFor(buddyState)
                        // pulseScale is driven by BezierFlightController
                        // through its own callback; this update keeps
                        // docked/thinking states at 1.0.
                        view.pulseScale = if (buddyState == BuddyState.POINTING) view.pulseScale else 1.0f
                    },
                )
                // State icon overlay — only on docked/listening/thinking
                // where cursorbuddy doesn't draw one. Pointing, flying,
                // acting, speaking show nothing; the lens is the focus.
                when (buddyState) {
                    BuddyState.DOCKED, BuddyState.DRAGGING -> IconGlyph(Icons.Outlined.PanTool, Color(0xFFF59E0B))
                    BuddyState.LISTENING -> IconGlyph(Icons.Outlined.GraphicEq, Color(0xFF3B82F6))
                    BuddyState.THINKING, BuddyState.STREAMING -> IconGlyph(Icons.Outlined.MoreHoriz, Color(0xFF3B82F6))
                    else -> Unit
                }
            }

            // Bubble beside the lens (recipe #1 companion positioning).
            if (bubble != null) {
                Spacer(Modifier.width(8.dp))
                BubbleChip(bubble)
            }
        }
    }
}

@Composable
private fun IconGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(18.dp)
            .offset(y = 0.dp),
    )
}

@Composable
private fun BubbleChip(bubble: BuddyBubble) {
    val (backgroundColor, textColor, text) = when (bubble) {
        is BuddyBubble.Transcript -> Triple(Color(0xFFFBBF24), Color(0xFF0B0B0F), bubble.text)
        is BuddyBubble.Action -> Triple(Color(0xFF14B8A6), Color.White, bubble.text)
        is BuddyBubble.Response -> Triple(Color(0xFF10B981), Color.White, bubble.text)
        is BuddyBubble.Navigation -> Triple(Color(0xFF3B82F6), Color.White, bubble.text)
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
    BuddyState.LISTENING -> LensRenderer.Tint.Cyan
    BuddyState.THINKING, BuddyState.STREAMING -> LensRenderer.Tint.Cyan
    BuddyState.FLYING, BuddyState.POINTING -> LensRenderer.Tint.Blue
    BuddyState.ACTING -> LensRenderer.Tint.Teal
    BuddyState.SPEAKING -> LensRenderer.Tint.Green
}

private fun baseScaleFor(state: BuddyState): Float = when (state) {
    BuddyState.THINKING -> 0.78f
    BuddyState.FLYING -> 0.9f
    BuddyState.POINTING -> 0.86f
    else -> 0.82f
}
