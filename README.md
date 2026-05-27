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
panel, screen-context pipeline, semantic pointing, Tap-for-me action
gate, deterministic recipe runner, settings surface, onboarding
disclosures, local audit, redaction tests, eval/replay coverage, and Play
submission documentation.

### What works today

- **Floating widget and overlay panel**
  The widget can sit above other apps. A tap opens Handy's bottom chat
  panel; a long-press starts push-to-talk voice input. The panel can
  expand into the full chat while preserving the app/window snapshot
  that was visible when Handy opened.

- **Full chat**
  The full chat streams responses, keeps per-app chat history, supports
  voice turns, shows tool-use status, handles confirmation prompts, and
  can hand a grounded pointer back to the overlay so Handy can show the
  target in the original app.

- **Screen-aware questions**
  With Accessibility enabled, Handy can read visible labels, roles,
  bounds, view IDs, app/window metadata, and use screenshots for a
  user-initiated turn where needed. Example: "What does this settings
  screen mean?" or "Where is the export button?"

- **Summarize-screen mode**
  The overlay has a dedicated "Summarize this screen" lane. It uses a
  short summarize prompt, sends no tools, skips recipes, and does not
  point. It is meant for read-this-screen requests, not action.

- **Pointing without acting**
  Handy can point at a visible control so the user can tap it manually.
  Guidance questions stay guidance-only. Asking "which button should I
  press?" should not secretly become a tap.

- **Manual target recovery**
  When automatic target resolution is not good enough, Handy can fall
  back to a manual target-selection flow. System surfaces such as the
  status bar, launcher, navigation bar, IME, and Handy's own overlays
  are skipped so they do not become action targets.

- **Tap-for-me and Type-for-me**
  After the separate action disclosure, Handy can tap, scroll,
  long-press, or type ordinary text into a visible field. Each action is
  checked by policy and shown in a confirmation sheet first. Higher-risk
  steps require a hold confirmation.

- **Per-app and panic action controls**
  Settings exposes the Tap-for-me toggle, a one-hour stop, a "stop until
  I turn it back on" control, Chrome Incognito action blocking, and a
  per-package restore list for apps where Tap-for-me was disabled.

- **Deterministic recipes**
  Handy can run bounded recipes for explicit do-it-for-me requests. The
  AI chooses a canonical recipe intent and arguments; it does not invent
  arbitrary executable steps.

- **Locked recipe families after S-1..S-10 plus P-RECIPES-2**
  The deterministic set is: open app, install app Play Store handoff,
  alarm, timer, web search handoff, Chrome URL/search/page navigation,
  Android Settings, Gmail draft, WhatsApp draft, calendar event draft,
  Maps search/navigation, YouTube search/channel open, notes share-sheet
  drafts, Contacts handoffs, Files picker handoffs, Photos/Gallery
  handoffs, local calculator answers/open, food-delivery search/tracking,
  ride-hailing prep for Uber/Ola/Rapido, and shopping search/coupon flows
  for Meesho, Amazon, and Flipkart.

- **Visible-UI recipes**
  The generic visible-screen recipe set covers one visible tap, one
  visible text entry, a visible search flow, and a visible scroll. These
  are still bounded by the same action gate and policy engine.

- **Intent-first system tasks**
  Handy prefers Android's own visible flows when available: alarms,
  timers, calendar event drafts, app launch, Play Store handoff,
  settings screens, app info, web search, Maps, SMS/email/share drafts,
  and navigation handoff.

