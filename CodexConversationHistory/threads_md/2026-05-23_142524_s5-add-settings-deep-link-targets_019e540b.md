# S5: Add settings deep-link targets

## Metadata

- Thread ID: `019e540b-dce7-76f1-8f1b-706d7f9243e0`
- Created: 2026-05-23 14:25:24 IST
- Updated: 2026-05-23 14:33:09 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T14-25-24-019e540b-dce7-76f1-8f1b-706d7f9243e0.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `9a3b522b86a93b8d5ae3bc4b101b7ff5d4cc3ca0`
- Messages exported: 14

## Brief Summary

This conversation focused on: Add RINGTONE, DND, BRIGHTNESS, SCREEN_TIMEOUT to SettingsTarget so common Settings requests have a deterministic deep-link. None of these toggle anything — they only open the screen. FILES TO KNOW - core/src/main/kotlin/com/handy/core/acti… Likely related git changes: 9a3b522 Add deterministic open app recipe; 325d239 S5.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `9a3b522b86a93b8d5ae3bc4b101b7ff5d4cc3ca0`
- Likely related commits:
  - `9a3b522` 2026-05-23 14:22:07 IST [medium] Add deterministic open app recipe. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/OpenAppRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/intent/LaunchableAppIndex.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/OpenAppRecipeTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt, app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt, core/src/main/kotlin/com/handy/core/agent/RecipeIntent.kt, core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt, core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt, core/src/main/kotlin/com/handy/core/agent/UserGoal.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  - `325d239` 2026-05-23 14:50:58 IST [high] S5. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/AndroidSettingsRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/AndroidSettingsRecipeTest.kt, core/src/main/kotlin/com/handy/core/action/AssistantAction.kt, core/src/main/kotlin/com/handy/core/llm/AvailableTools.kt, core/src/test/kotlin/com/handy/core/llm/AvailableToolsTest.kt

## Conversation

### USER 2026-05-23T08:55:25.727Z

Read the standing rules. Single-pass.

GOAL
Add RINGTONE, DND, BRIGHTNESS, SCREEN_TIMEOUT to SettingsTarget so
common Settings requests have a deterministic deep-link. None of
these toggle anything — they only open the screen.

FILES TO KNOW
- core/src/main/kotlin/com/handy/core/action/AssistantAction.kt
- android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/AndroidSettingsRecipe.kt
- android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt
  (the isTooSensitiveForSettingsRecipe() function — your new targets
  must land in the `false` arm explicitly, never `true`)

Grep: every persisted reference to SettingsTarget ordinals (DataStore,
JSON history). Append the new enum values at the end to preserve
ordinals.

IMPLEMENT
- AssistantAction.SettingsTarget: append RINGTONE, DND, BRIGHTNESS,
  SCREEN_TIMEOUT with @SerialName values
  ("ringtone", "dnd", "brightness", "screen_timeout").
- AndroidIntentDispatcher: add when-branches:
    RINGTONE -> Intent(Settings.ACTION_SOUND_SETTINGS)
    DND -> Intent(Settings.ACTION_ZEN_MODE_SETTINGS)  // API 23+
    BRIGHTNESS -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
    SCREEN_TIMEOUT -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
  Add the same chooser-fallback pattern existing targets use.
- AndroidSettingsRecipe: add keyword matchers:
    "ringtone" / "sound" → RINGTONE
    "do not disturb" / "dnd" / "silent mode" → DND
    "brightness" → BRIGHTNESS
    "screen timeout" / "sleep" / "screen off" → SCREEN_TIMEOUT
- DefaultActionPolicyEngine.isTooSensitiveForSettingsRecipe:
  add the 4 new targets to the `false` arm explicitly.

Tests:
- Extend (or create) AndroidSettingsRecipeTest with the 4 new
  keyword resolutions.
- Extend DefaultActionPolicyEngineTest: each of the 4 new targets is
  allowed.

SELF-VERIFY
./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug

STOP CONDITIONS (revert + report only)
- A persisted DataStore field uses SettingsTarget by ordinal AND the
  ordering cannot be preserved.

DEBUG_LOG ENTRY: standard.

### ASSISTANT 2026-05-23T08:55:35.537Z

I’ll read the local rules and the relevant code/tests first, then make the enum and routing changes in one focused pass before running the requested Gradle verification.

### ASSISTANT 2026-05-23T08:55:45.770Z

I see one untracked `handy_macos_ref/` directory already present, so I’ll leave it alone. The first scan found the enum is string-serialized in action schemas/history paths, and I’m narrowing that now to confirm there’s no ordinal persistence trap.

### ASSISTANT 2026-05-23T08:56:19.626Z

The Settings recipe currently refuses unknown screens and the policy blocks only five sensitive targets. I’ve also spotted the advertised `dispatch_action` schema has a hand-written settings enum, so I’ll update that too so the serialized target names are actually callable.

### ASSISTANT 2026-05-23T08:57:21.914Z

**Plan**

