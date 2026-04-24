package com.handy.app.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.handy.app.theme.HandyColors

/**
 * Legacy glass + bubble aliases — scope §15 recipe #8. Surfaces now use
 * [HandyColors] warm-amber tokens; bubble colours are the V2 taxonomy
 * re-tuned to sit inside that palette.
 */
object GlassPalette {
    val DarkTopStop: Color get() = HandyColors.GlassTint
    val DarkBottomStop: Color get() = HandyColors.GlassTint
    val DarkStroke: Color get() = HandyColors.GlassBorder

    val DarkInputBg: Color get() = HandyColors.ChipBg
    val DarkInputStroke: Color get() = HandyColors.ChipBorder

    val DarkChipTop: Color get() = HandyColors.ChipBg
    val DarkChipBottom: Color get() = HandyColors.ChipBg

    val AccentBlue: Color get() = HandyColors.Accent
    val AccentBlueDark: Color get() = HandyColors.Accent
    val Teal: Color get() = HandyColors.BubbleAction
    val GreenResponse: Color get() = HandyColors.BubbleResponse
    val YellowTranscript: Color get() = HandyColors.BubbleTranscript
    val BlueNavigation: Color get() = HandyColors.BubbleNavigation
    val DangerRed: Color get() = HandyColors.Danger

    val GlassGradient: Brush = Brush.linearGradient(
        colors = listOf(HandyColors.GlassTint, HandyColors.GlassTint),
    )
    val ChipGradient: Brush = Brush.linearGradient(
        colors = listOf(HandyColors.ChipBg, HandyColors.ChipBg),
    )
}

/**
 * Rounded glass strip — used where a full [com.handy.app.theme.HandyGlassBottomSheet]
 * is not needed (e.g. legacy call sites). Prefer tokens from [HandyColors].
 */
fun Modifier.glassSurface(
    cornerDp: Int = 20,
    strokeColor: Color = HandyColors.GlassBorder,
): Modifier = this
    .clip(RoundedCornerShape(cornerDp.dp))
    .background(HandyColors.GlassTint)
    .border(BorderStroke(0.5.dp, strokeColor), RoundedCornerShape(cornerDp.dp))

@Composable
fun Modifier.glassCard(cornerDp: Int = 20): Modifier =
    this.glassSurface(cornerDp = cornerDp)
        .padding(12.dp)
