# CO1 : Add coexistence test pack

## Metadata

- Thread ID: `019e4ed4-036d-7022-ae79-140dc424e930`
- Created: 2026-05-22 14:06:17 IST
- Updated: 2026-05-22 14:15:02 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/22/rollout-2026-05-22T14-06-17-019e4ed4-036d-7022-ae79-140dc424e930.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `1cdfb151c8fefda96513309e1414fc8619e30136`
- Messages exported: 13

## Brief Summary

This conversation focused on: a documented test pack the team runs before every beta. No likely related git commits were found for this thread. It may have been planning/review-only, debugging without a commit, or committed as part of a neighboring bundled thread.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `1cdfb151c8fefda96513309e1414fc8619e30136`
- Likely related commits: none found

## Conversation

### USER 2026-05-22T08:36:23.732Z

Goal: a documented test pack the team runs before every beta.

Files to touch (new):
- docs/COEXISTENCE_TESTS.md (TalkBack on/off, Switch Access on/off,
  large font, large display, high contrast, reduce motion, haptics
  off, RTL, Hindi system locale, gesture nav + 3-button nav, split
  screen, foldable; one row per case with pass/fail criteria)
- app/src/androidTest/kotlin/com/handy/app/CoexistenceSmokeTests.kt
  (automated subset: TalkBack-state listener wiring, RTL layout
   smoke, large-font smoke)

Acceptance:
- Manual run on at least Pixel + one OEM device produces a complete
  pass/fail table. 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-22T08:36:34.146Z

I’ll inspect the Android project structure and existing test patterns first, then add the doc plus instrumentation smoke tests in the local style. After that I’ll run the most relevant Gradle checks so we can catch compile or test wiring issues before handing it back.

### ASSISTANT 2026-05-22T08:36:39.507Z

I found an existing `AccessibilityStateMonitor` and several AndroidJUnit4 instrumentation tests, so I can keep the new smoke tests close to the current project conventions. The repo already has docs under `docs/`, and there’s one unrelated untracked `handy_macos_ref/` directory that I’ll leave alone.

### ASSISTANT 2026-05-22T08:37:25.577Z

The automated part can stay lightweight and deterministic: one test exercises the accessibility monitor listener object directly, and two Compose smoke tests render a representative Handy surface under RTL and 1.8x font scale. That gives us beta guardrails without needing privileged test writes to Android secure settings.

### ASSISTANT 2026-05-22T08:39:47.195Z

I’m about to add two new files only: the beta coexistence test pack in `docs/` and the instrumentation smoke class under `app/src/androidTest`. The test code will avoid privileged system-setting mutations, so it should compile and run on normal CI/device lanes.

### ASSISTANT 2026-05-22T08:41:15.396Z

The two files are in place. I’m moving into the audit pass now: first compile the new androidTest source, then build the debug app/test APKs, and if anything shakes loose I’ll fix it in-place.

### ASSISTANT 2026-05-22T08:41:31.277Z

Gradle didn’t get as far as compiling because the shell can’t find a Java runtime. I’m checking the usual Android Studio bundled JBR paths next so I can run the same Gradle audit with an explicit `JAVA_HOME` instead of stopping on the environment.

### ASSISTANT 2026-05-22T08:42:10.589Z

There isn’t a JDK in the normal system or Android Studio locations so far. I’m doing a slightly broader local search before considering a temporary local JDK download; the code audit itself is clean enough to keep moving, but I want an actual compiler result if the machine gives us one.

### ASSISTANT 2026-05-22T08:42:35.909Z

Found the Android Studio bundled JBR under the user Applications folder. I’ll rerun Gradle with `JAVA_HOME` pointed there; that should give us the real compile/build signal.

### ASSISTANT 2026-05-22T08:43:18.878Z

The main compile/build audit passed: debug app and debug androidTest APK both assembled successfully, with only an existing deprecation warning in an unrelated OS test. I’m doing one more practical check now: unit tests plus whether any Android device is attached for connected smoke execution.

### ASSISTANT 2026-05-22T08:43:45.181Z

There is an emulator attached (`emulator-5554`), so I can run the new connected smoke tests instead of only compiling them. I’ll run just `CoexistenceSmokeTests` to keep the device audit focused on the new pack.

### ASSISTANT 2026-05-22T08:44:10.654Z

The new connected smoke pack ran green on the attached Pixel 9 Pro AVD: 3 tests, build successful. I’m just doing the final status/diff check now so the wrap-up is precise and doesn’t accidentally claim anything about the unrelated untracked folder.

### ASSISTANT 2026-05-22T08:45:02.350Z

Implemented the coexistence beta pack.

Added [docs/COEXISTENCE_TESTS.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/docs/COEXISTENCE_TESTS.md:1) with the before/after impact, automated subset command, common beta script, full pass/fail table, and practical end-to-end manual plan. The table covers TalkBack on/off, Switch Access on/off, large font/display, high contrast, reduce motion, haptics off, RTL, Hindi locale, gesture/3-button nav, split screen, and foldable.

Added [CoexistenceSmokeTests.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/androidTest/kotlin/com/handy/app/CoexistenceSmokeTests.kt:66) with the automated subset:
- TalkBack/accessibility-state listener wiring smoke
- RTL layout smoke
- Large-font 1.8x layout smoke

Before: beta checks could pass on a default Pixel while missing real-world setups like TalkBack focus, Hindi wrapping, or OEM Switch Access scanning. After: every beta has a repeatable Pixel + OEM pass/fail gate, plus automated smoke coverage for the riskiest deterministic pieces.

Verification passed:
- `:app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest`
- `:app:testDebugUnitTest`
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.handy.app.CoexistenceSmokeTests`

Connected run completed green: 3 tests on `Pixel_9_Pro(AVD) - 15`. No build errors or automated-lane app crashes observed. The remaining acceptance step is the actual manual Pixel + OEM device run using the new table. Unrelated untracked `handy_macos_ref/` was left untouched.
