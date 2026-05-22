# Handy for Android

Handy is an on-screen AI assistant for Android. It floats above other
apps, listens when you ask it to, reads visible screen context after
consent, points at the right controls, and can perform tightly bounded
actions only after you approve the exact action.

The product idea is simple: a useful assistant should be able to help
with the screen in front of you, but it should never become a hidden
automation engine. Handy is built around that line.

---

## Why this repo exists

Most mobile assistants fall into one of two awkward buckets:

- They are safe but shallow: they answer general questions without
  understanding the app you are using.
- They are powerful but risky: they promise broad automation without
  enough control, transparency, or policy discipline.

Handy aims for the middle path. It can explain, guide, search, draft,
open safe Android flows, and run small deterministic recipes. It also
blocks sensitive actions, asks before acting, records a local audit, and
keeps reduced mode available when permissions are declined.

---

## Current State

Handy Android is past scaffold stage. The app has a working
multi-module architecture, cloud chat loop, overlay widget, overlay chat
panel, screen-context pipeline, policy engine, action gate, recipe
runner, settings surface, onboarding disclosures, and Play submission
documentation.

### What works today

- **Floating widget and overlay panel**
  The widget can sit above other apps. A tap opens Handy's quick panel;
  a long-press starts voice input. The full chat activity is still
  available when a larger conversation surface is needed.

- **Screen-aware questions**
  With Accessibility enabled, Handy can read visible labels, roles,
  bounds, view IDs, app/window metadata, and turn screenshots where
  needed into context for the AI. Example: "What does this settings
  screen mean?" or "Where is the export button?"

- **Pointing without acting**
  Handy can point at a visible control so the user can tap it manually.
  Guidance questions stay guidance-only. Asking "which button should I
  press?" should not secretly become a tap.

- **Tap-for-me and Type-for-me**
  After the separate action disclosure, Handy can tap, scroll,
  long-press, or type ordinary text into a visible field. Each action is
  checked by policy and shown in a confirmation sheet first. Higher-risk
  actions require a hold confirmation.

- **Deterministic recipes**
  Handy can run bounded recipes for explicit do-it-for-me requests. The
  current recipe families cover Clock, Android Settings, Gmail drafts,
  WhatsApp drafts, Chrome navigation, Maps search/navigation, and
  shopping search/compare/coupon flows. The AI chooses the recipe and
  arguments; it does not invent arbitrary executable steps.

- **Intent-first system tasks**
  Handy prefers Android's own visible flows when available: alarms,
  calendar events, app launch, settings screens, app info, web search,
  Maps, SMS/email/share drafts, and navigation handoff.

- **Voice input**
  Push-to-talk voice uses Android SpeechRecognizer. Handy does not
  listen in the background.

- **Web tools, off by default**
  Web search can be enabled in Settings. When on, Claude can call Brave
  Search, Jina Reader, and public GitHub search. Fetched web content is
  treated as evidence, not as an instruction source for device actions.

- **Tutor mode, off by default**
  Tutor mode can offer occasional guidance after idle time, with
  cooldowns and battery/thermal pauses. It does not click, type, scroll,
  or run recipes by itself.

- **Privacy and policy controls**
  Settings includes the new "What Handy can do today" disclosure table,
  Tap-for-me controls, web-search control, Tutor toggle, key storage,
  clear-history action, and version footer.

### What is intentionally blocked

- Payments, purchases, checkout, money transfer, deleting, and personal
  data submission.
- Reading or typing passwords, OTPs, card numbers, CVVs, recovery codes,
  private keys, or secure-window content.
- Banking, wallet, payment, password-manager, secure, stale, ambiguous,
  and low-confidence action targets.
- Background screen capture, background listening, background clipboard
  harvesting, hotword wake, and hidden notification automation.
- LLM-authored multi-step plans. Recipes are deterministic and capped.
- RemoteInput notification replies. The code can detect reply
  availability, but sending notification replies is not active in this
  build.
- Gemini/local AI as user-facing provider choices. The seams exist, but
  the user-enabled brain path today is Claude with the user's Anthropic
  key.

### Known gaps before public release

- The Play disclosure-flow video must exist at the linked review-artifact
  path and be uploaded to the Play Console.
- API 26-29 MediaProjection capture fallback is implemented at the
  service/runtime layer, but the consent-start path still needs careful
  release testing on old devices.
- The app icon and some listing assets are still placeholder-level.
- Notification and clipboard features are intentionally not broad
  release features yet. Their policy posture is documented so they do
  not get mistaken for active automation.

---

