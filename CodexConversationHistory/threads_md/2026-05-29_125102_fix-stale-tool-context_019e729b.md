# Fix stale tool context

## Metadata

- Thread ID: `019e729b-a2ac-7910-9f79-e186740c6856`
- Created: 2026-05-29 12:51:02 IST
- Updated: 2026-05-29 13:04:57 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/29/rollout-2026-05-29T12-51-02-019e729b-a2ac-7910-9f79-e186740c6856.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `18a6bfe1da7e9e5369a74189b12cc976427e6d00`
- Messages exported: 12

## Brief Summary

This conversation focused on: Fix stale tool context Likely related git changes: d6e556f Fixed tool context in Main Chat window.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `18a6bfe1da7e9e5369a74189b12cc976427e6d00`
- Likely related commits:
  - `d6e556f` 2026-05-29 13:09:17 IST [medium] Fixed tool context in Main Chat window. Files: app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt, app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt, app/src/main/kotlin/com/handy/app/tutor/TutorModeController.kt, app/src/test/kotlin/com/handy/app/chat/ChatViewModelVoiceTest.kt, app/src/test/kotlin/com/handy/app/foreground/HandyForegroundAppMonitorTest.kt, core/src/main/kotlin/com/handy/core/foreground/ForegroundAppMonitor.kt

## Conversation

### USER 2026-05-29T07:23:44.587Z

# Files mentioned by the user:

## Screenshot 2026-05-29 at 12.50.47 PM.png: /Users/satvik.bansal/Desktop/Screenshot 2026-05-29 at 12.50.47 PM.png

## My request for Codex:
All good so far except the tool context in the main chat window. when no app is opened, it doesnt show any context bar there as expected. but if i open an app, open handy on that, then kill that app and kill handy too but open handy again, the context of the last opened app is still retained despite that app not being active anymore. can you deeply review this inplementation and fix this properly? evaluate the current code and all possible scenarios in extreme depth. be very thorough. ensure we are being comprehensive and fixing this properly. ensure no other functional aspects are changing and no new bugs are being instroduced. deeply audit the neighbouring services too after the code for chanbges - especially the inplmentation of the tool context in the chat overlay window. we dont want to ruin existing work as a lot fo effort has gone into it. 

<image name=[Image #1]>
</image>

### ASSISTANT 2026-05-29T07:23:55.876Z

I’ll trace how the “tool context” is sourced, cached, and rendered in both the main chat and overlay paths, then patch the narrowest place that owns the stale lifecycle. I’ll be careful around neighboring services so we preserve the working behavior when an app is genuinely active.

### ASSISTANT 2026-05-29T07:24:05.186Z

I’ve found the likely surface: the full chat has a `ToolNameBar` and the overlay has its own cached-at-tap `PanelSnapshot` path. Next I’m narrowing the ownership boundary so the main chat doesn’t keep rendering a historical foreground target after that target has gone away.

### ASSISTANT 2026-05-29T07:25:22.717Z

The smell is pretty clear now: the foreground monitor can clear its internal “last app” and it already sends `null` to the overlay-panel refresher, but the main chat’s public foreground stream is non-null only. That means a replayed Gmail snapshot can be delivered to `ChatViewModel`, and the later “no app visible” clear has no way to reach the main chat.

### ASSISTANT 2026-05-29T07:27:50.788Z

I’m going to make the foreground stream itself nullable, so “there is no active app to chat about” becomes a first-class state instead of an implied absence of events. Then the main chat can hide the context pill on that explicit clear, while overlay handoff snapshots remain frozen when they’re supposed to be.

### ASSISTANT 2026-05-29T07:29:47.597Z

The code changes are in place now. I added regression coverage for both sides of the contract: the monitor’s replay cache must become `null` after launcher/home, and the full chat must clear stale context unless it is deliberately bound to an overlay handoff snapshot.

### ASSISTANT 2026-05-29T07:30:01.355Z

The first test run is blocked by the local shell not seeing a Java runtime. I’m going to look for an installed Android Studio/JBR runtime and rerun Gradle with `JAVA_HOME` pointed at it if it exists.

### ASSISTANT 2026-05-29T07:32:20.791Z

There is a Gradle-managed JDK cached under `~/.gradle/jdks`, so I can still verify without changing the machine. I’m rerunning the focused suite with `JAVA_HOME` set to that JDK.

### ASSISTANT 2026-05-29T07:33:06.578Z

The nullable stream exposed one compile site I hadn’t seen in the first search: tutor mode listens to the concrete monitor flow and expects only real app snapshots. I’m patching that collector to ignore `null`, which preserves its existing behavior exactly.

### ASSISTANT 2026-05-29T07:33:46.204Z

Focused coverage is green now. Since this touches a shared foreground contract, I’m going one step wider and running the app unit-test suite so we catch any neighboring regressions outside the exact classes I touched.

### ASSISTANT 2026-05-29T07:34:17.014Z

The full app unit-test suite passed. I’m also checking `:core` and `:android-runtime` test tasks because the foreground monitor interface lives in `:core`, and I want the module boundary covered too.

### ASSISTANT 2026-05-29T07:34:57.263Z

Fixed the stale context bug at the source.

Root cause: the foreground monitor cleared its internal `lastSnapshot`, and the overlay already received that `null`, but the main chat’s public stream could only replay non-null snapshots. So `ChatViewModel` could reopen and see the old Gmail snapshot even after the active app was gone.

Changed:
- [ForegroundAppMonitor.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/foreground/ForegroundAppMonitor.kt:48): foreground stream now supports `null` as “no active app context.”
- [HandyForegroundAppMonitor.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt:51): one shared nullable replay stream now feeds both main chat and overlay context refresh.
- [ChatViewModel.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt:163): main chat clears back to default/hidden context on `null`, while still ignoring foreground changes during explicit overlay handoff.
- [TutorModeController.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/tutor/TutorModeController.kt:83): tutor idle nudges cancel on `null` foreground instead of acting on stale app context.

Added regression coverage for replay clearing, main chat clearing, and handoff preservation in:
- [HandyForegroundAppMonitorTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/test/kotlin/com/handy/app/foreground/HandyForegroundAppMonitorTest.kt:153)
- [ChatViewModelVoiceTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/test/kotlin/com/handy/app/chat/ChatViewModelVoiceTest.kt:136)

Verification passed:
- `:app:testDebugUnitTest` focused suite for foreground/chat/overlay
- `:app:testDebugUnitTest`
- `:core:test`
- `:android-runtime:testDebugUnitTest`

I left the unrelated untracked `handy_macos_ref/` folder untouched.