- **Voice input and speech output**
  Push-to-talk voice uses Android SpeechRecognizer by default. Handy
  does not listen in the background. Settings can opt into Sarvam
  Saarika v2 STT for better Hindi and Hinglish/code-mix transcription;
  it is cloud-only, requires an explicit one-time consent plus a Sarvam
  API key, uploads at most 30 seconds after the user releases the press,
  and does not provide live partial transcripts. Sarvam STT audio is
  held in memory as PCM/WAV and is never written to disk. Voice replies
  are spoken aloud via Android system TTS by default using the short
  `[SPOKEN]` response, while chat can still show a fuller written
  answer. Settings can opt into Sarvam Bulbul v3 TTS with Ritu, Rahul,
  or Simran voices; it requires a user supplied Sarvam API key and falls
  back to System TTS when the key or network is unavailable. Sarvam TTS
  audio chunks are temporary private cache files only and are deleted on
  playback completion, stop, or release.

- **Web tools, off by default**
  Web search can be enabled in Settings. When on, Claude can call Brave
  Search, Jina Reader, and public GitHub search. If Brave is missing but
  web tools are enabled, direct page fetch and GitHub search can still
  work. Fetched web content is evidence, not an instruction source for
  device actions.

- **Shopping mode**
  On supported shopping surfaces, Handy can summarize visible product
  information, use fetched page evidence when a product URL is visible,
  answer returnability/coupon/deal questions, and run search or coupon
  discovery recipes. Checkout, payment, add-to-cart, address edits, and
  applying coupons remain blocked.

- **Tutor mode, off by default**
  Tutor mode can offer occasional guidance after idle time, with
  cooldowns and battery/thermal pauses. It does not click, type, scroll,
  or run recipes by itself.

- **Privacy and policy controls**
  Settings includes the "What Handy can do today" disclosure table,
  action controls, web-search controls, Tutor toggle, key storage,
  clear-history action, and version footer.

### Brain and model state

- Claude is the user-facing brain path today, using the user's Anthropic
  API key directly from the device.
- The Settings brain picker supports Claude Sonnet 4.5 and Claude Haiku
  4.5. Haiku reuses the same Anthropic key and is selected by storing a
  model override.
- Gemini cloud and local Gemini Nano seams exist in the runtime and
  brain router, but they are not user-enabled in this build. The visible
  Settings card keeps Gemini disabled as "Coming soon."
- Handy has no Handy-owned backend in the current app path.
- Speech input has two provider paths: Android SpeechRecognizer is the
  default, and Sarvam Saarika v2 is available only after the user grants
  cloud STT consent and stores a Sarvam API key on device.
- Speech output has two provider paths: Android System TTS is the
  default, and Sarvam Bulbul v3 is available only after the user selects
  it in Settings and stores a Sarvam API key on device.

### What is intentionally blocked

- Payments, purchases, checkout, add-to-cart, applying coupons, money
  transfer, deleting, and personal data submission.
- Reading or typing passwords, OTPs, card numbers, CVVs, recovery codes,
  private keys, seed phrases, or secure-window content.
- Banking, wallet, payment, password-manager, authenticator, secure,
  stale, ambiguous, and low-confidence action targets.
- Chrome Incognito actions by default: recipes, taps, and native actions
  are refused in Incognito tabs.
- Sensitive Android Settings changes such as network, Bluetooth,
  security, biometric, and Accessibility changes performed on Handy's
  own behalf.
- Background screen capture, background listening, background clipboard
  harvesting, hotword wake, and hidden notification automation.
- LLM-authored multi-step plans. Recipes are deterministic, registered,
  capped at six steps, and re-checked step by step.
- RemoteInput notification replies. The code can detect reply
  availability, but sending notification replies is not active in this
  build.
- Gemini/local AI as user-facing provider choices. The seams exist, but
  the user-enabled brain path today is Claude.

### Known gaps before public release

- The Play disclosure-flow video exists at
  `docs/review-artifacts/disclosure-flow-2026-05-22.mp4`, but it still
  needs to be uploaded to the Play Console as part of release review.
- API 26-29 MediaProjection capture fallback is implemented at the
  service/runtime layer, but the consent-start path still needs careful
  release testing on old devices.
