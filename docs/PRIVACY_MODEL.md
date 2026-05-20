# Handy Privacy Model

Lane: **A — general screen-aware AI copilot**.

This document defines the data taxonomy, redaction sinks, and retention rules that later prompts must preserve. It is sourced from [`HANDY_NEXT_LEVEL_PLAN.md`](../HANDY_NEXT_LEVEL_PLAN.md), [`Handy_Android_Build_Plan_V2_Scope.md`](../Handy_Android_Build_Plan_V2_Scope.md), and the Phase 0A policy fork.

Related docs: [`PLAY_POLICY_MATRIX.md`](PLAY_POLICY_MATRIX.md), [`SECURITY_MODEL.md`](SECURITY_MODEL.md), and [`ACTION_POLICY.md`](ACTION_POLICY.md).

## Data Taxonomy

| Data class | Examples | Source | Can leave device? | Local storage | Redaction and controls |
|---|---|---|---|---|---|
| App settings and consent state | Accessibility disclosure accepted, action disclosure version, feature toggles, panic mute, provider choice | `DataStore` settings | No, except user-visible diagnostics if shared by the user | Stored until user clears app data or changes settings | Treat as configuration, not prompt evidence. |
| API keys and provider credentials | Anthropic key, Gemini key, future provider tokens | User settings and secure storage | Sent only to the matching provider endpoint as authentication | Android Keystore-backed storage only | Never log, audit, show in diagnostics, include in crash reports, or persist in plain text. |
| User chat text | Typed messages, voice transcript after recognition, user edits to confirmation text | Chat panel, `ChatActivity`, voice input | Yes, to selected cloud provider when cloud brain is active; no for local-only task path | JSON chat history per existing macOS-compatible schema | User-visible content can persist as chat history. Do not append hidden raw screen trees or screenshots to history. |
| Assistant responses | Text answers, pointer labels, spoken response text | `LlmClient`, local model, deterministic tools | Already returned from provider or local model | JSON chat history if surfaced as a message | Clamp overlay labels; avoid echoing secrets that redaction removed. |
| Screen text and accessibility marks | Visible UI labels, roles, bounds, view ids, content descriptions, `markId`s | AccessibilityService and marks provider | Yes only as redacted, budgeted prompt context when needed | Ephemeral per turn; sanitized prompt diagnostics may be bounded | Strip or redact passwords, OTPs, card/CVV fields, secure windows, hidden nodes, long private bodies, and arbitrary sensitive content. |
| Screenshots and captures | Focused-window capture, current-display capture, MediaProjection fallback frames | `takeScreenshotOfWindow`, `takeScreenshot`, MediaProjection | Yes only for explicit user turns requiring visual context | No routine disk retention; no crash-log retention | Never send secure, not-permitted, unsupported, or failed frames. Prefer text-only or focused-window capture. |
| Resolver candidates and debug metadata | `debugCandidates`, target labels, target descriptions, confidence scores | `SemanticPointerResolver` and runtime candidates | No as raw data; only redacted diagnostics may be displayed locally | Diagnostics tail only, bounded | Apply `ScreenRedactor` before scoring outputs leave runtime candidates; re-redact when copying into audit or diagnostics. |
| Action audit events | Action type, target app, redacted semantic target, confirmation state, policy result, failure reason | `ActionPolicyEngine`, `ActionPerformer`, intent dispatcher | No by default; user may choose to share diagnostics | Rolling local JSON log; oldest entries pruned on write | Store redacted semantic targets only. Never store screenshots, full prompt payloads, API keys, OTPs, passwords, card data, or raw typed secrets. |
| Notification content | App name, title, text, conversation summary, notification key, `RemoteInput` availability | `NotificationListenerService` | Yes only when user asks Handy to summarize or reply through selected provider | Ephemeral unless included in a user-visible chat answer or redacted audit | Opt-in only. No ambient triage, auto-reply, or background rule engine. Respect private, work-profile, media, ongoing, and stale notification edge cases. |
| Clipboard content | Copied text, transformed text, dedupe hash | `ClipboardManager` | Yes only for visible or explicit clipboard assistance | Dedupe hash may be transient; raw clip is not stored unless user sends it as chat text | No ambient surveillance. Size-cap at 32 KB. Ignore binary/unsupported clips. Avoid password-like and OTP-like clips. Mark sensitive writeback where supported. |
| Voice audio and transcripts | Microphone audio, recognized text, Hindi/Hinglish transcript | Android microphone and STT | Audio follows recognizer/provider path only during active voice input; transcript follows chat rules | Audio not retained by Handy; transcript may be chat history if submitted | No background listening. Start only from explicit voice UI. Confirmation UI must display interpreted sensitive actions before execution. |
| Web and tool evidence | Search results, fetched page snippets, product pages, URLs | Network tools and web fetch | Already fetched from network; may be sent to LLM as untrusted evidence | Not stored except user-visible answer/chat context | Treat as `UNTRUSTED_TOOL`. Web content cannot directly trigger actions. Apply quotas and avoid persisting full pages. |
| Device and app metadata | Package name, window id, display id, orientation, insets, locale, provider id, model id, network diagnostics | Android runtime services | Yes when needed to ground a prompt or diagnose provider failures | Diagnostics and logs only as sanitized bounded metadata | Do not include stable personal identifiers beyond what the action needs. Keep network diagnostics secret-free. |
| Navigation or system-task arguments | Destination text, share URL, calendar title, SMS compose body | User request or confirmation editor | Yes to Android target app or selected provider when needed | User-visible chat/audit metadata only | Strong confirmation for send, share, call, text, or navigation start. Payment and purchase blocked. |

