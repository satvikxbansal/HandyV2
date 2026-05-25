# Fix-E: Skip system overlay taps

## Metadata

- Thread ID: `019e4fa4-3e1d-7be2-a7f9-452c81dfa15e`
- Created: 2026-05-22 17:53:44 IST
- Updated: 2026-05-22 18:04:06 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/22/rollout-2026-05-22T17-53-44-019e4fa4-3e1d-7be2-a7f9-452c81dfa15e.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `72b969e944ad7befc7b65e32b36da02630351da7`
- Messages exported: 12

## Brief Summary

This conversation focused on: Skip Android system overlays too (status bar, nav bar, system UI) Likely related git changes: 0a8f0a3 FIX-E: Tighten ManualTargetSelector package skip-list.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `72b969e944ad7befc7b65e32b36da02630351da7`
- Likely related commits:
  - `0a8f0a3` 2026-05-22 18:05:14 IST [high] FIX-E: Tighten ManualTargetSelector package skip-list. Files: DEBUG_LOG.md, app/src/androidTest/kotlin/com/handy/app/overlay/ManualTargetSelectorTest.kt, app/src/main/kotlin/com/handy/app/overlay/ManualTargetSelector.kt, app/src/test/kotlin/com/handy/app/overlay/ManualTargetSelectorSkipListTest.kt

## Conversation

### USER 2026-05-22T12:28:11.637Z

Goal: Skip Android system overlays too (status bar, nav bar, system UI)
in addition to Handy's own package.

Files to touch:
- app/src/main/kotlin/com/handy/app/overlay/ManualTargetSelector.kt

Required changes:
- In handleAccessibilityEvent: skip when sourcePackage.equals(...)
  any of: appPackageName, "com.android.systemui", "android",
  "com.android.launcher3", and any package starting with
  "com.google.android.inputmethod" or "com.android.inputmethod".
- Extract the skip-list to a companion-object Set<String> for clarity.
- Add a unit test ManualTargetSelectorSkipListTest:
  * captureNodeForTest with sourcePackage="com.android.systemui"
    while active returns true (acknowledged) but does NOT capture.

Do NOT:
- Use AccessibilityNodeInfo.packageName for the comparison (it's
  unreliable); stick with event.packageName.

RCA log entry:
- Title: "DL-### ManualTargetSelector accepted system-overlay taps"
- Root cause: Skip-list only excluded Handy's own package. Some OEM
  frameworks emit TYPE_VIEW_CLICKED from systemui windows when the
  user taps the status bar; selector would resume flight to that
  node.
- Prevention rule: any code that captures a node from a foreign
  package must explicitly enumerate the trustworthy packages, not
  just deny one.

Tests: ./gradlew :app:test 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-22T12:28:20.477Z

I’ll inspect the selector and test layout first, then make the smallest code/test change and run the app unit tests. I’ll also look for the repo’s RCA/log convention before adding that entry.

### ASSISTANT 2026-05-22T12:28:31.300Z

I found the existing instrumentation coverage and the debug log. Next I’m reading the selector and test scaffolding so the new skip-list test lands in the right test source set without fighting Android framework limitations.

### ASSISTANT 2026-05-22T12:29:35.304Z

I’m going to make the selector treat the Android/system overlay packages as “handled but not captured” while a manual selection is active. That keeps the selection mode alive when the user hits the status bar or keyboard area, instead of letting that system node become Buddy’s target.

### ASSISTANT 2026-05-22T12:30:14.346Z

The local unit test is in place, and I updated the existing instrumentation assertion to match the new “active selector acknowledged this event” behavior. I’m running the requested app unit test task now; if anything shakes loose, I’ll fix it before the RCA entry gets finalized.

### ASSISTANT 2026-05-22T12:30:23.923Z

The first Gradle invocation hit the host’s missing default Java runtime, which this repo has worked around before with its cached JDK 17. I’m switching to that pinned JDK path and rerunning the same task.

### ASSISTANT 2026-05-22T12:30:56.581Z

The requested `:app:test` task is green. I noticed the new test was noisy because `AccessibilityNodeInfo.recycle()` is deprecated in the mockable Android API, so I’m adding the same file-level suppression pattern this repo already uses around accessibility tests and then I’ll do a final verification pass.

### ASSISTANT 2026-05-22T12:31:31.350Z

The RCA entry is appended as `DL-071`. I’m doing a final build/test sweep now, including debug APK assembly, then I’ll check the working diff and device availability for a launch smoke test if an emulator is attached.

### ASSISTANT 2026-05-22T12:32:04.351Z

Whitespace diff checks are clean. `adb` is not on PATH, so I’m checking the usual Android SDK location before deciding whether a launch smoke test is available on this machine.

### ASSISTANT 2026-05-22T12:32:15.276Z

An emulator is attached, so I’m going one step further than compile-only: install the debug APK, cold-launch the onboarding activity, and scan fresh logcat for runtime crashes.

### ASSISTANT 2026-05-22T12:33:22.141Z

I made a tiny readability cleanup after the audit pass, so I’m rerunning the requested unit-test task once more against the final files.

### ASSISTANT 2026-05-22T12:34:06.545Z

Implemented the system-overlay skip fix.

Changed [ManualTargetSelector.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/ManualTargetSelector.kt) so manual target capture now ignores, using `event.packageName` only:

`com.handy.android`, `com.android.systemui`, `android`, `com.android.launcher3`, plus prefixes `com.google.android.inputmethod*` and `com.android.inputmethod*`.

Before: if the user entered “Tap the right one” mode and accidentally tapped the status bar, nav bar, launcher, or keyboard area, Handy could capture that system node and resume Buddy flight to the wrong place.

After: those taps are acknowledged while selection remains active, but no node is captured. Practical example: user tries to select a button in Gmail, brushes the status bar first, and Handy now waits for the real Gmail target instead of flying toward a SystemUI/status-bar node.

Added [ManualTargetSelectorSkipListTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/test/kotlin/com/handy/app/overlay/ManualTargetSelectorSkipListTest.kt), covering the requested `captureNodeForTest(..., "com.android.systemui")` case: returns true, stays active, does not capture. Also updated the existing instrumentation expectation for skipped own-package clicks.

Added RCA entry `DL-071 — ManualTargetSelector accepted system-overlay taps` in [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md).

**Verification**
- `./gradlew :app:test --stacktrace` passed.
- `./gradlew :app:test :app:assembleDebug --stacktrace` passed.
- `./gradlew :app:installDebug --stacktrace` installed on `emulator-5554`.
- Cold-launched `OnboardingActivity`: `Status: ok`, process stayed alive.
- Fresh logcat scan found no `AndroidRuntime`, `FATAL EXCEPTION`, `am_crash`, or Handy exception/error matches.
- `git diff --check` passed.

**Manual Test Plan**
1. Enable Handy accessibility and overlay permissions.
2. Open any app with a visible tappable target, for example Gmail “Compose” or Chrome search field.
3. Start manual target selection from the chip or widget long-press.
4. Tap the status bar, clock area, notification shade edge, navigation bar, launcher area, and keyboard area.
5. Confirm Handy does not pulse/capture those system areas and selection mode remains active.
6. Tap the real app target after each system tap and confirm Buddy captures that target normally.
7. Repeat with Gboard visible, then with the launcher visible.
8. Repeat in gesture nav and 3-button nav if available.
9. Watch logcat for `ManualTargetSelector: captured pkg=` and confirm captures only happen for the real foreground app package, not `com.android.systemui`, `android`, launcher, or input method packages.

Note: `handy_macos_ref/` was already untracked and I left it untouched.
