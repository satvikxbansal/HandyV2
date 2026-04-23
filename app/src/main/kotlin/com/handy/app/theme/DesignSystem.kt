package com.handy.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Handy's Apple-class design tokens. Direct translation of the macOS
 * `DesignSystem.swift` values, with Inter substituted for SF Pro (see
 * guardrails / build plan §11).
 *
 * New tokens require a `DESIGN_NOTES.md` entry.
 */

object HandyColors {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF141419)
    val SurfaceElevated = Color(0xFF1C1C22)
    val TextPrimary = Color(0xFFF2F2F5)
    val TextSecondary = Color(0xFF9A9AA2)
    val Accent = Color(0xFF3B82F6)
    val AccentMuted = Color(0x403B82F6)
    val Amber = Color(0xFFF59E0B)
    val Success = Color(0xFF10B981)
    val Danger = Color(0xFFEF4444)
    val Border = Color(0xFF2A2A31)
}

object HandyDimens {
    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 16.dp
    val RadiusXl = 20.dp

    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
    val Space40 = 40.dp

    val WidgetSize = 56.dp
    val WidgetBorder = 2.dp
}

object HandyMotion {
    const val QuickMs: Int = 150
    const val DefaultMs: Int = 250
    const val EmphaticMs: Int = 350
}

private val darkColors = darkColorScheme(
    primary = HandyColors.Accent,
    onPrimary = HandyColors.TextPrimary,
    background = HandyColors.Background,
    onBackground = HandyColors.TextPrimary,
    surface = HandyColors.Surface,
    onSurface = HandyColors.TextPrimary,
    surfaceVariant = HandyColors.SurfaceElevated,
    onSurfaceVariant = HandyColors.TextSecondary,
    outline = HandyColors.Border,
    error = HandyColors.Danger,
)

private val lightColors = lightColorScheme(
    primary = HandyColors.Accent,
    onPrimary = Color.White,
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF0B0B0F),
    surface = Color.White,
    onSurface = Color(0xFF0B0B0F),
    surfaceVariant = Color(0xFFE8E8EC),
    onSurfaceVariant = Color(0xFF4A4A52),
    outline = Color(0xFFD7D7DC),
    error = HandyColors.Danger,
)

private val HandyTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

val LocalHandyColors = staticCompositionLocalOf { HandyColors }

@Composable
fun HandyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) darkColors else lightColors
    CompositionLocalProvider(LocalHandyColors provides HandyColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HandyTypography,
            content = content,
        )
    }
}