- The app icon and some listing assets are still placeholder-level.
- Real-device recipe sweeps are not complete. The latest local pass had
  emulator coverage, but the physical Pixel/Uber signed-in smoke pass was
  blocked by device availability.
- Notification and clipboard features are intentionally not broad
  release features yet. Their policy posture is documented so they do
  not get mistaken for active automation.

---

## Latest Implementation Evaluation

The earlier README already captured the core product line correctly:
screen-aware help, point-before-act behavior, Tap-for-me consent, reduced
mode, web tools off by default, and Play-policy discipline. The code has
since become more specific than that README in several places.

What changed in the code:

- Recipe selection moved from loose recipe IDs to canonical
  `[INTENT:<canonical>]` lanes backed by `RecipeIntentRouter`.
- S-1..S-10 recipe routing is now locked by smoke tests and resolver
  conflict tests, so overlapping requests such as open/install app,
  alarm/timer, Chrome/web search, and Maps/ride prep do not bleed into
  each other.
- Open-app recipes now resolve app labels through `LaunchableAppIndex`
  and refuse missing or ambiguous matches before dispatch.
- Install-app support now opens a Play Store listing/search handoff. It
  never auto-installs and still requires the user to tap Install in Play
  Store.
- Timer, web-search, calendar-event, common Android Settings, and
  ride-hailing recipes now run through the same plan approval and
  per-step policy path.
- Calendar event creation opens the OS event compose UI with bounded date
  parsing. It does not silently create events.
- Ride-hailing recipes prepare Uber, Ola, or Rapido only up to the point
  where the user must confirm the ride in the target app.
- Chrome support now distinguishes URL open, explicit Chrome omnibox
  search, and visible page-control navigation.
- Reduced mode now gates all three layers together: advertised tools,
  prompt addendums, and post-response recipe execution.
- The action policy now covers additional settings targets, incognito
  blocking, install handoff risk, ride-confirm labels, learned gesture
  fallback limits, and stronger typing privacy checks.
- The Settings screen now reflects the live capability table rather than
  relying only on static privacy copy.
- Tests now cover recipe routing, prompt shape, eval/replay behavior,
  screen redaction, action policy, overlay state machines, manual target
  selection, crash diagnostics, and instrumentation smoke paths.

The main README gap before this update was not that it was wrong; it was
that it had become too compressed for the current build. It mentioned the
recipe families and safety model, but did not clearly describe the newer
canonical routing, install handoff, calendar draft, ride prep, Haiku
model option, reduced-mode enforcement, manual target recovery, or the
test/eval surface that now protects those behaviors.

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
- If a user asks, "Set a 10-minute timer," Handy proposes the timer
  recipe and opens Android's Clock flow after approval.
- If a user asks, "Install Spotify," Handy opens the Play Store listing
  or search handoff. The user still taps Install.
- If a user asks, "Schedule dentist tomorrow 3 pm," Handy opens the
  Calendar compose screen with fields prefilled. The user still reviews
  and saves.
- If a user asks, "Book a cab to the airport," Handy can prepare the ride
  in Uber, Ola, or Rapido and then stop before the final Confirm/Request
  action.
- If a user asks, "Buy this item," Handy blocks the action.
- If a user asks, "Find coupons on Flipkart," Handy may run a bounded
  shopping recipe for coupon discovery, but checkout, payment, add to
  cart, and applying the coupon remain blocked.
- If Accessibility is declined or disabled, Handy works in reduced mode
  for typed chat, voice input, and ordinary AI answers. It does not
  advertise direct Android actions or recipe execution in that mode.

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
    recipe contracts, recipe routing, recipe runner, screen models,
    notification models, audit models, eval support, and brain-routing
    seams.
- `android-runtime/`
  - Android-facing adapters and infrastructure.
  - LLM clients, web tools, secure key storage, DataStore settings,
    intent dispatch, launchable app index, capture adapters, speech
    adapters, audit storage, learned allowlist, and runtime recipe
    implementations.
