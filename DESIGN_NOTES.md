# Handy Android — Design Notes

A running log of design decisions, deviations from the plan / rules,
and deferred work. Small decisions live here; architectural or
behavioural changes go in the build plan with a DL-XXX cross-reference
when one exists.

## Deviations from the canonical plan / rules

- **macOS `loadingVerbs` count.** The build plan mentions "34 strings";
  the macOS Swift source actually ships 30. Android ports the 30 that
  exist. Flag: if macOS grows the list, `LoadingVerbs.ALL` must track
  it verbatim.
- **macOS `ChatMessage.timestamp` wire format.** Swift's default
  `JSONEncoder` stores `Date` as a seconds-since-reference-date
  `Double` (Apple 2001 epoch). Android serialises `timestampEpochMs`
  as a Unix epoch `Long`. Full cross-platform round-trip requires a
  translator at the JsonHistoryStore boundary; v1 does not yet import
  macOS history files — defer.
- **`:core` `PromptCatalog` examples.** Example prose mentions macOS
  apps ("Final Cut", "Figma export panel"). Per the guardrail, every
  non-adaptation word is preserved verbatim — only the POINT tag
  syntax differs. Claude understands the examples as illustrations of
  output format.

## Toolchain pairing (DL-017, April 2026)

Android Studio Panda 4 | 2025.3.4 is the reference IDE. We ship on the
**conservative 8.x branch** of AGP even though Panda 4 ships with AGP
9.2.0-alpha — stable beats canary for a product headed to Play.

| Stack item | Version | Why |
|---|---|---|
| AGP | 8.13.2 | Last stable on 8.x; Panda 4 supports 4.0–9.1. |
| Kotlin | 2.2.21 | Pairs with AGP 8.13; Kotlin 2.3 needs AGP 8.13.2, 2.4 needs AGP 9.1. |
| KSP | 2.2.21-2.0.4 | Matches Kotlin prefix. |
| Gradle wrapper | 8.14.3 | Minimum for AGP 8.13; JDK 17 toolchain. |
| Compose BOM | 2025.11.01 | Latest stable BOM as of cut-off. |
| Hilt | 2.57.2 | Current stable. |
| OkHttp | 5.3.2 | 5.x stable; SSE factory API unchanged from 4.x. |
| JUnit Jupiter | 5.14.0 | Paired with `junit-platform-launcher 1.14.0` (required on Gradle 8.14+ to avoid `OutputDirectoryProvider not available`). |
| `compileSdk` / `targetSdk` | 36 | Google Play mandate April 2026. |
| `minSdk` | 26 | Unchanged. |
| Emulator | Pixel 9 Pro API 36 | Android 16 stable, Google APIs image. Pixel 10 also fine. |

Kotlin 2.2 notes:
- `-Xannotation-default-target=param-property` is enabled in the
  top-level `build.gradle.kts` to quiet the KT-73255 forward-compat
  warning that now fires on every Hilt `@Inject constructor(@Qualifier …)`
  site.
- Kotlin 2.2 is stricter about nullable receivers — `MediaProjection?`
  from `getMediaProjection(...)` now requires `?.apply { }` instead of
  `.apply { }` (fixed in MediaProjectionCaptureService).

## Emulator (DL-017)

Existing AVD on the dev machine: `Pixel_7` on `android-35`. Retire
after the new one is verified working.

New AVD (canonical shipping target):

1. Android Studio > **Device Manager** > **Create Virtual Device**.
2. Pick **Pixel 9 Pro** (or Pixel 10 if available).
3. System image tab > **Android 16.0 (API 36) · Google APIs** for
   `arm64-v8a` (Apple Silicon) or `x86_64` (Intel Macs / Linux).
   Download if not installed.
4. Name it `Pixel_9_Pro_API_36`; keep defaults. Finish.
5. First boot: open Settings > Accessibility > Handy > toggle on so
   `HandyAccessibilityService.onServiceConnected` fires.
6. Keep `Pixel_7` around until the API 36 AVD has run the Phase 4
   verification matrix successfully, then delete via Device Manager.

CLI equivalent (requires `~/Library/Android/sdk/cmdline-tools/latest`
installed — open Android Studio > SDK Manager > SDK Tools >
**Android SDK Command-line Tools (latest)**, accept licenses):

```bash
sdkmanager "system-images;android-36;google_apis;arm64-v8a"
avdmanager create avd -n Pixel_9_Pro_API_36 \
  -k "system-images;android-36;google_apis;arm64-v8a" \
  -d pixel_9_pro
```

## Dependencies

Every new library lands here with a one-line justification.

- **`androidx.dynamicanimation`** — SpringAnimation for the widget
  snap-to-edge. Matches macOS motion tokens.
- **`androidx.security:security-crypto`** — `EncryptedSharedPreferences`
  for API keys. Deprecation is noted; tink-based replacement (`1.1.x`)
  is a drop-in when the feature stabilises.
- **`com.squareup.okhttp3:okhttp-sse`** — Server-Sent Events for the
  Claude stream. No Retrofit, no "AI" helper libraries.
