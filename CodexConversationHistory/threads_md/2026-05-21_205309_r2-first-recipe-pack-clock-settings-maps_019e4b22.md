# R2: First recipe pack: Clock + Settings + Maps

## Metadata

- Thread ID: `019e4b22-240f-7743-80bb-60926ca0ed01`
- Created: 2026-05-21 20:53:09 IST
- Updated: 2026-05-21 21:09:38 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/21/rollout-2026-05-21T20-53-09-019e4b22-240f-7743-80bb-60926ca0ed01.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `892dd4db52eef172717c1666275302691e916f23`
- Messages exported: 11

## Brief Summary

This conversation focused on: ship the three lowest-risk recipes. No likely related git commits were found for this thread. It may have been planning/review-only, debugging without a commit, or committed as part of a neighboring bundled thread.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `892dd4db52eef172717c1666275302691e916f23`
- Likely related commits: none found

## Conversation

### USER 2026-05-21T15:23:17.170Z

Goal: ship the three lowest-risk recipes.

Files to touch (new):
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/AndroidSettingsRecipe.kt
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/MapsRecipe.kt

Notes:
- Recipes match on package + (optional) windowTitle / mark patterns.
- No hardcoded coordinates. Match by viewId/text/role from
  GroundingSnapshot.
- ClockRecipe initial flow: "set alarm at HH:MM" → AlarmClock
  intent FIRST; only fall through to a UI walk if the intent is
  unavailable.

Acceptance:
- "Set a 7am alarm" succeeds via intent on stock + Samsung;
  "Turn on dark mode" via SettingsRecipe deep-link.
- AndroidSettingsRecipe never toggles HIGH-risk settings (network,
  biometric, accessibility) — those route to
  PolicyDecision(allowed=false, reason="settings-too-sensitive").

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS. 

Tests:
- Snapshot-based recipe unit tests in :core (recognizes / propose);
  execution tested via instrumented in :app.

### ASSISTANT 2026-05-21T15:23:24.885Z

I’ll trace the existing recipe and grounding patterns first, then add the three files in the same style and run the relevant Gradle checks so we catch compile trouble early. After that I’ll give you the before/after and a practical manual test pass.

### ASSISTANT 2026-05-21T15:23:38.872Z

There are already several unrelated modified files in the workspace, so I’ll leave those alone and keep this work scoped to the new recipe files plus any absolutely necessary registration/test touchpoints if the project requires them. I’ve found the core recipe abstractions and am reading those next.

### ASSISTANT 2026-05-21T15:24:25.725Z

The current registry only knows the generic core recipes, so the three new runtime recipe classes will need to plug into that path somehow; I’m checking the project plan notes now to keep the shape aligned with the intended v2 architecture.

### ASSISTANT 2026-05-21T15:27:58.275Z

I’ve got the shape now: the runner currently only knows tap/type/scroll, so intent-first recipes need one small core command path for “native Android action” plus the three runtime recipe objects. I’m going to keep UI actions node-based, route Settings high-risk targets through policy, and wire the app registry to include the runtime pack.

### ASSISTANT 2026-05-21T15:33:06.175Z

The first implementation pass is in: native actions are now a recipe command, app execution uses the Android intent dispatcher, Settings has explicit sensitive-target denial, and the three runtime recipes are present. I’m going into compile/test now; this is where small API mismatches usually show themselves.

### ASSISTANT 2026-05-21T15:33:23.711Z

Gradle hit an environment issue before touching Kotlin: macOS can’t locate a Java runtime from the shell. I’m going to look for the project’s/Android Studio’s bundled JDK and rerun with `JAVA_HOME` pointed at it rather than treating that as a code failure.

### ASSISTANT 2026-05-21T15:35:10.453Z

Core tests, runtime unit tests, and the debug app build are green with `JAVA_HOME` set to the bundled OpenJDK 17. I added one instrumented test file too, so I’m compiling the Android test APK now to make sure that side has no hidden import or runner issues.

### ASSISTANT 2026-05-21T15:35:37.667Z

The test APK compiles too, and there’s an emulator attached. I’m going to run the narrow new instrumented recipe execution test on it; that gives us an actual device-side sanity check without creating alarms or changing settings on the emulator.

### ASSISTANT 2026-05-21T15:36:02.591Z

The instrumented test found a JUnit shape issue in the new test itself, not app code: the Kotlin expression-bodied `runBlocking` method compiled to a non-void signature. I’m making that test a normal block-bodied JUnit method and rerunning the same narrow device test.

### ASSISTANT 2026-05-21T15:39:38.516Z

Implemented the three lowest-risk recipes and wired them into execution.

**What Changed**
Before: recipes could only tap/type/scroll visible UI, so “set a 7am alarm” or “open dark mode settings” had to rely on generic LLM tool behavior or brittle UI guidance.

After:
- [ClockRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt:22) parses `7am`, `07:00`, `7:30 PM`, etc. and dispatches `AlarmClock.ACTION_SET_ALARM` via `AssistantAction.SetAlarm`.
- [AndroidSettingsRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/AndroidSettingsRecipe.kt:14) opens safe Settings deep-links like dark theme/display settings.
- [MapsRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/MapsRecipe.kt:13) dispatches Maps search, and navigation only when explicitly requested.
- [RecipeRunner.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt:12) now supports native intent recipe steps without hardcoded coordinates.
- [DefaultActionPolicyEngine.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt:60) blocks sensitive Settings targets with `reason="settings-too-sensitive"`.

Practical example: “Set a 7am alarm” now becomes a trusted recipe step that asks Android’s Clock app to set 7:00 AM, which is much safer across stock Android and Samsung than tapping through clock UI. “Turn on Wi-Fi” routes to policy denial instead of opening or toggling network settings.

**Verification**
Passed:
- `:core:test`
- `:android-runtime:testDebugUnitTest`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:assembleDebugAndroidTest`
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.handy.app.agent.RecipeNativeActionExecutionTest` on `Pixel_9_Pro(AVD) - 15`

I used the local OpenJDK 17 path because the shell’s default Java runtime was not registered.

**Manual Test Plan**
1. Install debug build on stock Android emulator/device and one Samsung device.
2. Enable Handy accessibility, overlay, and action disclosure.
3. Ask: “Set a 7am alarm for me.” Confirm the recipe plan. Verify Clock opens and an alarm for 7:00 AM is created or presented by the OS Clock handler.
4. Repeat with “Set an alarm at 19:45” and “Set a 7:30pm alarm called gym.”
5. Ask: “Turn on dark mode for me.” Confirm Handy opens the dark theme/display Settings deep-link. Verify it does not use coordinates.
6. Ask high-risk Settings requests: “turn on wifi,” “open accessibility settings,” “change fingerprint.” Expected: no Settings launch; policy denial shows `settings-too-sensitive`.
7. Ask: “Search maps for coffee near me.” Confirm Maps opens with a search result/query.
8. Ask: “Navigate to airport.” Verify Handy uses the navigation action and requires the stronger confirmation path.
9. Rotate device / change display size and repeat one Clock, one Settings, one Maps flow to confirm no coordinate dependency.
10. Check Diagnostics policy tail/audit: successful native actions are logged as intent-backed; sensitive Settings attempts are denied.

Note: the workspace had unrelated modified files before I started; I left those alone.
