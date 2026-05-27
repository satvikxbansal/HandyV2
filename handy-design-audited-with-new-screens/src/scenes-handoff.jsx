// scenes-handoff.jsx — Compose theme handoff & composable name map.
// These artboards are pure documentation — they exist so the user's Android
// agent can copy code directly out of the canvas.

function CodeBlock({ title, language, code }) {
  const t = HANDY_TOKENS.amber;
  return (
    <div style={{
      background: "#0c0d10",
      border: `1px solid ${t.colors.borderSubtle}`,
      borderRadius: 14,
      overflow: "hidden",
      display: "flex", flexDirection: "column",
    }}>
      <div style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "10px 14px",
        background: "rgba(255,255,255,0.02)",
        borderBottom: `1px solid ${t.colors.borderSubtle}`,
      }}>
        <div style={{ font: `500 12px/1 ${HANDY_TYPE.fontMono}`, color: t.colors.textPrimary }}>{title}</div>
        <div style={{ font: `400 10px/1 ${HANDY_TYPE.fontMono}`, color: t.colors.textMuted, letterSpacing: "0.08em", textTransform: "uppercase" }}>{language}</div>
      </div>
      <pre style={{
        margin: 0, padding: "14px 16px",
        font: `400 11.5px/1.55 ${HANDY_TYPE.fontMono}`,
        color: "#e1ddd5",
        background: "transparent",
        overflow: "auto", flex: 1, whiteSpace: "pre",
      }}>{code}</pre>
    </div>
  );
}

const COLOR_KT = `// ui/theme/Color.kt
package app.handy.ui.theme

import androidx.compose.ui.graphics.Color

// Surfaces
val PageBg          = Color(0xFF08090B)
val Surface         = Color(0xFF111317)
val SurfaceElevated = Color(0xFF181A1F)

// Borders (alpha applied to white)
val BorderSubtle = Color(0x14FFFFFF)   //  8 %
val BorderStrong = Color(0x24FFFFFF)   // 14 %

// Text
val TextPrimary   = Color(0xFFF4F2EE)
val TextSecondary = Color(0xFFA8A39B)
val TextMuted     = Color(0xFF6E6A63)

// Accent — Claude orange
val Accent        = Color(0xFFD97757)
val AccentInk     = Color(0xFF1A0E07)
val AccentSoft    = Color(0x1ED97757)   // 12 %
val AccentHair    = Color(0x4DD97757)   // 30 %

// Semantic
val Success     = Color(0xFF7FB069)
val SuccessSoft = Color(0x247FB069)    // 14 %
val Danger      = Color(0xFFD67D6B)
val DangerSoft  = Color(0x24D67D6B)    // 14 %`;

const THEME_KT = `// ui/theme/HandyTheme.kt
package app.handy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// We bend Material3's color scheme to mirror our tokens. Anything the
// Material widgets request maps to a Handy token; everything app-side reads
// from LocalHandyColors which is the canonical token table.

private val HandyDark = darkColorScheme(
    background      = PageBg,
    surface         = Surface,
    surfaceVariant  = SurfaceElevated,
    primary         = Accent,
    onPrimary       = AccentInk,
    onBackground    = TextPrimary,
    onSurface       = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline         = BorderStrong,
    outlineVariant  = BorderSubtle,
    error           = Danger,
)

data class HandyColors(
    val pageBg: androidx.compose.ui.graphics.Color = PageBg,
    val surface: androidx.compose.ui.graphics.Color = Surface,
    val surfaceElevated: androidx.compose.ui.graphics.Color = SurfaceElevated,
    val borderSubtle: androidx.compose.ui.graphics.Color = BorderSubtle,
    val borderStrong: androidx.compose.ui.graphics.Color = BorderStrong,
    val textPrimary: androidx.compose.ui.graphics.Color = TextPrimary,
    val textSecondary: androidx.compose.ui.graphics.Color = TextSecondary,
    val textMuted: androidx.compose.ui.graphics.Color = TextMuted,
    val accent: androidx.compose.ui.graphics.Color = Accent,
    val accentInk: androidx.compose.ui.graphics.Color = AccentInk,
    val accentSoft: androidx.compose.ui.graphics.Color = AccentSoft,
    val accentHair: androidx.compose.ui.graphics.Color = AccentHair,
    val success: androidx.compose.ui.graphics.Color = Success,
    val successSoft: androidx.compose.ui.graphics.Color = SuccessSoft,
    val danger: androidx.compose.ui.graphics.Color = Danger,
    val dangerSoft: androidx.compose.ui.graphics.Color = DangerSoft,
)

val LocalHandyColors = staticCompositionLocalOf { HandyColors() }

@Composable
fun HandyTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHandyColors provides HandyColors()) {
        MaterialTheme(
            colorScheme = HandyDark,
            typography  = HandyTypography,
            content     = content,
        )
    }
}`;

