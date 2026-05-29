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

<!-- CAPABILITIES:README:START -->
Handy Android is past scaffold stage. The capability claims in this block are generated from [`docs/CAPABILITIES.yaml`](docs/CAPABILITIES.yaml); edit the manifest, then run `./gradlew generateCapabilityDocs`.

### Active

- **Screen explanations** (`screen_explain`) - reads visible UI text, labels, roles, bounds, view IDs, and app/window metadata via Android Accessibility after consent.
- **Pointing** (`pointing`) - buddy flies to visible controls for guidance; no auto-tap.
- **Deterministic recipes** (`recipes`) - registered, bounded, policy-checked recipes only; no LLM-authored free-form plans. Includes: `open_app`, `install_app`, `clock_alarm`, `set_timer`, `web_search`, `chrome_open_url`, `chrome_search`, `chrome_visible_tap`, `android_settings`, `gmail_draft`, `whatsapp_draft`, `calendar_event`, `maps_search`, `maps_navigation`, `youtube_search`, `notes_draft`, `contacts_handoff`, `files_picker`, `photos_handoff`, `calculator`, `food_delivery`, `ride_hailing_prep`, `shopping_search`, `visible_tap`, `visible_text_entry`, `visible_search`, `visible_scroll`.
- **System speech output** (`tts_system`) - Android TextToSpeech for spoken replies.
- **Android speech recognition** (`stt_android`) - Android SpeechRecognizer for push-to-talk voice input; on-device-first or on-device-only modes.

### Off by default

- **Tap-for-me** (`tap_for_me`) - node-first taps and scrolls after Tap-for-me disclosure, per-action confirmation, and fresh screen verification; gesture fallback only on learned apps.
- **Type-for-me** (`type_for_me`) - ordinary non-sensitive editable fields only; password, OTP, card, CVV, recovery-code, private-key, and secure-window typing is blocked.
- **Sarvam speech output** (`tts_sarvam`) - Sarvam Bulbul v3 cloud TTS, opt-in, user-supplied API key required.
- **Sarvam speech recognition** (`stt_sarvam`) - Sarvam Saarika v2 cloud STT, opt-in consent, user-supplied API key required.
- **Web tools** (`web_tools`) - Brave web_search, Jina fetch_page, and GitHub github_search for public information only; fetched content cannot trigger device actions.
- **Tutor mode** (`tutor_mode`) - rate-limited advisory guidance after idle time; cannot click, type, scroll, or run recipes by itself.
- **Clipboard assist** (`clipboard_assist`) - visible-only clipboard text help with size caps, dedupe, and secret-like content skips.

### Coming soon / out of beta

- **Notification summaries** (`notification_summaries`) - notification listener plumbing exists, but user-facing notification processing and RemoteInput replies are not active. Reason: out of beta scope.
- **Payments and checkout** (`payments`) - payments, purchases, checkout, money transfer, add-to-cart, applying coupons, and address or card edits stay blocked. Reason: out of beta scope.
- **Banking app automation** (`banking_app_automation`) - banking, wallet, payment, authenticator, password-manager, and secure-window actions stay blocked. Reason: out of beta scope.

### Recipe families

- `open_app`, `install_app`, `clock_alarm`, `set_timer`, `web_search`, `chrome_open_url`, `chrome_search`, `chrome_visible_tap`, `android_settings`, `gmail_draft`, `whatsapp_draft`, `calendar_event`, `maps_search`, `maps_navigation`, `youtube_search`, `notes_draft`, `contacts_handoff`, `files_picker`, `photos_handoff`, `calculator`, `food_delivery`, `ride_hailing_prep`, `shopping_search`, `visible_tap`, `visible_text_entry`, `visible_search`, `visible_scroll`

### Practical behavior

