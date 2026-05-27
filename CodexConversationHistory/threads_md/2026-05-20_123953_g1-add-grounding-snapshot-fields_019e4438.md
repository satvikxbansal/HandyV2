# G1: Add grounding snapshot fields

## Metadata

- Thread ID: `019e4438-2ef2-7311-9a21-ef25347fd76e`
- Created: 2026-05-20 12:39:53 IST
- Updated: 2026-05-20 12:56:14 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/20/rollout-2026-05-20T12-39-53-019e4438-2ef2-7311-9a21-ef25347fd76e.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `811f07048dbf510e2e550fef603125c3dad54695`
- Messages exported: 13

## Brief Summary

This conversation focused on: 1) Extend TurnScreenContext into GroundingSnapshot (added fields: windowId, displayId, orientation, windowBounds, safeInsets, imeVisible, imeBounds, densityDpi, locale, uiMode, rootBoundsHash, treeHash, capturedAtMs, privacyFlags). Keep TurnScreenContext as a… Likely related git changes: 796f32d Add MediaProjection capture source and grounding.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `811f07048dbf510e2e550fef603125c3dad54695`
- Likely related commits:
  - `796f32d` 2026-05-20 13:18:09 IST [low] Add MediaProjection capture source and grounding. Files: android-runtime/src/main/kotlin/com/handy/runtime/capture/MediaProjectionCaptureSourceImpl.kt, android-runtime/src/main/kotlin/com/handy/runtime/capture/ScreenCapturePipeline.kt, app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt, app/src/main/kotlin/com/handy/app/di/AppRuntimeBindings.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt, app/src/main/kotlin/com/handy/app/screen/ScreenContextBuilder.kt, app/src/main/kotlin/com/handy/app/service/MediaProjectionCaptureService.kt, app/src/test/kotlin/com/handy/app/screen/ScreenContextBuilderBudgetTest.kt, core/src/main/kotlin/com/handy/core/orchestrator/ConversationOrchestrator.kt, core/src/test/kotlin/com/handy/core/screen/GroundingSnapshotTest.kt

## Conversation

### USER 2026-05-20T07:09:56.693Z

Goal:
1) Extend TurnScreenContext into GroundingSnapshot (added fields:
   windowId, displayId, orientation, windowBounds, safeInsets,
   imeVisible, imeBounds, densityDpi, locale, uiMode, rootBoundsHash,
   treeHash, capturedAtMs, privacyFlags). Keep TurnScreenContext as a
   type alias for back-compat.
2) Thread activeWindowIdHint into capturePipeline.capture(...).
3) Wire a real MediaProjectionCaptureSource so API 26-29 stops
   returning Unsupported. If you can't ship a full MediaProjection
   wire-up in this prompt, instead surface CaptureResult.Unsupported
   honestly in the UI text and keep the foreground-service shell.
4) Add a 200ms per-step withTimeoutOrNull around marks/tree/capture
   in ScreenContextBuilder.

Files to touch:
- (new) core/src/main/kotlin/com/handy/core/screen/GroundingSnapshot.kt
- core/src/main/kotlin/com/handy/core/screen/TurnScreenContext.kt
  (deprecate; typealias TurnScreenContext = GroundingSnapshot)
- app/src/main/kotlin/com/handy/app/screen/ScreenContextBuilder.kt
- android-runtime/src/main/kotlin/com/handy/runtime/capture/ScreenCapturePipeline.kt
  (read activeWindowIdHint properly on all tiers)
- (new) android-runtime/src/main/kotlin/com/handy/runtime/capture/MediaProjectionCaptureSourceImpl.kt
- app/src/main/kotlin/com/handy/app/service/MediaProjectionCaptureService.kt
- app/src/main/kotlin/com/handy/app/di/AppRuntimeBindings.kt
- core/src/main/kotlin/com/handy/core/orchestrator/ConversationOrchestrator.kt
  (downstream rename only)

Implementation notes:
- safeInsets via WindowMetrics + WindowInsets.Type.systemBars() |
  displayCutout() | ime(). For API < 30, fall back to the
  current resource-id approach but mark `unreliable=true` in the
  PrivacyFlags / InsetsSnapshot.
- rootBoundsHash: hash(windowBounds + IME state + topmost-window id) —
  cheap, used for stale-screen detection.
- treeHash: optional, hash of (mark count, top 10 mark labels)
  — used by Phase 2's stale-screen check.
- Budget timeouts: each of (marks, tree, capture) gets its own 200ms
  withTimeoutOrNull. If capture exceeds budget, log Timber.w and
  return CaptureResult.Failed("budget-exceeded"); never block the
  whole turn.

Acceptance:
- Every OrchestrationRequest carries a GroundingSnapshot with
  non-null windowId/displayId when accessibility is connected.
