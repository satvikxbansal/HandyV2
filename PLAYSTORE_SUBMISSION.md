# Handy for Android — Play Store Submission Dossier

_Last updated: 2026-05-22. Owner: @satvik. Companion to
[`PRIVACY_POLICY.md`](PRIVACY_POLICY.md), [`DESIGN_NOTES.md`](DESIGN_NOTES.md),
and [`DEBUG_LOG.md`](DEBUG_LOG.md)._

This document is the source of truth for every field in the Google Play
Console submission. Copy answers from here into the Console verbatim so
what we say on the store matches what the app actually does.

---

## 1. App-level metadata

| Field | Value |
|---|---|
| **Application ID** | `com.handy.android` |
| **App name** | Handy |
| **Short description (80 chars)** | On-screen AI assistant that reads visible UI and points you to the right tap. |
| **Category** | Productivity |
| **Contact email** | <fill in before first submission> |
| **Contact website** | <fill in; points to the privacy policy host> |
| **Privacy policy URL** | <public URL hosting `PRIVACY_POLICY.md`> |
| **Ads** | No |
| **In-app purchases** | No |
| **Target audience** | Adults (18+) |

---

## 2. Target SDK (mandatory April 2026)

| Field | Value |
|---|---|
| `minSdk` | 26 |
| `targetSdk` | 36 (Android 16) |
| `compileSdk` | 36 |