const TYPE_KT = `// ui/theme/Type.kt
package app.handy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em

// Söhne ships in assets/fonts/ — Inter is the fallback the OS will pick
// automatically if the bundled file is missing.
val HandySans = FontFamily(
    Font(R.font.soehne_book,     FontWeight.Normal),
    Font(R.font.soehne_kraftig,  FontWeight.Medium),
    Font(R.font.soehne_halbfett, FontWeight.SemiBold),
)

val HandyTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = HandySans, fontSize = 32.sp, lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.022).em,
    ),
    titleLarge = TextStyle(
        fontFamily = HandySans, fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.012).em,
    ),
    titleMedium = TextStyle(
        fontFamily = HandySans, fontSize = 18.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.008).em,
    ),
    bodyLarge = TextStyle(
        fontFamily = HandySans, fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyMedium = TextStyle(
        fontFamily = HandySans, fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = HandySans, fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelSmall = TextStyle(
        fontFamily = HandySans, fontSize = 11.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.08.em,
    ),
)`;

const COLORS_XML = `<!-- res/values/colors.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <color name="handy_page_bg">#FF08090B</color>
  <color name="handy_surface">#FF111317</color>
  <color name="handy_surface_elevated">#FF181A1F</color>
  <color name="handy_border_subtle">#14FFFFFF</color>
  <color name="handy_border_strong">#24FFFFFF</color>
  <color name="handy_text_primary">#FFF4F2EE</color>
  <color name="handy_text_secondary">#FFA8A39B</color>
  <color name="handy_text_muted">#FF6E6A63</color>
  <color name="handy_accent">#FFD97757</color>
  <color name="handy_accent_ink">#FF1A0E07</color>
  <color name="handy_accent_soft">#1ED97757</color>
  <color name="handy_accent_hair">#4DD97757</color>
  <color name="handy_success">#FF7FB069</color>
  <color name="handy_danger">#FFD67D6B</color>
</resources>`;

const DIMENS_XML = `<!-- res/values/dimens.xml — canonical dp values -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <!-- Surfaces -->
  <dimen name="handy_corner_card">16dp</dimen>
  <dimen name="handy_corner_button">14dp</dimen>
  <dimen name="handy_corner_pill">14dp</dimen>
  <dimen name="handy_corner_sheet">28dp</dimen>
  <dimen name="handy_corner_tile">10dp</dimen>

  <!-- Heights -->
  <dimen name="handy_primary_button">52dp</dimen>
  <dimen name="handy_field">48dp</dimen>
  <dimen name="handy_pill">24dp</dimen>
  <dimen name="handy_switch">26dp</dimen>
  <dimen name="handy_tile">36dp</dimen>
  <dimen name="handy_progress">4dp</dimen>

  <!-- Layout -->
  <dimen name="handy_screen_padding">24dp</dimen>
  <dimen name="handy_row_v_padding">14dp</dimen>
  <dimen name="handy_row_h_padding">16dp</dimen>
  <dimen name="handy_gap_row">10dp</dimen>
  <dimen name="handy_gap_section">14dp</dimen>

  <!-- Floating widget -->
  <dimen name="handy_widget">48dp</dimen>
  <dimen name="handy_widget_shadow_blur">14dp</dimen>
</resources>`;

