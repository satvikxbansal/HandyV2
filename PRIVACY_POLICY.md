# Handy for Android - Privacy Policy

_Last updated: 2026-05-22. Draft for Play review. This policy is
written to match the current Android implementation and the related
Play submission dossier._

Handy is an on-screen AI assistant for Android. It can answer questions
about the visible app, point at controls, and, after a separate opt-in,
perform tightly bounded actions that you explicitly approve.

Handy does not run its own server. When cloud AI is used, your device
talks directly to the selected provider using your own API key.

## What Handy can do

- Show a floating widget and overlay chat panel so you can ask about
  the app you are using.
- Listen only when you start voice input, such as long-pressing the
  widget or using the chat voice control.
- Read visible screen text, UI labels, roles, bounds, view IDs, and
  current app/window metadata through Android Accessibility after you
  grant that access.
- Capture the active window for a user-initiated turn when visual
  context is needed.
- Point at a visible control without tapping it.
- Use Android intents for explicit system tasks, such as opening an
  app, opening a settings screen, creating a calendar event, opening a
  Maps search, or opening a share/compose flow.
- Use optional web-search tools when web search is enabled in Settings.
- Use Tap-for-me after a second disclosure and consent step. Tap-for-me
  can tap, scroll, long-press, or type ordinary text only after Handy
  shows the exact action and you confirm it.
- Run deterministic recipes for explicit do-it-for-me requests. Current
  recipe families include Clock, Android Settings, Gmail drafts,
  WhatsApp drafts, Chrome navigation, Maps search/navigation, and
  shopping search/compare/coupon flows.
- Keep a local, redacted action audit so you can inspect actions,
  cancellations, failures, and policy blocks.
- Offer Tutor mode when enabled. Tutor mode is advisory and rate
  limited; it does not act on the device by itself.

## What Handy will not do

- Handy will not listen in the background.
- Handy will not capture the screen in the background.
- Handy will not read secure-window content, password fields, OTPs,
  card numbers, CVVs, or credential-like fields.
- Handy will not type passwords, OTPs, card details, CVVs, recovery
  codes, private keys, or short verification codes.
- Handy will not pay, purchase, checkout, transfer money, delete,
  submit personal data, or change sensitive account/security settings
  in this build.
- Handy will not let fetched web pages or tool results trigger actions
  on your phone.
- Handy will not run open-ended LLM-authored plans. Recipes are
  deterministic, capped, checked against current screen state, and
  stopped when the app or screen changes.
- Handy will not auto-send messages. Gmail and WhatsApp recipes draft
  or open flows, then require confirmation before any send step.
- Handy will not auto-triage, auto-reply, auto-dismiss, or archive your
  notifications in the background.
- Handy will not read your clipboard in the background.
- Handy will not sell, rent, or share your data with advertisers.

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

## What leaves the device

- Your typed message or voice transcript.
- The minimum screen context needed for that turn, such as visible
  labels, roles, bounds, app/window metadata, and optional screenshot
  data when needed.
- Optional web-search queries and fetched-page URLs when web search is
  enabled.
- Action arguments sent to Android target apps through visible platform
  flows, such as a Maps query, calendar title, mail draft, or share text.

Cloud AI traffic goes directly to Anthropic today using your API key.
Web tools, when enabled, use Brave Search, Jina Reader, and the public
GitHub API over HTTPS.

## What stays on the device

- API keys are stored in Android Keystore-backed encrypted storage.
- Chat history is stored as JSON in app-private storage.
- Action audit entries are stored locally and redacted.
- Per-turn screen snapshots are ephemeral and are not appended to chat
  history as hidden raw data.
- Handy does not retain microphone audio after recognition.
- Timber/logcat diagnostics must not contain API keys, screenshots, raw
  prompts, raw notification bodies, raw clipboard contents, or raw
  accessibility trees.

## Permissions

| Permission or access | Used for |
|---|---|
| `RECORD_AUDIO` | Push-to-talk voice input only after you start voice capture. |
| `SYSTEM_ALERT_WINDOW` | Floating widget, overlay panel, and action confirmation sheet. |
| Accessibility service | Visible screen reading, pointing, target verification, and separately confirmed Tap-for-me actions. |
| `POST_NOTIFICATIONS` | Foreground-service notification that keeps Handy visible and stoppable. |
| `FOREGROUND_SERVICE_*` | Android-required service types for overlay/microphone and API 26-29 screen-capture fallback. |
| `BIND_QUICK_SETTINGS_TILE` | Optional Quick Settings tile entry point. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Future notification summaries; off by default and no RemoteInput reply sending today. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Talking to Claude and optional Brave/Jina/GitHub web tools over HTTPS. |

Handy does not request `QUERY_ALL_PACKAGES`. Launchable-app lookup uses
the Android launcher intent query path.

## Your controls

- Turn off Accessibility in Android Settings -> Accessibility -> Handy.
- Use reduced mode without Accessibility.
- Turn Tap-for-me off, mute it for one hour, or disable it until you turn
  it back on.
- Keep Chrome Incognito actions blocked.
- Disable web search.
- Turn Tutor mode off.
- Clear all chat history in Settings.
- Delete or rotate API keys in Settings.
- Clear app data or uninstall Handy for a full local wipe.

## Contact

Questions about this policy: open a GitHub issue on the project repo or
use the contact email listed in the Play Console once the app is live.
