# Handy — Next-Level Plan
**Hardening + Pointer Excellence + Agent Transformation**

_Last updated: 2026-05-20. Companion to [`Handy V3.pdf`](Handy V3.pdf),
[`Handy_Android_Build_Plan_V2_Scope.md`](Handy_Android_Build_Plan_V2_Scope.md),
[`PLAYSTORE_SUBMISSION.md`](PLAYSTORE_SUBMISSION.md), and
[`DEBUG_LOG.md`](DEBUG_LOG.md)._

This document is intentionally exhaustive. It (a) audits what shipped in
the recent hardening pass, (b) names the residual risks in the current
code, and (c) lays out the next leap — pointer/flight excellence first,
then a bounded Android agent. Section 12 ("Codex prompts") is the
copy-paste section. Read sections 1–11 once before pasting anything.

> Build sequence: **context correctness → pointer correctness → flight
> polish → bounded tap-for-me → typed agent actions → app recipes →
> supervised multi-step agent**. Anything that skips ahead breaks
> trust faster than it adds capability.

---

## 1. What I actually verified in the repo

I read the code, not just the commit messages. Verified files and
contracts the plan depends on:

| Area | File | What it does today |
|---|---|---|
| Fail-closed gate | `core/src/main/kotlin/com/handy/core/action/ActionExecutionGate.kt` | Requires both `tapForMeEnabled` AND `actionDisclosureVersionAccepted >= 1`. |
| Per-turn grounding | `app/src/main/kotlin/com/handy/app/screen/ScreenContextBuilder.kt` | Builds a single `TurnScreenContext` with marks, screen text, capture, mode, accessibility state, failure reason. |
| Redaction | `core/src/main/kotlin/com/handy/core/privacy/ScreenRedactor.kt` | Field-level password redaction, Luhn-checked card redaction, context-gated OTP/CVV redaction. Applied to marks AND tree. |
| Stable mark IDs | `core/src/main/kotlin/com/handy/core/overlay/AccessibilityMarkIds.kt` + `PanelSnapshotScreenText.kt` | `m1, m2…` assigned per snapshot. Tree serializer prints them. |
| Pointer resolver v2 | `android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt` | Candidate ranking, confidence score, ambiguity / low-confidence reasons, recycles all owned nodes. Returns `markId`. |
| Flight driver | `app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt` | Explicit `Preparing → Flying → Pointing` states, blue navigation bubble, sticky 30 s safety timeout, safe-area landing using insets, refuses low/ambiguous targets. |
| Bezier controller | `app/src/main/kotlin/com/handy/app/widget/BezierFlightController.kt` | Distance-clamped duration, arc height, smoothstep, overshoot pulse, returnToDock OR sticky pulse, `cancelAll()`. |
| Pixel pointing | `core/src/main/kotlin/com/handy/core/parsing/AssistantMarkupParser.kt` | Pixel form still parsed for fixtures, but `OverlayChatPipeline` and `flyTo` both ignore it in normal mode. |
| Untrusted evidence | `android-runtime/src/main/kotlin/com/handy/runtime/llm/HandyToolRunner.kt` | Wraps web/GitHub/page output with an explicit "untrusted external evidence" preamble. |
| Brain router fix | `core/src/main/kotlin/com/handy/core/brain/BrainRouter.kt` | Cloud-required tasks now correctly route cloud-only. |
| Action seam | `app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt` + `SwitchingActionPerformer.kt` | Node-first / gesture-second, audited, gated by cached `gesturesEnabled` flag (no `runBlocking`). |
| Capture | `android-runtime/src/main/kotlin/com/handy/runtime/capture/ScreenCapturePipeline.kt` + `app/src/main/kotlin/com/handy/app/service/MediaProjectionCaptureService.kt` | Three-tier (API 34+, 30–33, MediaProjection). Black-frame → SecureWindow detection. **MediaProjectionCaptureSource is not wired** — see §3. |
| Tests | `core/src/test/kotlin/com/handy/core/action/ActionExecutionGateTest.kt`, `…/privacy/ScreenRedactorTest.kt`, `…/brain/BrainRouterTest.kt`, `…/capture/RequestBudgeterTest.kt`, `…/accessibility/SemanticPointerResolverTest.kt` | Phase 0–3 changes are test-covered at the pure-Kotlin layer. |

This is a much stronger base than most overlay-style assistants. The
remaining work is shifting from "almost shippable" to "production-grade
+ first real agent capability."

---

## 2. Bottom-line product position (don't drift from this)

Handy is a **screen-aware Android copilot**, not a hidden background bot.
The trust contract has six lines and every feature must respect them:

1. I show what I see.
2. I show what I will do.
3. I never act on low confidence.
4. I never hide automation.
5. I never send / pay / delete / purchase without explicit confirmation.
6. I always allow stop / cancel, and I keep an audit trail you can read.

