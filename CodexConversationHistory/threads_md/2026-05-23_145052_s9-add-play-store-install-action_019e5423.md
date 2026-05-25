# S9: Add Play Store install action

## Metadata

- Thread ID: `019e5423-2ed8-7903-970b-7a53b98e8d86`
- Created: 2026-05-23 14:50:52 IST
- Updated: 2026-05-23 15:04:28 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T14-50-52-019e5423-2ed8-7903-970b-7a53b98e8d86.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `9a3b522b86a93b8d5ae3bc4b101b7ff5d4cc3ca0`
- Messages exported: 13

## Brief Summary

This conversation focused on: Add AssistantAction.InstallApp and InstallAppRecipe that opens the Play Store listing for a given package or search query. We never auto-install — the user taps Install. FILES TO KNOW - core/src/main/kotlin/com/handy/core/action/AssistantA… Likely related git changes: c7027a5 S9: Add playstore install action.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `9a3b522b86a93b8d5ae3bc4b101b7ff5d4cc3ca0`
- Likely related commits:
  - `c7027a5` 2026-05-23 15:16:03 IST [high] S9: Add playstore install action. Files: DEBUG_LOG.md, android-runtime/src/androidTest/kotlin/com/handy/runtime/intent/AndroidIntentDispatcherInstallAppTest.kt, android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/InstallAppRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt, android-runtime/src/main/kotlin/com/handy/runtime/llm/HandyToolRunner.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/InstallAppRecipeTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt, core/src/main/kotlin/com/handy/core/action/AssistantAction.kt, core/src/main/kotlin/com/handy/core/llm/AvailableTools.kt

## Conversation

### USER 2026-05-23T09:20:54.395Z

Read the standing rules. Single-pass.

GOAL
Add AssistantAction.InstallApp and InstallAppRecipe that opens the
Play Store listing for a given package or search query. We never
auto-install — the user taps Install.

FILES TO KNOW
- core/src/main/kotlin/com/handy/core/action/AssistantAction.kt
- android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt
  (look at OpenUrl handler for fallback pattern)
- android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt
  (confirm play.google.com / market: URLs are not blocked by
  isPaymentUrl / isBetaBlocked. If they ARE, whitelist them
  explicitly in this prompt.)
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/AndroidRuntimeRecipes.kt
- core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt

IMPLEMENT
- AssistantAction: add
    @Serializable @SerialName("install_app")
    data class InstallApp(val packageHint: String? = null,
                          val searchQuery: String? = null) : AssistantAction() {
      init { require(!packageHint.isNullOrBlank() || !searchQuery.isNullOrBlank()) {
        "InstallApp needs packageHint or searchQuery" } }
    }