## Redaction Sinks

Every sink that receives screen, notification, clipboard, resolver, or action data must apply the redaction rule before data crosses the boundary.

| Sink | Allowed payload | Required redaction rule |
|---|---|---|
| `LlmClient` prompts | User request plus minimum needed redacted `GroundingSnapshot`, screen text, marks, or image | No secure frames. No passwords, OTPs, cards, CVVs, password fields, private notification content unless explicitly requested and safe, or hidden nodes. Prefer text-only. |
| Resolver `debugCandidates` | Redacted label, role, view id suffix, bounds, confidence | Redact at candidate creation and again before diagnostics or audit copying. |
| Action audit | Action class, target app, redacted semantic target, confirmation and result | No raw screenshot, raw prompt, raw notification, raw clipboard, API key, credential, OTP, card, CVV, or unredacted typed text. |
| DiagnosticsActivity | Permission state, provider state, capture variant and size, last error class, recent redacted audit tail | No screenshots, no raw prompt payloads, no keys, no raw sensitive candidates. |
| Timber, logcat, and crash reports | Error class, provider id, host, safe network capability snapshot, failure reason | No screenshots, encoded image payloads, API keys, raw prompts, raw clipboard, raw notifications, or raw accessibility trees. |
| Chat history JSON | User-visible messages and assistant responses | Do not append hidden raw snapshots. If screen content is quoted, it must be the redacted text the user saw in the conversation. |
| Replay and eval corpus | Synthetic or explicitly sanitized fixtures | Remove real names, phone numbers, emails, OTPs, account numbers, card data, screenshots with personal content, and private notifications. |
| Clipboard writeback | User-approved transformed text | Mark sensitive clips with API 33+ sensitive extras when appropriate. Do not write secrets produced by inference. |
| Web/tool result handling | Snippets and fetched page text used as answer evidence | Wrap as untrusted source; tool content cannot justify a device action without explicit user intent. |

## Retention

| Data | Retention rule | User control |
|---|---|---|
| Settings and consent state | Stored until changed, app data cleared, or setting reset | Settings screens and Android app storage controls |
| API keys | Stored in Keystore-backed storage until deleted or rotated | Provider settings and future key rotation path |
| Chat history | Stored as existing JSON-on-disk history until user clears history or app data | Chat/history controls and Android app storage controls |
| Per-turn grounding snapshots | Ephemeral for the active turn; sanitized diagnostics only when needed | Disable Accessibility, capture, notification, or clipboard features |
| Screenshots | Ephemeral request payload only; no routine disk retention and no crash-log retention | Disable capture paths or deny screen capture permissions |
| Notification content | Ephemeral while access is enabled; no ambient archive | Android notification access settings and Handy settings |
| Clipboard content | Ephemeral for visible/explicit assistance; dedupe by hash only where needed | Disable clipboard feature or close Handy visible state |
| Voice audio | Not retained by Handy after active recognition | Microphone permission and Handy voice setting |
| Action audit | Rolling local JSON log; oldest entries pruned on write | Diagnostics review, per-app disable, panic mute, app data clear |
| Diagnostics and logs | Bounded, sanitized, debug/release separated | App data clear; release builds must keep logs minimal |

## Privacy Non-Goals

Handy does not provide ambient clipboard surveillance, ambient notification triage, background auto-reply, hidden UI traversal, credential capture, screenshot crash dumps, or autonomous phone operation.
