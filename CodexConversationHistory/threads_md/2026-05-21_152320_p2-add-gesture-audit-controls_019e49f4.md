# P2: Add gesture audit controls

## Metadata

- Thread ID: `019e49f4-3163-72d1-b969-7f6fb17122f9`
- Created: 2026-05-21 15:23:20 IST
- Updated: 2026-05-21 15:34:36 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/21/rollout-2026-05-21T15-23-20-019e49f4-3163-72d1-b969-7f6fb17122f9.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `1df199cbcd1a83458324a7f6e92e56c962c33eed`
- Messages exported: 10

## Brief Summary

This conversation focused on: every gesture Handy ever fired is reviewable, revocable, and Likely related git changes: 9bf3b0c Fixed widget clip on prod; bddd08e P2+P3: Support candidate options and audit review.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `1df199cbcd1a83458324a7f6e92e56c962c33eed`
- Likely related commits:
  - `9bf3b0c` 2026-05-21 15:23:53 IST [low] Fixed widget clip on prod. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/di/RuntimeModule.kt, android-runtime/src/main/kotlin/com/handy/runtime/llm/ClaudeLlmClient.kt, android-runtime/src/test/kotlin/com/handy/runtime/llm/ClaudeTransportFailureTest.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt, app/src/main/kotlin/com/handy/app/widget/WidgetContent.kt, app/src/main/res/xml/network_security_config.xml
  - `bddd08e` 2026-05-21 15:54:29 IST [low] P2+P3: Support candidate options and audit review. Files: android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt, app/src/androidTest/kotlin/com/handy/app/diagnostics/AuditReviewActivityTest.kt, app/src/main/AndroidManifest.xml, app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt, app/src/main/kotlin/com/handy/app/diagnostics/AuditReviewActivity.kt, app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt, app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt, app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPanelBridge.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt

## Conversation

### USER 2026-05-21T09:53:25.001Z

Goal: every gesture Handy ever fired is reviewable, revocable, and
killable.

Files to touch (new):
- app/src/main/kotlin/com/handy/app/diagnostics/AuditReviewActivity.kt

