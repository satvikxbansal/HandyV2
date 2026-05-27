# S2: Add TimerRecipe for StartTimer

## Metadata

- Thread ID: `019e548f-412e-7eb0-868c-14e23c3ecf0c`
- Created: 2026-05-23 16:48:55 IST
- Updated: 2026-05-23 16:56:20 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T16-48-55-019e548f-412e-7eb0-868c-14e23c3ecf0c.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `c7027a5a4ab74d5db0c6ff072309d118406af69e`
- Messages exported: 15

## Brief Summary

This conversation focused on: Wrap AssistantAction.StartTimer in a TimerRecipe so "set a 10-minute timer" follows the plan-approval flow. FILES TO KNOW - android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt (mirror structure) - core/src/main/kotlin/com/handy/core… Likely related git changes: 1c44ee5 S2: Add TimerRecipe for StartTimer.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `c7027a5a4ab74d5db0c6ff072309d118406af69e`
- Likely related commits:
  - `1c44ee5` 2026-05-23 16:57:25 IST [high] S2: Add TimerRecipe for StartTimer. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/TimerRecipe.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/TimerRecipeTest.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt, core/src/test/kotlin/com/handy/core/prompts/PromptCatalogTest.kt

## Conversation

### USER 2026-05-23T11:18:56.794Z

Read the standing rules. Single-pass.

GOAL
Wrap AssistantAction.StartTimer in a TimerRecipe so "set a 10-minute
timer" follows the plan-approval flow.

FILES TO KNOW
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt
  (mirror structure)
- core/src/main/kotlin/com/handy/core/action/AssistantAction.kt
  (StartTimer)
- android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt
  (StartTimer handler must already fire AlarmClock.ACTION_SET_TIMER)

Look at how ClockRecipe parses times. If extracting a duration parser
is justified (>50 lines of shared logic), put it in
core/src/main/kotlin/com/handy/core/agent/parsing/DurationParser.kt
with its own table-driven test. Otherwise inline a small parser
inside TimerRecipe.

IMPLEMENT
- Add android-runtime/.../recipes/TimerRecipe.kt:
  * propose() pulls "seconds" arg or parses goal.text.
  * Refuses if seconds <= 0 or > 24*3600 with "timer-out-of-range".
  * Refuses if no duration found with "missing-duration".
  * displayName "Set timer"; single-step NativeAction StartTimer.
- Add tests covering: "10-minute timer", "1 hour 30 minutes",
  "in 5 mins", "30 second timer", "timer for two hours", missing
  duration, out-of-range. Document any case the parser does not
  cover in KDoc.
- Register in AndroidRuntimeRecipes.defaultRecipes().
- Router: SET_TIMER → TimerRecipe.id.
- PromptCatalog: add intent example.

If you decide on a shared DurationParser, also add it to
DateTimeParser used by S-8 — keep the contract narrow.

SELF-VERIFY
./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug

STOP CONDITIONS (revert + report only)
- StartTimer's seconds field is not Int (e.g. it's Long or
  Duration) and your parser shape is incompatible.

DEBUG_LOG ENTRY: standard.

### ASSISTANT 2026-05-23T11:19:04.597Z

I’ll first find the repo’s standing rules and the timer/clock plumbing, then I’ll make the smallest scoped change and run the requested Gradle verification once.

### ASSISTANT 2026-05-23T11:19:17.866Z

I found `StartTimer` is already `seconds: Int`, so the stop condition doesn’t apply. I’m reading the existing recipe shape, router, prompt tests, and DEBUG_LOG prevention rules before editing.

### ASSISTANT 2026-05-23T11:20:25.914Z

The clock recipe’s time parsing is intentionally tiny and local. There isn’t existing shared date/time duration code, so I’m keeping the timer parser inline with a narrow KDoc instead of adding a core parser.

### ASSISTANT 2026-05-23T11:20:45.244Z

**Plan**

Goal: make timer requests such as “set a 10-minute timer” become an agent recipe proposal, so the normal plan approval flow runs before `AssistantAction.StartTimer`.

Files I’ll touch:
- `android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/TimerRecipe.kt`: new one-step recipe with a small inline duration parser.
- `android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt`: register `TimerRecipe` in `AndroidRuntimeRecipes.defaultRecipes()`.
- `android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/TimerRecipeTest.kt`: requested parser/proposal cases.
- `android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt`: pack includes `set_timer`.
- `core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt` and its test: add the recipe intent example.
- `DEBUG_LOG.md`: append the standard entry before I call it done.