- Manual: ask Handy a question with Settings → Sound visible; the
  Diagnostics log shows capture windowId == grounding windowId.
- An API 28 emulator either returns Image OR honestly surfaces
  Unsupported in the UI text (not silently null).
- Slow-pipeline fake test confirms 200ms cap.

Tests:
- New ScreenContextBuilderBudgetTest, GroundingSnapshotTest.
- ./gradlew :core:test :android-runtime:test :app:test

### ASSISTANT 2026-05-20T07:10:05.232Z

I’ll make the requested code changes end to end. I’m starting by mapping the current screen/capture types and test layout so the new snapshot lands where the repo already expects it.

### ASSISTANT 2026-05-20T07:10:17.183Z

I found the existing shape: `TurnScreenContext` is still a compact core DTO, while `ScreenContextBuilder` already has most of the runtime signals nearby. Next I’m reading the implementation and tests so I can preserve current call sites while widening the snapshot.

### ASSISTANT 2026-05-20T07:13:54.528Z

I’m going to add the core snapshot now, keeping the old constructor name through a typealias. I’m also putting the hash helpers in core so app code and tests use the same deterministic rules.

### ASSISTANT 2026-05-20T07:14:34.868Z

Now I’m updating the capture pipeline so the window hint is honored where Android allows it: direct window capture on API 34+, matching display capture on API 30-33, and propagation into the MediaProjection tier for API 26-29.

### ASSISTANT 2026-05-20T07:17:19.611Z

I’ve got the capture source in place. The next edit is the builder: it will gather the active window metadata, give marks/tree/capture independent 200 ms budgets, pass the active window id into capture, and return the widened snapshot.

### ASSISTANT 2026-05-20T07:19:46.405Z

The orchestration request now carries the grounding object directly while keeping the old `capture`/`screenText` fields alive for compatibility. I’m going to do a compile pass next, then I’ll add the focused tests against whatever the compiler tells us.

### ASSISTANT 2026-05-20T07:19:55.460Z

The first Gradle call hit the local macOS Java picker, so I’m rerunning with the repo’s known Gradle JDK path.

### ASSISTANT 2026-05-20T07:22:29.522Z

The budget test is in place around a deliberately slow capture pipeline. I’m running the requested module tests now; if a local test harness detail fights us, I’ll tighten that up rather than hand-wave it.

### ASSISTANT 2026-05-20T07:23:14.265Z

The new app budget test passes after a small JUnit rule visibility fix. I’m rerunning the full requested test set cleanly now.

### ASSISTANT 2026-05-20T07:23:33.348Z

All requested test modules are green. I’m doing one final diff/readback pass now to catch any accidental churn and summarize the exact files touched.

### ASSISTANT 2026-05-20T07:24:49.223Z

I spotted one stale capture-pipeline comment while reviewing and corrected it to match the display-id behavior. Since that touches compiled source, I’m rerunning the requested tests once more.

### ASSISTANT 2026-05-20T07:26:14.260Z

Implemented the grounding/capture pass.

- `GroundingSnapshot` carries the expanded window/display/insets/IME/hash/privacy fields, with `TurnScreenContext` kept as the deprecated alias: [GroundingSnapshot.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/screen/GroundingSnapshot.kt:17), [TurnScreenContext.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/screen/TurnScreenContext.kt:7)
- `ScreenContextBuilder` now returns `GroundingSnapshot`, reads active window metadata, logs grounding/capture window ids, passes `activeWindowIdHint`, and applies independent 200 ms budgets for marks/tree/capture: [ScreenContextBuilder.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/screen/ScreenContextBuilder.kt:63)
- Added a real one-shot MediaProjection source and wired it through the service/DI path; if no projection session is ready, the API 26-29 path surfaces `Unsupported` instead of silently nulling: [MediaProjectionCaptureSourceImpl.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/capture/MediaProjectionCaptureSourceImpl.kt:40)
- `OrchestrationRequest` now carries `grounding`, and chat/overlay call sites pass the built snapshot through: [ConversationOrchestrator.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/orchestrator/ConversationOrchestrator.kt:259)
- Added `GroundingSnapshotTest` and `ScreenContextBuilderBudgetTest`: [GroundingSnapshotTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/test/kotlin/com/handy/core/screen/GroundingSnapshotTest.kt:8), [ScreenContextBuilderBudgetTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/test/kotlin/com/handy/app/screen/ScreenContextBuilderBudgetTest.kt:38)

Verification passed:

`JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x.2/jdk-17.0.18+8/Contents/Home ./gradlew :core:test :android-runtime:test :app:test --stacktrace`

I did not run the manual Settings → Sound or API 28 emulator checks in this environment.
