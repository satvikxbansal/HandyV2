# Handy for Android — Play Store Submission Dossier

_Last updated: 2026-04-24. Owner: @satvik. Companion to
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

Verbatim from the in-app Accessibility service description
([`strings.xml → accessibility_service_description`](app/src/main/res/values/strings.xml)):

> Handy reads visible on-screen text and the UI tree so the assistant
> can answer questions about what you see and point at the right
> button. If you separately enable Tap-for-me, Handy can tap or scroll
> only after you confirm that exact action on screen. Handy blocks
> sensitive apps and you can turn this off at any time in Settings.

### 4.4 User benefit

- Answer "what does this button do?" / "how do I get to settings?"
  questions without screenshots.
- Point at the specific UI element the user should tap next.
- Detect the current foreground app so chat history is keyed per-app
  and the AI knows the user's context.

### 4.5 In-app disclosure

Shown in [`OnboardingActivity`](app/src/main/kotlin/com/handy/app/onboarding/OnboardingActivity.kt)
BEFORE any settings deep-link. Exact copy (string resource
`onboarding_disclosure_body`):

> Handy is an on-screen AI assistant. When you ask a question, Handy
> reads visible on-screen text via the Android Accessibility service
> and may capture the active window, then sends your message plus
> that context to Anthropic (Claude) over HTTPS using your own API
> key. Optional web-search tools send queries to Brave, Jina, and the
> public GitHub API only when you enable them in Settings. Tap-for-me
> is a separate opt-in after Accessibility is enabled: Handy can tap
> or scroll only after you confirm that exact action on screen, and
> sensitive apps are blocked. No data is sent to our servers — Handy
> has none. You can decline any permission and Handy will run in a
> reduced mode.

The action disclosure is shown after the Accessibility service is
enabled. Accepting it records the versioned Tap-for-me consent and
enables the Settings toggle; declining leaves Handy in read/point-only
mode. Every Tap-for-me action then shows an overlay confirmation sheet
with an 8-second auto-cancel, and sensitive confirmation levels require
a 1-second hold.

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

| Data type | Collected? | Shared with third parties? | Optional? | Encrypted in transit? | Users can request deletion? | Why |
|---|---|---|---|---|---|---|
| **Messages** (user-typed or transcribed) | Yes | Yes — Anthropic | No | Yes (HTTPS) | Yes — Settings → Clear all chat history | Core feature: the AI's input. |
| **Photos / screenshots** | Yes | Yes — Anthropic | No | Yes (HTTPS) | Yes — same action | Optional screen context for visual questions. Tied to a single user-initiated turn. Never captured from secured surfaces (OS-5). |
| **Other user-generated content** (on-screen text tree) | Yes | Yes — Anthropic | No | Yes (HTTPS) | Yes — same action | Accessibility tree snapshot, attached to the turn in plain text so Claude can answer UI questions. |
| **App interactions / action audit** (Tap-for-me result, target app, redacted target label, failure reason) | Yes | No | Yes — Tap-for-me opt-in only | N/A — local only | Yes — uninstall or clear app data | Local safety audit so users can review performed, cancelled, and policy-blocked actions such as `denylisted`. |
| **Search history / queries** | Yes (only when web search is enabled and the user typed a web-related query) | Yes — Brave Search / Jina Reader / GitHub Search | Yes (opt-in toggle) | Yes (HTTPS) | Yes — same action | Optional web-search tools. Off by default. |
| **Voice / audio recordings** | No | No | N/A | N/A | N/A | Audio is streamed into `android.speech.SpeechRecognizer` and is never stored by Handy. The recognizer may retain under the user's system speech settings. |
| **Contact info, financial info, health info, personal identifiers, location, browsing history, device IDs** | No | No | N/A | N/A | N/A | Not collected. |
| **Crashes / diagnostics** | No | No | N/A | N/A | N/A | Not collected. Timber logs never leave the device. |

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

Not applicable — Handy targets adults (18+) and is not in the
Designed For Families programme. Tap-for-me is an adult productivity
feature, remains opt-in, blocks banking/payment/password-manager apps,
and never runs a gesture without same-action confirmation.

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

- [ ] **In-app disclosure recording** — 30-second screen recording of the
  `OnboardingActivity` flow showing disclosure → mic grant →
  notifications grant → overlay grant → accessibility deep-link and
  toggle → Tap-for-me action disclosure → "Open Handy" becomes enabled.
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
