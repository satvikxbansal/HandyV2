# Handy — Android Redesign Handoff

## Overview

This package contains the redesigned UI for **Handy**, a context-aware AI assistant for Android. The prototypes cover the full product surface:

- **First-run permissions** onboarding
- **Floating widget** (the always-available entry point, with idle/hover/listening/thinking states)
- **Chat overlay** (quick-chat bottom sheet that floats over any app)
- **Full chat app** (the maximised view, empty + in-conversation states)
- **Settings** (with inline per-model API keys and Web Tools keys)

**Visual direction:** near-black glass surfaces, single warm amber accent (`#F0A868`), Inter as the sole font family, no emoji, no multi-gradient surfaces. One accent. Everything else neutral.

## About the Design Files

The files in `prototype/` are **design references built in HTML/React**. They are not production code to copy directly — treat them as the single source of truth for layout, measurements, colors, typography, and interaction states.

Your task in Cursor is to **recreate these designs in Android / Kotlin**, most likely using **Jetpack Compose**. Match the visual spec exactly; implement using your existing project patterns, themes, and component library. If the project doesn't yet have those patterns, this handoff doubles as a starting design system.

## Fidelity

**High-fidelity.** All colors, spacing, typography, radii, and shadows are final. Pixel-match the prototype. The only intentional variability is content (chip labels, suggestion copy) which is illustrative — use real backend-driven strings in production.

---

## Design Tokens

Drop these into your `ui/theme/` package. Shown as Compose `Color` / `Dp` / `TextStyle` so you can copy them directly.

### Colors

```kotlin
object HandyColors {
    // Surfaces
    val PageBg          = Color(0xFF07070A)   // app background
    val GlassTint       = Color(0x940C0A0E)   // rgba(12,10,14,0.58) — overlay fill
    val GlassBorder     = Color(0x38FFD2AA)   // rgba(255,210,170,0.22)
    val GlassHighlight  = Color(0x38FFDCB4)   // rgba(255,220,180,0.22)
    val InnerStroke     = Color(0x1AFFB478)   // rgba(255,180,120,0.10)
    val Divider         = Color(0x17FFDCB4)   // rgba(255,220,180,0.09)

    // Text
    val TextPrimary     = Color(0xFFFFF7EC)
    val TextSecondary   = Color(0x9EFFF7EC)   // 62% alpha
    val TextMuted       = Color(0x6BFFF7EC)   // 42% alpha

    // Accent (single warm amber)
    val Accent          = Color(0xFFF0A868)
    val AccentInk       = Color(0xFF2A1608)   // ink on accent button
    val AccentSoft      = Color(0x2EF0A868)   // 18% alpha, for lens glows & pressed states

    // Chips / inputs
    val ChipBg          = Color(0x17F0A868)   // 9% amber
    val ChipBorder      = Color(0x33F0A868)   // 20% amber

    // Status
    val Success         = Color(0xFF6FE0B3)
    val Danger          = Color(0xFFFF9A80)
}
```

### Typography

Single family: **Inter**, weights 400 / 500 / 600 / 700. Download from Google Fonts and declare a `FontFamily`.

```kotlin
val Inter = FontFamily(
    Font(R.font.inter_regular,  FontWeight.Normal),
    Font(R.font.inter_medium,   FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold,     FontWeight.Bold),
)

object HandyType {
    // Display — hero headings ("Ready when you are", Permissions H1)
    val Display       = TextStyle(fontFamily = Inter, fontSize = 26.sp, fontWeight = FontWeight.Bold,     lineHeight = 30.sp, letterSpacing = (-0.6).sp)
    // Title — header titles ("Handy" in overlay / full app / settings)
    val TitleLarge    = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.Bold,     lineHeight = 22.sp, letterSpacing = (-0.4).sp)
    val TitleMedium   = TextStyle(fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.Bold,     lineHeight = 20.sp, letterSpacing = (-0.3).sp)  // overlay header
    // Section header (Settings "Brain", "Modes", etc.)
    val SectionHeader = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = (-0.2).sp)
    // Body — chip / row titles, bubbles
    val Body          = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Normal,   lineHeight = 20.sp)
    val BodyStrong    = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    // Secondary text ("On Maps — how can I help?", settings row subtitles)
    val Caption       = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Normal,   lineHeight = 18.sp)
    val CaptionSmall  = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Normal,   lineHeight = 17.sp)
    // Overline — "TODAY · SEATTLE", "Granted" pill, Settings key labels
    val Overline      = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
}
```

