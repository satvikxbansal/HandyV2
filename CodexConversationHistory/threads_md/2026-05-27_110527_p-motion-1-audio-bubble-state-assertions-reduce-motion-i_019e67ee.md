# P-MOTION-1: Audio/bubble state assertions, reduce-motion, I…

## Metadata

- Thread ID: `019e67ee-3f05-7551-8d9b-65b15df06768`
- Created: 2026-05-27 11:05:27 IST
- Updated: 2026-05-27 11:30:37 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/27/rollout-2026-05-27T11-05-27-019e67ee-3f05-7551-8d9b-65b15df06768.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `1c887705f26c7827a64370f1bdb3761ee05e9b57`
- Messages exported: 13

## Brief Summary

This conversation focused on: With the flight controller and design system already mature, this is a small but important hardening pass: lock in legal FSM transitions, add a reduce-motion setting, and add the test sweeps that catch regressions. VERIFIED PRE-CONDITIONS - OverlayPresenterFs… Likely related git changes: b2a9b67 P-MOTION-1: Audio/bubble state assertions, reduce-motion, I….

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `1c887705f26c7827a64370f1bdb3761ee05e9b57`
- Likely related commits:
  - `b2a9b67` 2026-05-27 11:30:59 IST [medium] P-MOTION-1: Audio/bubble state assertions, reduce-motion, I…. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt, app/build.gradle.kts, app/src/debug/AndroidManifest.xml, app/src/debug/kotlin/com/handy/app/benchmark/FlightBenchmarkReceiver.kt, app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt, app/src/main/kotlin/com/handy/app/overlay/BuddyFlightCancellationPolicy.kt, app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenterFsm.kt, app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt

## Conversation

### USER 2026-05-27T05:35:32.473Z

GOAL
With the flight controller and design system already mature, this is a
small but important hardening pass: lock in legal FSM transitions, add a
reduce-motion setting, and add the test sweeps that catch regressions.

VERIFIED PRE-CONDITIONS
- OverlayPresenterFsmTest exists at app/src/test/kotlin/com/handy/app/overlay/.
- BezierFlightController has reduce-motion fallback via
  `reduceMotionEnabled: () -> Boolean = { !ValueAnimator.areAnimatorsEnabled() }`.
- Accessibility content descriptions + liveRegion are already wired in
  the V2 widget per the recent design migration.

IMPLEMENTATION

1. FSM legality assertion:
   File: app/.../overlay/OverlayPresenterFsm.kt (new)
   Encode the legal transition table:
       Docked → Listening | Thinking
       Listening → Thinking | Docked
       Thinking → ResponseVisible | Error
       ResponseVisible → AudioSpeaking | PreparingPoint | Docked
       AudioSpeaking → ResponseVisible | Docked
       PreparingPoint → Flying | Cancelled
       Flying → Pointing | Cancelled
       Pointing → ActionConfirm | Cancelled | Docked
       ActionConfirm → Acting | Cancelled
       Acting → ActionResult | Cancelled
       ActionResult → Docked
       Any → Cancelled → Docked
   In OverlayPresenter, wrap every state mutation in an
   `attemptTransition(next: BuddyState)` that consults this table.
   Illegal transitions log Timber.w + are dropped (don't throw — we don't
   want a UI bug to crash the service).

2. Reduce-motion setting:
   File: core/.../Settings.kt
       val reduceBuddyMotion: Boolean = false
   File: app/.../widget/BezierFlightController.kt
   - The existing reduceMotionEnabled function already exists; AND it
     with the setting:
         reduceMotionEnabled = { !ValueAnimator.areAnimatorsEnabled() ||
                                 settings.current().reduceBuddyMotion }

3. Cancellation tests:
   File: app/src/test/kotlin/com/handy/app/overlay/BuddyFlightDriverCancellationTest.kt
   Cover:
   - orientation change mid-flight → cancelIfStaleTarget("configuration_changed")
     (already in code at FloatingWidgetOverlayService.kt:342-343).
   - IME open mid-flight → re-resolve or cancel.
   - foreground package change mid-flight → cancelIfStaleTarget.
   - user drag mid-flight → cancel.
   - new voice turn while pointer is sticky → cancel.
   - accessibility service disconnect mid-flight → cancel.

