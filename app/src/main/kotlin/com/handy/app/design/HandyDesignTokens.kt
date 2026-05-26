package com.handy.app.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * New design system from handy-new-design-handoff/project/src/tokens.jsx
 * (Amber theme). Parallel to com.handy.app.theme.HandyColors - the
 * legacy theme stays untouched for non-onboarding screens.
 *
 * EVERY ALPHA IS LITERAL FROM THE JSX. Do not "round" them.
 */
object HandyDesign {
    object Colors {
        // Surfaces
        val PageBg = Color(0xFF08090B)
        val Surface = Color(0xFF111317)
        val SurfaceElevated = Color(0xFF181A1F)
        val PanelSurface = Color(0xF0101114) // quick-chat overlay panel, 94% dark glass
        val SurfaceGlass = Color(0x14FFFFFF) // overlay only - 8% white
        val ContextBarSurface = Color(0xC7181A1F) // 78% dark glass

        // Borders
        val BorderSubtle = Color(0x14FFFFFF) // 8% white
        val BorderStrong = Color(0x24FFFFFF) // 14% white
        val ContextBarBorderSubtle = Color(0x1FFFFFFF) // 12% white

        // Text
        val TextPrimary = Color(0xFFF4F2EE)
        val TextSecondary = Color(0xFFA8A39B)
        val TextMuted = Color(0xFF6E6A63)

        // Accent - Claude orange
        val Accent = Color(0xFFD97757)
        val AccentDeep = Color(0xFFC76547) // splash linear-gradient stop 1
        val AccentInk = Color(0xFF1A0E07)
        val AccentSoft = Color(0x1ED97757) // 12%
        val AccentHairline = Color(0x4DD97757) // 30%

        // Semantic
        val Success = Color(0xFF7FB069)
        val SuccessSoft = Color(0x247FB069) // 14%
        val Danger = Color(0xFFD67D6B)
        val DangerSoft = Color(0x24D67D6B) // 14%

        // USP / per-card palette
        val See = Color(0xFFD97757) // alias of Accent
        val SeeSoft = Color(0x24D97757) // 14%
        val Point = Color(0xFF3B82F6) // chart-4 blue
        val PointSoft = Color(0x333B82F6) // 20%
        val PointHair = Color(0x4D3B82F6) // 30%
        val PointHairline = Color(0x4D3B82F6) // 30%
        // Widget glows (40% alpha colored drop-shadow tone)
        val AccentGlow = Color(0x66D97757) // 40% of D97757
        val PointGlow = Color(0x663B82F6) // 40% of 3B82F6
        val PointTrail = Color(0x993B82F6) // 60% of 3B82F6 - flying trail focal
        val Act = Color(0xFF7FB069) // emerald
        val ActSoft = Color(0x247FB069) // 14%
        val Violet = Color(0xFFB19CD9)
        val VioletSoft = Color(0x24B19CD9) // 14%

        // Atmospheric
        val Plum = Color(0xFF3B1F2E)
        val PlumDeep = Color(0xFF1E0F19)
        val PlumSoft = Color(0x388C5276) // ~22%
        // Honey - secondary warm accent for Voice surfaces. It stays
        // in Handy's warm family while reading differently from Brain.
        val Honey = Color(0xFFF0C674)
        val HoneySoft = Color(0x2EF0C674) // 18%
        val HoneyHair = Color(0x5CF0C674) // 36%
        val HoneyInk = Color(0xFF1F1709)
    }

    object Dimens {
        // Canonical from DIMENS_XML in scenes-handoff.jsx.
        val CornerCard = 16.dp
        val CornerButton = 14.dp
        val CornerPill = 14.dp
        val CornerSheet = 28.dp
        val CornerSheetTop = 24.dp
        val CornerTile = 10.dp
        val CornerTileLarge = 12.dp
        val CornerRow = 18.dp
        val CornerCardLarge = 22.dp

        val PrimaryButton = 52.dp
        val Field = 48.dp
        val Pill = 24.dp
        val Tile = 36.dp
        val TileLarge = 40.dp
        val TileSheet = 44.dp
        val Progress = 4.dp

        val ScreenPadding = 24.dp
        val RowVPadding = 14.dp
        val RowHPadding = 16.dp
        val GapRow = 10.dp
        val GapSection = 14.dp

        val Widget = 48.dp
        val WidgetShadowBlur = 14.dp
    }
}
