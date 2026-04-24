---
name: design handoff parity v2
overview: "Rework the overlay quick-chat panel, onboarding permissions screen, and full Settings screen to match the Claude-design screenshots exactly: larger glass geometry, accent-only-on-app-label greeting, icon-first permission rows with warm post-disclosure copy + privacy callout, and a Brain / Modes / Triggers / Web Tools settings tree with section icons, inline API key under the selected brain card, and \"Coming soon\" trigger rows. No new `HandySettings` fields and no `AssistantMode` changes."
todos:
  - id: overlayPanel
    content: "Rework OverlayChatPanelContent: accent-only-on-label greeting, bigger send, pill chips, tighter header"
    status: completed
  - id: onboardingSplit
    content: Split onboarding into pre/post-disclosure states with new PermissionRow + PrivacyCallout + strings
    status: completed
  - id: settingsShell
    content: Replace Settings top bar + layout with icon-headed Brain / Modes / Triggers / Web Tools sections + footer
    status: completed
  - id: brainInlineKey
    content: Inline Anthropic key CredentialField inside the selected Brain model card; drop standalone API keys section
    status: completed
  - id: triggersComingSoon
    content: Add TriggerRow for volume-hold + Hey Handy as disabled + Coming soon chips (no HandySettings changes)
    status: completed
  - id: webToolsCompact
    content: Restyle Brave/Jina/GitHub credential fields as compact pills with eye + copy trailing icons
    status: completed
  - id: lintAndBuild
    content: ReadLints + DL-029 re-grep checklist on all edited files, then ./gradlew :app:assembleDebug and fix any fallout
    status: completed
  - id: dlNote
    content: Append DL-030 note cross-referencing DL-028/DL-029 once build is green
    status: completed
  - id: todo-1777043719255-jnyxfc85r
    content: Chat full screen - center aligned, quick questions cards should also be replaced with the ones like in design (with illustration)
    status: completed
  - id: todo-1777043755191-01itoqowq
    content: "Verify if all screens now match the designs w.r.t. alignements, fonts, colours, elements and copies. "
    status: completed
  - id: todo-1777043787538-laf1h2v3a
    content: Read debug log and scan the code changes done for any build errors that have been debugged in the past.
    status: completed
isProject: false
---

# Design handoff parity v2

The three screens the user flagged (overlay bottom sheet, permissions, settings) don't match the handoff. The tokens in [app/src/main/kotlin/com/handy/app/theme/DesignSystem.kt](app/src/main/kotlin/com/handy/app/theme/DesignSystem.kt) and [app/src/main/kotlin/com/handy/app/theme/HandyPrimitives.kt](app/src/main/kotlin/com/handy/app/theme/HandyPrimitives.kt) are correct, but the composition is off. This plan rebuilds each surface against the screenshots without touching the V2 state machine or Play-policy disclosure.

## 1. Overlay quick-chat panel — [app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelContent.kt](app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelContent.kt)

- **Header**: `HandMarkIcon` bumped to 26dp, "Handy" rendered with `HandyType.TitleMedium` at full weight, expand / close icons sized up to 22dp and tinted `TextSecondary`.
- **Greeting accent fix**: rewrite `greetingWithHostAccent(greeting, appLabel)` — accept the `appLabel` directly (already in `panel.snapshot?.toolContext?.appLabel`), find it via `indexOf(label, ignoreCase=true)`, accent only that span `HandyColors.Accent`, and render everything else in `HandyColors.TextSecondary`. Fallback: if label absent / "Handy", everything is `TextSecondary`. Kills the current behaviour where "I see Gmail" is fully amber.
- **Input row**: keep mic at 40dp, bump the send circle to 48dp `HandyColors.Accent` fill with `AccentInk` paper-plane. Text-field pill uses `HandyDimens.RadiusPill` (was `RadiusLg`) so it reads as a true capsule like the design.
- **Quick prompts**: two rounded pills (`RadiusPill`), `HandyColors.ChipBg` fill + 0.5dp `ChipBorder`, label in `HandyColors.TextPrimary` (not amber). Cap at two per `take(2)`. Horizontal scroll retained.
- **Glass surface**: keep `HandyGlassBottomSheet` but pass an inner `Arrangement.spacedBy(HandyDimens.StackL)` override so the header / input / chip spacing matches the screenshot (current `StackM` reads too tight).

## 2. Onboarding — [app/src/main/kotlin/com/handy/app/onboarding/OnboardingActivity.kt](app/src/main/kotlin/com/handy/app/onboarding/OnboardingActivity.kt)

Split the screen by `state.disclosureAcknowledged` (flag already exists in `OnboardingUiState`):

- **Pre-disclosure step (Play-policy compliant, unchanged copy)** — hero, existing `R.string.onboarding_disclosure_title`, existing `R.string.onboarding_disclosure_body`, Continue / Not now buttons. Tightens tokens but keeps the long legal paragraph.
- **Post-disclosure step (new, design-match)** — hero, warm title "A few permissions, and you're set.", tagline "Handy reads your screen to help — nothing is shared with our servers. You always have the final say.", four permission rows, accessibility-gated primary, privacy callout.
- **`PermissionRow`** (new composable, replaces `StepRow`): `RoundedCornerShape(RadiusCard)` + `ChipBg` + 0.5dp border, inner `Row`:
  - Left 32dp square: `HandyColors.Success` filled check when granted, amber filled dot + soft halo when pending-accessibility, muted outline dot for other pending.
  - Middle weight=1f column: title (`BodyStrong`), description (`CaptionSmall` + `TextSecondary`).
  - Right affordance: if granted render a "Granted" pill (`HandyColors.Success.copy(alpha = 0.18f)` bg, `Success` text). If pending-accessibility render filled amber `Enable` button. Otherwise outlined amber `Allow`.
