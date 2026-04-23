# Handy for Android — Privacy Policy

_Last updated: 2026-04-23. Draft aligned to v1 behaviour; review and
finalise before the Play Console submission (Phase 4 deliverable)._

Handy is an on-screen AI assistant. This policy describes exactly what
Handy does with your data.

## What Handy does on your device

- Runs a **floating widget** that opens the chat screen and starts
  push-to-talk voice capture.
- When you ask a question, Handy can **read visible on-screen text**
  from the current app via the Android Accessibility service, and can
  **capture the active window** to give the AI model visual context.
- Handy can **point at a UI element** using the Accessibility service
  to highlight what you should tap.
- For well-defined requests ("set a 10-minute timer", "open Gmail",
  "search Google for …"), Handy can **dispatch a native Android
  Intent** on your behalf. Handy does not tap or type on your behalf in
  v1.

## What Handy does NOT do

- Handy never taps, scrolls, types, or otherwise performs gestures on
  your behalf in v1. The Accessibility service is used for
  **reading + pointing only**.
- Handy does not take screenshots, read text, or capture the screen
  while the floating widget is not the active subject — every capture
  is tied to a single turn of conversation initiated by you.
- Handy does not observe secured screens (banking apps, password
  managers, incognito tabs) — such surfaces return "I can't see this
  screen" and Handy never sends a blank frame to the AI model.
- Handy does not sell, rent, or share your data with advertisers.

## What leaves the device

- **Your messages, optional screenshots, and the compact accessibility
  tree** are sent to Anthropic (Claude) over HTTPS when you ask a
  question. Your API key is required — Handy does not proxy traffic
  through its own servers.
- Optional: when you explicitly enable "Web search" in Settings and
  provide a Brave Search API key, Handy sends search queries to Brave,
  page fetches via Jina, and GitHub repository searches via the public
  GitHub API — exactly the providers listed in Settings.

## What stays on the device

- Your API keys are stored in `EncryptedSharedPreferences` backed by
  the Android Keystore — they never leave the device except inside the
  `Authorization` / `x-api-key` header of the specific request they
  authorise.
- Chat history is stored as JSON files in the app's private storage
  and is **not** uploaded anywhere. Clear all history any time from
  Settings → "Clear all chat history".
- Audio from push-to-talk is sent directly to the on-device recognizer
  when one is available (Android 12+); on older devices it goes to
  Google's cloud recognizer via the `SpeechRecognizer` API, following
  your system-level speech settings.

## Permissions

| Permission | Used for |
|------------|----------|
| `RECORD_AUDIO` | Push-to-talk voice input. |
| `SYSTEM_ALERT_WINDOW` | Floating widget overlay. |
| Accessibility service | Reading visible text and pointing at a UI element. Turn off any time in Android Settings → Accessibility. |
| `POST_NOTIFICATIONS` | Foreground-service notification that keeps Handy alive. |
| `FOREGROUND_SERVICE_*` | The Android 14+ required types for our assistant and media-projection services. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Talking to the Claude API (and optional Brave / Jina / GitHub when web search is on). |

Handy does **not** request `QUERY_ALL_PACKAGES`. The list of
launchable apps is built from `CATEGORY_LAUNCHER` query results.

## Your controls

- **Turn off Accessibility at any time** in Android Settings →
  Accessibility → Handy. Handy continues to work in a reduced mode
  (typed chat + voice) without screen reading or pointing.
- **Clear chat history** in Settings at any time.
- **Disable web search** (default: off). When off, no search tools are
  sent to the AI model.
- **Delete API keys** in Settings — all four key slots are nullable.
- **Uninstall** the app; Handy keeps no server-side state.

## Contact

Questions about this policy: open a GitHub issue on the project repo
(or the email listed in the Play Console listing once live).
