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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R

/**
 * Handy's design tokens — warm-amber glass direction.
 *
 * Translated from the Claude-Design handoff
 * (`handy-android-design system/project/design_handoff_handy_android/README.md`)
 * and the `handy-primitives.jsx` theme object (`HandyThemes.bold`).
 *
 * Discipline: one accent (warm amber), one font family (Inter), no
 * multi-gradient surfaces. Everything else is neutral. New tokens
 * require a DESIGN_NOTES.md entry per the repo rules.
 */
object HandyColors {
    // Canvas ------------------------------------------------------
    /** App background — near-black with a warm undertone. */
    val PageBg        : Color = Color(0xFF07070A)
    /** Legacy alias — `surface` and `pageBg` collapse to the same token. */
    val Background    : Color = PageBg

    // Glass surface tokens ---------------------------------------
    // Canonical values from `handy-android-design system/.../README.md`
    // § Design Tokens → Colors. Restored verbatim after DL-031/032
    // drifted them (see DL-033). This remains the dense fallback when
    // the bounded screenshot blur is unavailable; API 31+ panel blur
    // draws a pre-panel screenshot inside the sheet and relaxes only
    // the sheet-local alpha.
    /**
     * Near-black glass fill — **cool-neutral** undertone (R=12 G=10 B=14).
     * The 82% alpha below is the handoff's explicit no-blur fallback.
     * Spec reference colour at 58% alpha (`0x940C0A0E`) is kept in a
     * comment so future work with real backdrop blur can swap back.
     */
    val GlassTint     : Color = Color(0xD10C0A0E) // rgba(12,10,14,0.82)
    /** Warm inner top highlight used for specular sheen. rgba(255,220,180,0.22). */
    val GlassHighlight: Color = Color(0x38FFDCB4)
    /** 0.5dp hairline border on glass surfaces. rgba(255,210,170,0.22). */
    val GlassBorder   : Color = Color(0x38FFD2AA)
    /** Inner stroke that fakes refraction. rgba(255,180,120,0.10). */
    val InnerStroke   : Color = Color(0x1AFFB478)

    // Text --------------------------------------------------------
    val TextPrimary   : Color = Color(0xFFFFF7EC)
    val TextSecondary : Color = Color(0x9EFFF7EC) // 62%
    val TextMuted     : Color = Color(0x6BFFF7EC) // 42%

    // Accent ------------------------------------------------------
    /** Warm amber — the only accent in the system. */
    val Accent        : Color = Color(0xFFF0A868)
    /** Ink colour for labels on the accent fill. */
    val AccentInk     : Color = Color(0xFF2A1608)
    /** 18% accent — lens glow, user bubble fill, pressed states. */
    val AccentSoft    : Color = Color(0x2EF0A868)

    // Chips + dividers -------------------------------------------
    /** 9% amber — neutral chip backgrounds. */
    val ChipBg        : Color = Color(0x17F0A868)
    /** 20% amber — chip hairline border. */
    val ChipBorder    : Color = Color(0x33F0A868)
    /** 9% warm — section dividers. */
    val Divider       : Color = Color(0x17FFDCB4)

    // Status colours (used sparingly, per scope §3 bubble taxonomy) ------
    /** Muted jade — assistant response bubble + "Ready" dot. */
    val Success       : Color = Color(0xFF6FE0B3)
    /** Coral — error banners + destructive CTAs. */
    val Danger        : Color = Color(0xFFFF9A80)
    /** Listening pulse — same amber for visual continuity. */
    val Listening     : Color = Accent

    // V2 four-colour bubble taxonomy (scope §3) — re-tuned to sit
    // inside the amber palette. Preserves yellow/teal/green/blue
    // semantic distinction without a second hue becoming dominant.
    /** Yellow — live user transcript while listening. */
    val BubbleTranscript : Color = Color(0xFFF5C678) // warm amber-yellow
    /** Teal — passive action-in-progress (V2 tap-for-me). */
    val BubbleAction     : Color = Color(0xFF7BCFC2) // sage teal
    /** Green — clamped assistant response. */
    val BubbleResponse   : Color = Color(0xFF6FE0B3)
    /** Blue — pointer navigation label. */
    val BubbleNavigation : Color = Color(0xFF8BB6E8) // muted slate-blue