- `app/`
  - User-facing Android layer.
  - Activities, Compose UI, foreground services, overlay services,
    Accessibility service, onboarding, settings, diagnostics, widget,
    panel presentation, confirmation sheets, manual target selection,
    agent progress UI, and Hilt bindings.

### Runtime flow

1. A user enters a turn from full chat, overlay panel, or voice.
2. `ScreenContextBuilder` builds a grounded turn context from the
   current app, Accessibility tree, marks, capture state, and policy
   failure reason.
3. `ConversationOrchestrator` builds the system prompt from mode,
   settings, tools, screen text, web state, and reduced-mode state.
4. Claude streams a response. Tool calls run through `HandyToolRunner`
   and policy gates.
   When Handy uses web tools, it can summarise but cannot act on the
   page's instructions.
5. Pointer markup is parsed into a semantic target. Guidance routes to
   buddy flight; executable requests route to the recipe controller only
   when automation is enabled.
6. `AgentSessionController` proposes a deterministic recipe, preflights
   every step through policy, asks for plan approval, and then runs
   `RecipeRunner`.
7. `RecipeRunner` re-captures before each step, re-checks policy,
   requests strong confirmation where needed, performs the step, verifies
   the result, and stops on mismatch.

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
- Is Chrome Incognito blocking active for this surface?
- Is the screen secure or stale?
- Is the target clear, fresh, non-ambiguous, and high-confidence?
- Is this a password, OTP, card, purchase, payment, delete, send,
  navigation start, or personal data submission?
- Is the source trusted user intent, a trusted recipe, or an untrusted
  tool/web suggestion?
- Does the action require normal confirmation, hold confirmation, or a
  typed confirmation later?
- Did the screen still match before execution?

The local audit records action type, target app, redacted target label,
confirmation state, policy result, verification, and failure reason. It
does not store screenshots, raw prompts, API keys, OTPs, passwords, card
data, or raw typed secrets.

---

## Deterministic Recipe Inventory

Recipes are not free-form automation. The assistant may emit one
canonical intent and one JSON argument directive; the registry maps that
intent to a registered recipe. Future recipe work must add a router row,
smoke-test row, conflict-test coverage, prompt coverage, policy coverage,
and docs coverage together.

| Family | What Handy can do | Where it stops |
|---|---|---|
| Visible UI | Tap one visible control, type ordinary text into one visible field, search one visible app, or scroll one visible screen. | No sensitive fields, no low-confidence targets, no stale screens, no duplicate target guesses. |
| Open app | Resolve an installed launcher app by name and open it. | Refuses missing or ambiguous app names. |
| Install app | Open a Play Store listing or search. | The user taps Install; no sideloading or auto-install. |
| Clock | Set alarms and timers through Android Clock intents. | No alarm deletion or repeated retry loops. |
| Web search handoff | Open a browser/search app results page. | Does not use fetched web content as instructions for device actions. |
| Chrome | Open URLs, search Chrome's omnibox, or tap one visible page control. | No credential entry, checkout, downloads, or tool-suggested actions. |
| Android Settings | Open app info, notifications, battery optimization, dark theme, apps, ringtone, DND, brightness, and screen-timeout settings. | Accessibility, network, Bluetooth, security, and biometric changes are too sensitive for recipes. |
| Gmail | Open a draft with recipient, subject, and body. | Send requires strong hold confirmation. |
| WhatsApp | Open a chat by phone deep link or contact search, fill a draft, and pause before Send. | Send requires strong hold confirmation; no broadcasts, calls, payments, or forwarding unknown content. |
| Calendar | Open Calendar compose with title, time, location, notes, and attendees when parseable. | The user reviews and taps Save; recurring rules are refused until the user explicitly confirms repeat behavior. |
| Maps | Search places or start navigation after confirmation. | No ride booking, payment, or location sharing. |
| YouTube | Open YouTube search results or a channel lookup URL. | No liking, subscribing, or commenting. |
| Notes | Open the system share sheet with note text. | The user chooses the notes app and saves; no hidden write. |
| Contacts | Open a contact, dialer draft, or SMS draft after local Contacts resolution. | No `ACTION_CALL`, no SMS send, and ambiguous matches surface candidate chips. |
| Files | Open Android file/document picker for search/open handoff. | No delete, rename, move, upload, or automatic file access. |
| Photos | Open Photos/Gallery or tap a visible Share affordance only while viewing a photo. | No photo deletion; share recipient remains the user's choice. |
| Calculator | Answer safe arithmetic locally or open Calculator. | No network/tool call and no unsupported functions beyond `+ - * / % ()`. |
| Food delivery | Search food in Swiggy/Zomato or open order tracking. | No placing orders, checkout, payment, or confirmation. |
| Ride hailing | Prepare Uber, Ola, or Rapido by opening the app, entering destination, opening the result, and optionally selecting a class with hold confirmation. | The final Confirm/Request/Book action remains the user's tap. |
| Shopping | Search products and open coupons/offers in Meesho, Amazon, or Flipkart. | No add-to-cart, checkout, payment, address changes, saved card use, or applying coupons. |