4. Macrobenchmark:
   File: macrobenchmark/.../FlightJankBenchmark.kt
   - Drive a long-press → assistant point flow.
   - Assert jank < 5% (Compose's StartupBenchmark style).

ACCEPTANCE
- Illegal transitions logged but suppressed; legal ones execute.
- Reduce-motion = on → flight uses crossFadeThere path; no Bezier curve.
- Rotating mid-flight → pointer returns to dock, no stuck bubble.
- macrobench passes <5% jank target on Pixel 9.

DO NOT
- Redesign any visual.
- Touch contentDescription wiring (already shipped).
- Change BuddyState ordinals.

UPDATE
- DEBUG_LOG.md. 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS

### ASSISTANT 2026-05-27T05:35:38.612Z

I’ll harden this in the existing Android codebase and keep the scope tight: state legality, reduce-motion setting, cancellation coverage, macrobench, and DEBUG_LOG. I’ll start by reading the relevant overlay, flight, settings, and test files so the changes match the current architecture.

### ASSISTANT 2026-05-27T05:35:52.325Z

I see the repo has an untracked `handy_macos_ref/` already present; I’ll leave that alone. There isn’t a top-level `macrobenchmark/` directory, so I’ll inspect the Gradle layout before deciding whether to add the benchmark under an existing module or create the expected module shape.

### ASSISTANT 2026-05-27T05:36:20.686Z

The existing presenter currently enforces flight FSM legality with `require`, which is the opposite of the requested production behavior. I’m going to replace that with a BuddyState legality gate that logs and drops illegal UI state changes, then keep any flight FSM checks compatible where they already exist.

### ASSISTANT 2026-05-27T05:42:40.372Z

I’ve got the shape now. The important compatibility point is that `BuddyState` has fewer concrete states than the product FSM in the prompt, so I’m encoding the table against the existing enum with documented aliases instead of adding enum values or changing ordinals.

### ASSISTANT 2026-05-27T05:44:16.755Z

Next I’m wiring the reduce-motion preference through the settings model and DataStore, then the overlay service will observe it as a cached boolean so the animation callback stays synchronous and doesn’t block the main thread.

### ASSISTANT 2026-05-27T05:46:25.429Z

I’m adding the cancellation sweep as policy-level tests around the same decision helper the driver now calls. That keeps the unit tests fast and JVM-friendly while still locking down the exact stale-target decisions for rotation, IME, package changes, drag, sticky pointer voice turns, and accessibility disconnect.

### ASSISTANT 2026-05-27T05:49:15.423Z

The benchmark module is going in as a normal `com.android.test` macrobenchmark module, with AndroidX Benchmark 1.4.1 from the current stable release notes. I’m adding a custom trace metric so the test fails when frame timeline jank crosses 5%, instead of only recording the number.

### ASSISTANT 2026-05-27T05:50:26.948Z

I’m at the validation pass now. I’ll start with the affected JVM tests and macrobenchmark compilation; if the AndroidX benchmark APIs differ from memory, this is where we’ll catch it and tighten the benchmark code before the full build.

### ASSISTANT 2026-05-27T05:53:58.489Z

The targeted tests and macrobenchmark Kotlin compile are green after two small Gradle/API fixes: the benchmark plugin is unnecessary for a `com.android.test` module, and the JUnit4 rule lives under `androidx.benchmark.macro.junit4`. I’m moving to the full build/test/lint sweep now.

### ASSISTANT 2026-05-27T05:55:50.226Z

Full unit tests, androidTest compilation, macrobenchmark assembly, lint, and debug APK assembly are green. I’m doing one last runtime smoke if a device/emulator is attached, then I’ll update the debug log with the implementation impact and a practical manual test matrix.

### ASSISTANT 2026-05-27T05:57:04.872Z

One more useful hardening for the benchmark: a real macrobenchmark cannot speak into Android STT, so I’m adding a debug-only benchmark receiver that cancels the synthetic long-press voice session and fires the same `flyToPoint` driver path. It is excluded from release builds and keeps the benchmark deterministic.

### ASSISTANT 2026-05-27T06:00:37.630Z

Implemented the flight hardening pass.

Changed:
- Added Buddy FSM legality in [OverlayPresenterFsm.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayPresenterFsm.kt). Illegal transitions now `Timber.w` and drop instead of crashing.
- Wired `reduceBuddyMotion` through [Settings.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/model/Settings.kt), DataStore, Settings UI, and the flight controller callback.
- Added cancellation policy coverage in [BuddyFlightDriverCancellationTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/test/kotlin/com/handy/app/overlay/BuddyFlightDriverCancellationTest.kt), including rotate, IME, package change, drag, sticky voice turn, and accessibility disconnect.
- Added reduced-motion regression coverage in [BezierFlightControllerReduceMotionTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/test/kotlin/com/handy/app/widget/BezierFlightControllerReduceMotionTest.kt).
- Added `:macrobenchmark` with [FlightJankBenchmark.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/macrobenchmark/src/main/kotlin/com/handy/macrobenchmark/FlightJankBenchmark.kt), using AndroidX Benchmark `1.4.1` per the official [AndroidX Benchmark release notes](https://developer.android.com/jetpack/androidx/releases/benchmark).
- Updated [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md) with before/after examples and a detailed manual test plan.

Practical app impact:
Before, a bad overlay mutation like `Docked -> Flying` could crash in FSM enforcement paths. Now it logs and leaves Buddy safely docked. Before, users had to disable Android-wide animations to avoid Buddy’s flight arc. Now “Reduce Buddy motion” makes only Handy use the cross-fade path.

Validation passed:
```bash
git diff --check
./gradlew --no-daemon :core:test :android-runtime:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :macrobenchmark:assembleDebug :app:lintDebug :app:assembleDebug --stacktrace
```

Runtime smoke:
Installed the debug APK on `emulator-5554`, launched onboarding, confirmed the Handy process stayed alive, and logcat showed no `FATAL EXCEPTION`, `AndroidRuntime`, Handy ANR, force-finish, or Handy process-death entry.

Not run here: Pixel 9 macrobenchmark execution and the physical rotate/IME/accessibility-disconnect manual sweep. The exact Pixel 9 command and manual matrix are recorded in `DEBUG_LOG.md`.