    // Surface aliases kept for downstream compatibility during the
    // migration. All of them resolve to the new glass surface tokens.
    @Deprecated(
        "Use GlassTint or a GlassCard primitive.",
        ReplaceWith("HandyColors.GlassTint", "com.handy.app.theme.HandyColors"),
    )
    val Surface           : Color = Color(0xFF141419)
    @Deprecated(
        "Use GlassTint for elevated surfaces.",
        ReplaceWith("HandyColors.GlassTint", "com.handy.app.theme.HandyColors"),
    )
    val SurfaceElevated   : Color = Color(0xFF1C1C22)
    @Deprecated(
        "Use HandyColors.ChipBorder or GlassBorder.",
        ReplaceWith("HandyColors.ChipBorder", "com.handy.app.theme.HandyColors"),
    )
    val Border            : Color = Color(0x33F0A868)
    @Deprecated(
        "Use HandyColors.AccentSoft for accent-tinted surfaces.",
        ReplaceWith("HandyColors.AccentSoft", "com.handy.app.theme.HandyColors"),
    )
    val AccentMuted       : Color = AccentSoft
    @Deprecated(
        "Use HandyColors.Accent. There is no secondary amber token — one accent only.",
        ReplaceWith("HandyColors.Accent", "com.handy.app.theme.HandyColors"),
    )
    val Amber             : Color = Accent
}

/**
 * Explicit dp / radius scale. Values come from the handoff README's
 * `HandySpacing` and `HandyShapes` objects verbatim so Compose
 * measurements pixel-match the prototype.
 */
object HandyDimens {
    // Legacy aliases (kept during migration) — downstream code used
    // these names; they now map onto the new scale.
    val Space4  = 4.dp
    val Space8  = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
    val Space40 = 40.dp

    // V2 named scale (prototype tokens).
    val Gutter       = 16.dp  // screen edge gutter
    val CardPad      = 18.dp  // padding inside overlay / glass cards
    val RowPad       = 14.dp  // padding inside toggle / perm rows
    val SectionY     = 22.dp  // vertical space between settings sections
    val StackS       = 8.dp
    val StackM       = 12.dp
    val StackL       = 16.dp

    // Radii
    val RadiusSm     = 8.dp   // bare icon button squircle
    val RadiusMd     = 12.dp  // key field, small input pill
    val RadiusLg     = 14.dp  // input pill + empty-state suggestion cards
    val RadiusXl     = 16.dp  // perm rows, model cards, CTA button
    val RadiusCard   = 20.dp  // "At a glance" style cards (also used for toggle rows)
    val RadiusGlass  = 28.dp  // overlay bottom sheet
    val RadiusPill   = 999.dp // chips, send circle, text field pill

    // Widget lens
    /** Hero circle size — onboarding permissions lens, chat empty-state hero. */
    val WidgetSize     = 72.dp
    /**
     * Floating widget circle size — the draggable lens the user taps.
     * Intentionally compact (48dp) so the hand icon inside reads as the
     * dominant glyph; the amber ring is just a hairline around it. The
     * hero uses [WidgetSize] (72dp) instead where the hand is meant to
     * sit inside a larger halo.
     */
    val WidgetLensSize = 48.dp
    val WidgetBorder   = 1.5.dp // amber idle rim
    val WidgetGlow     = 40.dp  // outer halo for lens mark
}

/**
 * Motion tokens — scope §12 + handoff README interactions section.
 * Durations are `Int` to match Compose's animation spec APIs.
 */
object HandyMotion {
    const val QuickMs    : Int = 150  // opacity / hover
    const val DefaultMs  : Int = 200  // model-card expand / toggle flip
    const val EmphaticMs : Int = 300  // overlay ↔ full-app morph
    const val StreamMs   : Int = 500  // streaming dots stagger
    const val ListeningPulseMs : Int = 900  // 12-bar waveform period
    const val RotatingRimMs    : Int = 1200 // thinking rim period
    const val IdlePulseMs      : Int = 1600 // listening halo pulse
}

/* ------------------------------------------------------------------ */
/* Inter font family                                                  */
/* ------------------------------------------------------------------ */