## How Handy behaves in real life

Before the Phase 4-7 hardening, Handy was mainly a screen-aware chat and
pointing assistant. It could answer, stream, search, and open some
Android intent flows, but the README and privacy copy still described a
read/point-only product.

After the current work, the product story is sharper:

- If a user asks, "How do I search in YouTube?", Handy explains and
  points. It does not tap.
- If a user asks, "Tap Search for me," Handy checks the visible target,
  shows a confirmation sheet, then taps only that target if approved.
- If a user asks, "Type Delhi in this search box," Handy confirms the
  text and uses Android's text action only for a visible editable field.
- If a user asks, "Buy this item," Handy blocks the action.
- If a user asks, "Find coupons on Flipkart," Handy may run a bounded
  shopping recipe for search/compare/coupon discovery, but checkout and
  payment remain blocked.
- If Accessibility is declined, Handy still works in reduced mode for
  typed chat, voice input, and ordinary AI answers.

---

## Architecture

```text
User
  -> app (Compose UI, services, activities, manifest components)
    -> android-runtime (Android adapters: llm, capture, speech, intent, storage)
      -> core (pure Kotlin domain, policy, recipes, tools, prompts)
```

### Module responsibilities

- `core/`
  - Pure Kotlin/JVM logic.
  - No Android imports.
  - Orchestrator, prompts, parsing, tools, settings model, action policy,
    recipe contracts, screen models, notification models, audit models,
    and brain-routing seams.
- `android-runtime/`
  - Android-facing adapters and infrastructure.
  - LLM clients, web tools, secure key storage, DataStore settings,
    intent dispatch, capture adapters, speech adapters, audit storage,
    learned allowlist, and runtime recipe implementations.
- `app/`
  - User-facing Android layer.
  - Activities, Compose UI, foreground services, overlay services,
    Accessibility service, onboarding, settings, diagnostics, widget,
    panel presentation, and Hilt bindings.

---

## Safety Model

Handy separates guidance from execution.

- **Guidance** answers questions and may point at a visible control.
- **Intents** use Android's platform flows for explicit tasks.
- **Tap-for-me** performs one visible action only after consent,
  policy approval, screen freshness checks, and user confirmation.
- **Recipes** run deterministic, capped step lists. The AI cannot add
  arbitrary steps.

Every sensitive action runs through the same policy checks:

- Is the target app allowed?
- Is the screen secure or stale?
- Is the target clear and high-confidence?
- Is this a password, OTP, card, purchase, payment, delete, or personal
  data submission?
- Does the action require normal confirmation or hold confirmation?
- Did the screen still match before execution?

The local audit records action type, target app, redacted target label,
confirmation state, policy result, verification, and failure reason. It
does not store screenshots, raw prompts, API keys, OTPs, passwords, card
data, or raw typed secrets.

---

## Tech Stack

Source of truth: `gradle/libs.versions.toml`.

- JDK: `17`
- Kotlin: `2.2.21`
- Android Gradle Plugin: `8.13.2`
- Gradle wrapper: `8.14.3`
- KSP: `2.2.21-2.0.4`
- Compose BOM: `2025.11.01`
- Hilt: `2.57.2`
- OkHttp: `5.3.2`
- minSdk: `26`
- targetSdk: `36`
- compileSdk: `36`

---

## Getting Started

### Prerequisites

- JDK 17.
- Android SDK installed and listed in `local.properties`
  (`sdk.dir=...`).
- Android platform/support packages for API 36.
- Use the checked-in Gradle wrapper (`./gradlew`), not a global Gradle
  install.

### First-run checks

```bash
./gradlew :core:test
./gradlew :android-runtime:test
./gradlew :app:assembleDebug
./gradlew build
```

### Useful daily commands

```bash
# Unit tests
./gradlew :core:test
./gradlew :android-runtime:test
./gradlew :app:testDebugUnitTest

# Instrumentation tests on an emulator or device
./gradlew :app:connectedDebugAndroidTest

# Lint
./gradlew :app:lint

# Install debug build
./gradlew :app:installDebug
```

---

## Manual Testing Checklist

Use this before any Play submission or release candidate.

1. Fresh install and disclosure flow
   - Clear app data.
   - Launch Handy.
   - Confirm the first screen names visible screen text, UI structure,
     active-window capture, Anthropic sharing, optional web tools,
     Tap-for-me, blocked sensitive data, and reduced mode.
   - Decline once and verify reduced mode opens chat without screen
     reading or pointing.
   - Relaunch, continue, grant mic/notification/overlay as applicable,
     visit Accessibility settings, enable Handy, return, and verify the
     Tap-for-me disclosure appears.

