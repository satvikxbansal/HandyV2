package com.handy.app.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme

enum class WidgetState { IDLE, TOUCHED, DRAGGING, LISTENING, THINKING }

/**
 * Floating-widget visuals.
 *
 * Phase 3 ships a faithful static translation of the macOS visual
 * contract (idle hand → amber outline; listening → blue + waveform;
 * thinking → spinner). Polished motion (breathing, staggered pulses,
 * rotating gradient ring) lands when Phase 3 animation work resumes.
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