- **`com.jakewharton.timber:timber`** — Logging. Replaces `println` /
  `Log.*`, which are on the Forbidden list.
- **`androidx.startup:startup-runtime`** — Available for future
  bootstrap needs; currently unused.

## Deferred (v2 candidates)

- **Tap-for-me / do-it-for-me** via
  `AccessibilityService.dispatchGesture`. v1 binds
  `ActionPerformer → NoopActionPerformer`; v2 adds a real performer
  in the module that owns the AccessibilityService (currently `:app`).
- **Gemini brain**. The `LlmClient` seam is complete; v2 ships
  `GeminiLlmClient` alongside `ClaudeLlmClient` and a provider toggle
  in Settings.
- **Continuous guided workflow / multi-step runner**. Explicitly
  out-of-scope for v1 per the guardrails.
- **Accessibility-based tool-memory auto-switching** (per-app /
  per-site chat threads that follow the user across apps). The
  `ToolContext` and `UmbrellaSiteLabels` in `:core` are ready; the
  event hook in `HandyAccessibilityService.onAccessibilityEvent` is a
  v2 target.
- **Rich animations** — idle breathing, staggered listening pulses,
  rotating thinking ring, typewriter reveal. Phase 3 ships the static
  widget states and colour transitions; motion tokens are already
  defined.
- **Tutor mode** — prompt is present; enabling flow / idle-observation
  trigger land in v2.
- **On-device Gemini Nano fallback** — v2.

## Review artifacts checklist (Play Console submission)

Track completion here; every item is required before shipping v1.

- [ ] In-app Accessibility disclosure recording (OnboardingActivity).
- [ ] Canonical chat + pointing session (ChatActivity + widget tap).
- [ ] Accessibility-off reduced-mode experience.
- [ ] Play Console **Accessibility use declaration** — describes read
  + pointing + intent dispatch; explicitly says no tap-for-me in v1.
- [ ] Play Console **Foreground service use declaration** —
  `specialUse | microphone` for `AssistantForegroundService` and
  `mediaProjection` for `MediaProjectionCaptureService`.
- [ ] Privacy policy link (points to `PRIVACY_POLICY.md` or a hosted
  equivalent).
- [ ] Screenshots (widget idle / listening / thinking; chat with
  streaming response; settings).

## V2 design decisions (Phase 0 — source-of-truth alignment, 2026-04-24)

The entries below record the design decisions locked for V2 before
runtime code lands. The authoritative V2 spec is
[`Handy_Android_Build_Plan_V2_Scope.md`](Handy_Android_Build_Plan_V2_Scope.md);
this file records *why* and *what was considered and rejected*.

### Unified Buddy — the widget is the pointer

**Decision.** V2 collapses the macOS two-overlay split
(`FloatingAccessWidgetController` + `CompanionCursorManager`) into a
single overlay on Android. The floating widget itself is the pointer.
On a `[POINT]` the widget flies from its snapped-edge dock via Bezier
arc to the resolved target bounds, then stays as a sticky pointer until
the user/app interacts, a new request starts, the service disconnects, or
a safety timeout returns it to the dock. No separate
`PointerOverlayController` overlay ships in V2.

**Rationale.** The macOS companion anchors to `NSEvent.mouseLocation`;
Android has no persistent mouse cursor. Two overlays on Android would
double the `OverlayComposeHost` bookkeeping (lifecycle registry,
saved-state registry controller, viewmodel store) without buying the
macOS-native "companion follows cursor" affordance. The
`cursorbuddy-android-main` reference (AGPL-3.0) uses the same
`LensView` render on both its docked bubble and its flying pointer to
make them "feel like the same object"
([`PointerView.kt` L8–10](cursorbuddy-android-main/app/src/main/java/com/cursorbuddy/android/overlay/PointerView.kt))
— Handy goes one step further: not same render, same view.

**Considered and rejected.** (a) Two-overlay, widget-hides-while-pointing.
Clean but loses visual continuity and doubles OS-4 bookkeeping.
(b) Two-overlay, widget-stays-put, pointer-scouts-out. Keeps the
widget as a "home base" but adds a second window for the user to
reason about. Both flagged for reversal only via a new DESIGN_NOTES
deviation entry plus scope-doc update.

### Four-color bubble taxonomy

**Decision.** All assistant-surface bubbles on the Unified Buddy are
one of four colors with mutual-exclusion rules.

| Color | Meaning | Source | Clamp |
|---|---|---|---|
| Yellow | transcribed voice | macOS `overlayTranscriptBubble` | input transcript, no clamp |
| Teal | action-in-progress (NEW) | Android-only | ≤ 30 chars, passive status |
| Green | assistant response | macOS `overlayResponseBubble` | 110 chars (`clampVoiceSpokenForOverlay`) |
| Blue | pointer navigation label | macOS `navigationBubble` | 30 chars or random phrase |

Rules: only one bubble visible at a time; green suppresses blue; teal
suppresses green; yellow fades on processing start; teal is **passive
status, not interactive** — confirmation chips live in the overlay
chat panel (`PanelActionConfirming`), never on the lens.

