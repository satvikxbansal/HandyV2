# Goal: After a successful tap-for-me, OverlayPresenter leaves

## Metadata

- Thread ID: `019e457b-0eae-7be0-aa66-6eacd7814962`
- Created: 2026-05-20 18:32:33 IST
- Updated: 2026-05-20 18:37:48 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/20/rollout-2026-05-20T18-32-33-019e457b-0eae-7be0-aa66-6eacd7814962.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `9549a8659d3a0242cebe62879eb44a231b1b6ebd`
- Messages exported: 10

## Brief Summary

This conversation focused on: After a successful tap-for-me, OverlayPresenter leaves Likely related git changes: 9549a86 F1: Viewport-aware buddy flight, stale cancel & FSM; e07fa9b Some fixes.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `9549a8659d3a0242cebe62879eb44a231b1b6ebd`
- Likely related commits:
  - `9549a86` 2026-05-20 17:57:23 IST [medium] F1: Viewport-aware buddy flight, stale cancel & FSM. Files: DEBUG_LOG.md, app/build.gradle.kts, app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt, app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt, app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/main/kotlin/com/handy/app/widget/BezierFlightController.kt, app/src/test/kotlin/com/handy/app/overlay/BuddyFlightLandingGeometryTest.kt, app/src/test/kotlin/com/handy/app/overlay/OverlayPresenterFsmTest.kt, core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt
  - `e07fa9b` 2026-05-20 18:41:29 IST [low] Some fixes. Files: DEBUG_LOG.md, README.md, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/test/kotlin/com/handy/app/overlay/OverlayPresenterFsmTest.kt

## Conversation

### USER 2026-05-20T13:04:43.643Z

Goal: After a successful tap-for-me, OverlayPresenter leaves
flightFsm = ActionResult until the next turn starts a new transition.
Diagnostics shows a slightly misleading "stuck at ActionResult".
Reset to Docked when the action finishes.

Files to touch:
- app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt

Required changes:
- onActionFinished(): change `target = FlightFsm.ActionResult` to a
  two-step transition: first set ActionResult, then forceDocked. OR
  simpler: change `target = FlightFsm.ActionResult` to use forceDocked
  directly (Acting -> Docked is allowed via canResetToDocked).
- Add a unit test in OverlayPresenterFsmTest that asserts:
  onPreparingPoint -> onFlyingStart -> onPointingArrived ->
  onActionStarted -> onActionFinished ends with flightFsm == Docked.

RCA log entry (DEBUG_LOG.md):
- New entry "DL-### FSM stuck at ActionResult between turns".
- Root cause: F1 added the FSM transition table but onActionFinished
  used `target = FlightFsm.ActionResult` (correct for the moment of
  finish) and no subsequent caller forced the reset to Docked. Most
  next-turn paths (Listening/Thinking) can transition from
  ActionResult so functional flows worked, but Diagnostics + idle
  state were misleading.
- Prevention rule: every leaf FSM state (ActionResult, Error,
  Returning) must either auto-transition to a steady state in the
  same call or have an explicit drainer; document in
  OverlayPresenter Kdoc.

Tests:
- ./gradlew :app:testDebugUnitTest --tests "*OverlayPresenterFsmTest*"

### ASSISTANT 2026-05-20T13:05:22.259Z

I’ll make the FSM change and keep it tight to the presenter plus the existing FSM tests and debug log. First I’m going to locate the relevant method/test/log so the patch matches the project’s local style.

### ASSISTANT 2026-05-20T13:05:36.518Z

I found the method and the focused test class. There are already unrelated local changes in `DEBUG_LOG.md`, `README.md`, and an untracked `handy_macos_ref/`; I’ll leave those intact and append only the requested log entry.

### ASSISTANT 2026-05-20T13:06:24.936Z

The local transition table already permits `Acting -> Docked` through `canResetToDocked()`, so I’m taking the simpler route: `onActionFinished()` will drain directly to Docked in the same call. I’ll also add the named leaf-state rule to the presenter Kdoc so future FSM leaf states don’t hang around invisibly.

### ASSISTANT 2026-05-20T13:06:38.359Z

Code and the focused unit case are patched. I’m appending `DL-056` now, since the repo’s Android debug log currently ends at `DL-055`.

### ASSISTANT 2026-05-20T13:06:53.528Z

The implementation side is done; I’m running the requested focused Gradle test now. If the environment needs the repo’s JDK override, I’ll adapt and report that clearly.

### ASSISTANT 2026-05-20T13:07:02.749Z

The exact command hit the local Java-runtime lookup problem (`Unable to locate a Java Runtime`). I’m checking the repo/session’s known JDK paths next and will rerun the same test with `JAVA_HOME` if one is already available.

### ASSISTANT 2026-05-20T13:07:06.639Z

There’s a session JDK at `/tmp/codex-jdk17/Contents/Home`, so I’m rerunning the requested focused test with that `JAVA_HOME` exported.

### ASSISTANT 2026-05-20T13:07:22.006Z

The focused test passed with the available session JDK. I’m doing a quick whitespace/diff sanity pass and making the debug-log validation line reflect the actual command that succeeded.

### ASSISTANT 2026-05-20T13:07:48.064Z

Implemented.

Changed [OverlayPresenter.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt) so `onActionFinished()` now drains via `forceDocked`, leaving the FSM at `Docked` after a completed tap-for-me action. Also added the leaf-state Kdoc rule.

Added the focused FSM regression test in [OverlayPresenterFsmTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/test/kotlin/com/handy/app/overlay/OverlayPresenterFsmTest.kt), and appended `DL-056 — FSM stuck at ActionResult between turns` to [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md).

Validation passed:

```sh
JAVA_HOME=/tmp/codex-jdk17/Contents/Home PATH=/tmp/codex-jdk17/Contents/Home/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "*OverlayPresenterFsmTest*"
```

The plain `./gradlew ...` command first failed because this shell couldn’t locate a Java Runtime, so I reran it with the existing session JDK.