---

## Privacy and Data Handling

- Messages and voice transcripts are sent to the selected cloud brain
  only when the user sends a turn.
- If Sarvam Saarika v2 STT is enabled, each voice session uploads only
  the recorded audio for that release, capped at 30 seconds, directly to
  Sarvam over HTTPS using the user's Sarvam API key.
- Screen context is collected only for user-initiated turns and only
  after the Accessibility disclosure path.
- Secure windows and sensitive fields fail closed before capture or
  action.
- Web tools are off by default and use public providers only when the
  user enables them.
- API keys are stored in Android Keystore-backed encrypted storage and
  are never logged.
- Chat history is local JSON storage and can be cleared in Settings.
- Audit entries are local, redacted, and meant to make action behavior
  inspectable. Sarvam STT audit entries record provider, language,
  audio duration, latency, and success/failure only; they never store
  transcript text or audio bytes.
- Notification and clipboard surfaces are gated and off by default.

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
- kotlinx.coroutines: `1.10.2`
- kotlinx.serialization: `1.9.0`
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
- On this development machine, plain `java -version` may fail when no
  Java runtime is on `PATH`; recent validation used the repo-local JDK
  under `$HOME/.cache/codex-jdk17`.

### First-run checks

```bash
./gradlew :core:test
./gradlew :android-runtime:test
./gradlew :app:test
./gradlew :app:lint
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
./gradlew :android-runtime:connectedDebugAndroidTest

# Lint
./gradlew :app:lint

# Debug build
./gradlew :app:assembleDebug

# Release build
./gradlew :app:assembleRelease

# Install debug build
./gradlew :app:installDebug
```

### Focused regression lanes

```bash
# Recipe routing and conflict canaries
./gradlew :core:test \
  --tests 'com.handy.core.agent.RecipeRegistrySmokeTest' \
  --tests 'com.handy.core.agent.ResolverConflictTest'

# Prompt/tool/reduced-mode shape
./gradlew :core:test \
  --tests 'com.handy.core.prompts.PromptCatalogTest' \
  --tests 'com.handy.core.llm.AvailableToolsTest'

# Runtime recipe pack
./gradlew :android-runtime:testDebugUnitTest \
  --tests 'com.handy.runtime.agent.recipes.RuntimeRecipePackTest'

# Policy engine
./gradlew :android-runtime:testDebugUnitTest \
  --tests 'com.handy.runtime.action.DefaultActionPolicyEngineTest'
```

---

## Test and Validation Coverage

The repo currently has 69 Kotlin test files across unit and
instrumentation sources. The important lanes are:

- Pure-core module boundary test: `:core` must not import Android.
- Prompt tests for chat, voice, quick overlay, web search, shopping mode,
  recipes, reduced mode, and summarize-screen mode.