**Rationale.** Teal is the new color because V2 adds real tap-for-me,
which needs a clearly-distinct "something is being done on your behalf"
state. The three macOS colors map verbatim; teal is the only
Android-first addition.

### SDK divergence — `targetSdk / compileSdk = 36` (not 35 as the V2 prompt requested)

**Decision.** Keep `minSdk 26`, `targetSdk 36`, `compileSdk 36` (per
DL-017, April 2026). Treat API 37 as a future compatibility smoke lane.

**Divergence from the V2 prompt.** The V2 prompt (2026-04-24) says
`targetSdk 35 / compileSdk 35` with "API 36 smoke lane". The on-disk
rules say 36 because Google Play requires `targetSdk ≥ 36` for all new
apps and updates starting 2026-08-31, and DL-017 already validated the
toolchain + emulator against API 36. Shipping on 35 today would force
a second migration within months.

**Action requested from the user.** Please confirm: keep 36 (my
recommendation) or revert to 35 per the V2 prompt wording. If we stay
on 36, this divergence from the prompt text is deliberate and should
be preserved. If we revert to 35, the V2 scope doc §1 item 8 and the
rules need a matching update, and we accept a pre-Play-mandate
migration later in 2026.

### `cursorbuddy-android-main` — licensing and "recipes, not source"

**Decision.** The `cursorbuddy-android-main/` folder in this repo is
[jasonkneen/cursorbuddy-android](https://github.com/jasonkneen/cursorbuddy-android)
at the stated commit, under **AGPL-3.0**. Handy is not AGPL and will
not ship AGPL surface.

**Discipline.** We port **recipes, techniques, and numeric constants**
from cursorbuddy — not Kotlin source lines. Every borrowing in scope
§15 is a clean-room reimplementation in our own Compose / adapter
code. Specifically:

- **Glass Lens seven-layer composite** — we implement our own
  `LensRenderer` in Compose `Canvas` (or `AndroidView` wrapper). The
  gradient stop values, paint strokes, specular highlights, and
  chrome sweep are re-authored from the described visual, not copied.
- **Compact Accessibility-marks JSON** — our
  `AccessibilityMarksProvider` uses our own `UiNode` type and
  `kotlinx.serialization` encoder. The `isInteresting(node)`
  heuristic is re-stated from scratch; only the filter intent is
  shared.
- **Node-first-gesture-second tap** — our
  `AccessibilityGestureActionPerformer` is authored fresh around our
  `PerformResult` sealed type and `SemanticPointerResolver` contract.
  The cursorbuddy file is read for the technique, never imported.
- **IME choreography and flag combos** — these are Android platform
  constants; no AGPL concern, but we still author the wiring inside
  our own `OverlayPresenter`.

**Forbidden.** Copy-paste from any `cursorbuddy-android-main/**/*.kt`
into any Handy source file. If a V2 PR's diff shows a hunk that
originated in cursorbuddy, stop and rewrite.

**Attribution.** This `DESIGN_NOTES.md` entry and scope §15 are the
attribution path. The Play listing marketing copy does not need to
cite cursorbuddy because no AGPL surface ships.

---

## Claude Android design handoff (DL-028, April 2026)

Product surfaces now follow the warm-amber / near-black glass direction
documented in the internal handoff (`Inter`, single accent, `HandyDimens`
radii, four bubble semantics mapped to `HandyColors.Bubble*`). Full-app
`ChatActivity`, overlay `OverlayChatPanelContent`, `SettingsActivity`
(Brain picker + tutor), `OnboardingActivity`, and `DiagnosticsActivity`
were restyled in one pass so spacing and corner radii stay consistent.
`LensRenderer` tints were moved from cyan-forward defaults to
amber-forward glass while preserving the scope §15 seven-layer recipe.

**Brain picker.** Sonnet vs Haiku is `HandySettings.claudeModelOverride`
(`null` = default Sonnet id in the client, non-null = Haiku API id).
Gemini remains a non-interactive “coming soon” card — cloud routing is
still governed by `CloudProvider` elsewhere; do not imply Gemini is
selectable until parity tests land.

---

## Performance budget (enforced in Phase 4 pass)

From build plan §15:

- Cold start → widget visible ≤ 800 ms.
- Widget tap → chat first frame ≤ 250 ms.
- Long-press → first partial transcript ≤ 400 ms.
- Chat token → visible delta ≤ 50 ms.
- JPEG encode 1024-edge screenshot ≤ 40 ms.
- Idle memory footprint ≤ 110 MB.
- APK size ≤ 25 MB.

Baseline profile targets: `ChatActivity`, `FloatingWidgetOverlayService`.

---

## Overlay panel backdrop snapshot blur (May 2026)

**Decision.** The minimized overlay panel may blur a pre-panel
screenshot on Android API 31+, but only as Handy-owned bitmap content
clipped inside the rounded bottom sheet. The full-screen panel window
remains the IME/outside-tap owner and no Handy overlay window gets
`FLAG_BLUR_BEHIND`.

**Fallback.** When screenshot capture is unavailable, delayed, blocked,
or disabled by the system, the sheet keeps the dense `GlassTint`
fallback alpha so underlying app elements do not clash with the text.
