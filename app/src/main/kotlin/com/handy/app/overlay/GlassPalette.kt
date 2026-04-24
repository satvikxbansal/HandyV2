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

/**
 * Glassmorphism palette — cursorbuddy recipe #8 / scope §15.
 *
 * We deliberately do NOT use `WindowManager.LayoutParams.FLAG_BLUR_BEHIND`
 * or `RenderEffect.createBlurEffect` on the panel window. Blur is a
 * **surface** property here: two-stop TL→BR gradient + hairline stroke +
 * software-layer shadow. See `DESIGN_NOTES.md` → "cursorbuddy licensing".
 */
object GlassPalette {
    // Dark surface tokens — hex-alpha encoded.
    val DarkTopStop: Color = Color(0xEE243243)
    val DarkBottomStop: Color = Color(0xD91A2432)
    val DarkStroke: Color = Color(0x66FFFFFF)

    val DarkInputBg: Color = Color(0xCC101A26)
    val DarkInputStroke: Color = Color(0x66FFFFFF)

    val DarkChipTop: Color = Color(0xD9334B5D)
    val DarkChipBottom: Color = Color(0xBF243445)

    val AccentBlue: Color = Color(0xFF4FC3F7)
    val AccentBlueDark: Color = Color(0xFF81D4FA)
    val Teal: Color = Color(0xFF14B8A6)
    val GreenResponse: Color = Color(0xFF10B981)
    val YellowTranscript: Color = Color(0xFFFBBF24)
    val BlueNavigation: Color = Color(0xFF3B82F6)
    val DangerRed: Color = Color(0xFFEF4444)

    val GlassGradient: Brush = Brush.linearGradient(
        colors = listOf(DarkTopStop, DarkBottomStop),
    )
    val ChipGradient: Brush = Brush.linearGradient(
        colors = listOf(DarkChipTop, DarkChipBottom),
    )
}

/**
 * Helper: rounded glass surface with the V2 stroke + gradient. 20-22 dp
 * corners; software-layer shadow is the parent container's job (the
 * window itself stays unblurred — recipe #8).
 */
fun Modifier.glassSurface(
    cornerDp: Int = 20,
    strokeColor: Color = GlassPalette.DarkStroke,
): Modifier = this
    .clip(RoundedCornerShape(cornerDp.dp))
    .background(GlassPalette.GlassGradient)
    .border(BorderStroke(1.dp, strokeColor), RoundedCornerShape(cornerDp.dp))

@Composable
fun Modifier.glassCard(cornerDp: Int = 20): Modifier =
    this.glassSurface(cornerDp = cornerDp)
        .padding(12.dp)