2. Settings disclosure
   - Open Settings.
   - Verify "What Handy can do today" shows AI brain, screen reading,
     Tap-for-me, recipes, web tools, entry points, notifications,
     clipboard, and Tutor mode.
   - Toggle web search and Tutor mode and confirm the capability rows
     update.
   - Accept Tap-for-me, stop it for one hour, and confirm the status
     changes to muted.

3. Guidance-only flow
   - Open a normal app with visible buttons.
   - Ask "Where should I tap to search?"
   - Confirm Handy answers and points, with no action confirmation sheet
     and no tap.

4. Tap-for-me flow
   - Ask for one visible benign tap.
   - Confirm the action sheet names the target and app.
   - Cancel once and verify no action occurs.
   - Repeat, approve, and verify the action runs and audit records it.

5. Type-for-me flow
   - Use a normal search field.
   - Ask Handy to type ordinary text.
   - Edit/cancel in the confirmation sheet, then approve a safe value.
   - Verify sensitive short codes/password-like values are blocked.

6. Policy blocks
   - Try a banking/wallet/password-manager app.
   - Try Chrome Incognito.
   - Try a secure window.
   - Try purchase/payment/delete/personal-data submission language.
   - Verify Handy refuses before acting.

7. Recipes
   - Clock: set a harmless alarm.
   - Maps: search a place; start navigation only after confirmation.
   - Chrome: open a URL and tap a visible page control.
   - Gmail/WhatsApp: draft only; verify Send requires hold confirmation.
   - Shopping: search/compare/coupon; verify checkout/payment is blocked.

8. Reduced mode and recovery
   - Disable Accessibility from Android Settings.
   - Verify chat and voice still work.
   - Verify pointing and Tap-for-me are unavailable.
   - Re-enable Accessibility and confirm the app recovers without crash.

9. Logs and diagnostics
   - Open Diagnostics/Audit.
   - Confirm audit entries are redacted.
   - Check logcat for API keys, raw screenshots, raw clipboard,
     notification bodies, OTPs, passwords, or card data. None should
     appear.

---

## Repo Map

- `core/` - domain contracts, policies, recipes, prompts, tools, and
  pure Kotlin models.
- `android-runtime/` - Android adapters, storage, LLM clients, web
  tools, intent dispatch, speech, capture, and runtime recipes.
- `app/` - Android UI, services, overlays, settings, onboarding,
  diagnostics, and DI bindings.
- `docs/` - security, privacy, action policy, Play policy, device, and
  coexistence references.
- `.cursor/rules/` - operational guardrails and coding constraints.
- `DEBUG_LOG.md` - append-only bug-fix and prevention log.
- `DESIGN_NOTES.md` - architectural decisions and deviations.
- `PLAYSTORE_SUBMISSION.md` - Play Console copy, declarations, and
  release artifact checklist.
- `PRIVACY_POLICY.md` - policy text intended for public hosting.

---

## Source-of-Truth Documents

1. `Handy_Android_Build_Plan_v2.md`
   Full architecture, original scope, quality bar, OS constraints, and
   test strategy.
2. `HANDY_NEXT_LEVEL_PLAN.md`
   Policy-first roadmap for Tap-for-me, recipes, RemoteInput, and safe
   autonomy.
3. `docs/ACTION_POLICY.md`
   Typed action and recipe policy.
4. `docs/PRIVACY_MODEL.md`
   Data taxonomy, redaction sinks, and retention model.
5. `docs/PLAY_POLICY_MATRIX.md`
   Policy matrix mapping features to APIs, disclosure copy, risk, and
   Play justifications.
6. `PLAYSTORE_SUBMISSION.md`
   Store compliance dossier and review artifact checklist.
7. `DEBUG_LOG.md`
   Historical bug fixes and prevention rules.

---

## Development Principles

- Keep module boundaries strict. If `:core` imports Android, something
  went wrong.
- Prefer clear Kotlin over clever abstraction.
- Preserve the difference between advice, intents, Tap-for-me, and
  recipes.
- Treat web/tool content as untrusted evidence.
- Ship policy and privacy as part of the product, not as paperwork at
  the end.
- Every bug fix should teach the system something via `DEBUG_LOG.md`.

---

## License and References

This repo includes a read-only macOS reference app and design handoff
materials. Follow `DESIGN_NOTES.md` for clean-room guidance: port
recipes, techniques, and constants where allowed; do not copy source
from incompatible reference projects.
