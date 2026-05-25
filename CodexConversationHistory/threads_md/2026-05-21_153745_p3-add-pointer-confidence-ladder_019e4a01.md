# P3: Add pointer confidence ladder

## Metadata

- Thread ID: `019e4a01-6219-7f01-8f28-d8d28e1463f4`
- Created: 2026-05-21 15:37:45 IST
- Updated: 2026-05-21 15:52:40 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/21/rollout-2026-05-21T15-37-45-019e4a01-6219-7f01-8f28-d8d28e1463f4.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `9bf3b0c7531180e8befd1a6b6fefd08f7ad918e8`
- Messages exported: 9

## Brief Summary

This conversation focused on: ladder pointer behaviour by confidence; offer alternatives Likely related git changes: bddd08e P2+P3: Support candidate options and audit review.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `9bf3b0c7531180e8befd1a6b6fefd08f7ad918e8`
- Likely related commits:
  - `bddd08e` 2026-05-21 15:54:29 IST [low] P2+P3: Support candidate options and audit review. Files: android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt, app/src/androidTest/kotlin/com/handy/app/diagnostics/AuditReviewActivityTest.kt, app/src/main/AndroidManifest.xml, app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt, app/src/main/kotlin/com/handy/app/diagnostics/AuditReviewActivity.kt, app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt, app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt, app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPanelBridge.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt

## Conversation

### USER 2026-05-21T10:08:01.759Z

Goal: ladder pointer behaviour by confidence; offer alternatives
when ambiguous; let the user correct by voice without a new LLM
round trip.

Files to touch:
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
  (apply tier ladder)
- app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt
- (new) app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt
- core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt
  (CandidateOptions field)
- (new) core/src/main/kotlin/com/handy/core/agent/CorrectionIntent.kt
  + tiny classifier matching "no", "other one", "next", "previous",
  "the popup one"
- app/src/main/kotlin/com/handy/app/voice/VoiceController.kt
  (route corrections while POINTING)

Acceptance:
- Manual: ambiguous screen → chip row appears (0.4-0.7 band);
  tapping a chip flies to that target.
- Sticky pointing → "no, the other Continue" hops to the runner-up
  candidate from ResolvedPointTarget.debugCandidates.

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   

Tests:
- CorrectionIntentTest.

### ASSISTANT 2026-05-21T10:08:09.475Z

I’ll trace the existing overlay and voice flow first, then wire the ladder in the smallest shape that matches the current architecture. After that I’ll add the classifier test and give you the before/after plus a practical manual pass.

### ASSISTANT 2026-05-21T10:08:20.124Z

There are unrelated local changes already in the worktree, so I’ll leave those alone and only touch the requested overlay, voice, and core files plus the new test. I’ve found the main classes and am reading their actual state transitions now.

### ASSISTANT 2026-05-21T10:10:09.836Z

The existing sticky pointer already has a “wrong one?” manual fallback, but it doesn’t yet carry the resolver’s runner-up list forward. I’m going to make the candidate list first-class overlay state, then let both chip taps and correction phrases reuse the same “fly to candidate” path.

### ASSISTANT 2026-05-21T10:12:56.632Z

I’ve got the shape now: high confidence flies as before, middle/ambiguous results publish candidate options instead of guessing, and low confidence keeps falling back to manual selection. I’m starting the state/model edits, then I’ll wire the UI and voice correction path.

### ASSISTANT 2026-05-21T10:18:52.459Z

The UI path is wired now: ambiguous candidates render as a touchable overlay row near the pointer, while the old manual fallback stays available when there aren’t explicit alternatives. I’m tightening the correction flow and then I’ll run the focused test/build checks.

### ASSISTANT 2026-05-21T10:19:54.888Z

The code shape looks consistent on review. I’m running the new core classifier test first, then I’ll compile the app unit-test target to catch Compose/Hilt/Kotlin wiring issues.

### ASSISTANT 2026-05-21T10:20:06.897Z

The focused test didn’t start because this shell can’t see a Java runtime. I’m going to look for the Android Studio/Gradle JDK that’s often installed alongside Android projects and rerun with `JAVA_HOME` if it’s present.

### ASSISTANT 2026-05-21T10:22:40.874Z

Implemented the confidence ladder and correction path.

**What Changed**
Before: when Handy saw ambiguous targets like two visible `Continue` buttons, it either refused the pointer and fell into manual fallback, or landed on one target with no lightweight way to correct it. Saying “no, the other Continue” during sticky pointing would become a normal voice turn and could trigger another LLM round trip.

After:
- `>= 0.70` confidence: Handy flies as before, but stores runner-up candidates for correction.
- `0.40 - 0.70` or resolver `AMBIGUOUS`: Handy shows candidate chips from `ResolvedPointTarget.debugCandidates`.
- `< 0.40`: Handy keeps the existing manual fallback path.
- Tapping a chip flies directly to that candidate.
- While sticky pointing, voice phrases like `no`, `other one`, `next`, `previous`, and `the popup one` are classified locally and routed to the stored candidates without submitting to the LLM.

Key files:
- [BuddyFlightDriver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt)
- [OverlayPanelState.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt)
- [CandidateChipsBar.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt)
- [CorrectionIntent.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/CorrectionIntent.kt)

I also touched `FloatingWidgetOverlayService`, `OverlayPanelBridge`, and `ChatViewModel` so chips actually mount and consumed correction transcripts do not continue into chat submission.

**Manual Test Plan**
1. Build/install debug, grant overlay, accessibility, and microphone permissions.
2. Open a screen with one clear target, e.g. one `Continue` button. Ask Handy to point at it. Expected: no chip row, Buddy flies normally.
3. Open a screen with two similar targets, e.g. page `Continue` and popup `Continue`. Ask Handy to point to `Continue`. Expected: chip row appears near Buddy in the ambiguous band.
4. Tap each candidate chip. Expected: Buddy flies to that chip’s corresponding target; selected chip updates.
5. While Buddy is sticky-pointing at the first `Continue`, long-press Buddy and say `no, the other Continue`. Expected: Buddy hops to the runner-up candidate, no panel stream starts.
6. Repeat with `next`, `previous`, and `the popup one`. Expected: local candidate hop each time.
7. Test a very weak/unclear target. Expected: no candidate chips; existing `Wrong one?` manual fallback appears.
8. Confirm normal voice still works when Buddy is not pointing. Expected: transcript opens/submits through the normal overlay chat path.
9. Confirm tap-for-me still requires confirmation after a confident landing. Expected: no automatic tap bypass.
10. Rotate screen or open keyboard during pointing. Expected: stale target cancellation still returns Buddy safely.

**Verification**
- `git diff --check` passed.
- I added [CorrectionIntentTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/test/kotlin/com/handy/core/agent/CorrectionIntentTest.kt), but could not run Gradle tests because this shell has no Java runtime: `Unable to locate a Java Runtime.`