- AndroidIntentDispatcher: handle InstallApp:
    * packageHint → Intent(ACTION_VIEW, market://details?id=PACKAGE);
      catch ActivityNotFoundException → fall back to
      https://play.google.com/store/apps/details?id=PACKAGE via
      chooser.
    * else searchQuery → market://search?q=QUERY&c=apps with same
      https fallback.
- Add InstallAppRecipe: single-step Proposed with InstallApp action.
  displayName "Open Play Store"; summary "Show ${target} on Play Store".
  Refuse on missing both fields with "missing-app".
- Register in AndroidRuntimeRecipes.defaultRecipes().
- Router: INSTALL_APP → InstallAppRecipe.id.
- PromptCatalog: add "install spotify → [INTENT:install_app]".
- If DefaultActionPolicyEngine blocks Play Store URLs by accident,
  add explicit whitelist function isPlayStoreUrl(url) and exempt
  it from isPaymentUrl / isBetaBlocked.

Tests:
- InstallAppRecipeTest: packageHint → market URL; searchQuery →
  market search; missing both → Refused.
- Extend DefaultActionPolicyEngineTest: InstallApp allowed with
  NORMAL.
- AndroidIntentDispatcher unit test (if a test fake exists for it,
  otherwise integration smoke): falls back to https URL when market:
  not resolvable.

Update docs/ACTION_POLICY.md per-recipe table: add InstallApp row
(NORMAL, MEDIUM risk, blocks any in-app purchase).

SELF-VERIFY
./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug

STOP CONDITIONS (revert + report only)
- AssistantAction has serialization compatibility tests that the new
  case would break.

DEBUG_LOG ENTRY: standard.

### ASSISTANT 2026-05-23T09:21:03.456Z

I’ll read the repo guidance and the target files first, then make the narrow action/recipe/test/doc changes and run the requested Gradle verification.

### ASSISTANT 2026-05-23T09:21:11.056Z

I found the debug log and cursor-style rule references, plus the target symbols. I’m going to skim the standing rules and existing prevention entries now so the implementation lines up with the project’s house style.

### ASSISTANT 2026-05-23T09:21:50.319Z

Plan:
- Phase: Phase 4 hardening / action-policy + deterministic recipe layer.
- Acceptance check advanced: explicit `install_app` intent opens a visible Play Store flow, never installs silently, policy-gated as a normal visible intent, with recipe/router/prompt/docs/tests updated.
- Files I expect to touch: `AssistantAction.kt` for the serialized action; `AndroidIntentDispatcher.kt` for Play Store market/HTTPS fallback; `DefaultActionPolicyEngine.kt` only if Play Store URLs are accidentally blocked; recipe/router/prompt files for deterministic selection; runtime/core tests; `docs/ACTION_POLICY.md`; `DEBUG_LOG.md`.
- Interfaces I will not change: recipe runner contract, action performer/tap-for-me APIs, launchable app index behavior, Gradle/module boundaries.
- Prevention rules in scope: DL-072 requires new recipe families to register `RecipeIntent`/router mapping; DL-074 says enum/action additions must update dispatcher, recipe matcher/policy/schema-style surfaces together; DEBUG_LOG requires a same-change entry before completion. I’ll catch regressions with the requested Gradle sweep and focused new tests.

### ASSISTANT 2026-05-23T09:24:29.188Z

I’ve confirmed there are no existing AssistantAction serialization-compatibility tests, so the stop condition is clear. One nuance I found: Play Store URLs can be falsely caught by the generic payment-word URL block if a package/search term contains “payment”, so I’ll add the explicit Play Store URL exemption too.

### ASSISTANT 2026-05-23T09:25:14.409Z

I’m going to add the action and dispatcher path first, including the explicit Play Store URL whitelist in policy. Then I’ll wire the recipe/prompt/docs and drop in the focused tests.

### ASSISTANT 2026-05-23T09:29:44.433Z

The main wiring is in place now: the new action exists, dispatcher opens `market://` first with HTTPS chooser fallback, and policy treats install handoff as a medium-risk normal-confirmation action. I’m checking the diff for exhaustiveness and then I’ll run focused tests before the full Gradle sweep.

### ASSISTANT 2026-05-23T09:30:13.806Z

There’s no Java on the default PATH, matching the existing DL-074 note, so I’m using the repo-local JDK at `/Users/satvik.bansal/.cache/codex-jdk17` for all Gradle checks.

### ASSISTANT 2026-05-23T09:30:22.614Z

That first focused command was Gradle syntax, not code: `:android-runtime:test` is an aggregate Android task and doesn’t accept `--tests`. I’m rerunning against `testDebugUnitTest`, which is the actual unit-test task.

### ASSISTANT 2026-05-23T09:31:08.397Z

One small tightening before the full sweep: I’m making the recipe honor the exact contract that package/search must come from the recipe args. That way an empty invocation refuses with `missing-app` instead of trying to infer from prose.

### ASSISTANT 2026-05-23T09:31:30.766Z

Focused unit checks and the dispatcher instrumentation-smoke compile are green. I’m running the full requested verification now; this is the one that matters for handoff.

### ASSISTANT 2026-05-23T09:33:09.897Z

There is an emulator connected, so I’m going to run the dispatcher fallback smoke as an actual instrumentation test too, not just compile it.

### ASSISTANT 2026-05-23T09:34:28.641Z

Implemented the InstallApp flow end to end.

Added `AssistantAction.InstallApp`, the Play Store recipe, runtime registration, prompt/schema coverage, policy handling, docs, and DEBUG_LOG entry. The dispatcher now opens `market://details` or `market://search` first, then falls back to the HTTPS Play Store URL via chooser if the market intent is unavailable. Policy treats InstallApp as `MEDIUM` risk with `NORMAL` confirmation and exempts Play Store listing/search URLs from the payment-word URL block.

Key files:
- [AssistantAction.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/action/AssistantAction.kt:36)
- [InstallAppRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/InstallAppRecipe.kt:13)
- [AndroidIntentDispatcher.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt:235)
- [DefaultActionPolicyEngine.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt:102)
- [ACTION_POLICY.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/docs/ACTION_POLICY.md:106)

Verified:
- `./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug` passed with repo-local JDK.
- Dispatcher fallback instrumentation smoke passed on `emulator-5554`.
- `git diff --check` passed.
- Stop condition not hit: no AssistantAction serialization compatibility tests were present.

Note: pre-existing untracked `handy_macos_ref/` is still untouched.

Advanced Phase 4 / action-policy + deterministic recipes — acceptance check: InstallApp opens visible Play Store handoff, never auto-installs — status: PASS.