Interfaces I will not change:
- `AssistantAction.StartTimer(seconds: Int, label: String?)` stays as-is.
- `AndroidIntentDispatcher.fireTimer` stays on `AlarmClock.ACTION_SET_TIMER`.
- No shared `DurationParser` or `DateTimeParser` change; there is no existing shared date/time parser here, and the timer parser is not shared logic.

Relevant prevention rules:
- DL-072: new recipe families go through `RecipeIntent`/router mapping first; `SET_TIMER` is already mapped to `set_timer`, so the recipe id will match that contract.
- DL-076: serialized action, dispatcher, prompt, recipe registration, and tests need to land together for a canonical recipe action.
- Standing rules: keep it surgical and validate with the requested Gradle command.

Phase / section / acceptance: Phase 2 runtime recipes, `HANDY_NEXT_LEVEL_PLAN` Prompt R2 clock/intent recipe flow. Acceptance is that timer requests propose a single native `StartTimer` action and route through plan approval while still dispatching through stock Android timer intent.

### ASSISTANT 2026-05-23T11:21:40.147Z

I’m going to add the recipe and tests now. The parser will only handle whole-number English timer durations (`hour`, `minute`, `second` units, numeric or small number words), which is enough for the requested cases without turning this into a general natural-language time library.

### ASSISTANT 2026-05-23T11:23:00.783Z

One small refinement before I verify: I’m tightening the number-word table so unsupported compound words don’t get partially interpreted. That keeps the parser honest instead of accidentally reading “twenty five hours” as “five hours.”

### ASSISTANT 2026-05-23T11:24:04.241Z

The code is in place. I’m doing a whitespace sanity check and confirming the Java runtime path, then I’ll run the requested Gradle verification as the main pass.

### ASSISTANT 2026-05-23T11:24:07.911Z

Plain `java -version` isn’t available on PATH, matching the earlier repo history. I’ll use the repo-local JDK path for the Gradle verification so the actual requested tasks can run.

### ASSISTANT 2026-05-23T11:24:13.548Z

Now I’m running the full requested verification with `JAVA_HOME` pointed at the local JDK: `:core:test`, `:android-runtime:test`, `:app:test`, lint, and debug assembly.

### ASSISTANT 2026-05-23T11:24:27.374Z

The first verification pass caught a Kotlin initializer-order compile error in the new parser constants. I’m fixing that directly by moving the generated number-word map above the regex that uses it, then I’ll rerun the same verification command.

### ASSISTANT 2026-05-23T11:24:36.908Z

Initializer order is corrected. Re-running the requested Gradle suite now.

### ASSISTANT 2026-05-23T11:25:18.679Z

The full suite passed after the initializer-order fix. I’m doing the final diff/whitespace check and appending the required DEBUG_LOG entry now.

### ASSISTANT 2026-05-23T11:25:50.033Z

DEBUG_LOG is appended. I’m doing one last status/diff sanity pass; no more code changes after this unless something surprising shows up.

### ASSISTANT 2026-05-23T11:26:20.674Z

Implemented. `AssistantAction.StartTimer` is now wrapped by [TimerRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/TimerRecipe.kt:13), with `TimerRecipe.ID = "set_timer"` matching the existing `SET_TIMER` router mapping. It parses `seconds` args first, falls back to simple duration parsing, refuses `missing-duration` and `timer-out-of-range`, and proposes one native `StartTimer` step for plan approval.

Registered it in [ClockRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt:21), added the prompt example in [PromptCatalog.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt:276), added requested coverage in [TimerRecipeTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/TimerRecipeTest.kt:16), and appended [DL-077](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1267). I kept the parser inline and documented its unsupported cases in KDoc.

Verification passed with the repo-local JDK:

`JAVA_HOME=/Users/satvik.bansal/.cache/codex-jdk17 PATH=/Users/satvik.bansal/.cache/codex-jdk17/bin:$PATH ./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug`

`git diff --check` also passed. Pre-existing untracked `handy_macos_ref/` was left untouched.

Advanced Phase 2 / Prompt R2 — acceptance check: timer recipe via plan approval — status: PASS.