This is also a Play-policy requirement, not a nice-to-have. The 2026
Play accessibility-service policy is tightening; general-purpose
assistants are explicitly **not** eligible for `isAccessibilityTool`
exemption and must meet the prominent-disclosure + affirmative-consent
bar before any tap-for-me ships. ([Play Console Help → Use of the
AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en),
[Best practices for prominent disclosure and consent](https://support.google.com/googleplay/android-developer/answer/11150561))

---

## 3. Residual risks in the current code (in priority order)

These are concrete, file-specific failure modes I found that the
hardening pass did not yet close. Each one becomes a prompt in §12.

### 3.1 `canPerformGestures="true"` is missing from accessibility config
**File:** `app/src/main/res/xml/accessibility_service_config.xml`.
**Symptom:** `AccessibilityService.dispatchGesture(...)` silently
returns false on some OEMs; node-first taps work, gesture-fallback taps
don't. ([Android Developers → AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#canPerformGestures))
**Why this matters:** the moment we enable tap-for-me, ~30% of touch
targets (Compose surfaces without `clickable`, custom drawing) fall
back to `dispatchGesture` and silently do nothing.
**Fix:** add the attribute; gate behind a feature flag so it can be
turned off in a dev build if a Play reviewer flags it.

### 3.2 `actionDisclosureVersionAccepted` has no writer
**Symptom:** the gate in `ActionExecutionGate` will never open in
production. There is no UI / onboarding path that sets it to `1`.
**Why this matters:** until a disclosure screen and a writer exist,
shipping the gesture performer is purely theoretical.
**Fix:** dedicated disclosure activity + DataStore writer + Play copy.
See §6 Phase H4.

### 3.3 Full-chat from cold start has no mark IDs
`AccessibilityTreeReader.read()` produces `UiNode` trees but does
**not** assign `markId`. The serializer prints mark IDs only when
present; `ScreenContextBuilder` calls `.withoutMarkIds()` when there is
no `effectivePanel`. So the LLM, in full chat opened without a panel
handoff, gets `<screen_ui>` text without `m1, m2…` and must fall back
to text / viewId / desc matching.
**Fix:** synthesize stable mark IDs in `AccessibilityTreeReader` (BFS
ordered, same `m1, m2…` convention) AND give the resolver access to
the same marks-by-id lookup for full-chat turns. See §5 Phase P2.

### 3.4 No re-resolve before action
`AccessibilityGestureActionPerformer.perform()` calls the resolver
once. Between the LLM's emission of `[POINT:markId=m3]` and the
gesture fire, the screen can scroll, a modal can appear, an ad can
slide in, or the foreground app can change. There is also no check
that the foreground package today equals the package the LLM was
grounded on.
**Fix:** before any node action, (a) re-fetch `rootInActiveWindow`,
(b) compare `windowId` + `packageName` to the grounding snapshot,
(c) re-run the resolver, (d) abort with "screen changed — try again"
if anything diverges.

### 3.5 No pre-action confirmation UI for tap-for-me
`BuddyFlightDriver.flyToAndTap` happily taps after a 250 ms grace
delay if the gate is open. There is no "About to tap _Continue_ — tap
for me / cancel" sheet, no haptic prompt, no audit checkpoint.
**Fix:** insert a `TapForMeConfirmationSheet` (overlay or panel chip)
with two buttons; sensitive actions (send / pay / delete) require
stronger friction with a typed or held confirmation. See §6 Phase H4.

### 3.6 Resolver may match Handy's own overlay
The candidate collector walks `rootInActiveWindow`, which on devices
that include overlay windows in the active root can include Handy's
own widget nodes. There is no `samePackage` filter.
**Fix:** in `SemanticPointerResolver.collectLiveCandidates`, skip
nodes whose root's package equals `BuildConfig.APPLICATION_ID`.
Penalise other system-overlay packages.

### 3.7 No `ACTION_SET_TEXT` (no typing)
The performer only does taps, long-presses, and scroll. The agent
cannot type. This blocks every "send a WhatsApp", "search for X",
"fill the OTP" intent the user will reasonably ask for.
**Fix:** add a `TypeText(target, text)` action with
`AccessibilityNodeInfo.ACTION_SET_TEXT` + `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`.
Surface a redaction-aware confirmation. See §7 Phase A1.

### 3.8 MediaProjection fallback is declared but not wired
`ScreenCapturePipeline.takeByMediaProjection` requires a
`MediaProjectionCaptureSource`, but `AppRuntimeBindings.provideScreenCapturePipeline`
constructs the pipeline with `mediaProjectionSource = null` (default).
On API 26–29 every capture returns `CaptureResult.Unsupported`.
**Fix:** either wire a real source (with the existing
`MediaProjectionCaptureService`) or change the prompt and UX so the
app honestly reports "screenshot unsupported on this OS" instead of
silently dropping context. See §4 Phase H1.

### 3.9 ScreenContextBuilder runs capture on `Dispatchers.Main.immediate`
Every panel/full-chat turn jumps to main to call `marksProvider`,
`treeReader`, and the screenshot APIs. Marks and tree must walk on
main (accessibility nodes are not thread-safe), but the screenshot
callback path doesn't have to. There is no per-step timing budget.
**Fix:** keep marks/tree on main, but (a) add a 200 ms hard ceiling
per step, (b) move capture to a coroutine and let it return
`CaptureResult.Failed("budget exceeded")` if the user is mid-typing.
Long term, run marks on a dedicated `LooperScope`.

### 3.10 Accessibility events filter is too narrow for an agent
`accessibility_service_config.xml` listens for
`typeWindowStateChanged|typeWindowContentChanged|typeViewFocused|typeViewClicked`.
Multi-step actions need `typeViewScrolled` (to know a scroll
completed) and `typeViewTextChanged` (to verify text-set actions).
Without these the agent cannot tell whether its action worked.
**Fix:** broaden the event mask once we add multi-step verification.

### 3.11 Disclosure copy still says "v1 never taps"
`accessibility_service_description` string + onboarding body + Play
submission §4.3 + §4.5 all say "Handy never taps, types, or scrolls
on your behalf." Shipping the gesture performer in user-visible form
without updating these is a Play-policy violation **and** breaks the
trust contract in §2.
**Fix:** new copy in Phase H4 + Play submission rev.

### 3.12 No instrumentation / replay harness for the resolver
`SemanticPointerResolverTest` covers algorithmic edges but there is
no captured-screen replay. A regression in node ranking would not
surface until a manual run. Cursor-style "tap reliability" numbers
(target: ≥ 95% on real apps) are unmeasurable today.
**Fix:** add a JSON snapshot format (`PanelSnapshot` + a fake live
tree) + a JUnit replay runner. Backfill with 20 real screens.

### 3.13 Buddy flight has no cancellation on system events
`HandyAccessibilityService.maybeDismissStickyPointer` only acts on
`TYPE_VIEW_CLICKED` and `TYPE_TOUCH_INTERACTION_START`. Window-state
change, orientation change, IME open, accessibility-service
disconnect — none cancel an in-flight `flyToBounds`.
**Fix:** route these signals into `BuddyFlightDriver.cancel()`.

### 3.14 No allowlist / denylist for sensitive apps
Banking, payments, password managers should never receive a synthetic
tap. There is no denylist. Compose-only games may also crash if
ACTION_CLICK fires twice. We need a curated denylist + a learned
allowlist (apps where node-first taps have succeeded N times).
**Fix:** simple package denylist in core, opt-in allowlist.

### 3.15 Audit log has no UI to review or revoke
Events go to `FileAuditStore`. `DiagnosticsActivity` shows them but
gives no way to flag a wrong tap, no per-app revoke, no "stop all
tap-for-me for the next hour" panic switch.
**Fix:** richer audit UI in Phase T1.

---

## 4. Phase H: Production hardening (close §3 risks before any new feature)

Goal: get from "Phase 0–3 shipped" to "I would let my own mom run this
on her main phone." No new user-visible capabilities; only confidence,
safety, and Play-readiness.

### H1 — Honest capture / no-capture story (closes §3.8, §3.9)
- Wire `MediaProjectionCaptureSource` (or remove the API 26–29 fall
  through and surface `CaptureResult.Unsupported` to the user).
- Hard 200 ms budget per capture step in `ScreenContextBuilder`.
- New UX strings for the four honest outcomes the v3 plan calls out:
  `TextOnlyContext`, `VisionOnlyContext`, `TextAndVisionContext`,
  `NoContextButCanChat`, `SecureScreenNoContext`,
  `PermissionRequiredNoContext`.
- Acceptance: no request silently claims screen context when none
  exists; secure screens produce the safe message; full-chat cold-
  start reports the real state.

### H2 — Same-package + window guard (closes §3.4, §3.6)
- `SemanticPointerResolver.resolve` accepts an optional
  `expectedPackageName` and refuses candidates outside it (penalty,
  not hard ban — overlays sometimes legitimately match).
- Skip Handy's own package in candidate collection.
- New helper `LiveScreenGuard` in `:android-runtime`: returns the
  current `(packageName, windowId)`; the action performer calls it
  immediately before any `node.performAction` or `dispatchGesture`.
- Acceptance: a manual test (open Instagram, ask Handy "tap follow",
  then swipe to another app before the LLM responds) yields a clear
  "screen changed" message and zero stray taps.

### H3 — Cancellation on system signals (closes §3.13)
- `HandyAccessibilityService.onAccessibilityEvent` adds
  `TYPE_WINDOW_CONTENT_CHANGED` (existing) and `TYPE_VIEW_SCROLLED`
  routing into `BuddyFlightDriver.cancelIfStaleTarget()`.
- `Configuration.orientation` change in `OverlayPresenter` cancels
  flight + resets pointer.
- IME visibility changes (the existing widget service watches insets)
  cancel flight.
- Acceptance: rotate device mid-flight → buddy returns to dock with no
  log warnings; turn off Handy accessibility service mid-flight → no
  crash, audit row records "service disconnected".

### H4 — Disclosure + opt-in writer for tap-for-me (closes §3.2, §3.5, §3.11)
- New `ActionDisclosureActivity` with two prominent toggles (read +
  tap), explicit consent button, copy reviewed against Play §4
  prominent-disclosure rules.
- DataStore writer sets `actionDisclosureVersionAccepted = 1` only on
  affirmative tap; flag persists, but Settings shows a one-tap
  revoke.
- `TapForMeConfirmationSheet` composable rendered inside the existing
  overlay panel: shows the planned action + target snippet + cancel.
- Sensitive actions (send / pay / delete / call) require typed-word
  confirmation ("send" / "delete") OR a hold-to-confirm gesture.
- Update `accessibility_service_description` string,
  `onboarding_disclosure_body` string,
  `PLAYSTORE_SUBMISSION.md` §4.3 / §4.5 / §5.1 to match.
- Acceptance: fresh install → onboarding → accessibility consent →
  action disclosure → tap-for-me toggle visible → real gesture fires
  only after sheet confirmation; revoke clears the gate.

### H5 — `canPerformGestures` + service config sweep (closes §3.1, §3.10)
- Add `android:canPerformGestures="true"` to
  `accessibility_service_config.xml`.
- Broaden event mask to include `typeViewScrolled|typeViewTextChanged`
  (gated on the action disclosure to keep idle CPU low).
- Confirm `notificationTimeout` (currently 100 ms) is appropriate for
  agent flows; raise to 250 ms if synthetic-tap → content-changed
  latency is high on real devices.
- Acceptance: gesture fallback now actually fires on Compose surfaces
  in a curated test app.

### H6 — Resolver / capture instrumentation (closes §3.12)
- Snapshot serializer in `:core`: persist `(PanelSnapshot, fake
  AccessibilityNodeInfo tree)` to JSON in a dev-only flag path.
- JUnit `SemanticPointerResolverReplayTest` that reads each snapshot
  and asserts `(spec → expectedMarkId)`.
- Backfill 20 real screens: Settings, Gmail, WhatsApp, Maps, Amazon,
  Meesho, Flipkart, Chrome, YouTube, Instagram, Photos, Files, Notes,
  Calendar, Phone, Messages, Drive, Spotify, Uber, Truecaller.
- Macrobenchmark target for overlay frame jank during flight.
- Acceptance: replay suite green; dashboard prints "% resolved at ≥
  0.9 confidence" per app pack.

### H7 — Sensitive-app denylist + learned allowlist (closes §3.14)
- `:core` `ActionAppPolicy` with a static denylist (initial set:
  major Indian + global banking apps, password managers, UPI apps,
  Wallet, GPay, PhonePe, PayTM, BHIM).
- `:android-runtime` `LearnedAllowlist` (DataStore-backed) that
  tracks per-package successful node-first taps; the
  `SwitchingActionPerformer` widens its gate as a package crosses a
  threshold.
- Acceptance: opening a denylisted app silently disables tap-for-me;
  the audit shows "skipped — denylisted".

### H8 — Audit UX + per-app revoke (closes §3.15)
- New `AuditReviewActivity` (or sub-section of
  `DiagnosticsActivity`): grouped by day + app, each row has
  "Report wrong tap" + "Disable tap-for-me here".
- Panic switch in settings: "Stop all tap-for-me for the next hour".
- Acceptance: a user can find every gesture Handy fired in the past
  week and revoke per-app in two taps.

> When H1–H8 are green you can ship a closed beta of tap-for-me with
> a real Play-policy story. Until then, the gate must stay closed.

---

## 5. Phase P: Pointer + Flight excellence (the magic Handy is known for)

This is the most important user-visible bet. The order is intentional —
P1 reuses what's already there; P2/P3 fix the cold-start gap; P4 makes
the flight feel inevitable.

### P1 — Confidence-tiered behaviour
`SemanticPointerResolver` already emits confidence. Wire it through
to UX:
- `≥ 0.90`: fly + optionally `flyToAndTap` (still gate-respecting).
- `0.70–0.90`: fly only; suppress tap.
- `0.40–0.70`: don't fly. Speak "I can see _Continue_ in the popup or
  at the bottom — which one?" and show a candidate chip row in the
  panel.
- `< 0.40`: don't fly. Say "I can't pin that one — open the panel and
  point me at it."
- Add a settings toggle: "Confirm before pointing" (default off).

### P2 — Mark IDs in the full tree (closes §3.3)
- `AccessibilityTreeReader.walk` assigns `markId = "m{counter}"` in
  BFS order for any node `shouldEmit` would print.
- Update `ScreenContextBuilder` to **not** strip mark IDs in
  full-chat / no-panel mode (the resolver now also walks the live
  tree).
- Resolver gains a "tree fallback" mode: when given marks from a
  full-tree snapshot, use them as candidate hints alongside live
  nodes.
- Acceptance: the prompt `<screen_ui>` block always contains `m1, m2…`
  whether opened via overlay or full chat.

### P3 — Resolver upgrades for real-world UIs
- Penalise non-foreground packages (uses `expectedPackageName` from
  H2).
- Penalise overlap with system bars (status / nav) — if a candidate's
  bounds intersect the inset, it's almost certainly wrong.
- Bonus for `node.text == userSpokenText.substring`.
- Detect duplicate labels in different windows ("Save" in dialog vs
  app) and treat the dialog instance as higher score when a dialog is
  topmost.
- Hidden-by-keyboard heuristic: if the IME is shown and a candidate
  is below the keyboard top, drop confidence to 0.
- Acceptance: replay suite hits ≥ 95% on the 20-app pack.

### P4 — Flight state machine + geometry polish
- Codify the FSM the v3 plan calls for:
  `Docked → Listening → Thinking → Answering → PreparingPoint → Flying → Pointing → (OptionalActionConfirm → Acting → ActionResult) → Returning → Docked`.
- Replace today's ad-hoc booleans in `OverlayPanelState` with this
  enum.
- `chooseLandingPosition` already prefers safe-area bands; add:
  - rotate target rect by 90°/180°/270° hints from `Configuration`,
  - shrink-to-fit when the target rect itself overlaps insets,
  - "always-visible chevron" rule: when buddy + target both fit, draw
    a one-frame chevron that points from buddy to target's true
    center even if the buddy must be offset.
- Add subtle arrival haptic (off by default; setting respects "system
  haptics off").
- Acceptance: macrobenchmark < 5% jank on Pixel 6+ during flight;
  zero "buddy under nav bar" reports.

### P5 — Speak-and-correct
Voice loop: while a sticky pointer is up, listening for "no, the other
one" or "the one in the popup" routes to the next candidate without a
fresh LLM round trip.
- Acceptance: user can say "no, the other Continue" and the buddy
  hops to the runner-up candidate from `debugCandidates`.

---

## 6. Phase A: Bounded Android agent (do small things, well)

Only start this after H1–H8 and P1–P3 are green.

### A1 — Typing capability (`ACTION_SET_TEXT`)
- New `TapTarget` extension `TapTarget.AtNodeWithText(text: String, …)`
  is not the right shape; instead add a new action
  `ActionPerformer.typeText(target, text)`.
- Implementation: re-resolve, verify `target.isEditable`,
  `Bundle().apply { putCharSequence(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }`,
  `node.performAction(ACTION_SET_TEXT, args)`. Fall back to focus +
  IME insert via `commitText` if `ACTION_SET_TEXT` returns false
  ([Android Developers → AccessibilityNodeInfo.AccessibilityAction](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction)).
- Redaction: never type text that the redactor would scrub.
- LLM prompt addendum: a `type_text` capability hint with a strict
  schema (`{markId, text}`) and example.
- Confirmation: the sheet shows the text being typed, truncated; the
  user can edit before tapping "Type for me".

### A2 — Verified actions
After every action, observe accessibility events for a 1500 ms window
to confirm the expected state transition:
- Tap → `TYPE_VIEW_CLICKED` from the same node, OR a
  `TYPE_WINDOW_CONTENT_CHANGED` whose `getSource()` overlaps target
  bounds.
- Scroll → `TYPE_VIEW_SCROLLED`.
- Type → `TYPE_VIEW_TEXT_CHANGED` from the same node with the new
  text matching.
- Failure → audit row `AuditResult.Failed("no-confirm")`, surface
  "did that work? I didn't see the screen change."

### A3 — Bounded "Do with me" stepper (max 5 steps)
- Pure-Kotlin planner in `:core`: `AgentPlan(goal, steps)` with each
  step being `(action, targetSpec, expectation, recoverHint)`.
- Run loop:
  1. Show plan in panel (collapsed).
  2. Run step 1. Recapture. Verify (A2).
  3. If success, advance; if failure, abort or ask.
- Hard rules: max 5 steps, no plan that includes
  send/pay/delete/post without explicit per-step confirmation, must
  re-confirm if the foreground app changes between steps.

### A4 — System task layer (intent-first)
Most agent value comes from `dispatch_action` intents (which Handy
already has), not synthetic taps. Expand and improve:

| Task | Mechanism (preferred) | Mechanism (fallback) |
|---|---|---|
| Set alarm / timer | `AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` | Tap recipe in Clock app |
| Compose email | `ACTION_SENDTO` `mailto:` | Gmail recipe (focus body, type) |
| Compose SMS | `ACTION_SENDTO` `smsto:` | Messages recipe |
| Compose WhatsApp | `wa.me/<num>?text=…` | WhatsApp recipe |
| Create note | Keep `ACTION_SEND` text/plain | Notes recipe (limited) |
| Calendar event | `ContentResolver` insert via `CalendarContract` — opens UI | (none — UI flow) |
| Maps search / navigate | `geo:` / `google.navigation:` | Maps recipe |
| Web search | `ACTION_WEB_SEARCH` | Browser open URL |
| Reply to notification | `RemoteInput` from listener (opt-in) | None |

Add `RemoteInputReply` flow for direct-reply notifications using the
existing `HandyNotificationListenerService` and Android's
`RemoteInput.getResultsFromIntent` ([Receiving Voice Input from a
Notification](https://developer.android.com/training/wearables/notifications/voice-input)).

### A5 — App recipe packs
Per `Handy_Android_Build_Plan_V2_Scope.md`, the recipe contract is a
simple `AppRecipe` interface. Ship recipes for the high-value bounded
flows:
- Android Settings (find setting, toggle on/off).
- Clock (alarm/timer).
- Gmail (draft / search).
- WhatsApp (open chat, draft).
- Chrome (open URL, search, summarize visible page via fetch_page).
- Maps (search, start nav).
- Calendar (create event).
- Photos (share selected, search).
- Files (open, share).
- Meesho / Amazon / Flipkart (search + summarize + compare via
  fetch_page on product URL).

Each recipe declares: `recognizes(snapshot)`,
`proposeActions(userGoal, snapshot)`,
`validateBeforeAction(action, liveSnapshot)`, `execute(action)`.

### A6 — Offline-friendly local mini-brain for "what is this screen?"
For privacy-preferring users, the simple "describe this screen" path
should be answerable by `LocalGenAiClient` (Gemini Nano) when the
device supports it. `BrainRouter` already has the seam.

---

## 7. Phase V: Voice-first + vernacular (VANI lessons)

Meesho's VAANI hit 1.5M users in a month with 22% conversion lift on
Hindi-/English-conversational shopping. ([Storyboard18 → Meesho
unveils Vaani](https://www.storyboard18.com/brand-marketing/meesho-unveils-vaani-ai-voice-assistant-to-transform-india-shopping-ws-l-93095.htm),
[YourStory](https://yourstory.com/2026/03/meesho-launches-ai-shopping-assistant-vaani),
[Rozana Spokesman → 22% increase](https://www.rozanaspokesman.com/lifestyle/tech/250326/meesho-launches-voice-shopping-feature-vaani-reports-22-increa.html))

For Handy:

### V1 — Hindi + Hinglish overlay/voice loop
- Tag `VoiceController` with `Locale("hi-IN")` when system or app
  language is Hindi; fall back to English.
- Prompt catalogue gets a Hindi mirror (only the spoken parts; the
  internal control tags stay English).
- TTS provider matrix: prefer system Hindi voice on-device; let users
  upgrade to Sarvam Bulbul for premium voice (already in
  `TtsProvider`).
- Acceptance: "is product return karna hai" routes correctly on a
  Meesho product page and the spoken response is Hindi.

### V2 — "Shop with me" mode (VANI-flavoured)
- Domain prompt pack triggered when foreground package matches a
  shopping app. Quick prompts: "Find cheaper", "Is this returnable?",
  "Compare with X", "Show coupon", "Track order", "Help me return".
- Use `fetch_page` to read the product page when the LLM needs more
  than the visible UI.
- Acceptance: visible quality lift on Meesho/Amazon/Flipkart product
  pages.

---

## 8. Phase T: Trust + policy hardening (must ship with H4)

### T1 — Play submission rev
- Update `PLAYSTORE_SUBMISSION.md` §4.3 / §4.5 / §5.1 / §5.4 with the
  tap-for-me copy and disclosure flow.
- Add a video walkthrough of the disclosure flow + tap-for-me
  confirmation (Play reviewers ask for this).
- New permission-declaration entry for `BIND_NOTIFICATION_LISTENER_SERVICE`
  (already declared, but the policy answer needs to be sharper for
  the agent use case).

### T2 — Per-feature transparency surface
A single "What Handy can do today" screen in Settings, generated from
real feature flags (not hardcoded), with a "stop the world" panic
button.

### T3 — Local data retention
- All audit JSON: 30-day rolling, user can wipe immediately.
- Snapshot replay corpus (dev builds only): isolated dir, never
  shipped in release.

---

## 9. Competitor + reference cross-walk

| Source | Idea | Handy adoption |
|---|---|---|
| [tiptour-macos (milind-soni)](https://github.com/milind-soni/tiptour-macos) | "Autopilot" vs "Tour Guide" modes | Maps to `Tap-for-me` vs `Guide`. Add `Do with me` for stepped agent. |
| tiptour | Focus highlight ("act on this area") | Add a "long-press to highlight a region" gesture in the overlay; resolver scopes candidates to the rect. |
| [Clicky (Farza Majeed)](https://isaacflath.com/writing/how-clicky-works) | Glowing triangle flies and explains | Already there. Steal the *emotional* polish: idle micro-animations, tiny eye blinks, settle-into-corner physics. |
| Clicky | No hardware control, only points | This is the trust default; tap-for-me stays off until user opts in. |
| [Meesho VAANI](https://yourstory.com/2026/03/meesho-launches-ai-shopping-assistant-vaani) | Hindi voice, conversational shopping | Phase V. |
| VAANI | Bounded domain (shopping) | Phase A5 app recipes — start with shopping packs. |
| [browser-use/macOS-use](https://github.com/browser-use/macOS-use) | "Make apps accessible for AI agents" | Same model: marks-first, gestures-second, observable result. Validates the resolver-v2 + verify-after-action design. |

---

## 10. Metrics to track from day one

| Metric | Target | How to measure |
|---|---|---|
| Pointer correct (real apps) | ≥ 95% | Replay corpus + weekly internal eval |
| Pointer correct (curated) | ≥ 99% | Replay corpus |
| Confidence-≥0.9 share of pointed turns | ≥ 80% | Audit log aggregation |
| Tap-for-me success (foreground unchanged) | ≥ 98% | Audit `Dispatched` / `(Dispatched+Failed+Cancelled)` |
| Tap-for-me on wrong app | 0 | LiveScreenGuard rejects |
| Tap on secure screen | 0 | CaptureResult guard |
| Overlay frame jank (Pixel 6) | < 5% | Macrobenchmark |
| Crash-free sessions | > 99.5% | Crashlytics-free; use Timber → file → opt-in upload |
| Cold-start to widget tap response | < 250 ms | Existing target |
| Cold-start to first stream chunk | < 2 s on 4G | Manual run grid |

---

## 11. Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Play removes app for AccessibilityService misuse | Med-High | H4 disclosure + T1 submission rev + audit transparency + denylist |
| Tap on banking screen (catastrophic) | Low (with H7) | Static denylist + capture-secure → no taps + foreground guard |
| OEM accessibility variance breaks `dispatchGesture` | Med | H5 `canPerformGestures` + device matrix in P3 acceptance + Maestro-driven QA |
| Compose semantics deny mark IDs | Med | Add `Modifier.testTag(...)` fallback only in our own UI; for third-party apps, accept lower coverage with explicit fallback prompts |
| Hindi STT quality on cheap phones | Med | Prefer on-device Android recognizer + offer Sarvam toggle |
| Multi-step plan goes off-rails | Med | Hard 5-step cap, re-confirm on app change, audit per step |
| Overlay sustained-memory growth | Low | Cancellable jobs, node recycling already there, add macrobench |

---

## 12. Codex prompts — phased, copy-pasteable

These prompts assume the agent (Codex) opens the repo at root,
follows `.cursor/rules/10-handy-project-guardrails.mdc`, and updates
`DEBUG_LOG.md` after each prompt with a `DL-###` entry. **Run them in
order**, run unit tests + a real-device smoke check between each, and
commit after every prompt.

> Convention used in every prompt:
> - `Goal:` what to build.
> - `Files to touch:` exact paths.
> - `Acceptance:` what must be true / what tests must pass.
> - `Do NOT:` guardrails.
> - `Tests:` what to run.

### Prompt H1 — Wire MediaProjection + per-step capture budget

```
You are working on Handy Android (multi-module Gradle: :core,
:android-runtime, :app).

Goal:
1) Wire the MediaProjection screen-capture path that the manifest and
   ScreenCapturePipeline already declare. Today
   AppRuntimeBindings.provideScreenCapturePipeline constructs the
   pipeline with mediaProjectionSource = null, so on API 26-29 every
   capture returns CaptureResult.Unsupported.
2) Add a 200ms hard budget per capture step in ScreenContextBuilder
   so a slow or hung screenshot never blocks a user turn.

Files to touch:
- app/src/main/kotlin/com/handy/app/service/MediaProjectionCaptureService.kt
- (new) android-runtime/src/main/kotlin/com/handy/runtime/capture/MediaProjectionCaptureSourceImpl.kt
- app/src/main/kotlin/com/handy/app/di/AppRuntimeBindings.kt
- app/src/main/kotlin/com/handy/app/screen/ScreenContextBuilder.kt
- core/src/main/kotlin/com/handy/core/screen/CaptureResult.kt (only
  if you need to add a new sealed subtype like
  CaptureResult.Failed("budget exceeded"))

Implementation notes:
- The MediaProjectionCaptureSource implementation should:
  * Start MediaProjectionCaptureService with the user's projection
    consent (a one-time prompt; cache the consent in DataStore).
  * Use ImageReader + VirtualDisplay; close everything in release().
  * Respect MediaProjection.Callback onStop().
- ScreenContextBuilder: wrap each of the 3 capture/tree steps in
  withTimeoutOrNull(200L); on timeout, log Timber.w and return null
  / Failed instead of cancelling the whole build.

Acceptance:
- On an API 28 emulator, capture() returns CaptureResult.Image (or
  SecureWindow when behind a secure surface), never Unsupported.
- A pre-baked slow ScreenCapturePipeline test fake returns
  Failed("budget exceeded") after 200ms; no test takes > 250ms.
- ConversationOrchestrator still gracefully degrades when capture is
  null (no behaviour change in the LLM path).
- Add unit tests:
  * MediaProjectionCaptureSourceImplTest (release idempotency,
    onStop cleanup).
  * ScreenContextBuilderBudgetTest with a fake slow pipeline.

Do NOT:
- Change the `:core` capture contract enums except to add a new
  Failed subtype if absolutely required.
- Touch redaction.
- Send any MediaProjection bitmap to the model without going through
  ScreenCapturePipeline.bitmapToResult (so black-frame / secure
  detection still runs).

Tests:
- ./gradlew :core:test :android-runtime:test
- (Manual) install on an API 28 device, ask "what's on screen" in a
  text-only app, confirm capture works.

Update DEBUG_LOG.md with a new DL entry summarising the change and
attach a screenshot of the MediaProjection consent dialog.
```

### Prompt H2 — Same-package + window guard before any action

```
Goal:
1) Make the pointer resolver and gesture performer refuse to act on a
   stale or different window than the one the LLM was grounded on.
2) Skip Handy's own overlay nodes when collecting candidates.

Files to touch:
- android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt
- (new) android-runtime/src/main/kotlin/com/handy/runtime/accessibility/LiveScreenGuard.kt
- app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
- app/src/main/kotlin/com/handy/app/di/AppRuntimeBindings.kt
- core/src/main/kotlin/com/handy/core/screen/TurnScreenContext.kt
  (only to add an `expectedPackage: String?` and `expectedWindowId: Int?`)

Implementation notes:
- SemanticPointerResolver.resolve(spec, fallbackMarks,
  expectedPackage: String? = null, expectedWindowId: Int? = null).
  When set, apply a -80 score penalty (not hard ban) for candidates
  whose root package or windowId doesn't match.
- collectLiveCandidates: skip nodes whose root.packageName ==
  BuildConfig.APPLICATION_ID (use a runtime constant; avoid pulling
  :app code into :android-runtime — pass the package in via DI).
- LiveScreenGuard.snapshot(): returns
  data class LiveScreen(packageName: String?, windowId: Int?,
  rootBoundsHash: Int) by reading rootInActiveWindow on the main
  thread.
- AccessibilityGestureActionPerformer.perform(): right before the
  node action, call LiveScreenGuard.snapshot() and compare to the
  caller-supplied expected snapshot (BuddyFlightDriver should pass
  what ScreenContextBuilder recorded). On mismatch, audit
  AuditResult.Failed("screen-changed") and return
  PerformResult.Failed("screen changed since grounding").
- BuddyFlightDriver.flyTo / flyToAndTap thread an
  expectedPackage/windowId from the original snapshot through
  resolve() and the action call.

Acceptance:
- New unit test asserting that a candidate from a different package
  scores lower than one in the expected package, all else equal.
- Manual test: open Settings, ask Handy "tap Wi-Fi", swipe to
  another app before the response arrives — buddy returns to dock
  with an error toast, no spurious tap, audit row "screen-changed".
- Resolver no longer matches Handy's own overlay nodes.

Do NOT:
- Hard-ban cross-window matches; some apps legitimately switch
  windowId on small UI changes. Penalty > 0 is enough.
- Add Android imports to :core.

Tests:
- ./gradlew :core:test :android-runtime:test :app:test
- Add an instrumented test in app/src/androidTest that uses the new
  LiveScreenGuard against a controllable test activity.

Update DEBUG_LOG.md.
```

### Prompt H3 — Cancellation on window / orientation / IME / a11y disconnect

```
Goal: any system signal that invalidates a flight target must cancel
the flight cleanly.

Files to touch:
- app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
- app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt
  (only if it owns the configuration / inset observation)
- app/src/main/res/xml/accessibility_service_config.xml
  (event mask only — gated as described in H5)

Implementation notes:
- HandyAccessibilityService.onAccessibilityEvent: in addition to
  current behaviour, treat TYPE_WINDOW_CONTENT_CHANGED *with a
  different packageName than the grounding snapshot* as a cancel
  signal. Use BuddyFlightDriver to test current grounding and
  cancel.
- BuddyFlightDriver.cancelIfStaleTarget(reason: String): if state ==
  PREPARING_POINT or FLYING, call cancel() and Timber.d the reason.
- FloatingWidgetOverlayService: observe Configuration changes and
  WindowInsets (IME visibility) via doOnApplyWindowInsets; on change,
  invoke cancelIfStaleTarget.
- Service unbind path: BuddyFlightDriver.cancel() on
  detachService(); already present, ensure no leak.

Acceptance:
- Rotate device mid-flight → buddy lands at the dock, log shows
  "rotation".
- Open the IME mid-flight → flight cancels.
- Disable Handy accessibility service mid-flight → no crash, audit
  row "service-disconnected".

Do NOT:
- Cancel during the sticky POINTING phase for routine content-change
  events; only cancel when the target's bounds become invalid or
  when the package changes.

Tests:
- Add a unit test for BuddyFlightDriver.cancelIfStaleTarget that uses
  a fake presenter to verify state transitions.
- Manual rotation + IME tests on a real device.

Update DEBUG_LOG.md.
```

### Prompt H4 — Action disclosure activity + tap-for-me confirmation sheet + Play copy

```
Goal: open the ActionExecutionGate properly. No tap-for-me without
explicit, prominent, in-app disclosure + per-action confirmation.

Files to touch (new):
- app/src/main/kotlin/com/handy/app/onboarding/ActionDisclosureActivity.kt
- app/src/main/kotlin/com/handy/app/overlay/TapForMeConfirmationSheet.kt
- core/src/main/kotlin/com/handy/core/action/ConfirmationPolicy.kt
  (extend; AssistantAction.isDestructive is already there — add a
  fun confirmationLevel(action): NORMAL | STRONG)
- app/src/main/res/values/strings.xml (new strings)

Files to touch (existing):
- app/src/main/kotlin/com/handy/app/onboarding/OnboardingActivity.kt
  (link to the new ActionDisclosureActivity AFTER accessibility is
  granted; do not gate accessibility on action consent)
- android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt
  (writer for actionDisclosureVersionAccepted; existing reader works)
- app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt
  (toggle: Tap-for-me; revoke clears the disclosure version so the
  gate closes; show a clear "what this does / what it cannot do"
  block)
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
  (after flyTo's land, do NOT auto-tap; instead present
  TapForMeConfirmationSheet and only call actionPerformer.tap on
  positive confirmation)
- app/src/main/res/values/strings.xml — REPLACE the
  accessibility_service_description and onboarding_disclosure_body
  strings so they no longer say "v1 never taps", and instead reflect
  the new opt-in tap-for-me reality.
- PLAYSTORE_SUBMISSION.md §4.3, §4.5, §5.1, §5.4 to match.

Implementation notes:
- ActionDisclosureActivity copy must:
  * Name the data accessed (visible text, view ids, bounds).
  * Name the action: "with your confirmation, Handy can tap, scroll,
    or type on the screen."
  * Affirmative-action button: "Allow tap-for-me" (writes
    actionDisclosureVersionAccepted = 1).
  * Decline path returns to Settings with the toggle off.
- TapForMeConfirmationSheet: shows the planned action + target
  snippet + cancel. Sensitive actions (DialNumber, ComposeSms send,
  any explicit `send`/`pay`/`delete` token in the user's request)
  require a hold-to-confirm (1.0s).
- The sheet must time out after 8 s of inactivity → cancel.

Acceptance:
- Fresh install: onboarding → accessibility consent → action
  disclosure → tap-for-me toggle visible.
- With the gate open, no gesture fires without the confirmation
  sheet.
- Revoke from Settings sets actionDisclosureVersionAccepted = 0 and
  the gate closes within the same session.
- New unit test ActionExecutionGateRevokeTest.

Do NOT:
- Auto-enable tap-for-me on upgrade.
- Use platform AlertDialog for the confirmation sheet (it would
  steal focus and break the overlay flight UX). Use a Compose
  bottom sheet inside the existing overlay.

Tests:
- ./gradlew :core:test :app:test
- Manual: full disclosure flow + revoke + sensitive-action confirm.

Update DEBUG_LOG.md.
```

### Prompt H5 — canPerformGestures + broader event mask (gated)

```
Goal: enable the gesture-fallback path that AccessibilityGestureActionPerformer
already implements, and broaden the event mask so multi-step verify
can work.

Files to touch:
- app/src/main/res/xml/accessibility_service_config.xml
- app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt
  (apply broader event mask only when the action disclosure is
  accepted; otherwise keep current narrow mask)
- (optional) app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt
  (show a "Gesture dispatch allowed" row)

Implementation notes:
- Add android:canPerformGestures="true" to the XML.
- In HandyAccessibilityService.onServiceConnected, after the existing
  flag adjustment, read the cached disclosure version. If accepted,
  set serviceInfo.eventTypes to include TYPE_VIEW_SCROLLED +
  TYPE_VIEW_TEXT_CHANGED. Otherwise keep the current mask.
- Re-apply when the user toggles the disclosure (broadcast or just
  reconnect the service via Settings deep link).

Acceptance:
- Gesture fallback fires on a Compose target without `clickable`.
- Diagnostics shows the active event mask.
- Idle CPU and battery profile do not regress when the disclosure is
  off.

Tests:
- Manual on Pixel + 1 OEM (Samsung if available).

Update DEBUG_LOG.md.
```

### Prompt H6 — Resolver replay harness + 20-app corpus

```
Goal: turn pointer accuracy into a measurable, regression-tested
property.

Files to touch (new):
- core/src/main/kotlin/com/handy/core/screen/replay/SnapshotReplay.kt
- core/src/test/kotlin/com/handy/core/screen/replay/SemanticPointerResolverReplayTest.kt
- core/src/test/resources/replay/<app>/screen_<n>.json
  (seed with synthetic snapshots for now; real device captures arrive
   incrementally)

Files to touch:
- (none in :app) — the replay harness must be pure Kotlin.

Implementation notes:
- SnapshotReplay schema:
  {
    "package": "...",
    "windowId": 123,
    "marks": [ /* AccessibilityMark JSON */ ],
    "spec": { "markId": "m3" } | { "text": "Continue" },
    "expectedMarkId": "m3"
  }
- The test creates a fake AccessibilityNodeInfo-like graph by
  treating marks as the candidate set (no Android dependency).
  SemanticPointerResolver should accept a "fake-mode" constructor or
  the test should call a new method `scoreCandidates(marks, spec)`
  that's already extractable from the current code.
- Aggregate pass/fail and print per-app accuracy at test end.

Acceptance:
- ./gradlew :core:test prints "Pointer replay accuracy: X/Y" per app
  group with X/Y > 0.95 on the seeded corpus.
- Regression: a deliberately worse scoring formula fails the test.

Do NOT:
- Touch :android-runtime; this harness is pure Kotlin.

Update DEBUG_LOG.md.
```

### Prompt H7 — Sensitive-app denylist + learned allowlist

```
Goal: never tap inside banking, payment, password apps. Learn safe
apps over time.

Files to touch (new):
- core/src/main/kotlin/com/handy/core/action/ActionAppPolicy.kt
- android-runtime/src/main/kotlin/com/handy/runtime/storage/LearnedAllowlistStore.kt

Files to touch:
- app/src/main/kotlin/com/handy/app/accessibility/SwitchingActionPerformer.kt
- app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt
- app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt
  (show current denylist + learned allowlist counts)

Implementation notes:
- ActionAppPolicy.STATIC_DENYLIST: a curated set of package patterns
  (banking, UPI wallets, password managers). Use prefix match
  (com.icicibank., net.one97.paytm., com.phonepe., com.google.android.apps.walletnfcrel.,
  com.lastpass., com.bitwarden., com.android.systemui, etc).
- LearnedAllowlistStore: DataStore-backed counts per package. Tap
  succeeded → +1; failed/cancelled → 0 reset. A threshold (e.g. 5
  successes) flips a flag.
- SwitchingActionPerformer must consult both before delegating.

Acceptance:
- Opening a banking app silently disables tap-for-me, audit row
  AuditResult.NotPermitted with reason "denylisted".
- After N successful taps in a non-denylisted app, the allowlist
  flag flips and Diagnostics reflects it.

Tests:
- Unit test ActionAppPolicyTest.

Update DEBUG_LOG.md.
```

### Prompt H8 — Audit review activity + panic switch

```
Goal: every gesture Handy ever fired is reviewable, revocable, and
killable.

Files to touch (new):
- app/src/main/kotlin/com/handy/app/diagnostics/AuditReviewActivity.kt

Files to touch:
- app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt
  (link to the new activity)
- app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt
  (panic switch: "Disable tap-for-me for 1 hour")
- android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt
  (a new long: tapForMeMutedUntilEpochMs)
- core/src/main/kotlin/com/handy/core/action/ActionExecutionGate.kt
  (extend gesturesAllowed to also check tapForMeMutedUntilEpochMs)

Implementation notes:
- AuditReviewActivity: list grouped by day + app, each row has
  "Report wrong tap" (adds the package to a soft denylist; counts
  ignored after 1 success again) and "Disable here" (adds the
  package to a per-user denylist).
- Panic switch is a clock-relative mute, not a permanent off.

Acceptance:
- Manual: tap-for-me + panic mutes for an hour, gate closes within
  the same session.
- "Report wrong tap" disables that package without affecting others.

Tests:
- Unit test for the new gate condition.

Update DEBUG_LOG.md.
```

### Prompt P1 — Confidence-tiered behaviour

```
Goal: visible behaviour change keyed off resolver confidence.

Files to touch:
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
- app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt
- (new optional) app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt
- core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt
  (add a CandidateOptions field)

Implementation notes:
- Tier ladder:
  ≥0.90 → fly + (if gate open) confirmation-sheet path.
  0.70–0.90 → fly only; no auto-tap regardless of gate.
  0.40–0.70 → no fly. Present 3 candidate chips
    (debugCandidates.take(3)) in the panel ("did you mean Continue
    in the popup, the bottom one, or…?"). On chip tap, fly to that
    one.
  <0.40 → no fly. Speak/show "I can't pin that — open the panel".
- Settings toggle: "Always confirm before pointing" (default off).

Acceptance:
- Manual: a curated ambiguous screen (two "Continue" buttons) shows
  the chip row; tapping a chip flies to that exact target.
- Resolver replay regression unaffected.

Tests:
- Update SemanticPointerResolverReplayTest with at least 3 ambiguous
  cases.

Update DEBUG_LOG.md.
```

### Prompt P2 — Mark IDs in the full tree + resolver fallback

```
Goal: full-chat cold-start gets mark IDs in the prompt so the LLM
can choose with the same precision as overlay flow.

Files to touch:
- android-runtime/src/main/kotlin/com/handy/runtime/accessibility/AccessibilityTreeReader.kt
- core/src/main/kotlin/com/handy/core/screen/ScreenTextSerializer.kt
  (already prints markId when present; just verify)
- app/src/main/kotlin/com/handy/app/screen/ScreenContextBuilder.kt
  (REMOVE the .withoutMarkIds() branch — the tree now has stable IDs)
- android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt
  (when given a tree without companion marks, build mark-by-id index
  from the tree itself for fallback)
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  (no copy change unless screenTextAddendum needs a line saying "ids
  may come from either the overlay or this tree")

Implementation notes:
- AccessibilityTreeReader.walk: BFS counter -> markId for any node
  where shouldEmit() returns true. Same naming convention as
  AccessibilityMarkIds (m1, m2…).
- ScreenContextBuilder: when synthesizedPanel == null but tree text
  is present, keep mark IDs.
- Resolver: accept a treeFallbackMarks: List<UiNode>? param; convert
  to RuntimeCandidate.fromTreeNode at score time.

Acceptance:
- The prompt's <screen_ui> block contains m1, m2… in full chat
  opened cold (no panel handoff).
- Manual: ask "tap settings gear" in a fresh ChatActivity → buddy
  flies based on markId.
- Replay corpus extended with 5 cold-start full-tree snapshots.

Tests:
- ./gradlew :android-runtime:test :core:test

Update DEBUG_LOG.md.
```

### Prompt P3 — Resolver upgrades for real-world UIs

```
Goal: lift pointer accuracy on real apps. Apply package, inset,
duplicate-label, and IME heuristics.

Files to touch:
- android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt
- (small) app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt
  if you need a hook for IME visibility.

Implementation notes:
- Penalise candidates outside expectedPackage (-80 already added in H2;
  verify and keep).
- Penalise candidates whose bounds intersect status-bar / nav-bar
  insets (-30).
- Bonus +15 when the spec.text is contained verbatim in the user
  message (resolver currently doesn't see the user text; thread it
  through via spec or via a new optional param on resolve()).
- Duplicate labels: if a dialog window is topmost (windows() ordering
  hint), prefer the candidate inside the dialog package window over
  the same-text candidate in the app window.
- IME visible: drop candidates below IME top to 0 confidence.

Acceptance:
- Replay corpus accuracy ≥ 95% on the 20-app pack.
- New unit tests for each heuristic in isolation.

Tests:
- ./gradlew :android-runtime:test

Update DEBUG_LOG.md.
```

### Prompt P4 — Flight FSM + landing polish + arrival haptic

```
Goal: codify the flight state machine and fix the last "buddy under
nav bar" / wrong-side landing edge cases.

Files to touch:
- core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt
- app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
- app/src/main/kotlin/com/handy/app/widget/BezierFlightController.kt
  (only if needed for the new landing chevron tick)

Implementation notes:
- Replace ad-hoc booleans with a FlightFsm enum:
  Docked, Listening, Thinking, Answering, PreparingPoint, Flying,
  Pointing, ActionConfirm, Acting, ActionResult, Returning. The
  Presenter only allows transitions defined in a `from -> to` map.
- chooseLandingPosition: add a final "shrink-to-fit" pass that
  guarantees the chosen rect is fully inside the safe area; if not,
  reduce padding before falling back to the next candidate.
- Add arrival haptic via VibratorManager (gated by a setting; respect
  Settings → System haptics off).

Acceptance:
- Manual: tap-flight on a Pixel and a 16:9 OEM device — no buddy
  under bars in any orientation.
- Macrobenchmark < 5% jank.

Tests:
- New OverlayPresenterFsmTest that asserts illegal transitions
  throw.

Update DEBUG_LOG.md.
```

### Prompt P5 — Speak-and-correct candidate hop

```
Goal: while a sticky pointer is up, "no, the other one" hops to the
runner-up candidate without a new LLM call.

Files to touch:
- app/src/main/kotlin/com/handy/app/voice/VoiceController.kt
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
- core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt
  (cache last resolved.debugCandidates for the duration of POINTING)

Implementation notes:
- Tiny intent classifier in :core: matches "no", "other one", "next",
  "previous", "the popup one" → CorrectionIntent (none, next, prev,
  inDialog).
- BuddyFlightDriver.applyCorrection(intent) picks the next/prev
  candidate from cache and re-runs flyTo.

Acceptance:
- Manual: ambiguous screen → first fly + sticky → say "no, the other
  Continue" → buddy hops without a fresh model round-trip.

Tests:
- Unit test for the classifier; manual for the e2e.

Update DEBUG_LOG.md.
```

### Prompt A1 — Typing capability (ACTION_SET_TEXT)

```
Goal: add `typeText(target, text)` to ActionPerformer; gate it
through the same confirmation flow as taps.

Files to touch:
- core/src/main/kotlin/com/handy/core/action/ActionPerformer.kt
  (add `suspend fun typeText(target: TapTarget, text: String): PerformResult`)
- core/src/main/kotlin/com/handy/core/action/ActionPerformer.kt
  (add ActionCapability.TYPE)
- app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt
- app/src/main/kotlin/com/handy/app/accessibility/SwitchingActionPerformer.kt
- core/src/main/kotlin/com/handy/core/privacy/ScreenRedactor.kt
  (no change; called from performer)
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  (add a typing-capability addendum, gated on a settings flag)
- app/src/main/kotlin/com/handy/app/overlay/TapForMeConfirmationSheet.kt
  (typing variant: shows the text being typed; user can edit)

Implementation notes:
- typeText impl: re-resolve target → verify node.isEditable AND
  ACTION_SET_TEXT in node.actionList; otherwise return Unsupported.
- Bundle().apply {
    putCharSequence(
      AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
      sanitizedText
    )
  }
- sanitizedText: pass through ScreenRedactor.redactText with the
  field's context; if the redacted text differs from the requested
  text (i.e. the model tried to type a card / OTP), refuse with
  PerformResult.Failed("blocked by privacy filter") and audit.
- Fallback: focus node + InputConnection-style nothing is available
  from a11y; if ACTION_SET_TEXT returns false, log and surface
  failure (do not try to dispatchGesture per-keystroke — that's a
  rabbit hole; v1 keeps ACTION_SET_TEXT only).

Acceptance:
- Manual: ask "search for milk" in Amazon → confirmation sheet → on
  confirm, the search field gets the text and submits via Enter (or
  the recipe taps Search; that's A5).
- Privacy filter refuses to type a card number that the model
  happens to emit; audit row "blocked".

Tests:
- AccessibilityGestureActionPerformerTypeTextTest with a fake node.

Update DEBUG_LOG.md.
```

### Prompt A2 — Verified actions (event-confirmed)

```
Goal: every action checks the screen actually changed.

Files to touch:
- (new) android-runtime/src/main/kotlin/com/handy/runtime/accessibility/ActionEventObserver.kt
- app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt
  (feed events into the observer)
- app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt
  (await confirmation up to 1500ms; on timeout, audit and report)
- core/src/main/kotlin/com/handy/core/audit/AuditEvent.kt
  (add a verifiedBy: String? field, e.g. "view-clicked", "scrolled",
   "text-changed", or null when unverified)

Implementation notes:
- ActionEventObserver: StateFlow<AccessibilityEventSummary> that
  HandyAccessibilityService emits to for the right types
  (TYPE_VIEW_CLICKED, TYPE_VIEW_SCROLLED, TYPE_VIEW_TEXT_CHANGED,
  TYPE_WINDOW_CONTENT_CHANGED).
- Performer.tap: after dispatch, wait via withTimeoutOrNull(1500L) on
  a flow that filters by source matching node bounds / window id.
- Audit row carries verifiedBy.

Acceptance:
- Manual: tap a button → audit shows verifiedBy = "view-clicked".
- Tap a button whose handler does nothing → verifiedBy = null,
  Diagnostics highlights "did that work?" prompt.

Tests:
- Unit test for ActionEventObserver matcher logic.

Update DEBUG_LOG.md.
```

### Prompt A3 — Bounded "Do with me" stepper

```
Goal: a strict 5-step supervised plan runner.

Files to touch (new):
- core/src/main/kotlin/com/handy/core/agent/AgentPlan.kt
- core/src/main/kotlin/com/handy/core/agent/AgentRunner.kt
- app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt
- app/src/main/kotlin/com/handy/app/overlay/AgentProgressBubble.kt

Files to touch:
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  (new "agent mode" addendum: must emit AgentPlan JSON, one step at
   a time on request; max 5 steps)
- app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt
  (when the assistant returns an AgentPlan, hand to AgentRunner)

Implementation notes:
- AgentPlan: List<AgentStep>. Each step = action + targetSpec +
  expectation + recoverHint.
- AgentRunner.run(): for each step, (1) capture, (2) call resolver,
  (3) show confirmation if action is sensitive, (4) call performer,
  (5) wait for ActionEventObserver verification, (6) capture again,
  (7) compare to expectation. On failure, ask user "retry / skip /
  cancel".
- Hard rules: max 5 steps, abort if foreground package changes,
  abort if any step is denylisted (H7), every sensitive step must be
  explicitly confirmed.

Acceptance:
- Manual: "Set a 7am alarm in Clock" runs end to end (open Clock,
  tap +, type "07:00", tap Save) with a visible progress bubble and
  audit per step.
- A plan that asks for >5 steps is rejected by the runner.

Tests:
- AgentRunnerTest with a fake performer/resolver.

Do NOT:
- Let the agent send any message / open any payment without an
  explicit per-step confirmation.

Update DEBUG_LOG.md.
```

### Prompt A4 — System task layer expansion + notification reply

```
Goal: lean on intents wherever possible; add RemoteInput reply for
the notification listener.

Files to touch:
- core/src/main/kotlin/com/handy/core/action/AssistantAction.kt
  (add `ReplyToNotification(key, text)` and `CreateNote(text, title?)`
   — keep CreateNote backed by ACTION_SEND text/plain initially)
- android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt
- app/src/main/kotlin/com/handy/app/notifications/HandyNotificationListenerService.kt
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  (announce these actions in the dispatch_action prompt addendum)

Implementation notes:
- Notification reply: enumerate active notifications, find the matching
  Notification.Action with RemoteInput, build the reply intent with
  Bundle + RemoteInput.addResultsToIntent, sendIntent.send().
  Reference: developer.android.com/training/wearables/notifications/voice-input
- This stays opt-in; respects the existing notificationListenerEnabled
  setting; user-visible confirmation always required.

Acceptance:
- Manual: receive a WhatsApp notification, ask Handy "reply 'on my
  way'" → confirmation sheet → reply lands. (Test with WhatsApp Web
  or another messaging app that supports direct reply.)

Tests:
- Unit test for the AndroidIntentDispatcher Reply path with a faked
  StatusBarNotification.

Update DEBUG_LOG.md.
```

### Prompt A5 — App recipe pack v1 (Clock, Gmail, WhatsApp, Maps, Chrome, Settings)

```
Goal: ship 6 high-value recipes; each is small, testable, opt-in.

Files to touch (new):
- core/src/main/kotlin/com/handy/core/agent/AppRecipe.kt
- core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/
  ClockRecipe.kt
  GmailRecipe.kt
  WhatsAppRecipe.kt
  MapsRecipe.kt
  ChromeRecipe.kt
  AndroidSettingsRecipe.kt

Files to touch:
- app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt
  (give the LLM "we have a recipe for <foreground package>; prefer it
   over generic taps")
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt

Implementation notes:
- AppRecipe interface (from Handy_Android_Build_Plan_V2_Scope.md):
  fun recognizes(snapshot: TurnScreenContext): Boolean
  fun proposeActions(userGoal: String, snapshot: TurnScreenContext): List<RecipeAction>
  fun validateBeforeAction(action: RecipeAction, liveSnapshot: TurnScreenContext): ValidationResult
  fun execute(action: RecipeAction): ActionResult
- Recipes match on package + (optional) windowTitle / mark patterns.
- No hardcoded coordinates. Match on viewIds and text; degrade
  gracefully when the app updates.

Acceptance:
- Manual: "Set a 7am alarm" runs via ClockRecipe on stock + GrapheneOS
  + Samsung; "Search for milk in Amazon" — pending until A5.2 ships
  the Amazon pack.

Tests:
- Snapshot-based recipe tests in :core (no Android dependency for
  recognizes/proposeActions; execute is in :android-runtime).

Update DEBUG_LOG.md.
```

### Prompt A6 — Local "describe this screen" via Gemini Nano

```
Goal: when the user says "what's on this screen" with screen text
present and local AI enabled, route via LocalGenAiClient instead of
cloud.

Files to touch:
- core/src/main/kotlin/com/handy/core/brain/BrainRouter.kt
  (route description tasks to local when supported)
- android-runtime/src/main/kotlin/com/handy/runtime/llm/GeminiNanoLocalGenAiClient.kt
  (verify availability check; surface to UI)
- app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt
  (use AssistantTask with localTask = Describe)

Acceptance:
- With local AI on and the device is supported, the "what's on this
  screen" path does not hit Claude; a Diagnostics row says "local".

Update DEBUG_LOG.md.
```

### Prompt V1 — Hindi + Hinglish loop

```
Goal: Hindi voice in/out + Hindi prompt mirror, with Hinglish
fallback.

Files to touch:
- app/src/main/kotlin/com/handy/app/voice/VoiceController.kt
- android-runtime/src/main/kotlin/com/handy/runtime/speech/AndroidSttClient.kt
  (locale-aware)
- android-runtime/src/main/kotlin/com/handy/runtime/speech/AndroidTtsClient.kt
  (locale + voice selection)
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  (Hindi mirror of CHAT_SYSTEM_PROMPT + VOICE_SYSTEM_PROMPT; keep
   internal control tags in English)
- app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt
  (Language picker: System / English / Hindi)

Acceptance:
- Voice utterance in Hindi → spoken reply in Hindi (system voice or
  Sarvam if configured) with the same [SPOKEN]…[/SPOKEN] and
  [POINT:…] discipline.
- Mixed Hinglish input answered in matching register.

Update DEBUG_LOG.md.
```

### Prompt V2 — "Shop with me" mode

```
Goal: domain-scoped Hindi voice shopping assistant on the major
Indian shopping apps.

Files to touch:
- core/src/main/kotlin/com/handy/core/prompts/QuickPromptCatalog.kt
  (add Meesho/Amazon/Flipkart quick prompts)
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  (shopping-mode addendum: ask if returnable, find coupons, compare)
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/
  ShoppingRecipePack.kt

Acceptance:
- On a Meesho product page, the panel shows the shopping prompts and
  "compare with similar" calls fetch_page + summarises.

Update DEBUG_LOG.md.
```

### Prompt T1 — Play submission rev + transparency page

```
Goal: ship-ready Play story.

Files to touch:
- PLAYSTORE_SUBMISSION.md §4.3, §4.5, §5.1, §5.4
- PRIVACY_POLICY.md (add tap-for-me + agent paragraphs)
- README.md (current-state list)
- app/src/main/res/values/strings.xml (every accessibility / disclosure
  copy string)
- app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt
  (new "What Handy can do today" section, generated from flags)

Acceptance:
- A reviewer reading the disclosure + settings can name exactly what
  Handy will and will not do.

Update DEBUG_LOG.md.
```

---

## 13. After all the above

Re-evaluate. The right next bets at that point are:
- a 3rd "brain" path (a small on-device action model trained on the
  audit corpus),
- an explicit `[SHARED_CONTEXT]` block letting the LLM remember
  cross-app intent across turns ("the Amazon thing I was just
  looking at"),
- and a small marketplace for community-contributed app recipes.

The plan ends here on purpose. Anything past §6 is speculative until
the H/P/A phases land.
