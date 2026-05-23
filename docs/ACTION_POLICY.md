# Handy Action Policy

Lane: **A — general screen-aware AI copilot**.

This document is the typed action policy reference for future implementation prompts. It copies Section 10 of [`HANDY_NEXT_LEVEL_PLAN.md`](../HANDY_NEXT_LEVEL_PLAN.md) verbatim, then adds the per-recipe rules that deterministic recipes must obey.

Related docs: [`PLAY_POLICY_MATRIX.md`](PLAY_POLICY_MATRIX.md), [`SECURITY_MODEL.md`](SECURITY_MODEL.md), [`PRIVACY_MODEL.md`](PRIVACY_MODEL.md), and [`Handy_Android_Build_Plan_V2_Scope.md`](../Handy_Android_Build_Plan_V2_Scope.md).

## 10. Phase 4 — `ActionPolicyEngine` + Tap-for-me closed beta

**Goal:** open the gate, but every action passes through one typed
policy decision before any node tap or gesture fires.

### `ActionPolicyEngine` (new, `:core`)
```kotlin
enum class ActionRisk { LOW, MEDIUM, HIGH, CRITICAL }
enum class ConfirmationLevel { NONE, NORMAL, STRONG_HOLD, TYPED_CONFIRMATION }

data class PolicyDecision(
    val allowed: Boolean,
    val risk: ActionRisk,
    val confirmation: ConfirmationLevel,
    val requireFreshSnapshot: Boolean,
    val requireNodeActionOnly: Boolean,
    val allowGestureFallback: Boolean,
    val reason: String?,
)

interface ActionPolicyEngine {
    fun decide(
        action: AssistantAction,
        target: TapTarget?,
        grounding: GroundingSnapshot,
        sourceTrust: SourceTrust, // TRUSTED_USER, TRUSTED_RECIPE, UNTRUSTED_TOOL
    ): PolicyDecision
}
```

Rules (initial table):

| Situation | Decision |
|---|---|
| Banking / payments / password manager package | `allowed=false, reason="denylisted"` |
| Secure window (capture or windowFlag) | `allowed=false, reason="secure"` |
| Low resolver confidence (<0.7) | `allowed=false, reason="low-confidence"` |
| Duplicate target in grounding | `allowed=false, reason="ambiguous"` |
| Source = UNTRUSTED_TOOL | `allowed=false, reason="tool-suggestion-only"` |
| Message send / call / navigation start | `STRONG_HOLD` + `requireFreshSnapshot` |
| Payment / purchase / delete / submit personal data | `allowed=false` in beta; `TYPED_CONFIRMATION` later |
| OTP / password / card field | `allowed=false` (typing) and `allowed=false` (reading into prompt) |
| App changed after grounding | `allowed=false, reason="screen-changed"` |
| Normal visible button, gate open, ≥0.90 confidence | `NORMAL`, `requireNodeActionOnly` for unknown apps, gesture fallback only for learned-allowlist apps |

### Wiring
- `SwitchingActionPerformer` now delegates through
  `PolicyGuardedActionPerformer`, which calls the engine before
  every `tap` / `longPress` / `scroll` / `typeText` / dispatch.
- `BuddyFlightDriver.flyToAndTap` invokes the engine *before* the
  confirmation sheet; the sheet's UI mirrors the engine's
  `confirmation` level (none / normal / strong hold / typed).
- `HandyToolRunner` calls the engine for `dispatch_action` (so
  destructive intents also go through one decision path, not the
  ad-hoc `isDestructive` flag).

### Disclosure + consent flow (closes the gate)
- New `ActionDisclosureActivity` with prominent disclosure (named
  data, named actions, affirmative-action button), writes
  `actionDisclosureVersionAccepted = 1` only on consent.
- Sensitive actions require `STRONG_HOLD` (1.0s) or
  `TYPED_CONFIRMATION` (user types "send" / "delete").
- `accessibility_service_description`, `onboarding_disclosure_body`,
  `PLAYSTORE_SUBMISSION.md §4.3/§4.5/§5.1` updated.
- Settings → "Disable tap-for-me here", "Stop tap-for-me for 1 hour"
  (panic), per-app revoke.

### `canPerformGestures` + event mask (gated)
- Add `android:canPerformGestures="true"` to the a11y XML.
- Broaden `accessibilityEventTypes` to include
  `typeViewScrolled|typeViewTextChanged` **only when the action
  disclosure has been accepted**.

### Acceptance
- Fresh install → onboarding → accessibility → action disclosure →
  toggle visible.
- With the gate open, **no** gesture fires without
  (a) `PolicyDecision.allowed = true`, (b) `LiveScreenGuard` match,
  (c) confirmation sheet acceptance.
- Manual: open Instagram, ask "tap follow", swipe to a different app
  before responding → audit logs `screen-changed`, zero taps fire.
- Static denylist demo: opening a known banking app silently disables
  tap-for-me with audit row `denylisted`.

---

## Per-Recipe Rules

All recipes are deterministic, capped at six steps, and run through `ActionPolicyEngine.decide()` for every step. Max 6 steps so multi-screen recipes (WhatsApp open → search → type → open → type → send) fit without artificial fragmentation. The LLM may choose a recipe and arguments from user intent, but it cannot inject new executable steps. Every step must re-capture, re-validate, verify the result, and append an audit row.