### Spacing / Radii / Elevation

```kotlin
object HandyShapes {
    val PhoneCardRadius    = 28.dp   // overlay bottom sheet
    val LargeCard          = 20.dp   // "At a glance" style
    val Card               = 16.dp   // model cards, perm rows
    val Input              = 14.dp   // text field rounding
    val Pill               = 999.dp  // chips, send button, mic, field pill
    val IconBtnSquircle    = 8.dp    // bare icon buttons
}

object HandySpacing {
    val gutter    = 16.dp   // screen edge gutter
    val cardPad   = 18.dp   // padding inside overlay/glass cards
    val rowPad    = 14.dp   // padding inside toggle/perm rows
    val sectionY  = 22.dp   // vertical space between settings sections
    val stackS    = 8.dp
    val stackM    = 12.dp
    val stackL    = 16.dp
}
```

**Shadow recipe for glass cards** (overlay, model cards selected):
```
0dp 24dp 60dp rgba(0,0,0,0.55) — ambient drop
0dp 6dp 16dp rgba(0,0,0,0.4)   — contact
+ inner 0.5dp border @ GlassHighlight (top)
+ inner 0.5dp stroke @ InnerStroke (full)
```

**Backdrop blur:** 30dp blur + 1.6 saturation. If your min SDK is below API 31, fake it with a translucent fill (rgba(12,10,14,0.82)) and skip the blur on older devices.

---

## Screens

### 0 · Permissions onboarding — `P1`

**Purpose:** First-run consent flow. Walk the user through the four Android permissions Handy needs and make the privacy story explicit.

**Layout (top → bottom):**
1. Status bar (system).
2. Lens mark — a 72dp circle centered horizontally at top.
   - Fill: radial gradient `GlassHighlight → transparent @ 55%` over `AccentSoft`.
   - Stroke: 1.5dp `Accent @ 53%`.
   - Outer glow: 40dp blur `Accent @ 27%`.
   - Inside: `HandMark` icon at 32dp, tinted `Accent`.
3. Heading — "A few permissions, and you're set." — `Display` size, centered, 2-line break between comma and "and".
4. Sub — "Handy reads your screen to help — nothing is shared with our servers. You always have the final say." — `Caption`, `TextSecondary`, centered, max 300dp width, line-height 1.55.
5. **Permission rows** (gap 10dp, padding 16dp horizontal):
   - Microphone — granted
   - Notifications — granted
   - Draw over other apps — granted
   - Accessibility — **action** (user must grant)
6. **Privacy callout card** — Success-tinted, shield icon + "Your data stays yours. Handy talks directly to Anthropic using *your* API key. No servers of ours in the middle."
7. **CTA button** — "Open Handy →" — 52dp tall, 16dp corner, full-width minus 16dp side margins, Accent fill, AccentInk label, `BodyStrong` weight, drop shadow `0 10 24 -8 Accent @ 53%`.

**Permission row spec:**
- Height: content-sized, padding `14dp 16dp`.
- Corner: 16dp.
- Fill: `ChipBg`; border: 0.5dp `ChipBorder` (or `Success @ 30%` when granted).
- Leading icon: 36dp square, 10dp corner, fill `AccentSoft` (pending) or `Success @ 12%` (granted), icon 16dp in `Accent` or `Success`.
- Title: `BodyStrong`. Detail: `CaptionSmall`, `TextSecondary`.
- Trailing:
  - Granted → "Granted" pill: 28dp tall, padding 10dp, 999 corner, fill `Success @ 14%`, label `Success`, `Overline`-ish 11sp/600.
  - Pending → "Enable" button: 32dp tall, 10dp corner, fill `Accent`, label `AccentInk`, shadow `0 4 10 -3 Accent @ 53%`.

---

### 1 · Floating widget — `W1`, `W2`, `W3`

The always-on entry point that floats on top of other apps via `SYSTEM_ALERT_WINDOW`. Implement as a `Service` with a `ComposeView` hosted in a `WindowManager` overlay.

**Size:** 72dp circle. **Position:** user-draggable, snaps to edges.

**Four states:**

| State       | Visual                                                                 |
|-------------|------------------------------------------------------------------------|
| `idle`      | Glass circle, 1.5dp `Accent` rim, centered hand 28dp.                  |
| `hover`     | Same, but rim brightens to solid `Accent`; scale 1.05.                 |
| `listening` | Rim replaced by **animated waveform ring** — 12 radial bars animating vertical height (loop 0.9s, staggered 75ms per bar). Accent-coloured. |
| `thinking`  | Full rim rotating arc — conic gradient `Accent → transparent`, rotation 1.2s linear infinite. |