- Recipe router, registry smoke, resolver conflict, runner, and user-goal
  execution-gate tests.
- Date/time parsing tests for calendar recipes.
- Runtime recipe tests for open app, install app, timer, calendar,
  YouTube, notes, contacts, files, photos, calculator, food delivery,
  web search, Android Settings, Chrome, ride hailing, and pack
  registration.
- Action policy tests for sensitive fields, incognito, install handoff,
  settings targets, ride confirmation labels, and gesture fallback.
- Eval tests for duplicate targets, Hinglish shopping, intent-first
  behavior, mark-id selection, no-context honesty, secure windows,
  sensitive actions, and tool-injection.
- Replay tests for semantic pointer resolution.
- App tests for overlay presenter state, buddy-flight geometry, manual
  target selection, chat confirmation broker, target handoff, screen
  context budgets, sensitive logging, and crash diagnostics redaction.
- Instrumentation smoke tests for foreground service behavior, chat
  typing, secure windows, audit review, diagnostics screenshots,
  coexistence, mark-id handoff, install-app dispatcher fallback, manual
  target selection, and native recipe execution.

Recent full local validation used:

```bash
./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug
./gradlew :app:assembleRelease
```

Emulator install/launch crash smoke passed on `emulator-5554`. Physical
Pixel and signed-in app sweeps remain release-blocking manual work.

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
     reading, pointing, direct actions, or recipes.
   - Relaunch, continue, grant mic/notification/overlay as applicable,
     visit Accessibility settings, enable Handy, return, and verify the
     Tap-for-me disclosure appears.

2. Settings disclosure
   - Open Settings.
   - Verify "What Handy can do today" shows AI brain, screen reading,
     Tap-for-me, incognito block, recipes, web tools, entry points,
     notifications, clipboard, and Tutor mode.
   - Switch between Sonnet and Haiku and confirm the Anthropic key is
     shared.
   - Toggle web search and Tutor mode and confirm the capability rows
     update.
   - Accept Tap-for-me, stop it for one hour, and confirm the status
     changes to muted.
   - Stop Tap-for-me until turned back on and confirm recipes/actions are
     unavailable while chat still works.

3. Guidance-only flow
   - Open a normal app with visible buttons.
   - Ask "Where should I tap to search?"
   - Confirm Handy answers and points, with no action confirmation sheet
     and no tap.

4. Summarize-screen flow
   - Open a dense screen.
   - Tap the summarize quick prompt.
   - Confirm Handy returns a 2-4 sentence summary, uses no web/tool
     status, emits no pointer, and does not route a recipe.

5. Tap-for-me flow
   - Ask for one visible benign tap.
   - Confirm the action sheet names the target and app.
   - Cancel once and verify no action occurs.
   - Repeat, approve, and verify the action runs and audit records it.

6. Type-for-me flow
   - Use a normal search field.
   - Ask Handy to type ordinary text.
   - Edit/cancel in the confirmation sheet, then approve a safe value.
   - Verify sensitive short codes/password-like values are blocked.

7. Policy blocks
   - Try a banking/wallet/password-manager app.
   - Try Chrome Incognito.
   - Try a secure window.
   - Try purchase/payment/delete/add-to-cart/apply-coupon/personal-data
     submission language.
   - Verify Handy refuses before acting.

