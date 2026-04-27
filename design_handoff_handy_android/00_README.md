# Handy Android — Design Handoff

This bundle is for a Cursor / coding-agent session that's about to update the
Kotlin codebase to match the redesign. It contains:

- `prototype/` — the working HTML prototype (open `Handy Redesign.html` in
  any browser). All screens are reachable; this is the source of truth for
  visuals, layout, and copy.
- `01_icons_and_drawables.md` — every icon as a Compose-ready
  `<vector>` drawable
- `02_chat_overlay.md` — chat overlay (rounded-bottom card, header, bubbles, input)
- `03_floating_widget.md` — floating widget with the four states
- `04_permissions.md` — first-run permissions screen
- `05_full_app_and_settings.md` — full chat view + Settings (Brain / Modes / Triggers / Web Tools)
- `screenshots/` — flat PNG references per screen

The redesign is a **visual + interaction refresh**, not a re-architecture.
Existing services (`FloatingWidgetService`, `HandyAccessibilityService`,
the room DB, the LLM client) stay. We're updating Compose UI and a couple
of state machines.

---

## Tokens — drop these into `Color.kt`, `Type.kt`, `Theme.kt`

```kotlin
// Bold theme — single source of truth (the previous "Calm" theme was dropped).
object HandyColors {
    val Bg              = Color(0xFF0A0A0F)   // page bg
    val Bg2             = Color(0xFF111118)   // alt section bg
    val Surface         = Color(0xFF15151D)   // cards
    val Surface2        = Color(0xFF1B1B25)   // raised cards / callouts

    val GlassTint       = Color(0xCC141420)   // 80% — glass disc & overlay fill
    val GlassBorder     = Color(0x33FFFFFF)   // hairline white
    val GlassInnerStroke= Color(0x14FFFFFF)   // subtle inner stroke
    val GlassHighlight  = Color(0x29FFFFFF)   // top-left specular sheen

    val Accent          = Color(0xFFE1A95F)   // amber — primary action / brand
    val AccentInk       = Color(0xFF1A1108)   // text on accent
    val AccentSoft      = Color(0x29E1A95F)   // 16% — tinted backgrounds, chips
    val ChipBorder      = Color(0x33E1A95F)   // 20% — chip / icon-square borders

    val Listening       = Color(0xFF8FD4DA)   // cyan — listening halo & waveform
    val Success         = Color(0xFF7AB58C)
    val SuccessSoft     = Color(0x297AB58C)

    val TextPrimary     = Color(0xFFF5F2EA)
    val TextSecondary   = Color(0xB3F5F2EA)   // 70%
    val TextTertiary    = Color(0x80F5F2EA)   // 50%
    val Border          = Color(0x1AFFFFFF)   // 10%
}

// Type — Inter, weights 400/500/600/700. Use Material 3 `Typography`.
val HandyFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
```

Type scale (sp / weight / letter-spacing):
- `displayLarge`     32 / 700 / -0.4
- `headlineLarge`    24 / 700 / -0.3
- `titleLarge`       18 / 600 / -0.2
- `titleMedium`      16 / 700 / -0.2  ← settings section titles
- `titleSmall`       14 / 600 / -0.1
- `bodyLarge`        15 / 400 / 0
- `bodyMedium`       14 / 400 / 0     ← chat bubbles, default
- `bodySmall`        13 / 400 / 0     ← settings subtitles, captions
- `labelMedium`      12 / 500 / 0.1   ← chips, status pills
- `labelSmall`       11 / 500 / 0.2   ← provider tags, micro-captions

Spacing: 4dp grid. Common spans: 4, 8, 10, 12, 14, 16, 20, 24, 32, 48.

Radii:
- 8dp — small chips
- 9dp — section icon squares
- 12–14dp — cards
- 16dp — overlay / sheets
- 20dp — message bubbles
- 99dp — pills, FABs

Elevation in dark UI = inset highlights, not box-shadow. Cards get a 0.5dp
top inset at `0x0DFFFFFF` for the "lifted" feel; below uses `Surface2`
instead of shadow.

---

## Component → file map

| Surface             | Prototype file                          | Doc                                  |
|---------------------|-----------------------------------------|--------------------------------------|
| Floating widget     | `components/handy-widget.jsx`           | `03_floating_widget.md`              |
| Chat overlay        | `components/handy-overlay.jsx`          | `02_chat_overlay.md`                 |
| Full app + settings | `components/handy-fullapp.jsx`, `handy-settings.jsx` | `05_full_app_and_settings.md` |
| Permissions         | `components/handy-permissions.jsx`      | `04_permissions.md`                  |
| Backdrops (Maps/Home for the prototype only) | `components/handy-backdrops.jsx` | n/a — prototype scaffolding |
| Primitives (HandMark, glass surfaces, icons) | `components/handy-primitives.jsx` | `01_icons_and_drawables.md` |

Open the `.jsx` files for measurements, gradients, and animation specifics
that don't show up in the screenshots. Search for the component name
(`function FloatingWidget`, etc.) and read the `style={{ … }}` blocks —
they map almost directly to Compose modifiers.

---

## What changed vs. the previous build

1. **Single Bold theme** — the previous Calm/Bold split was dropped.
   Everything is dark-amber-on-near-black.
2. **Chat overlay header alignment** — title + subtitle are one stack;
   action icons (expand, close) sit at the **title row**, not centered
   between title and subtitle.
3. **Floating widget thinking state** — the spinning rim is no longer a
   separate SVG circle drawn on top. The disc itself rotates inside a
   sweep-gradient wrapper; the inner content counter-rotates so the hand
   stays upright.
4. **First-run permissions** — new screen (artboard `P1` in the prototype).
   Mic / Notifications / Overlay / Accessibility, plus a privacy callout.
5. **Settings → Brain** — model cards expand inline to show their API key
   field when selected. Removed the standalone "API Keys" section.
6. **Settings → Web Tools** — new section. Brave / Jina / GitHub each have
   a toggle + inline API key.
7. **Settings section header layout** — icon, title, and subtitle are one
   locked Row. Subtitle no longer hangs at a bare `marginLeft: 38px`; it's
   nested under the title at the same indent.

---

## Implementation order (suggested)

1. Drop in tokens (`Color.kt`, `Type.kt`) and the `ic_*` drawables.
2. `HandMark` composable (everywhere).
3. Floating widget — port `FloatingWidget` from the prototype, swap state
   machine to the new four-state enum.
4. Chat overlay — port `ChatOverlay` Compose-side, wire to `MessageRepository`.
5. Permissions screen — new screen, gate `MainActivity` behind it on first run.
6. Full app screen — same content as overlay, full-screen container, swap
   expand→collapse.
7. Settings — rewrite the existing settings activity using the new section
   pattern and inline-key model cards.

Each step is independently testable; ship in this order to keep the app
working at every commit.