**Idle base recipe:**
```
Box(
  Modifier.size(72.dp).clip(CircleShape)
    .background(brush = Brush.radialGradient(
       0f to GlassHighlight, 0.55f to Color.Transparent))
    .background(AccentSoft)
    .border(1.5.dp, Accent.copy(alpha = 0.85f), CircleShape)
    .shadow(14.dp, CircleShape, ambientColor = Accent, spotColor = Accent)
)
```

---

### 2 · Chat overlay — `O1` through `O4`

The bottom-sheet quick chat. Sits **above any app** (Home, Photos, Maps, Docs), driven by the same overlay service. The card is full-width minus 12dp side gutters, 12dp from the bottom of the safe area, corner 28dp.

**Card chrome:**
- `GlassCard` — backdrop-blur 30dp + saturation 1.6, fill `GlassTint`, border 0.5dp `GlassBorder`.
- Inner specular highlight: radial gradient top-center `GlassHighlight → transparent`, opacity 0.6, via overlay `Box`.
- Shadow: see the glass shadow recipe above.

**Header row** (padding 18dp; gap 12dp):
- **Hand icon** — `HandMark` at 22dp, tinted `Accent`. *No circle frame.* Sits inline next to the title.
- **Title** — "Handy", 18sp / 700 / -0.3 letter-spacing, white.
- **Subtitle** (row 2) — `On {Host} — how can I help?`, `Caption`. `{Host}` is the foreground app ("Home", "Photos", "Maps", or the user's current app), rendered in `Accent` medium-weight.
- Trailing: two **bare icon buttons** (no circle outline) — `expand` and `close`, 16dp icons, 28dp square hit target, 0.75 opacity default, 1.0 on hover.

**Input row:**
- Mic — 40dp circle, fill `ChipBg`, border 0.5dp `ChipBorder`.
- Text field — flex-1, 40dp tall, 999 corner, fill `ChipBg`, border 0.5dp `ChipBorder` (→ `Accent` when focused), horizontal padding 16dp, placeholder "Ask me anything…" in `TextMuted`.
- Send — 40dp circle, fill `Accent`, icon 17dp `AccentInk`, shadow `0 6 14 -4 Accent @ 53%`.

**Quick chips** (gap 10dp, wraps):
- Max **2 chips per context** (configurable). Per-context defaults:
  - Home: `Open my calendar`, `Set a timer`
  - Photos: `Find photos from Paris`, `Make a collage`
  - Maps: `Nearest coffee`, `Avoid tolls`
- Chip: padding `7dp 12dp`, 999 corner, fill `ChipBg`, border 0.5dp `ChipBorder`, label `BodyStrong` 12sp.

**Light-app variant (`O4`):** same overlay on a white Docs app — the glass is dark-on-light. **No color change** inside the card; only the status bar flips to dark content.

---

### 3 · Full chat app — `F1`, `F2`

Launched by tapping the widget or expanding the overlay. Full-screen, no system bars.

**Header** (padding `18dp 20dp 14dp`, gap 14dp, bottom border 0.5dp `Divider`):
- Leading: `HandMark` 32dp, `Accent`. No circle.
- Title block (stacked, gap 3dp):
  - "Handy" — 20sp / 700 / -0.4 letter-spacing.
  - Status row — 6dp `Success` dot + "Ready" in `CaptionSmall` `TextSecondary`.
- Trailing cluster (gap 4dp, all identical 32dp square bare icon buttons, 0.72 opacity):
  - `history` icon
  - `collapse` icon (inverse of `expand` — arrows point INWARD to center)
  - `settings` icon

**Empty state (`F1`):**
- Vertical gap 24dp from header.
- Centered **lens mark**: 72dp circle, same recipe as Permissions lens, with `Accent @ 20%` 40dp outer glow. `HandMark` 32dp `Accent` centered.
- Title — "Ready when you are" — 22sp / 600 / -0.4, centered.
- Sub — "Ask a question, point at a button, or tell Handy to do something for you." — `Caption`, `TextSecondary`, two-line.
- **Suggestion grid** — 2×2, gap 8dp:
  - Sparkle icon / "Summarize this screen"
  - Camera icon / "What's in this photo?"
  - Bolt icon / "Set a 10-minute timer"
  - Globe icon / "Look this up online"
  - Card: padding `12dp 14dp`, 14dp corner, fill `ChipBg`, border 0.5dp `ChipBorder`.

**In-conversation (`F2`):**
- Context chip just under header — "📷 Context from **Photos** — Change", fill `ChipBg`, row of info.
- Timestamp pill `Today 6:29 PM` — `Overline`-size `TextMuted`, centered.
- **User bubble** — right-aligned, max width 78%, fill `AccentSoft`, corner 18dp / 6dp top-right (tailed), `Body` text.
- **Assistant bubble** — left-aligned, 24dp hand avatar to the left (with small glass circle is OK here since it's inline with the chat flow), fill `ChipBg`, corner 18dp / 6dp top-left.
- **"Acting" status pill** under the assistant bubble — animated 6dp `Success` dot (pulse 1.2s), "Working on it" label.
- **Photo attachments** — inline row of 3 gradient tiles, 72dp tall, 10dp corner, 6dp gap.

**Composer** (bottom):
- Padding `10dp 14dp 14dp`, top border 0.5dp `Divider`.
- Same `InputRow` as the overlay, placeholder "Ask Handy anything…".

---

### 4 · Settings — `S1`

Single scrolling list. **Sticky header** with back chevron + "Settings" title.

**Sections** (each is: icon bubble + title + optional subtitle + children):

**Brain** — pick the model that powers Handy. Your key unlocks it.
- Stacked `ModelCard`s.
- Each card: radio + title + detail, plus **inline API key field** when selected.
  - **Claude Sonnet 4.5** — Best reasoning · Anthropic — selected, key saved (shows "READY" pill + masked key).
  - **Claude Haiku 4.5** — Faster · lower cost — note "Uses the same key as Sonnet" with a success check.
  - **Gemini 2.5 Pro** — Coming soon — disabled.
- Key field: 42dp tall, 12dp corner, fill `rgba(0,0,0,0.25)`, border 0.5dp `ChipBorder`, 14dp left padding, monospace value or regular placeholder; trailing 30dp square eye + copy buttons.

**Modes**
- Toggle rows for "Assistant" (on), "Tutor", "Focus". `ToggleRow` spec in prototype.

**Triggers**
- Toggles: "Long-press floating widget" (on), "Volume-down hold" (on), "Hey Handy" (off, noted as battery-heavy).

**Web Tools**
- Toggle "Enable web search" (on).
- **Nested, indented:** Brave Search key, Jina Reader key, GitHub key — each a `KeyField`.

**Footer:** `Handy · 2.0.1 · Made for Android` — `CaptionSmall` `TextMuted`, centered, top padding 24dp.

---

## Interactions & Behavior

### Floating widget
- **Long-press** → starts voice capture (`listening` state). Release → `thinking` until model replies → expands into overlay.
- **Tap** → expand into chat overlay.
- **Drag** → reposition; snap to nearest edge on release. Persist position.
- **Swipe off bottom edge** → dismiss for session (with undo toast for 5s).

### Overlay ↔ Full app
- **Expand button** → animated morph from bottom-sheet card (360×variable) to full-screen. Use shared-element transitions (`Modifier.sharedElement` via `SharedTransitionScope` in Compose). 300ms, emphasized easing.
- **Close button** → dismiss overlay, return to widget.
- **Drag down on the card** → also closes.

### Chat
- **Assistant "acting" state** → while the model is calling tools, show the "Working on it" pill with a 6dp pulsing success dot (pulse animation 1.2s ease-in-out infinite, opacity 0.4 ↔ 1.0).
- **Voice button in input row** → switches input to waveform visualizer while recording.

### Settings
- **Model selection** → radio + card expand/collapse (300ms ease-out). Only the selected model shows its API key field.
- **API key field** → eye button toggles visibility (dots → actual chars). Copy button shows a 1.5s "Copied" toast pill.
- **Web search toggle** → when off, nested key fields animate to 0 height (clip-content) + 0.5 opacity.

### Permissions
- **Enable button** → triggers Android permission request flow. On grant → row flips to "Granted" variant with a 200ms crossfade and a success haptic.
- **Open Handy CTA** → disabled until at least Microphone + Notifications granted. Accessibility is recommended but skippable (footnote copy).

---

## State Management

Expose these `StateFlow`s (or equivalent) from your assistant service layer:

```kotlin
data class HandyUiState(
    val widgetState: WidgetState,              // Idle | Hover | Listening | Thinking
    val overlayVisible: Boolean,
    val hostAppLabel: String,                  // "Home", "Photos", "Maps", "Docs"…
    val messages: List<ChatMessage>,
    val isActing: Boolean,
    val acknowledgedPermissions: Set<Permission>,
)

data class HandySettings(
    val selectedModel: ModelId,
    val keys: Map<ModelId, String>,            // masked in UI
    val activeModes: Set<Mode>,
    val triggers: Set<Trigger>,
    val webSearchEnabled: Boolean,
    val webKeys: WebKeys,
)

sealed interface WidgetState { Idle; Hover; Listening; Thinking }
```

Key considerations:
- **`hostAppLabel`** must update when the foreground app changes (`UsageStatsManager`, or the Accessibility service events). Drives the "On Maps — how can I help?" subtitle and the chip suggestions.
- **`isActing`** is true whenever the model is executing tools. Drives the assistant bubble's "Working on it" pill.

---

## Required permissions (Android)

Map to the four cards in the Permissions screen:

| Card                      | Manifest permission                    | Runtime flow |
|---------------------------|----------------------------------------|--------------|
| Microphone                | `android.permission.RECORD_AUDIO`      | Standard runtime request. |
| Notifications             | `android.permission.POST_NOTIFICATIONS`| Runtime on 13+. |
| Draw over other apps      | `android.permission.SYSTEM_ALERT_WINDOW` | `Settings.canDrawOverlays()` → `ACTION_MANAGE_OVERLAY_PERMISSION` intent. |
| Accessibility             | `BIND_ACCESSIBILITY_SERVICE` (service) | Deep-link to `ACTION_ACCESSIBILITY_SETTINGS`. |

---

## Icons

All icons in the design are **1.6 stroke-width, rounded caps/joins, 24×24 viewport**. Use **Material Icons (Outlined)** where they exist, or inline these SVGs from `prototype/components/handy-primitives.jsx`:

- `mic`, `send`, `close`, `expand`, `collapse`, `settings`
- `sparkle`, `plus`, `chevronRight`, `check`, `eye`, `copy`
- `brain`, `modes`, `bolt`, `key`, `globe`, `waveform`, `camera`, `history`
- **`HandMark`** — the Handy logo (a raised palm glyph) — copy the SVG path as-is to an Android `VectorDrawable`. This is the ONLY branded mark.

Note the `expand` / `collapse` pairing: `expand` has arrows pointing **outward** to the four corners (→ full-screen). `collapse` is the exact mirror — arrows pointing **inward** to the center. Keep this pairing semantically consistent across surfaces.

---

## Files in this bundle

```
design_handoff_handy_android/
├── README.md                          ← this file
└── prototype/
    ├── Handy Redesign.html            ← main prototype; open in a browser
    ├── design-canvas.jsx              ← canvas shell (not production code)
    ├── tweaks-panel.jsx               ← in-design tweak controls (not production code)
    └── components/
        ├── handy-primitives.jsx       ← Theme tokens + icon SVGs + HandMark + GlassCard
        ├── handy-backdrops.jsx        ← Mock app backdrops (Home / Photos / Maps / Docs)
        ├── handy-widget.jsx           ← Floating widget, all four states
        ├── handy-overlay.jsx          ← Chat overlay (bottom sheet)
        ├── handy-fullapp.jsx          ← Full chat app (empty + conversation)
        ├── handy-settings.jsx         ← Settings screen
        └── handy-permissions.jsx      ← Onboarding permissions screen
```

Open `prototype/Handy Redesign.html` in any modern browser to explore. The design is presented on a pan/zoom canvas — drag to pan, pinch to zoom, click any artboard to focus fullscreen.

---

## Suggested Compose implementation order

1. **Theme + tokens** (`HandyColors`, `HandyType`, `HandyShapes`) — 1 session.
2. **`HandMark` vector drawable** + primitives (`GlassCard`, `BareIconBtn`, chip, input row) — 1 session.
3. **Floating widget service** with state machine — 1 session.
4. **Chat overlay composable** (bottom sheet) — 1 session.
5. **Full chat screen** (empty + conversation) — 1–2 sessions.
6. **Settings screen** with nested sections — 1 session.
7. **Permissions onboarding** + runtime request flow — 1 session.

Ship behind a feature flag until the whole surface is migrated.