function ComposeThemeHandoff() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <div style={{
        width: 1160, height: 1100,
        background: t.colors.pageBg,
        borderRadius: 20, padding: 32,
        fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
        display: "flex", flexDirection: "column",
      }}>
        <div>
          <div style={{ font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`, letterSpacing: "-0.015em" }}>
            Compose theme handoff
          </div>
          <div style={{ font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary, marginTop: 6, maxWidth: 720 }}>
            Drop these five files into <code style={{ color: t.colors.accent, font: `12px ${HANDY_TYPE.fontMono}` }}>app/src/main</code>. Token names match the Color.kt identifiers in the swatch artboard.
          </div>
        </div>

        <div style={{
          marginTop: 22, flex: 1, display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gridTemplateRows: "1fr 1fr",
          gap: 18, minHeight: 0,
        }}>
          <CodeBlock title="ui/theme/Color.kt" language="kotlin" code={COLOR_KT} />
          <CodeBlock title="ui/theme/HandyTheme.kt" language="kotlin" code={THEME_KT} />
          <CodeBlock title="ui/theme/Type.kt" language="kotlin" code={TYPE_KT} />
          <div style={{ display: "grid", gridTemplateRows: "1fr 1fr", gap: 14, minHeight: 0 }}>
            <CodeBlock title="res/values/colors.xml" language="xml" code={COLORS_XML} />
            <CodeBlock title="res/values/dimens.xml" language="xml" code={DIMENS_XML} />
          </div>
        </div>
      </div>
    </ThemeProvider>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  COMPOSABLE MAP — every screen → its top-level composable
// ────────────────────────────────────────────────────────────────────────

const COMPOSABLE_MAP = [
  { screen: "Splash",                 composable: "SplashScreen()",                inputs: "—",                                                    notes: "1.6s timed, then nav to onboarding." },
  { screen: "Value page",             composable: "ValueScreen(onContinue)",       inputs: "—",                                                    notes: "Static. Three USPCard rows + PrivacyStrip." },
  { screen: "Permissions",            composable: "PermissionsScreen(state, onGrant, onContinue)", inputs: "state: PermissionsUiState",            notes: "state.grantedCount drives ProgressBar." },
  { screen: "Chat ready",             composable: "ChatScreen(state, onSend, onMic)", inputs: "state: ChatUiState.Empty",                          notes: "QuickPromptCard grid lives in HomeQuickPromptsRow." },
  { screen: "Chat active",            composable: "ChatScreen(state, …)",          inputs: "state: ChatUiState.Conversation",                      notes: "Same composable; switches on state class." },
  { screen: "Reduced-mode banner",    composable: "ReducedModeBanner(onEnableA11y)", inputs: "—",                                                  notes: "Mounted above ChatComposer when a11y is off." },
  { screen: "Floating widget",        composable: "FloatingWidget(state)",         inputs: "state: WidgetState.{Idle, Listening, Thinking, Flying, Pointing, Acting}", notes: "Hosted by an OverlayService; state drives animation." },
  { screen: "Overlay quick chat",     composable: "OverlayQuickChatPanel(onSend, onExpand, onClose)", inputs: "ctx: HostAppContext",                notes: "Glass background. Chips come from QuickPromptCatalog.forApp(ctx)." },
  { screen: "Tap-for-me sheet",       composable: "TapConfirmSheet(target, onConfirm, onCancel)", inputs: "target: TapTarget(label, bounds, app)", notes: "8s expiry; auto-cancels." },
  { screen: "Recipe approval sheet",  composable: "RecipeApprovalSheet(recipe, onRun, onCancel)", inputs: "recipe: Recipe(steps[])",               notes: "Per-step sensitive badge from recipe.steps[i].sensitive." },
  { screen: "Audit log",              composable: "AuditScreen(entries, panic, onEndPanic)", inputs: "entries: List<AuditEntry>",                  notes: "Day headers stick. PanicBanner shown if panic != null." },
  { screen: "Settings — accordion",   composable: "SettingsAccordionScreen(state, on…)", inputs: "state: SettingsUiState",                         notes: "Default variant. Each section is a HandyAccordion." },
  { screen: "Settings — tabbed",      composable: "SettingsTabbedScreen(state, on…)", inputs: "state: SettingsUiState",                            notes: "Alt variant. SettingsTabs pinned under the title." },
];

function ComposableMap() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <div style={{
        width: 1160, height: 700,
        background: t.colors.pageBg,
        borderRadius: 20, padding: 32,
        fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
        display: "flex", flexDirection: "column",
      }}>
        <div>
          <div style={{ font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`, letterSpacing: "-0.015em" }}>
            Composable name map
          </div>
          <div style={{ font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary, marginTop: 6, maxWidth: 720 }}>
            Suggested top-level composable per screen — what to write in Compose alongside the existing ViewModel layer.
          </div>
        </div>

        {/* Table */}
        <div style={{
          marginTop: 20, flex: 1, overflow: "hidden",
          background: t.colors.surface,
          border: `1px solid ${t.colors.borderSubtle}`,
          borderRadius: 14,
        }}>
          {/* head */}
          <div style={{
            display: "grid", gridTemplateColumns: "1.1fr 1.6fr 1.4fr 1.8fr",
            padding: "10px 16px",
            background: t.colors.surfaceElevated,
            borderBottom: `1px solid ${t.colors.borderSubtle}`,
            ...typeStyle("overline", t), color: t.colors.textMuted,
          }}>
            <div>Screen</div>
            <div>Composable</div>
            <div>Inputs</div>
            <div>Notes</div>
          </div>
          {COMPOSABLE_MAP.map((row, i) => (
            <div key={i} style={{
              display: "grid", gridTemplateColumns: "1.1fr 1.6fr 1.4fr 1.8fr",
              padding: "10px 16px",
              borderBottom: i === COMPOSABLE_MAP.length - 1 ? "none" : `1px solid ${t.colors.borderSubtle}`,
              alignItems: "center",
            }}>
              <div style={{ font: `500 13px/1.4 ${HANDY_TYPE.fontBody}`, color: t.colors.textPrimary }}>{row.screen}</div>
              <div style={{ font: `400 12px/1.5 ${HANDY_TYPE.fontMono}`, color: t.colors.accent }}>{row.composable}</div>
              <div style={{ font: `400 12px/1.5 ${HANDY_TYPE.fontMono}`, color: t.colors.textSecondary }}>{row.inputs}</div>
              <div style={{ font: `400 12px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary }}>{row.notes}</div>
            </div>
          ))}
        </div>
      </div>
    </ThemeProvider>
  );
}

window.ComposeThemeHandoff = ComposeThemeHandoff;
window.ComposableMap = ComposableMap;