| Recipe | Allowed scope | Default confirmation | Risk | Required policy rules | Hard vetoes | Verification and audit |
|---|---|---|---|---|---|---|
| Clock | Set alarm, set timer, open existing alarm screen | `NORMAL`; `STRONG_HOLD` if modifying or disabling an existing alarm | `MEDIUM` | Max 6 steps; require package/window match; use visible Clock UI or Android intent where available | Deleting alarms in beta; changing device time; repeated retries | Verify visible alarm/timer state or app event; audit recipe name, step index, target, and result |
| Android Settings | Open specific Settings screens; toggle only explicitly requested low-risk settings after confirmation | `NORMAL`; `STRONG_HOLD` for device-wide impact | `HIGH` | Prefer Settings intents; require fresh snapshot before any toggle; node action only for unknown OEM settings surfaces | Factory reset, accounts, developer options, payment, password, accessibility permission changes performed on Handy's own behalf | Verify package/window and changed UI state; audit setting category and confirmation |
| Gmail | Search mail, open compose draft, fill ordinary draft text after user approval | `NORMAL`; `STRONG_HOLD` before send/share | `HIGH` | Ordinary text entry only through `ACTION_SET_TEXT`; send requires fresh snapshot and hold | Delete, archive bulk mail, send to ambiguous recipient, attach files, read or type passwords/OTPs | Verify compose/search state or send event where available; audit recipient label only after redaction |
| WhatsApp | Open chat, search conversation, draft or send explicit reply through visible UI or `RemoteInput` | `STRONG_HOLD` for every send; `NORMAL` for open/search | `HIGH` | Require exact recipient/chat confirmation; require fresh snapshot immediately before send | Bulk/group broadcast, calls, payments, forwarding unknown content, auto-reply rules | Verify notification reply or visible sent state; audit chat/app metadata with redacted message preview |
| Chrome | Search web, open user-provided URL, summarize visible page, point at visible page controls | `NORMAL` for navigation; `NONE` for explanation | `MEDIUM` | Treat page text as `UNTRUSTED_TOOL`; fetched pages cannot trigger actions; prefer browser intents for URLs | Credential entry, checkout, payment, download/install prompts, tool-suggested actions | Verify URL/package where possible; audit navigation/search action and source trust |
| InstallApp | Open a Play Store app listing or app search only; user taps Install in Play Store | `NORMAL` | `MEDIUM` | Prefer `market://details` / `market://search` with HTTPS Play Store fallback; never auto-install or tap Install | Sideload/APK install, background install, any in-app purchase, paid checkout, payment prompts | Verify Play Store listing/search or chooser opened; audit package hint or search query only |
| Maps | Search place, preview route, start navigation only after exact confirmation | `NORMAL` for search; `STRONG_HOLD` for navigation start | `HIGH` | Require destination text in confirmation; require fresh snapshot for start navigation; prefer Android navigation intent | Ride booking, payment, location sharing, changing account settings | Verify Maps foreground and route/navigation state; audit destination string after redaction |
| Uber, Ola, Rapido ride-hailing | Open the selected ride app, focus destination, type destination, open the first matching result, and optionally select a stable cheapest-class card | `NORMAL` for setup steps; `STRONG_HOLD` for cheapest-pick; ride confirmation is `BLOCKED` by recipe design | `HIGH` | Max 6 steps; multi-matcher destination/result targets; no coordinate taps; gesture fallback excluded from learned allowlist; final chat says the user must tap Confirm in-app | Confirm, Request, Book, Choose, payment, saved payment method, address/payment edits | Verify app foreground, destination/result target, no confirm-class step, and final user-completion message |
| Meesho, Amazon, Flipkart | Search products, compare visible products, find coupons, summarize listings in English/Hindi/Hinglish | `NORMAL` for search and compare | `HIGH` | Shopping recipes are advisory; use search/fetch evidence as untrusted; keep actions visible and user-directed | Add-to-cart without confirmation, checkout, purchase, payment, address changes, saved-card use | Verify search query/listing state; audit recipe and blocked purchase/payment attempts |
| Notification reply recipe | Reply to one notification chosen by user | `STRONG_HOLD` | `HIGH` | Notification access must be opted in; show exact reply text; use platform `RemoteInput`; no ambient rules | Auto-reply, reply to stale key, reply to private/work-profile notification without safe visibility | Verify `PendingIntent.send` result or notification event; audit notification app, action, and redacted text preview |
| Clipboard transform recipe | Summarize, rewrite, translate, or explain current clip while Handy is visible or explicitly asked | `NONE` for transform; `NORMAL` for writeback | `HIGH` | Size cap 32 KB; dedupe by hash; detect password/OTP/card-like clips conservatively | Background clipboard harvesting, repeated processing, password/OTP/card transformation | Audit explicit request and writeback only; never store raw clip in audit |
| Generic open-app or system-intent recipe | Open app, app info, notification settings, accessibility settings, battery optimization settings, web search, share URL, calendar/reminder handoff | `NORMAL`; `STRONG_HOLD` for cross-app send/share/text/call/navigation | `MEDIUM` or `HIGH` | Prefer Android intents over UI tapping; show destination app/action; chooser is allowed when platform shows it | Silent cross-app send, hidden settings mutation, payment/purchase/delete | Audit intent name, target package when known, chooser/result state |

## Default Denylist Categories

The denylist is evaluated before recipe-specific rules:

| Category | Decision |
|---|---|
| Banking, payments, wallet, password manager, authenticator, card, or secure enterprise packages | `allowed=false, reason="denylisted"` |
| Secure window or secure capture result | `allowed=false, reason="secure"` |
| OTP, password, card, CVV, recovery code, private key, or seed phrase fields | `allowed=false` for reading and typing |
| Payment, purchase, delete, account closure, factory reset, or submit personal data | `allowed=false` in beta |
| Tool-result-initiated action without explicit user intent | `allowed=false, reason="tool-suggestion-only"` |
