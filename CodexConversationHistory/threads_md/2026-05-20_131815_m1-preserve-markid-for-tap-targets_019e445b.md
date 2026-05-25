# M1: Preserve markId for tap targets

## Metadata

- Thread ID: `019e445b-4ee1-7040-932b-ede4fa2c10e0`
- Created: 2026-05-20 13:18:15 IST
- Updated: 2026-05-20 13:29:39 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/20/rollout-2026-05-20T13-18-15-019e445b-4ee1-7040-932b-ede4fa2c10e0.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `796f32dc75b1d0abc3735e28858887421f18ee48`
- Messages exported: 14

## Brief Summary

This conversation focused on: end-to-end markId preservation so the buddy lands AND taps on Likely related git changes: 796f32d Add MediaProjection capture source and grounding.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `796f32dc75b1d0abc3735e28858887421f18ee48`
- Likely related commits:
  - `796f32d` 2026-05-20 13:18:09 IST [low] Add MediaProjection capture source and grounding. Files: android-runtime/src/main/kotlin/com/handy/runtime/capture/MediaProjectionCaptureSourceImpl.kt, android-runtime/src/main/kotlin/com/handy/runtime/capture/ScreenCapturePipeline.kt, app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt, app/src/main/kotlin/com/handy/app/di/AppRuntimeBindings.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt, app/src/main/kotlin/com/handy/app/screen/ScreenContextBuilder.kt, app/src/main/kotlin/com/handy/app/service/MediaProjectionCaptureService.kt, app/src/test/kotlin/com/handy/app/screen/ScreenContextBuilderBudgetTest.kt, core/src/main/kotlin/com/handy/core/orchestrator/ConversationOrchestrator.kt, core/src/test/kotlin/com/handy/core/screen/GroundingSnapshotTest.kt

## Conversation

### USER 2026-05-20T07:48:24.697Z

Goal: end-to-end markId preservation so the buddy lands AND taps on
the same node. No more "pointer correct, tap wrong" bug.

Files to touch:
- core/src/main/kotlin/com/handy/core/action/ActionPerformer.kt
  (extend TapTarget.AtNode):
      data class AtNode(
          val markId: String?,
          val role: String?,
          val text: String?,
          val viewId: String?,
          val desc: String?,
          val expectedPackage: String?,
          val expectedWindowId: Int?,
          val snapshotHash: String?,
      ) : TapTarget()
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
  (flyToAndTap builds TapTarget.AtNode from the RESOLVED target +
  the grounding snapshot's expectedPackage/expectedWindowId/snapshotHash;
  passes ResolvedPointTarget.markId in)
- (new) android-runtime/src/main/kotlin/com/handy/runtime/accessibility/LiveScreenGuard.kt
  (data class LiveScreen(packageName, windowId, rootBoundsHash);
   fun snapshot(): LiveScreen reads rootInActiveWindow on main)
- app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt:
  * In perform(): call LiveScreenGuard.snapshot(); if
    (packageName, windowId) != (target.expectedPackage,
    target.expectedWindowId), audit AuditResult.Failed("screen-changed")
    and return PerformResult.Failed("screen-changed").
  * Build SemanticPoint from TapTarget.AtNode preferring markId
    when present. The resolver will use markId as its primary key.
- android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt
  (accept expectedPackage/expectedWindowId; penalise mismatches
   −80; skip nodes whose root.packageName ==
   BuildConfig.APPLICATION_ID — pass the package in via constructor
   so :android-runtime stays clean of :app).
- app/src/main/kotlin/com/handy/app/di/AppRuntimeBindings.kt
  (provide the application package to the resolver constructor).

Acceptance:
- Add a NEW invariant test in :app instrumented suite:
  "if assistant emits [POINT:markId=m7], every recorded
  ResolvedPointTarget, TapTarget, and audit row references m7".
- Manual: in a screen with two "Continue" buttons, the assistant
  says [POINT:markId=m2] → buddy flies to m2 → tap fires on m2 (not
  the duplicate).
- Manual: swipe to another app between flight and tap → audit
  records "screen-changed", no spurious tap.

Tests:
- ./gradlew :core:test :android-runtime:test :app:test

Do NOT:
- Change the parser; the [POINT:markId=…] form already exists.
- Hard-ban cross-window matches in the resolver — keep it a penalty.

### ASSISTANT 2026-05-20T07:48:32.017Z

I’ll wire this through the actual flow and keep the parser alone. First I’m going to inspect the current target/resolver/performer code and the existing test structure so the new invariant test fits the project instead of becoming a little island.

### ASSISTANT 2026-05-20T07:48:43.027Z