8. Recipes
   - OpenApp: open an installed app by name; verify ambiguous names are
     refused.
   - InstallApp: open a Play Store listing/search; verify Handy does not
     tap Install.
   - Clock: set a harmless alarm and timer.
   - Calendar: open an event draft with a bounded time phrase.
   - Maps: search a place; start navigation only after confirmation.
   - Chrome: open a URL, search the omnibox, and tap a visible page
     control.
   - YouTube: search a video and open a channel; verify like/subscribe/
     comment requests are refused.
   - Notes: share "buy milk" and confirm the chooser has that text.
   - Contacts: call Mom opens the dialer draft, text Maya opens an SMS
     draft, and duplicate Rohan contacts show candidate chips.
   - Files: open file search/document picker; verify mutation language is
     refused.
   - Photos: open Gallery, share the currently viewed photo via the
     visible Share control, and verify delete requests are refused.
   - Calculator: verify "23% of 4500" answers in chat with no recipe run;
     open Calculator separately.
   - Food delivery: find biryani on Swiggy/Zomato and track an order;
     verify ordering/payment language is refused.
   - Gmail/WhatsApp: draft only; verify Send requires hold confirmation.
   - Ride hailing: prepare a ride and verify Handy stops before Confirm
     or Request.
   - Shopping: search/coupon discovery; verify checkout/payment/apply
     flows are blocked.

9. Reduced mode and recovery
   - Disable Accessibility from Android Settings.
   - Verify chat and voice still work.
   - Verify pointing, direct actions, and recipes are unavailable.
   - Re-enable Accessibility and confirm the app recovers without crash.

10. Logs and diagnostics
    - Open Diagnostics/Audit.
    - Confirm audit entries are redacted.
    - Check logcat for API keys, raw screenshots, raw clipboard,
      notification bodies, OTPs, passwords, or card data. None should
      appear.

---

## Repo Map

- `core/` - domain contracts, policies, recipes, prompts, tools, evals,
  and pure Kotlin models.
- `android-runtime/` - Android adapters, storage, LLM clients, web
  tools, intent dispatch, speech, capture, and runtime recipes.
- `app/` - Android UI, services, overlays, settings, onboarding,
  diagnostics, confirmations, agent progress, and DI bindings.
- `docs/` - security, privacy, action policy, Play policy, device, and
  coexistence references.
- `docs/review-artifacts/` - release review media such as disclosure-flow
  videos.
- `.cursor/rules/` - operational guardrails and coding constraints.
- `DEBUG_LOG.md` - append-only bug-fix and prevention log.
- `DESIGN_NOTES.md` - architectural decisions and deviations.
- `PLAYSTORE_SUBMISSION.md` - Play Console copy, declarations, and
  release artifact checklist.
- `PRIVACY_POLICY.md` - policy text intended for public hosting.
- `handy_macos_ref/` - read-only macOS reference app. Treat it as
  reference material, not as Android source.

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
4. `docs/SECURITY_MODEL.md`
   Threat model, invariants, trust boundaries, and mitigations.
5. `docs/PRIVACY_MODEL.md`
   Data taxonomy, redaction sinks, and retention model.
6. `docs/PLAY_POLICY_MATRIX.md`
   Policy matrix mapping features to APIs, disclosure copy, risk, and
   Play justifications.
7. `docs/DEVICE_MATRIX.md`
   Device/OS validation checklist and blocked manual passes.
8. `PLAYSTORE_SUBMISSION.md`
   Store compliance dossier and review artifact checklist.
9. `DEBUG_LOG.md`
   Historical bug fixes and prevention rules.

---

## Development Principles

- Keep module boundaries strict. If `:core` imports Android, something
  went wrong.
- Prefer clear Kotlin over clever abstraction.
- Preserve the difference between advice, intents, Tap-for-me, and
  recipes.
- Treat web/tool content as untrusted evidence.
- Gate reduced mode at every layer: tools, prompts, and executors.
- Ship policy and privacy as part of the product, not as paperwork at
  the end.
- Every bug fix should teach the system something via `DEBUG_LOG.md`.
- Every new recipe needs router, prompt, policy, smoke, conflict, docs,
  and release-checklist coverage.

---

## License and References

This repo includes a read-only macOS reference app and design handoff
materials. Follow `DESIGN_NOTES.md` for clean-room guidance: port
recipes, techniques, and constants where allowed; do not copy source
from incompatible reference projects.
