# Handy for Android - Privacy Policy

_Last updated: 2026-05-26. Draft for Play review. This policy is
written to match the current Android implementation and the related
Play submission dossier._

Handy is an on-screen AI assistant for Android. It can answer questions
about the visible app, point at controls, and, after a separate opt-in,
perform tightly bounded actions that you explicitly approve.

Handy does not run its own server. When cloud AI is used, your device
talks directly to the selected provider using your own API key.

## What Handy can do

<!-- CAPABILITIES:PRIVACY_DISCLOSURES:START -->
This section is generated from [`docs/CAPABILITIES.yaml`](docs/CAPABILITIES.yaml).

### Active by default or permission

- **Screen explanations** (`screen_explain`) - reads visible UI text, labels, roles, bounds, view IDs, and app/window metadata via Android Accessibility after consent.
- **Pointing** (`pointing`) - buddy flies to visible controls for guidance; no auto-tap.
- **Deterministic recipes** (`recipes`) - registered, bounded, policy-checked recipes only; no LLM-authored free-form plans. Includes: `open_app`, `app_search`, `install_app`, `clock_alarm`, `set_timer`, `web_search`, `chrome_open_url`, `chrome_search`, `chrome_visible_tap`, `android_settings`, `gmail_draft`, `whatsapp_draft`, `calendar_event`, `maps_search`, `maps_navigation`, `youtube_search`, `notes_draft`, `contacts_handoff`, `files_picker`, `photos_handoff`, `calculator`, `food_delivery`, `ride_hailing_prep`, `shopping_search`, `visible_tap`, `visible_text_entry`, `visible_search`, `visible_scroll`.
- **System speech output** (`tts_system`) - Android TextToSpeech for spoken replies.
- **Android speech recognition** (`stt_android`) - Android SpeechRecognizer for push-to-talk voice input; on-device-first or on-device-only modes.

### Off by default until you opt in

- **Tap-for-me** (`tap_for_me`) - node-first taps and scrolls after Tap-for-me disclosure, per-action confirmation, and fresh screen verification; gesture fallback only on learned apps.
- **Type-for-me** (`type_for_me`) - ordinary non-sensitive editable fields only; password, OTP, card, CVV, recovery-code, private-key, and secure-window typing is blocked.
- **Sarvam speech output** (`tts_sarvam`) - Sarvam Bulbul v3 cloud TTS, opt-in, user-supplied API key required.
- **Sarvam speech recognition** (`stt_sarvam`) - Sarvam Saarika v2 cloud STT, opt-in consent, user-supplied API key required.
- **Web tools** (`web_tools`) - Brave web_search, Jina fetch_page, and GitHub github_search for public information only; fetched content cannot trigger device actions.
- **Tutor mode** (`tutor_mode`) - rate-limited advisory guidance after idle time; cannot click, type, scroll, or run recipes by itself.
- **Clipboard assist** (`clipboard_assist`) - visible-only clipboard text help with size caps, dedupe, and secret-like content skips.

### Not active in this beta

- **Notification summaries** (`notification_summaries`) - notification listener plumbing exists, but user-facing notification processing and RemoteInput replies are not active. Reason: out of beta scope.
- **Payments and checkout** (`payments`) - payments, purchases, checkout, money transfer, add-to-cart, applying coupons, and address or card edits stay blocked. Reason: out of beta scope.
- **Banking app automation** (`banking_app_automation`) - banking, wallet, payment, authenticator, password-manager, and secure-window actions stay blocked. Reason: out of beta scope.

## What Handy will not do

- Handy will not listen or capture the screen in the background.
- Handy will not let fetched web pages or tool results trigger actions on your phone.
- Handy will not run open-ended LLM-authored plans.
- Handy will not type passwords, OTPs, card numbers, CVVs, recovery codes, private keys, seed phrases, or secure-window content.
- Handy will not pay, purchase, checkout, transfer money, delete, add to cart, apply coupons, submit personal data, or automate banking/payment/password-manager/authenticator apps in this beta.

## What leaves the device

- Your typed message or recognized voice transcript when you send a turn.
- The minimum screen context needed for that turn when Accessibility is enabled, such as visible labels, roles, bounds, app/window metadata, and optional screenshot data when needed.
- Optional public web-search queries, fetched-page URLs, and public GitHub queries when web tools are enabled.
- Optional Sarvam cloud voice traffic only when the matching Sarvam voice feature is selected, consent is saved where required, and a user-supplied Sarvam API key is present.
- Action arguments sent through visible Android platform flows, such as a Maps query, calendar title, mail draft, or share text.

## What stays on the device

- API keys in Android Keystore-backed encrypted storage.
- Chat history in app-private storage.
- Redacted local action and timeline audit entries.
- Per-turn screen snapshots after the turn; they are ephemeral and are not appended to chat history as hidden raw data.
- Timber/logcat diagnostics must not contain API keys, screenshots, raw prompts, raw notification bodies, raw clipboard contents, or raw accessibility trees.
<!-- CAPABILITIES:PRIVACY_DISCLOSURES:END -->