Phase: Phase 7 deterministic app recipes, Prompt R2 / Android Settings recipe expansion. Acceptance check advanced: AndroidSettingsRecipe opens safe Settings deep-links and never toggles high-risk settings; new common Settings screens resolve deterministically to native Settings intents.

Files I’ll touch and why:
- `core/src/main/kotlin/com/handy/core/action/AssistantAction.kt`: append the four serialized enum values at the end.
- `android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt`: map the new targets to Settings intents.
- `android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/AndroidSettingsRecipe.kt`: add the keyword resolvers and labels.
- `android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt`: put the four new targets explicitly in the allowed `false` arm.
- `android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/AndroidSettingsRecipeTest.kt`: focused keyword-resolution coverage.
- `android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt`: allowed-policy coverage for the four targets.
- `core/src/main/kotlin/com/handy/core/llm/AvailableTools.kt` and its test if needed: keep the advertised `dispatch_action` schema in sync with the serialized settings enum.
- `DEBUG_LOG.md`: append the requested standard entry.

Interfaces I will not change: no `AssistantAction.OpenSettings` shape change, no recipe runner API change, no policy decision model change, no DataStore/JSON history schema change, and no Settings toggling behavior.

Persistence check so far: `DataStoreSettings` stores its own settings enums by `.name`, and `JsonHistoryStore` persists `ChatMessage`/`ConversationTurn`, not `SettingsTarget` ordinals. The only `.ordinal` hit is unrelated risk ordering. I’ll preserve enum ordinal compatibility by appending only.

Risks and checks: the main risks are missed exhaustive `when` arms, schema drift, and policy misclassification. I’ll catch those with the requested Gradle suite plus focused tests for recipe resolution, policy allowlisting, and the tool schema if I touch it.

### ASSISTANT 2026-05-23T08:57:51.421Z

I’m going to make the enum append-only and keep the recipe/policy changes deliberately boring: just new branches, no new abstractions. I’ll also sync the tool schema because DL-012’s prevention rule applies: advertised tool input must match executable action shapes.

### ASSISTANT 2026-05-23T08:58:52.738Z

The implementation is in place, including the schema sync and targeted tests. I’m doing a quick diff sanity check now, then I’ll run the full verification command exactly as requested.

### ASSISTANT 2026-05-23T08:59:06.029Z

The plain `./gradlew …` command immediately hit the local Java-runtime launcher error, so I’m checking the repo’s known JDK path and will rerun the same Gradle tasks with `JAVA_HOME`/`PATH` set.

### ASSISTANT 2026-05-23T08:59:45.656Z

The first compile caught a platform constant mismatch: this SDK does not expose `Settings.ACTION_ZEN_MODE_SETTINGS` to Kotlin even though that action string exists on Android. I’m switching that one branch to the documented action string constant locally, then I’ll rerun the same verification.

### ASSISTANT 2026-05-23T09:00:51.001Z

The rerun is green with the local JDK. I’m appending the requested DEBUG_LOG entry now, including the compile catch around the hidden DND action constant so the next person doesn’t step on that rake.

### ASSISTANT 2026-05-23T09:01:38.901Z

One small polish before I wrap: I’m making the schema test parse JSON instead of matching a raw substring, so it fails for the right reason if the advertised enum drifts.

### ASSISTANT 2026-05-23T09:02:02.308Z

The parsed-schema test tripped on this repo’s kotlinx serialization version lacking the `content` property import. I’m switching it to the already-used `contentOrNull` helper and rerunning; tiny snag, clean fix.

### ASSISTANT 2026-05-23T09:02:27.197Z

The final suite is green again. Because I fixed a real compile error in the test import after the log entry, I’m appending one more DEBUG_LOG entry rather than silently folding it into the previous one.

### ASSISTANT 2026-05-23T09:03:09.917Z

Implemented the SettingsTarget expansion.

Added append-only enum values in [AssistantAction.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/action/AssistantAction.kt:125), wired dispatcher branches in [AndroidIntentDispatcher.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt:125), added recipe keyword resolution in [AndroidSettingsRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/AndroidSettingsRecipe.kt:65), and put all four new targets explicitly in the policy `false` arm in [DefaultActionPolicyEngine.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt:202).

Also synced the `dispatch_action` schema, added focused recipe tests plus policy allow tests, and appended `DEBUG_LOG.md` entries `DL-074` and `DL-075`.

Verification:
`./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug` first failed because no Java runtime was on PATH. Rerun with the repo-local JDK passed:
`JAVA_HOME=/Users/satvik.bansal/.cache/codex-jdk17 PATH=/Users/satvik.bansal/.cache/codex-jdk17/bin:$PATH ./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug`

Persisted ordinal grep found no `SettingsTarget` ordinal references. `git diff --check` passed. The pre-existing untracked `handy_macos_ref/` remains untouched.

Advanced Phase 7 / Prompt R2 — acceptance check: Android Settings recipe safe deep-links — status: PASS.