Files to touch:
- app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt
- app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt
  (panic switch: "Stop tap-for-me for 1 hour", "Stop until I turn it
   back on"; per-app revoke list)
- android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt
  (mutedUntilEpochMs already added in P1; per-app revoke set)
- core/src/main/kotlin/com/handy/core/action/ActionExecutionGate.kt
  (extend gesturesAllowed to also check muted-until)

Acceptance:
- Manual: tap fires → audit row visible in AuditReviewActivity →
  "Disable here" disables that package; "Report wrong tap" emits a
  feedback intent (no network call yet).
- Panic switch: clicking it sets muted-until = now + 1h; engine
  refuses any decide=true for an hour.

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
Tests:
- ActionExecutionGateMuteTest, AuditReviewActivity instrumented test.

### ASSISTANT 2026-05-21T09:53:33.025Z

I’ll wire this through the existing diagnostics/settings/runtime paths first, then add the focused tests the acceptance calls out. I’m going to inspect the current audit/settings/gate shapes so the new review screen fits the app instead of arriving as a stranger.

### ASSISTANT 2026-05-21T09:53:43.535Z

There are already unrelated local edits in the tree, so I’ll leave those alone and keep my changes tightly scoped. The audit model and store are present; next I’m checking how activities are composed and how settings are persisted so the revoke/mute bits share the same source of truth.

### ASSISTANT 2026-05-21T09:57:34.018Z

I’ve got the shape now: the runtime already has a user package denylist wired into policy, and the audit store keeps a rolling file history. I’m going to add the review surface on top of that, route “Disable here” into the existing denylist, make Settings show and undo those per-app disables, and move the mute check into the central gesture gate.

### ASSISTANT 2026-05-21T09:58:54.311Z

The core kill switch is now centralized: the same `gesturesAllowed` gate that checks consent also checks the one-hour mute, and policy still reports `muted` so diagnostics stay understandable. I’m adding the review UI next, with callbacks that mutate only the user-controlled per-app denylist.

### ASSISTANT 2026-05-21T10:02:07.563Z

The review screen and settings list are in place now. I’m switching to verification: first the pure gate tests, then Kotlin compilation for the Android app/runtime, and I’ll tighten anything the compiler points at.

### ASSISTANT 2026-05-21T10:02:15.498Z

Gradle bounced because the shell has no Java runtime on PATH. There’s a repo note pointing at the local JDK shim used for this workspace, so I’m checking for that and will rerun with `JAVA_HOME` set if it’s still present.

### ASSISTANT 2026-05-21T10:03:05.645Z

Compilation is clean now; the Kotlin daemon had its familiar incremental-cache wobble, then Gradle fell back and succeeded. I’m running the runtime policy test that proves `decide()` stays blocked during the one-hour mute, plus a diff hygiene check.

### ASSISTANT 2026-05-21T10:03:19.187Z

The unit side is green: gate tests and the runtime policy suite passed. I compiled the instrumentation test too; I’ll do a quick device check before deciding whether to run it or report it as compile-only.

### ASSISTANT 2026-05-21T10:04:36.346Z

Implemented the audit-review and kill-switch path.

**What Changed**
Before: Handy logged recent actions in Diagnostics, but the user could not act on an audit row directly. A wrong tap in, say, `com.example.mail` was visible only as a passive diagnostics entry.

After: `AuditReviewActivity` shows stored audit rows with controls:
- `Disable here` adds that package to the tap-for-me denylist, so future gesture decisions for that app are blocked.
- `Report wrong tap` opens a local Android share/feedback intent with redacted audit details. No network call is made by Handy.
- Settings now has `Stop tap-for-me for 1 hour`, `Stop until I turn it back on`, and a `Disabled apps` list with `Allow again`.

The one-hour panic mute is now part of `ActionExecutionGate.gesturesAllowed(...)`, and `DefaultActionPolicyEngine` reports muted gesture decisions as `reason = "muted"`.

Key files:
- [AuditReviewActivity.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/diagnostics/AuditReviewActivity.kt:75)
- [SettingsActivity.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt:291)
- [DataStoreSettings.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt:92)
- [ActionExecutionGate.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/action/ActionExecutionGate.kt:15)

**Verification**
Passed:
- `:core:test --tests ActionExecutionGateTest --tests ActionExecutionGateMuteTest`
- `:android-runtime:testDebugUnitTest --tests DefaultActionPolicyEngineTest`
- `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
- `git diff --check`

I could not device-run the instrumentation test because `adb` is not available in this shell, but `AuditReviewActivityTest` compiles.

**Manual Test Plan**
1. Enable Tap-for-me through the action disclosure.
2. Trigger a confirmed tap in a harmless test app.
3. Open Diagnostics, tap `Review`, and confirm the audit row appears in `Review actions`.
4. Tap `Report wrong tap`; verify Android opens a share/chooser intent containing request id, provider, action, result, and redacted target.
5. Return, tap `Disable here`; verify the row changes to `Disabled here`.
6. Open Settings → Actions → `Disabled apps`; verify the package is listed.
7. Attempt another tap-for-me in that same app; verify policy blocks it as denylisted.
8. Tap `Allow again`; retry the same app and verify it is no longer user-denylisted.
9. Tap `Stop tap-for-me for 1 hour`; verify Settings shows muted copy and a gesture decision returns `muted`.
10. Move device clock past one hour or seed an expired mute; verify gestures work again when toggle/disclosure are still enabled.
11. Tap `Stop until I turn it back on`; verify Tap-for-me toggle is off and gesture decisions are blocked until manually re-enabled.
12. Recheck redaction with password/email targets: raw sensitive text should not appear in Diagnostics or Audit Review.
