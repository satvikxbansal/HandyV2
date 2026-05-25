# Fix-B: Fix mute capability reporting

## Metadata

- Thread ID: `019e4f8e-5017-7341-a763-7f3fae308480`
- Created: 2026-05-22 17:29:47 IST
- Updated: 2026-05-22 17:36:40 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/22/rollout-2026-05-22T17-29-47-019e4f8e-5017-7341-a763-7f3fae308480.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `df10120d6315dd88fe221e53985bd5a2d9cffbf9`
- Messages exported: 12

## Brief Summary

This conversation focused on: SwitchingActionPerformer.gesturesAllowed(snapshot) does not pass Likely related git changes: ac1a079 FIX-B — Make SwitchingActionPerformer consult the mute clock for capability reporting.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `df10120d6315dd88fe221e53985bd5a2d9cffbf9`
- Likely related commits:
  - `ac1a079` 2026-05-22 17:37:23 IST [high] FIX-B — Make SwitchingActionPerformer consult the mute clock for capability reporting. Files: DEBUG_LOG.md, app/src/main/kotlin/com/handy/app/accessibility/SwitchingActionPerformer.kt, app/src/test/kotlin/com/handy/app/accessibility/SwitchingActionPerformerMuteCapabilityTest.kt

## Conversation

### USER 2026-05-22T11:59:50.286Z

Goal: SwitchingActionPerformer.gesturesAllowed(snapshot) does not pass
nowEpochMs, so the mute clock isn't checked at the capability layer.
The action is still rejected by the policy engine, but Diagnostics
and ActionPerformer.capabilities report "open" while muted. Fix the
capability surface to match the policy reality.

Files to touch:
- app/src/main/kotlin/com/handy/app/accessibility/SwitchingActionPerformer.kt
- (none else; the gate signature already supports it)

Required changes:
- In the settings.flow.collectLatest block, compute
  `ActionExecutionGate.gesturesAllowed(snapshot, nowEpochMs =
  System.currentTimeMillis())`.
- Re-publish gesturesEnabled on a periodic tick (every 60s) so the
  flag drops back to false when the mute expires without an
  intervening settings change. Use a tickerFlow or a
  `kotlinx.coroutines.flow.flow { while (true) { delay(60_000); emit(Unit) } }`.
- Update SwitchingActionPerformer.capabilities to return noop.capabilities
  whenever gesturesEnabled = false.
- Add a unit test SwitchingActionPerformerMuteCapabilityTest:
  * When settings.tapForMeMutedUntilEpochMs > now → capabilities ==
    noop.capabilities.

Do NOT:
- Touch the policy engine.
- Move mute-checking out of the gate (engine still needs it).

RCA log entry:
- Title: "DL-### SwitchingActionPerformer.capabilities lied about mute"
- Root cause: The gate's single-arg overload predates the mute clock
  in P2. SwitchingActionPerformer was not updated when P2 shipped, so
  capability reporting and the policy engine disagreed during a mute
  window. Behaviour was safe (policy rejected), but observability was
  misleading.
- Prevention rule: every consumer of ActionExecutionGate must pass
  nowEpochMs; the no-arg overload should be removed in a follow-up
  refactor.

Tests: ./gradlew :app:test 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-22T11:59:57.305Z

I’ll make the requested scoped change and inspect the existing gate/perfomer tests first so the new test fits the repo’s style. Then I’ll run `./gradlew :app:test` and chase down anything that shakes loose.

### ASSISTANT 2026-05-22T12:00:10.765Z

I found the stale call in `SwitchingActionPerformer`: it still uses the one-arg gate overload, and `capabilities` is keyed off the performer toggle rather than the refreshed gesture flag. I’m reading the surrounding contracts and test helpers now so the fix lands narrowly.

### ASSISTANT 2026-05-22T12:01:23.356Z

The debug log is append-only and currently ends at `DL-067`, so I’ll add `DL-068` for this RCA after the code/test change. The implementation can stay compact: keep the latest settings snapshot, recompute with the explicit clock on settings changes, and recompute again from a 60-second coroutine tick.

### ASSISTANT 2026-05-22T12:01:26.390Z

I’m going to edit only the switching performer for production logic, add the focused unit test, and append the required RCA entry. I’ll leave the policy engine untouched.