Source of truth: [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

Android 16 behavior-change audit (DL-017) confirmed we are clean:
- Mandatory edge-to-edge: applied in DL-015 (`enableEdgeToEdge()` on every
  Activity, `statusBarsPadding` + `navigationBarsPadding` on custom chrome).
- Predictive back: no `onBackPressed` overrides exist in `:app`; the
  system dispatcher is used by default.
- BODY_SENSORS / health permissions: not requested.
- MediaStore#getVersion: not read.

---

## 3. Permissions declaration

The Console's permissions review screen asks for a short justification
for every `dangerous` / `special` permission. Paste these verbatim.

### 3.1 Runtime / dangerous permissions

| Manifest permission | Justification for Play Console |
|---|---|
| `android.permission.RECORD_AUDIO` | Push-to-talk voice input. User initiates every capture by long-pressing the floating widget or tapping the mic in chat. Audio is routed to `android.speech.SpeechRecognizer` (on-device when available; cloud-backed fallback follows the user's system speech settings). Audio is never stored. |
| `android.permission.POST_NOTIFICATIONS` | Required to show the foreground-service notification on Android 13+. The notification makes the user aware that Handy is running in the background and gives a one-tap path to stop it. |
| `android.permission.SYSTEM_ALERT_WINDOW` | Floating widget overlay. The widget is the primary way users invoke Handy — without the overlay, the assistant cannot follow them across apps. The overlay hosts only Handy's own chrome (idle, listening, thinking states). Typed chat lives in `ChatActivity`, not in an overlay. |
| `android.permission.PACKAGE_USAGE_STATS` | NOT REQUESTED. App detection relies on the Accessibility service only. |
| `android.permission.QUERY_ALL_PACKAGES` | NOT REQUESTED. Launchable-app index is built from `queryIntentActivities(Intent(ACTION_MAIN) + CATEGORY_LAUNCHER)` which is exempt from the Play restriction. |

### 3.2 Foreground Service types (Android 14+ mandatory)

| Service | `foregroundServiceType` | Justification |
|---|---|---|
| `AssistantForegroundService` | `specialUse\|microphone` | `specialUse`: keep the floating-widget overlay alive across app switches (user-configurable via the widget itself). The `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value is `on-screen AI assistant overlay that must remain visible across apps`. `microphone`: owns the mic during push-to-talk (Android 14 requires declaring the type when the service will record audio). |
| `MediaProjectionCaptureService` | `mediaProjection` | API 26–29 fallback only. Higher APIs use `AccessibilityService.takeScreenshot(OfWindow)` which does not need MediaProjection. Every capture is user-initiated (ChatActivity asks for consent at turn-time). |

### 3.3 Permission sets ("groups") that trigger Play review

| Permission | Declaration needed? |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Yes — see §4 below. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Yes — see §3.2 AssistantForegroundService. |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Yes — see §3.2 MediaProjectionCaptureService. |
| `FOREGROUND_SERVICE_MICROPHONE` | Yes — bundled with `specialUse` on AssistantForegroundService. |

---

## 4. Accessibility service declaration (critical)

Google Play reviews every app that declares
`android.permission.BIND_ACCESSIBILITY_SERVICE`. Fill the
"Accessibility service" section of the Permissions declaration form
with the following:

### 4.1 Does your app use an AccessibilityService?

**Yes.**

### 4.2 Is the AccessibilityService used to help users with disabilities?

**No.** Handy uses the AccessibilityService to read visible on-screen
text and point at UI elements so the AI can answer contextual
questions ("how do I export from Figma?") and highlight the right
button. This is an **augmentation use**, not an assistive-technology
use. Per Google Play policy, augmentation uses are allowed when the
app prominently discloses the access, obtains explicit user consent,
and provides a usable reduced-mode fallback.

### 4.3 What does the service do?

<!-- CAPABILITIES:PLAY_FEATURE_CLAIMS:START -->
Handy uses AccessibilityService for a general screen-aware AI copilot experience. After prominent disclosure and user consent, the service supports only the capabilities marked active in [`docs/CAPABILITIES.yaml`](docs/CAPABILITIES.yaml).

Active capabilities

- **Screen explanations** (`screen_explain`) - reads visible UI text, labels, roles, bounds, view IDs, and app/window metadata via Android Accessibility after consent.
- **Pointing** (`pointing`) - buddy flies to visible controls for guidance; no auto-tap.
- **Deterministic recipes** (`recipes`) - registered, bounded, policy-checked recipes only; no LLM-authored free-form plans. Includes: `open_app`, `install_app`, `clock_alarm`, `set_timer`, `web_search`, `chrome_open_url`, `chrome_search`, `chrome_visible_tap`, `android_settings`, `gmail_draft`, `whatsapp_draft`, `calendar_event`, `maps_search`, `maps_navigation`, `youtube_search`, `notes_draft`, `contacts_handoff`, `files_picker`, `photos_handoff`, `calculator`, `food_delivery`, `ride_hailing_prep`, `shopping_search`, `visible_tap`, `visible_text_entry`, `visible_search`, `visible_scroll`.
- **System speech output** (`tts_system`) - Android TextToSpeech for spoken replies.
- **Android speech recognition** (`stt_android`) - Android SpeechRecognizer for push-to-talk voice input; on-device-first or on-device-only modes.

Off-by-default capabilities

- **Tap-for-me** (`tap_for_me`) - node-first taps and scrolls after Tap-for-me disclosure, per-action confirmation, and fresh screen verification; gesture fallback only on learned apps.
- **Type-for-me** (`type_for_me`) - ordinary non-sensitive editable fields only; password, OTP, card, CVV, recovery-code, private-key, and secure-window typing is blocked.
- **Sarvam speech output** (`tts_sarvam`) - Sarvam Bulbul v3 cloud TTS, opt-in, user-supplied API key required.
- **Sarvam speech recognition** (`stt_sarvam`) - Sarvam Saarika v2 cloud STT, opt-in consent, user-supplied API key required.
- **Web tools** (`web_tools`) - Brave web_search, Jina fetch_page, and GitHub github_search for public information only; fetched content cannot trigger device actions.
- **Tutor mode** (`tutor_mode`) - rate-limited advisory guidance after idle time; cannot click, type, scroll, or run recipes by itself.
- **Clipboard assist** (`clipboard_assist`) - visible-only clipboard text help with size caps, dedupe, and secret-like content skips.

Not active in this beta

- **Notification summaries** (`notification_summaries`) - notification listener plumbing exists, but user-facing notification processing and RemoteInput replies are not active. Reason: out of beta scope.
- **Payments and checkout** (`payments`) - payments, purchases, checkout, money transfer, add-to-cart, applying coupons, and address or card edits stay blocked. Reason: out of beta scope.
- **Banking app automation** (`banking_app_automation`) - banking, wallet, payment, authenticator, password-manager, and secure-window actions stay blocked. Reason: out of beta scope.

Safety boundaries generated from the manifest:

- Web-tool output is informational evidence only and cannot trigger device actions.
- Tap-for-me and Type-for-me require a separate disclosure, a visible target, policy approval, and user confirmation.
- Payments, banking app automation, password/OTP/card typing, secure-window content, purchases, checkout, deletion, and personal-data submission are outside the active beta scope.
<!-- CAPABILITIES:PLAY_FEATURE_CLAIMS:END -->
Verbatim in-app Accessibility service description
([`strings.xml -> accessibility_service_description`](app/src/main/res/values/strings.xml)):

> Handy reads visible screen text, UI labels, roles, bounds, and the
> current app/window so it can answer questions, point at controls, and
> verify actions you explicitly request. If you separately enable
> Tap-for-me, Handy can tap, scroll, long-press, or type ordinary text
> only after on-screen confirmation. Handy blocks secure windows,
> passwords, OTP/card fields, payments, purchases, deletes, and
> sensitive apps. You can turn this off any time.

### 4.4 User benefit

- Answer "what does this button do?" / "how do I get to settings?"
  questions without screenshots.
- Point at the specific UI element the user should tap next.
- Detect the current foreground app so chat history is keyed per-app
  and the AI knows the user's context.

### 4.5 In-app disclosure

Disclosure locations:

- [`OnboardingActivity`](app/src/main/kotlin/com/handy/app/onboarding/OnboardingActivity.kt)
  shows the main Accessibility/data disclosure before any Android
  settings deep-link.
- [`ActionDisclosureActivity`](app/src/main/kotlin/com/handy/app/onboarding/ActionDisclosureActivity.kt)
  shows the separate Tap-for-me disclosure after Accessibility has been
  visited/enabled.
- [`SettingsActivity`](app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt)
  has a "What Handy can do today" section generated from
  [`docs/CAPABILITIES.yaml`](docs/CAPABILITIES.yaml). It tells
  reviewers which capabilities are active, off by default, or outside
  the active beta scope.

Main onboarding disclosure, exact copy from
[`strings.xml -> onboarding_disclosure_body`](app/src/main/res/values/strings.xml):

> Handy is an on-screen AI assistant. When you ask a question, Handy can
> read visible screen text, UI labels, roles, bounds, and the current
> app/window through Android Accessibility, and it may capture the
> active window for that turn. Handy sends your message plus the minimum
> needed screen context to Anthropic (Claude) over HTTPS using your own
> API key. Optional web-search tools send your search or page-fetch
> query to Brave, Jina, and the public GitHub API only when web search
> is enabled in Settings. Handy does not have our own server.
> Tap-for-me is a second opt-in after Accessibility: Handy can tap,
> scroll, long-press, or type ordinary text only after it shows the
> exact action and you approve it. Handy will not read or type
> passwords, OTPs, card details, secure-window content, payments,
> purchases, deletes, or personal-data submissions. You can decline
> permissions and Handy will still run in reduced mode for typed chat,
> voice input, and ordinary AI answers.

Tap-for-me action disclosure, exact behavior:

- Accepting records
  `actionDisclosureVersionAccepted = ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION`
  and enables the Settings Tap-for-me toggle.
- Declining leaves Handy in screen-reading, pointing, chat, voice, web
  search, and intent-first reduced-action mode. No Accessibility
  gesture performer is activated.
- Every Tap-for-me/Type-for-me action uses the overlay confirmation
  sheet. Higher-risk action levels require a hold confirmation.
- The Settings screen exposes Tap-for-me on/off, one-hour panic stop,
  "stop until I turn it back on", Chrome Incognito action blocking, and
  per-package restore controls.

Disclosure-flow video:
[`docs/review-artifacts/disclosure-flow-2026-05-22.mp4`](docs/review-artifacts/disclosure-flow-2026-05-22.mp4)

### 4.6 Reduced mode

If the user declines or disables the Accessibility toggle:
- App detection is disabled — the chat still works, but the tool-name
  bar is hidden.
- Pointing is disabled — no blue-arrow overlay.
- `dispatch_action` tool is disabled — no Intents fire.
- Typed chat + push-to-talk voice + Claude API roundtrip all continue
  to work.

The chat screen shows a non-dismissible
`AccessibilityNudgeBanner` (DL-016) with "Open Settings" whenever the
service is off — the user always has a one-tap return path.

---

## 5. Data safety form

Fill the Play Console "Data safety" section with the following
mapping. Our rule: **declare everything the user's message may contain
or that we intentionally attach to a turn**.

### 5.1 Data collected

Fill the Play Console Data safety form from this table. Declare
anything Handy intentionally attaches to a user turn or keeps locally
for transparency.

| Data type | Collected? | Shared with third parties? | Optional? | Encrypted in transit? | Users can request deletion? | Why / scope |
|---|---|---|---|---|---|---|
| **Messages** (typed text and voice transcripts) | Yes | Yes - Anthropic today | No for core chat | Yes (HTTPS) | Yes - Settings -> Clear all chat history, app data clear, or uninstall | Core assistant input. Voice audio is not stored by Handy; only the recognized transcript enters chat. |
| **Photos / screenshots / active-window captures** | Yes, per user-initiated turn when needed | Yes - Anthropic today | Yes in practice; screen context can be declined by reduced mode | Yes (HTTPS) | Yes - clear history/app data; screenshots are not routinely stored | Visual context for a question. Secure, unsupported, failed, or blocked captures are not sent. |
| **Other user-generated content** (visible screen text, UI labels, roles, bounds, view IDs, app/window metadata) | Yes when Accessibility is enabled | Yes - Anthropic today when attached to a turn | Yes - Accessibility can be declined/disabled | Yes (HTTPS) | Yes - clear history/app data; per-turn snapshots are ephemeral | Lets Handy answer screen questions, point at controls, and verify explicit actions. Password/OTP/card/secure-field content is blocked/redacted. |
| **App interactions / local action audit** (action type, target app, redacted target label, confirmation state, policy result, failure reason) | Yes | No by default | Yes - action features are opt-in/gated | N/A - local only | Yes - clear app data or uninstall | Lets users/reviewers see performed, cancelled, verified, and policy-blocked actions. No screenshots, raw prompts, API keys, or raw secrets. |
| **Search history / queries** | Yes only when web search is enabled and the user asks a web/page/repo query | Yes - Brave Search, Jina Reader, and public GitHub API | Yes - web search is off by default | Yes (HTTPS) | Yes - clear history/app data | Optional public web tools. Fetched content is untrusted evidence and cannot trigger device actions by itself. |
| **Notification content** (app label, title, text, key, reply availability) | Not active by default; collected only if notification feature flag and Android notification access are enabled | Not shared today unless future user-requested summary/reply flow sends it to selected provider | Yes - feature gated/off by default | Yes if later sent to provider | Yes - disable notification access, clear app data, uninstall | Current build publishes empty snapshots when the feature flag is off. RemoteInput reply sending is not active today. |
| **Clipboard content** | Not active by default; collected only while Handy is visible and clipboard assist is enabled | Yes only if the user sends/uses a clip in an AI turn | Yes - feature gated/off by default | Yes if sent to provider | Yes - disable clipboard assist, clear app data, uninstall | Visible-only clipboard help. 32 KB cap, hash dedupe, skips password/OTP/card-like clips, ignores URI/binary clips. |
| **Voice / audio recordings** | No persistent Handy collection | No by Handy | N/A | N/A | N/A | Audio is streamed to Android `SpeechRecognizer` only while the user starts voice input. System recognizer retention follows the user's Android speech settings. |
| **API keys and credentials** | Yes, user-provided keys | Sent only to the matching provider endpoint as auth headers | Yes - user must provide cloud keys for cloud features | Yes (HTTPS) | Yes - remove/rotate keys or clear app data | Stored in Android Keystore-backed encrypted storage. Never logged, audited, or shown in diagnostics. |
| **Contact info, financial info, health info, location, device IDs, advertising IDs** | No intentional collection | No | N/A | N/A | N/A | Not requested or used as product data. Some user messages may contain user-entered content; that is covered by Messages. |
| **Crashes / diagnostics** | No external crash reporter | No | N/A | N/A | N/A | Timber/logcat diagnostics stay local and must be secret-free. DiagnosticsActivity is local/read-only. |

### 5.2 Encryption in transit

**Yes** — enforced at the platform level via
[`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml)
(`cleartextTrafficPermitted="false"`) and the manifest flag
`android:usesCleartextTraffic="false"`.

### 5.3 Data deletion mechanism

Settings → "Clear all chat history" wipes the
[`JsonHistoryStore`](android-runtime/src/main/kotlin/com/handy/runtime/storage/JsonHistoryStore.kt)
directory. API keys are nullable (Update / Remove buttons on every
CredentialField). Uninstalling the app is a full wipe — Handy runs no
server-side state.

### 5.4 Families policy

Not applicable - Handy targets adults (18+) and is not submitted to the
Designed for Families programme.

Play Console target audience answer:

- Target age: adults 18+.
- Not designed for children.
- No ads.
- No in-app purchases.
- No gambling, dating, medical, financial-services, or child-directed
  content.

Reasoning: Handy is a productivity assistant that can read visible app
context and, after a second opt-in, perform confirmed device actions.
Those capabilities are intentionally adult-only. Tap-for-me, recipes,
notification seams, clipboard assist, and Tutor mode are all gated,
auditable, and documented, but they are still not appropriate for a
child-directed listing. Payment, purchase, checkout, transfer, delete,
password/OTP/card typing, and personal-data submission actions are
blocked in this build.

---

## 6. Target SDK and API requirement declarations

### 6.1 Edge-to-edge

Android 16 enforces edge-to-edge for `targetSdk = 36`. Confirmed in
DL-015 and DL-017 — every Activity calls `enableEdgeToEdge()` and
handles insets via Compose modifiers.

### 6.2 Predictive back

No-op — no `onBackPressed` overrides.

### 6.3 Foreground service permissions

Listed in `AndroidManifest.xml`:
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
`FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`.
All required based on the services we declare.

---

## 7. Review artifacts checklist

Every item must be ready before the submission is uploaded. Tick off
here as the artifacts land.

- [x] **In-app disclosure recording** — linked in §4.5 as
  [`docs/review-artifacts/disclosure-flow-2026-05-22.mp4`](docs/review-artifacts/disclosure-flow-2026-05-22.mp4).
  The recording must show `OnboardingActivity` disclosure -> mic grant
  -> notifications grant -> overlay grant -> accessibility deep-link
  and toggle -> Tap-for-me action disclosure -> Handy opens in ready or
  reduced mode.
- [ ] **Canonical chat + pointing session** — 30-second recording of:
  widget tap → chat opens with detected tool name → user asks a question →
  response streams → (post-v2: pointer arrow flies to a UI element).
- [ ] **Accessibility-off reduced mode** — 15-second recording showing
  the `AccessibilityNudgeBanner` and the chat working without detection
  (typed chat + voice still functional).
- [ ] **Screenshots for Play listing** (min 2, recommended 8):
  - Widget idle on home screen
  - Widget listening (waveform animation)
  - Widget thinking (spinner animation)
  - Chat with streaming response
  - Chat with web-search italic label
  - Chat with tool-name bar showing "Gmail" / "GitHub"
  - Onboarding disclosure screen
  - Settings screen
- [ ] **Feature graphic** (1024 × 500 PNG).
- [ ] **High-res app icon** (512 × 512 PNG, replace the
  `@android:drawable/sym_def_app_icon` placeholder in the manifest).
- [ ] **Privacy policy hosted at a public URL** — copy of
  `PRIVACY_POLICY.md`.

---

## 8. Pre-submission code audit (verified 2026-04-24)

| Check | Status |
|---|---|
| `allowBackup = false` | ✓ |
| `dataExtractionRules` excludes sharedpref + file domain | ✓ |
| `networkSecurityConfig` present, cleartext off | ✓ (added in DL-019) |
| `usesCleartextTraffic = false` | ✓ (added in DL-019) |
| No `QUERY_ALL_PACKAGES` | ✓ |
| No `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | ✓ |
| No `READ_CONTACTS` / `READ_CALL_LOG` / `READ_SMS` | ✓ |
| Accessibility service guarded by `BIND_ACCESSIBILITY_SERVICE` | ✓ |
| Foreground services declare type + subtype property | ✓ |
| OnboardingActivity disclosure fires BEFORE accessibility deep-link | ✓ |
| Reduced-mode fallback exists | ✓ (DL-016) |
| Chat nudge banner when a11y off | ✓ (DL-016) |
| `targetSdk = 36` | ✓ (DL-017) |
| No on-device analytics / crash reporters shipping user content | ✓ |
| All network calls HTTPS only | ✓ |
| API keys in EncryptedSharedPreferences | ✓ |

---

## 9. What to paste into the Console "App content" tab

| Section | Answer |
|---|---|
| **Privacy policy** | URL of hosted `PRIVACY_POLICY.md`. |
| **Ads** | No. |
| **App access** | All functionality available without a login; API keys are user-provided. |
| **Content ratings** | Questionnaire: no violence, no sexual content, no gambling, no drugs, no user-generated content published outside the user's device. Expected rating: ESRB Everyone / PEGI 3. |
| **Target audience** | Adults 18+. |
| **News app** | No. |
| **COVID-19 contact tracing** | No. |
| **Data safety** | See §5 of this document. |
| **Government app** | No. |
| **Financial features** | No. |
| **Health apps** | No. |

---

## 10. Dates and deadlines

| Event | Date |
|---|---|
| Google Play `targetSdk >= 36` mandate | **April 2026 (active now)** |
| Internal testing track open | <set when submission is ready> |
| Closed testing → open testing promotion | 14-day policy requires continuous closed-track activity — plan accordingly. |