- A user asking "Where is Search?" gets an explanation and Buddy pointing at the visible Search control.
- A user asking "Tap Search for me" gets a Tap-for-me confirmation first; the tap is refused if the target is stale, sensitive, ambiguous, or low confidence.
- A user asking "Type Delhi here" gets ordinary text insertion only into a visible non-sensitive field after confirmation.
- A user asking "Install Spotify" gets a Play Store listing or search handoff; Handy never taps Install.
- A user asking for a payment, banking action, password, OTP, card entry, checkout, purchase, delete, or personal-data submission gets a block instead of automation.
<!-- CAPABILITIES:README:END -->

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

## Product Notes: Recipes

Recipes are Handy's small pieces of product judgment. They are not
"remember these buttons and blindly press them later." The model can ask
for one canonical intent, such as `open_app`, `set_timer`, `book_ride`,
or `photos_share_current`, plus a small JSON argument block. After that,
the deterministic registry owns the plan. This keeps the AI expressive
enough to understand "book a cab to the airport", but unable to invent
new hidden automation steps at runtime.

The recipes fall into a few deliberately different shapes. Some are
native Android handoffs: open an app, open Play Store, set an alarm or
timer, open Calendar compose, Maps, YouTube, Files, Photos, Calculator,
Settings, food delivery, or a browser search. Some work on the visible
screen: tap, type, search, scroll, Chrome page controls, shopping search,
coupons, photo sharing, and ride prep. Some are draft-only: Gmail,
WhatsApp, SMS, notes, contacts, and sharing flows prepare text or open a
chooser, then stop before the final human decision. Calculator is even
more restrained: simple arithmetic is answered locally in chat without
touching another app at all.

The most important nuance is freshness. When the user opens Handy from
the floating panel, the panel has a useful cached snapshot of the app
behind it, including labels and mark IDs. That snapshot is good for
understanding and previewing the plan. But when a recipe actually starts,
Handy intentionally asks for a fresh live screen grounding and drops the
frozen panel snapshot. Every step repeats that live capture, resolves the
target again, re-runs policy, performs the step, and then verifies that
something expected happened. This avoids the dangerous blend of "old
Photos context" with "new current-screen controls."

Foreground app detection is built for that same reason. The Accessibility
service listens to real-time window-change events, and Handy also
proactively scans the current accessibility windows when the widget or
chat opens. It chooses the topmost non-Handy app window, filters out
Handy's own overlay, keyboards, launchers, Recents, and System UI, and
extracts browser URL bars when possible so `Chrome on Gmail` and
`Chrome on GitHub` do not share one memory. The overlay panel refreshes
its idle app context after a short settle delay, but defers refreshes
while Handy is listening, streaming, confirming, pointing, or acting.

That is why recipes stop cleanly when reality changes. If the user
switches apps, goes home, the foreground disappears, Accessibility is
disconnected, the window changes shape, or the screen tree no longer
matches the target, the runner treats it as a privacy stop rather than a
recoverable hiccup. Launch steps are the one exception: opening WhatsApp,
Uber, Play Store, or Clock is allowed to change packages, then Handy
waits briefly and re-grounds inside the new app. After that, UI steps
must stay in the expected package.

The edge cases are product decisions, not afterthoughts. Open-app refuses
missing or ambiguous app names. Contacts asks the user to pick when names
collide. Calendar refuses recurring rules until the user clarifies
repeat behavior. Photos will share only when the user is already viewing
a photo. Shopping can search or find coupons, but not add to cart, apply
coupons, pay, or change addresses. Food delivery can search or track, but
not order. Ride hailing can fill the destination and optionally select a
class with a hold confirmation, then says the user must tap Confirm or
Request themselves. Gmail and WhatsApp draft, but Send is a separate
strong-hold step. Install opens Play Store; the user taps Install.

The same lane discipline applies when the screen is messy. Low confidence
targets, duplicate labels, stale mark IDs, secure windows, password-like
fields, OTPs, cards, payments, deletes, and Chrome Incognito all fail
closed. Tool-suggested device actions are blocked or upgraded to explicit
confirmation. Recipes are capped at six steps, audited locally, and
covered by routing, conflict, fixture, policy, and verification tests. In
reduced mode, Handy still chats, speaks, and answers, but recipes are
removed from the prompt, direct action tools are not advertised, and the
executor will not run them.

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