### ASSISTANT 2026-05-22T12:01:56.687Z

The code now tracks the latest settings snapshot and recomputes the gesture gate on both settings updates and a one-minute ticker. Next I’m running the new targeted test first, then the full requested `:app:test`.

### ASSISTANT 2026-05-22T12:02:22.363Z

The first Gradle attempt hit the machine’s missing default Java runtime, not a code failure. I’m switching to the repo/session JDK that prior validations used and rerunning the same targeted test.

### ASSISTANT 2026-05-22T12:03:08.625Z

The focused mute capability regression test is green. I’m running the full requested app test task now with the same JDK env so compile, Hilt/KSP, and both debug/release unit variants get exercised.

### ASSISTANT 2026-05-22T12:03:41.768Z

`./gradlew :app:test` is green. I’m doing one extra packaging-oriented pass (`:app:assembleDebug`) plus a diff hygiene check because this touches an injected app singleton and I want the handoff to be boring in the best way.

### ASSISTANT 2026-05-22T12:04:18.279Z

Packaging is green too. I found `adb` outside PATH, so I’m checking whether a device/emulator is attached for a quick launch smoke; if none is connected I’ll call that out rather than pretending we exercised runtime startup.

### ASSISTANT 2026-05-22T12:04:40.796Z

The emulator smoke passed: debug APK installed, `OnboardingActivity` launched, the process stayed alive, and fresh logcat had no `AndroidRuntime`/fatal Handy crash matches. I’m appending the RCA entry now with the exact validation trail.

### ASSISTANT 2026-05-22T12:06:40.393Z

Implemented.

Changed [SwitchingActionPerformer.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/accessibility/SwitchingActionPerformer.kt:44) so it now:
- passes `nowEpochMs = System.currentTimeMillis()` into `ActionExecutionGate.gesturesAllowed(...)`
- keeps the latest settings snapshot and refreshes the gate every 60s
- returns `noop.capabilities` whenever `gesturesEnabled` is false

Added [SwitchingActionPerformerMuteCapabilityTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/test/kotlin/com/handy/app/accessibility/SwitchingActionPerformerMuteCapabilityTest.kt:20), covering: muted future timestamp means `capabilities == noop.capabilities`.

Added RCA entry `DL-068` in [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1122).

**Before / After**
Before: a user could mute Tap-for-me, and actual actions were still safely rejected by policy, but the capability surface could look open. Practically: Diagnostics or tool capability reporting could imply “tap/type/scroll available” while Handy would refuse the action at execution time.

After: during mute, `ActionPerformer.capabilities` exposes the noop surface, so observers and callers see the same reality as policy: gestures are unavailable. When the mute expires, the 60s tick refreshes the flag without needing the user to toggle a setting.

**Validation**
- `./gradlew :app:test` passed using the local JDK at `/Users/satvik.bansal/.cache/codex-jdk17`
- targeted new test passed
- `git diff --check` passed
- `./gradlew :app:assembleDebug` passed
- installed debug APK on `emulator-5554`, launched `OnboardingActivity`, process stayed alive, and fresh logcat had no `AndroidRuntime` / `FATAL EXCEPTION` / Handy crash matches

**Manual Testing Plan**
1. Install debug APK and enable Accessibility, overlay, action disclosure, and Tap-for-me.
2. Confirm baseline unmuted state: Settings/Diagnostics should show Tap-for-me allowed; a benign tap-for-me request should reach normal confirmation/policy flow.
3. Use Settings → Tap-for-me panic mute for 1 hour.
4. Immediately check Diagnostics/capability UI: Tap-for-me may remain enabled as a preference, but gesture/action capability should appear muted/closed.
5. From overlay chat and full chat, ask Handy to tap/type/scroll a harmless visible target. Expect no dispatched gesture while muted.
6. Check audit/log output: action should fail closed; no gesture dispatch should be recorded.
7. Leave the app process alive until the mute expires; within the next 60s tick, capability reporting should reopen without changing settings.
8. Repeat with app backgrounded then foregrounded during mute.
9. Kill and relaunch while still muted; initial settings collection should recompute closed.
10. Turn Tap-for-me off/on after mute; verify settings updates still take effect immediately and no crashes appear in logcat.

I left the policy engine untouched. The pre-existing untracked `handy_macos_ref/` folder was also left alone.