The repo has an unrelated untracked `handy_macos_ref/`; I’ll leave it untouched. I’ve found the relevant classes, and next I’m reading their current shape plus the nearby tests so the mark id stays in the same data path all the way down.

### ASSISTANT 2026-05-20T07:49:56.901Z

The current bug shape is clear: `flyToAndTap()` resolves for flight, throws that resolved identity away, then the performer re-resolves from weaker semantic fields. I’m going to change that into a single resolved handoff: resolved mark id plus the grounding package/window/hash go into the tap target, then the performer verifies the live screen and re-resolves with mark id as the first-class key.

### ASSISTANT 2026-05-20T07:51:50.091Z

I’m about to edit the data handoff surfaces: `TapTarget.AtNode` gets the mark/window/hash fields, the resolver learns expected package/window and live mark ids, and the performer gets a guard before it touches the screen.

### ASSISTANT 2026-05-20T07:55:13.688Z

The core wiring is in place. I’m adding a focused instrumentation invariant now: it parses an emitted `m7`, builds the resolved handoff target, runs the performer down the audit path, and asserts the same id appears in every recorded layer.

### ASSISTANT 2026-05-20T07:56:13.423Z

I’ve got the code path stitched together and the invariant test written. Now I’m running the requested unit test set first; after that I’ll compile the instrumentation test too, because the new acceptance test lives under `app/src/androidTest`.

### ASSISTANT 2026-05-20T07:56:23.491Z

The Gradle run stopped before compilation because this shell can’t see a Java runtime. I’m going to look for a bundled JDK/JBR on the machine and, if one exists, rerun Gradle with `JAVA_HOME` pointed at it.

### ASSISTANT 2026-05-20T07:56:59.212Z

There is a Gradle-provisioned Temurin 17 under `~/.gradle/jdks`, so this is recoverable without installing anything. I’m rerunning the requested tests with `JAVA_HOME` set to that JDK.

### ASSISTANT 2026-05-20T07:57:18.928Z

The requested unit tests are green with the local JDK. I’m doing one more check now: compiling the app instrumentation tests so the new Android test is verified by the build, even though it wasn’t part of the unit-test command.

### ASSISTANT 2026-05-20T07:57:37.248Z

The new instrumentation compile exposed two existing androidTest build gaps: Truth wasn’t on the androidTest classpath, and an older fake `LlmClient` is missing the newer tool-aware method. I’m going to fix those small test-build issues so the suite can actually compile with the new invariant included.

### ASSISTANT 2026-05-20T07:58:03.881Z

Instrumentation compilation is green too after fixing those test-suite wiring gaps. I’m rerunning the requested unit-test command once more after the small Gradle test dependency change, so the final status is clean from the current tree.

### ASSISTANT 2026-05-20T07:58:26.074Z

The requested unit tests are clean. I don’t see an attached device from `adb devices`, so I’m not going to pretend I ran the instrumentation test on hardware; the instrumentation sources do compile now. I’m doing a final diff pass for accidental churn and any obvious edge-case misses.

### ASSISTANT 2026-05-20T07:59:39.528Z

Implemented the markId-preserving tap path end to end.

Key changes:
- Extended `TapTarget.AtNode` with `markId`, expected package/window, and snapshot hash in [ActionPerformer.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/action/ActionPerformer.kt:25).
- Changed `flyToAndTap()` to keep the resolved target and build the tap target from that resolved identity in [BuddyFlightDriver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt:248).
- Added `LiveScreenGuard` and `screen-changed` fail-closed behavior before tap dispatch in [LiveScreenGuard.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/accessibility/LiveScreenGuard.kt:17) and [AccessibilityGestureActionPerformer.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt:133).
- Updated the resolver to use live stable mark ids, accept expected package/window, penalize mismatches, and skip Handy’s own root package in [SemanticPointerResolver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt:71).
- Added the requested instrumentation invariant test in [MarkIdHandoffInvariantTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/androidTest/kotlin/com/handy/app/pointing/MarkIdHandoffInvariantTest.kt:27).

Verification:
- `./gradlew :core:test :android-runtime:test :app:test` passed.
- `./gradlew :app:compileDebugAndroidTestKotlin` passed.
- I used the Gradle-provisioned Temurin 17 JDK because the shell had no default Java runtime.
- I did not run connected instrumentation tests on a device; `adb devices` did not show an attached target.

I also fixed the existing androidTest compile wiring by adding Truth to `androidTestImplementation` and updating the old OS-5 fake `LlmClient` to implement the current interface. The unrelated untracked `handy_macos_ref/` was left untouched.