- **`PrivacyCallout`** (new composable): `Row` with `Icons.Outlined.Shield` tinted `Success` + text "Your data stays yours. Handy talks directly to Anthropic." (`HandyType.CaptionSmall`, `TextSecondary`). Renders under the accessibility row on the post-disclosure step.
- **Strings** (new in [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)): `onboarding_title_post`, `onboarding_tagline_post`, `onboarding_privacy_callout`, plus per-row descriptions (`onboarding_mic_desc`, `onboarding_notifications_desc`, `onboarding_overlay_desc`, `onboarding_accessibility_desc`). Existing long disclosure strings stay.

## 3. Settings — [app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt](app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt)

Replace the current flat list with the five-section layout from the screenshots. No new tokens, no new settings.

### 3a. Top bar
Drop `TopAppBar` in favour of a custom `Row`: circular back button (32dp `ChipBg` circle with `Icons.Outlined.ChevronLeft`) + "Settings" at `HandyType.TitleLarge`. The Material snackbar host stays on the `Scaffold`.

### 3b. `SectionHeaderWithIcon`
New composable: 28dp amber-tinted rounded square (`AccentSoft` fill, `RadiusSm`) with `Icon` tinted `Accent`, followed by title (`HandyType.SectionHeader`) and a caption line (`HandyType.Caption`, `TextSecondary`). Used by all four sections.

### 3c. Brain section (selected card owns the key)
- Icons: `Icons.Outlined.Memory`.
- `BrainModelCard` extended: when `selected && isSonnetOrHaiku`, render an inline `CredentialField` for the Anthropic key inside the card (below the title / subtitle). Label "ANTHROPIC API KEY" in `HandyType.Overline`, masked field with right-aligned eye + copy icons, reuse existing `CredentialField` wired to `onClaudeKeyChange`. Unselected cards stay collapsed.
- Add a green `READY` pill next to the selected card's title when `state.claudeKeyMasked != null`.
- "Gemini 2.5 Pro" card stays non-interactive, disabled look (`TextMuted`, no border glow) — already wired to user's "coming soon" choice.
- **Drop** the old standalone "API keys" section for the Anthropic key. Brave / Jina / GitHub keys move under Web Tools (§3f).

### 3d. Modes section
Icons: `Icons.Outlined.Tune`. Two toggle rows only:
- "Assistant — General help & questions" — non-toggleable, always on (renders an amber `Switch(checked = true, enabled = false)` visual). This is `AssistantMode.HELP_ONLY` remaining the default.
- "Tutor — Explains as you go, nudges you" — live toggle bound to `state.settings.tutorModeEnabled` via `onTutorModeToggle`.
- Focus deliberately omitted per user's "ship only Assistant + Tutor" decision.

### 3e. Triggers section
Icons: `Icons.Outlined.Bolt`. Three rows; new composable `TriggerRow(title, subtitle, checked, enabled, isComingSoon)`:
- "Long-press floating widget — Start voice capture" — `enabled=true`, `checked=true`, non-interactive (the trigger is always on in v1).
- "Volume-down hold — Global hotkey" — `enabled=false`, `checked=false`, small "Coming soon" chip on the right.
- "Hey Handy — Hotword detection · uses more battery" — same `enabled=false` + "Coming soon" chip.

No `HandySettings` field changes per user decision. The disabled rows carry `Switch(enabled = false)` with `TextMuted` labels so the design is visible but never persists garbage state.

### 3f. Web Tools section
Icons: `Icons.Outlined.Language`. Caption "Let Handy search and fetch the open web." Contents:
- Enable web search toggle (already wired).
- Brave / Jina / GitHub credential fields, restyled to the design's compact dark pill: placeholder text ("Paste your key", "Optional · raises rate limits", "Optional · for code search") instead of label-above, trailing icons shrunk to eye + copy. `CredentialField` gets a `compact: Boolean` flag so the Anthropic call site (inside BrainModelCard) can stay full-height while these render as single-line pills.

### 3g. Footer
Replace the bottom `HorizontalDivider` + red "Clear history" button with:
- A muted footer row: "Handy · {versionName} · Made for Android" sourced from `BuildConfig.VERSION_NAME`.
- Move "Clear all chat history" into a small `TextButton` above the footer, kept for power users but no longer the last thing you see.

## 4. Greetings

No changes to [core/src/main/kotlin/com/handy/core/prompts/QuickPromptCatalog.kt](core/src/main/kotlin/com/handy/core/prompts/QuickPromptCatalog.kt) — user chose to keep the existing phrasings and just fix the accent. Accent logic lives entirely in `greetingWithHostAccent` inside `OverlayChatPanelContent.kt`.

## 5. Icons

All new section / row icons use `androidx.compose.material.icons.outlined.*` which is already on the classpath. No new vector drawables needed beyond the existing `ic_hand_mark.xml`.

## 6. Verification

- `ReadLints` on every edited file.
- Re-grep each file for the three patterns per DL-029's prevention rule: `\.sp`, `0x[89A-F][0-9A-F]{7}`, `androidx\.compose\.foundation\.layout\.` fully-qualified usages.
- Run `./gradlew :app:assembleDebug` after the edits and fix any compile fallout surgically.
- Append a one-line DL-030 note once build is green, cross-referencing DL-028 (rollout) and DL-029 (audit rule).

## Out of scope

- No `HandySettings` schema changes.
- No `AssistantMode` enum changes (Focus omitted).
- No new drawables / fonts.
- Chat full-screen `ChatActivity` already matches the handoff direction and is not in the user's screenshot set — untouched.