val Inter: FontFamily = FontFamily(
    Font(R.font.inter_regular,  FontWeight.Normal),
    Font(R.font.inter_medium,   FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold,     FontWeight.Bold),
)

/**
 * Typography tokens — Inter with the exact `fontSize` / `fontWeight` /
 * `lineHeight` / `letterSpacing` values from the handoff `HandyType`
 * block. Match these in every composable instead of inventing local
 * `TextStyle`s.
 */
object HandyType {
    /** Hero headings — "Ready when you are", Permissions H1. */
    val Display       : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 26.sp, fontWeight = FontWeight.Bold,
        lineHeight = 30.sp, letterSpacing = (-0.6).sp,
    )
    /** Large title — full-app header "Handy". */
    val TitleLarge    : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        lineHeight = 22.sp, letterSpacing = (-0.4).sp,
    )
    /** Medium title — overlay header "Handy". */
    val TitleMedium   : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        lineHeight = 20.sp, letterSpacing = (-0.3).sp,
    )
    /**
     * Settings screen top-bar title — 20sp **SemiBold** (not Bold) with
     * -0.3 letter-spacing. Distinct from `TitleLarge` (20sp Bold) which
     * is used by the full chat app header. Spec: `handy-settings.jsx`.
     */
    val SettingsTitle : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp, letterSpacing = (-0.3).sp,
    )
    /**
     * Empty-state hero title — 22sp SemiBold -0.4. Distinct from
     * `Display` (26sp Bold). Spec: `handy-fullapp.jsx` `EmptyState`
     * "Ready when you are" title.
     */
    val EmptyHeroTitle : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp, letterSpacing = (-0.4).sp,
    )
    /** Settings "Brain", "Modes" headers. */
    val SectionHeader : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp, letterSpacing = (-0.2).sp,
    )
    /** Chip labels, row titles, chat bubbles. */
    val Body          : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    )
    val BodyStrong    : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    )
    /** Subtitle / caption — "On Maps — how can I help?", settings row detail. */
    val Caption       : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    )
    val CaptionSmall  : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Normal,
        lineHeight = 17.sp,
    )
    /** Overline — "TODAY · SEATTLE", "Granted" pill, Settings key labels. */
    val Overline      : TextStyle = TextStyle(
        fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
    )
}

/* ------------------------------------------------------------------ */
/* Material3 scheme mapping                                           */
/* ------------------------------------------------------------------ */

private val darkColors = darkColorScheme(
    primary = HandyColors.Accent,
    onPrimary = HandyColors.AccentInk,
    background = HandyColors.PageBg,
    onBackground = HandyColors.TextPrimary,
    surface = HandyColors.PageBg,
    onSurface = HandyColors.TextPrimary,
    surfaceVariant = HandyColors.GlassTint,
    onSurfaceVariant = HandyColors.TextSecondary,
    outline = HandyColors.ChipBorder,
    outlineVariant = HandyColors.Divider,
    error = HandyColors.Danger,
    onError = HandyColors.AccentInk,
)

private val lightColors = lightColorScheme(
    // Light scheme is retained for AA-contrast fallback only — the
    // product ships dark. No surface treatment is designed for light
    // mode in V2; tokens here are intentionally neutral.
    primary = HandyColors.Accent,
    onPrimary = HandyColors.AccentInk,
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF1A1A1F),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1F),
    surfaceVariant = Color(0xFFF3EEE6),
    onSurfaceVariant = Color(0xFF6A6A72),
    outline = Color(0xFFE0D9CE),
    outlineVariant = Color(0xFFEFE7DA),
    error = HandyColors.Danger,
    onError = HandyColors.AccentInk,
)

private val HandyTypography = Typography(
    displayLarge  = HandyType.Display,
    titleLarge    = HandyType.TitleLarge,
    titleMedium   = HandyType.TitleMedium,
    bodyLarge     = HandyType.Body,
    bodyMedium    = HandyType.Body,
    bodySmall     = HandyType.Caption,
    labelLarge    = HandyType.BodyStrong,
    labelMedium   = HandyType.CaptionSmall,
    labelSmall    = HandyType.Overline,
)

/**
 * App-wide token access. Provided alongside `MaterialTheme` so
 * composables can read raw `HandyColors`, `HandyType`, etc. without
 * depending on Material3's reduced colour scheme.
 */
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