## Tap-for-me

Tap-for-me is off until you accept the separate action disclosure. After
that, Settings still controls whether the action gate is open.

When Tap-for-me is on, Handy can perform only the specific visible
action you asked for. Examples:

- "Tap Search for me" can tap a visible Search button after Handy
  resolves the target, verifies the screen still matches, and shows the
  confirmation sheet.
- "Scroll down" can perform one bounded scroll. Handy does not keep
  scrolling until it finds something.
- "Type Delhi into this field" can use Android's `ACTION_SET_TEXT` only
  for an editable visible field, after showing you the text first.

Tap-for-me is blocked for sensitive apps and surfaces, including
banking, payments, wallets, password managers, secure windows,
password/OTP/card fields, low-confidence targets, stale screens,
payments, purchases, deletes, and personal-data submissions.

Controls available today:

- Settings -> Tap-for-me toggle.
- Settings -> Stop Tap-for-me for 1 hour.
- Settings -> Stop until I turn it back on.
- Settings -> Block Chrome Incognito actions.
- Per-package disabled-app restore list.
- Android Settings -> Accessibility -> Handy to turn the service off
  entirely.

## Deterministic recipes

Recipes are not free-form automation. The AI may choose a recipe and
arguments, but it cannot invent new executable steps. Each recipe step
is predefined, policy-checked, confirmed when sensitive, executed
through Android intents or Accessibility actions, and verified where
possible.

Current examples:

- Clock: set an alarm through the Android alarm intent.
- Android Settings: open safe settings screens. Sensitive settings
  targets are blocked by policy.
- Gmail: open a draft with recipient/subject/body filled, then pause
  before Send.
- WhatsApp: open/search a chat and fill a draft, then pause before Send.
- Chrome: open a URL or tap a visible page control; page summaries use
  fetch-page tools instead of actions.
- Maps: search a place or start navigation only after confirmation.
- Shopping apps: search, compare, or find coupons for Meesho, Amazon,
  or Flipkart. Purchase, checkout, payment, and address/card actions
  are blocked.

## Notifications and RemoteInput

The codebase contains a narrow `NotificationListenerService` for future
notification assistance, and it detects whether a notification exposes a
platform `RemoteInput` reply affordance.

Current user-facing behavior is intentionally limited:

- Notification processing is off by default.
- The Settings capability table states when notification processing is
  off.
- When the notification feature flag is off, Handy publishes no
  notification snapshots and does not process notification content.
- RemoteInput reply sending is not active in this build. Handy does not
  send notification replies today.

Future RemoteInput reply support must show the exact reply text, require
strong confirmation, use the platform notification action path, and
write a redacted local audit entry.

## Clipboard assist

Clipboard assist is opt-in and visible-only. When enabled, Handy can
look at copied text only while the Handy chat or panel is visible.

Safeguards:

- No background clipboard listener while Handy is hidden.
- 32 KB size cap.
- SHA-256 dedupe so the same clip is not repeatedly processed.
- Password-like, OTP-like, and card-like clips are skipped.
- URI and binary clips are ignored.
- Sensitive write-back is marked with Android's sensitive clip extras
  where supported.

## Permissions

| Permission or access | Used for |
|---|---|
| `RECORD_AUDIO` | Push-to-talk voice input only after you start voice capture. Android STT is default; optional Sarvam STT uploads capped session audio only after explicit Settings consent. |
| `SYSTEM_ALERT_WINDOW` | Floating widget, overlay panel, and action confirmation sheet. |
| Accessibility service | Visible screen reading, pointing, target verification, and separately confirmed Tap-for-me actions. |
| `POST_NOTIFICATIONS` | Foreground-service notification that keeps Handy visible and stoppable. |
| `FOREGROUND_SERVICE_*` | Android-required service types for overlay/microphone and API 26-29 screen-capture fallback. |
| `BIND_QUICK_SETTINGS_TILE` | Optional Quick Settings tile entry point. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Future notification summaries; off by default and no RemoteInput reply sending today. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Talking to Claude, optional Sarvam STT/TTS, and optional Brave/Jina/GitHub web tools over HTTPS. |

Handy does not request `QUERY_ALL_PACKAGES`. Launchable-app lookup uses
the Android launcher intent query path.

## Your controls

- Turn off Accessibility in Android Settings -> Accessibility -> Handy.
- Use reduced mode without Accessibility.
- Turn Tap-for-me off, mute it for one hour, or disable it until you turn
  it back on.
- Keep Chrome Incognito actions blocked.
- Disable web search.
- Switch speech recognition back to Android STT or revoke Sarvam STT
  consent in Settings.
- Turn Tutor mode off.
- Clear all chat history in Settings.
- Delete or rotate API keys in Settings.
- Clear app data or uninstall Handy for a full local wipe.

## Contact

Questions about this policy: open a GitHub issue on the project repo or
use the contact email listed in the Play Console once the app is live.
