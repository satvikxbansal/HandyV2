# You are working on Handy Android (multi-module: :core, :android-runtime, :app).

## Metadata

- Thread ID: `019e53ec-e434-7611-b89b-1f51a3079d02`
- Created: 2026-05-23 13:51:34 IST
- Updated: 2026-05-23 14:04:44 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T13-51-34-019e53ec-e434-7611-b89b-1f51a3079d02.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `93754259560473853c03421ed3f1e0ba7089d498`
- Messages exported: 18

## Brief Summary

This conversation focused on: Before adding S-1..S-10 recipes, introduce a tiny intent-routing layer so that "open Spotify", "set a timer for 10 minutes", "search the web for X", "install X from Play Store", "remind me at 6 PM" each map to exactly one recipe without LL… Likely related git changes: 9a3b522 Add deterministic open app recipe.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `93754259560473853c03421ed3f1e0ba7089d498`
- Likely related commits:
  - `9a3b522` 2026-05-23 14:22:07 IST [medium] Add deterministic open app recipe. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/OpenAppRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/intent/LaunchableAppIndex.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/OpenAppRecipeTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt, app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt, core/src/main/kotlin/com/handy/core/agent/RecipeIntent.kt, core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt, core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt, core/src/main/kotlin/com/handy/core/agent/UserGoal.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt

## Conversation

### USER 2026-05-23T08:21:36.888Z

You are working on Handy Android (multi-module: :core, :android-runtime, :app).
Repo guardrails live under .cursor/rules/.

GOAL
Before adding S-1..S-10 recipes, introduce a tiny intent-routing layer
so that "open Spotify", "set a timer for 10 minutes", "search the web
for X", "install X from Play Store", "remind me at 6 PM" each map to
exactly one recipe without LLM-side ambiguity. Today RecipeRegistry
walks recipes in order and the first non-Refused wins; with 6 more
recipes that becomes a coin-flip when prompts overlap.

EVALUATION (DO NOT WRITE CODE YET — REPORT FINDINGS FIRST)
1. Open and re-read in full:
   - core/src/main/kotlin/com/handy/core/agent/UserGoal.kt
   - core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt
   - core/src/main/kotlin/com/handy/core/agent/AppRecipe.kt
   - core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt
   - android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/*.kt
2. Identify how UserGoal.fromAssistantText currently maps the LLM's
   directive (e.g. `[RECIPE:clock_alarm]`) to a recipe id.
3. Confirm there is no central "intent → recipe id" lookup today and
   that recipe selection happens by walking recipes in registry order.
4. Confirm that all existing recipes use `propose()` which returns
   either Refused(reason) or Proposed(plan).
5. Report back in chat: the file paths, the exact selection mechanism
   today, and your proposed minimal addition. Wait for confirmation
   before implementing.

IMPLEMENTATION (after evaluation is acknowledged)
Files to add:
- (new) core/src/main/kotlin/com/handy/core/agent/RecipeIntent.kt
- (new) core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt
- (new) core/src/test/kotlin/com/handy/core/agent/RecipeIntentRouterTest.kt

Files to touch:
- core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt
- core/src/main/kotlin/com/handy/core/agent/UserGoal.kt (only if a new
  helper is needed; otherwise leave alone)
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt (update
  the recipe instruction line to teach the LLM to emit the canonical
  intent token list)

What RecipeIntent looks like:
    enum class RecipeIntent(val canonical: String) {
        OPEN_APP("open_app"),
        SET_ALARM("set_alarm"),
        SET_TIMER("set_timer"),
        WEB_SEARCH("web_search"),
        INSTALL_APP("install_app"),
        OPEN_SETTING("open_setting"),
        CREATE_CALENDAR_EVENT("create_calendar_event"),
        DRAFT_GMAIL("draft_gmail"),
        DRAFT_WHATSAPP("draft_whatsapp"),
        OPEN_CHROME_URL("open_chrome_url"),
        SHOPPING_SEARCH("shopping_search"),
        SHOPPING_FIND_COUPONS("shopping_find_coupons"),
        BOOK_RIDE("book_ride"),
    }

What RecipeIntentRouter does:
- A single fun `routeOrNull(goal: UserGoal): Pair<RecipeIntent, AppRecipe>?`
- Has a static map: RecipeIntent → recipe id (read by the registry).
- Looks at `goal.requestedRecipe` and `goal.requestedIntent` (add
  this field to UserGoal if missing — it's just the canonical string
  the LLM emitted via [INTENT:foo]).
- Returns the (intent, recipe) pair or null.

Update RecipeRegistry.propose() to:
1. First consult the router. If the router resolves to a single
   recipe, use it directly.
2. Otherwise fall back to current behavior (walk recipes in order).

Update PromptCatalog: replace the current "[RECIPE:<id>]" hint with
"emit [INTENT:<canonical>] when the user clearly wants the deterministic
flow; the runner will pick the right recipe." Provide the canonical
list in the prompt.

Tests:
- RecipeIntentRouterTest covers:
  * Router returns the right recipe for every intent in the enum.
  * Router returns null for unknown intent.
  * Registry.propose falls back to legacy walk when router returns null.

DON'T BREAK
- Every existing AppRecipe must still propose() unchanged.
- Existing RecipeRunner tests must pass.
- Confirm with: ./gradlew :core:test :android-runtime:test :app:test

RCA log entry (DEBUG_LOG.md):
Title: "DL-### RecipeIntentRouter added to prevent recipe ambiguity
before 6 new recipes land"
Root cause: Original RecipeRegistry walked recipes in declaration
order; adding overlapping recipes (open_app vs install_app, alarm vs
timer) would have made selection brittle.
Prevention rule: any new recipe family added in the future must
register a RecipeIntent enum value first.

REPORT BACK after implementation: confirm all 5 universal invariants
plus this prompt's test, and paste the test output.

### ASSISTANT 2026-05-23T08:21:43.196Z

I’ll do the evaluation pass only first. I’m going to read the guardrails and the agent/recipe files, then I’ll report the current routing mechanics and the smallest change I’d make before touching code.

### ASSISTANT 2026-05-23T08:22:02.133Z

I see one unrelated untracked directory, `handy_macos_ref/`; I’ll leave it alone. Next I’m reading the rules and the exact files named in the prompt, with line numbers so the selection path is easy to verify.

### ASSISTANT 2026-05-23T08:22:20.348Z

The relevant guardrails here are the pure-`:core` boundary, the intent-dispatch rule against free-text regex inference, and the V2 “no multi-step runner” fence. This proposed router can stay tiny and deterministic inside `:core`, which fits that shape.

### ASSISTANT 2026-05-23T08:23:30.954Z

**Evaluation Findings**

I re-read the requested files:

- [UserGoal.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/UserGoal.kt)
- [RecipeRegistry.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt)
- [AppRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/AppRecipe.kt)
- [RecipeRunner.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt)
- Runtime recipes under [android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes)

Current mechanism:

- `UserGoal.fromAssistantText()` does **not** parse `[RECIPE:clock_alarm]` today. It parses the text form:
  `use recipe <recipe_id> with args {...}`
- The recipe id is extracted by `RecipeInvocation.directiveRegex` in [UserGoal.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/UserGoal.kt:87), then stored as `goal.requestedRecipe.recipeId`.
- [PromptCatalog.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt:262) currently teaches the LLM to emit that `use recipe <recipe_id> with args` directive.

One important correction: I **cannot confirm** that `RecipeRegistry` currently walks recipes in order and picks the first non-`Refused`. Current [RecipeRegistry.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt:13) does exact lookup by `requestedRecipe.recipeId` using `recipes.firstOrNull { it.id.equals(...) }`, then calls only that recipe’s `propose()`. Registry order only matters if duplicate ids exist.

I did confirm there is no central `intent -> recipe id` lookup today: no `RecipeIntent`, no `requestedIntent`, and no canonical intent routing layer. `RecipeIntentDispatcher` in [RecipeRunner.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt:205) is unrelated; it dispatches native actions during recipe execution.

All existing `AppRecipe` implementations use `propose(goal, invocation, grounding)` and return the sealed `RecipeProposal`: either `Proposed(plan)` or `Refused(reason)`.

**Proposed Minimal Addition**

Add a tiny canonical intent layer without changing existing recipe `propose()` implementations:

- Add `RecipeIntent` enum with the canonical strings.
- Add `requestedIntent: String?` to `UserGoal`, parsed from `[INTENT:foo]`, while preserving existing `requestedRecipe`.
- Add `RecipeIntentRouter`, backed by a static `RecipeIntent -> recipeId` map and the registry’s recipe list.
- Update `RecipeRegistry.propose()` to consult the router first, then fall back to the current exact recipe-id behavior.
- Update `PromptCatalog` so the model emits `[INTENT:<canonical>]` plus the existing JSON recipe args/directive shape as needed, or we need to decide whether `[INTENT]` replaces the recipe directive entirely.

One spec mismatch to confirm before implementation: the requested canonical enum has no explicit `REMINDER` intent, but the goal examples include “remind me at 6 PM.” Should that route to `CREATE_CALENDAR_EVENT`, or should the enum include a reminder-specific intent?

### USER 2026-05-23T08:26:47.773Z

Continue building

### ASSISTANT 2026-05-23T08:26:58.709Z

Phase 4 / V2 pre-S-recipes hardening: I’ll implement the deterministic recipe-intent routing layer and its tests, keeping `:core` Android-free and leaving every existing recipe’s `propose()` behavior intact. First I’m checking the debug log numbering and any relevant prevention rules, then I’ll post the required Plan block before editing.

### ASSISTANT 2026-05-23T08:27:14.916Z

The last debug-log rule that touches this area is DL-063: action plumbing must stay opt-in and must not hijack guidance questions. I’m going to preserve that gate and make `[INTENT]` a selector for deterministic recipe directives, not a new free-text inference path.

### ASSISTANT 2026-05-23T08:27:42.078Z

**Plan**

1. User-visible goal: add a tiny canonical `[INTENT:<token>]` routing layer so overlapping future recipes resolve to one recipe id before proposal logic runs.

2. Files I will touch:
   - `core/src/main/kotlin/com/handy/core/agent/RecipeIntent.kt`: new canonical intent enum.
   - `core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt`: new deterministic intent-to-recipe resolver.
   - `core/src/main/kotlin/com/handy/core/agent/UserGoal.kt`: parse and strip `[INTENT:foo]` into `requestedIntent`, while preserving existing recipe directive parsing.
   - `core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt`: consult the router first, then fall back to current exact recipe-id lookup.
   - `core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt`: teach the LLM the canonical intent token list alongside the existing recipe args directive.
   - `core/src/test/kotlin/com/handy/core/agent/RecipeIntentRouterTest.kt`: cover enum routing, unknown intent, and legacy fallback.
   - Existing prompt/user-goal tests as needed for the new marker text.
   - `DEBUG_LOG.md`: append DL-072 per the user’s requested RCA entry.

3. Interfaces I will not change:
   - `AppRecipe.propose(...)` stays exactly the same.
   - Existing runtime recipe objects stay unchanged.
   - `RecipeRunner` execution, policy checks, confirmations, and `RecipeIntentDispatcher` stay unchanged.
   - No Android imports enter `:core`.

4. Risks and checks:
   - Risk: `[INTENT]` accidentally bypasses DL-063 guidance gating. Catch: `AgentSessionController` still gates on recipe execution wording; I’ll also keep existing recipe directives compatible.
   - Risk: future intent ids map to recipes not present yet. Catch: router returns `null` if the mapped recipe id is absent, so legacy fallback still works.
   - Risk: prompt test expectations drift. Catch: update focused tests and run `./gradlew :core:test :android-runtime:test :app:test`.

Advanced Phase 4 / pre-S-recipes routing hardening — acceptance check: deterministic intent-to-recipe selection before overlapping S recipes land — status: IN PROGRESS.

### ASSISTANT 2026-05-23T08:28:54.456Z

I’m starting the file edits now. The core trick is small: `[INTENT:set_alarm]` selects `clock_alarm`, while the existing JSON args directive remains the argument carrier, so old recipe directives still work.

### ASSISTANT 2026-05-23T08:30:12.978Z

The first implementation pass is in place. I’m doing a compile-oriented read now, especially around the new prompt string and router tests, because tiny parser changes love to fail in deeply boring ways.

### ASSISTANT 2026-05-23T08:30:21.104Z

The focused test command immediately hit the machine’s missing default Java runtime, not Kotlin errors. I’ve seen this repo use a bundled JDK path in prior log entries, so I’m locating that and rerunning with `JAVA_HOME` pinned.

### ASSISTANT 2026-05-23T08:31:28.895Z

The focused tests are green now. I’m appending the requested RCA entry as DL-072, then I’ll run the full requested module test set.

### ASSISTANT 2026-05-23T08:32:07.052Z

Core’s full test pass has completed inside the requested run; the eval/replay metrics are still green. Gradle is now compiling and running the Android runtime/app unit test slices, so I’m letting that finish before touching anything else.

### ASSISTANT 2026-05-23T08:32:27.514Z

The requested full test command passed across `:core`, `:android-runtime`, and `:app`. I’m just replacing the “pending” validation line in the DL entry with the actual command/output, then I’ll do a final diff sanity check.

### ASSISTANT 2026-05-23T08:33:20.197Z

One prompt wording issue popped out in the sanity grep: the canonical intent list does not include the low-level visible UI recipes, so I’m tightening that paragraph to distinguish intent-routed flows from the existing tap/type/search/scroll primitives. That keeps old recipes usable without asking the model to invent canonical tokens that do not exist.

### ASSISTANT 2026-05-23T08:33:53.667Z

The targeted tests are green again after the prompt tightening and the extra registry-first assertion. I’m rerunning the full requested command once more so the recorded validation matches the final diff, not the previous version.

### ASSISTANT 2026-05-23T08:34:44.513Z

Implemented the intent-routing layer and kept the recipe execution surface stable.

Added [RecipeIntent.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeIntent.kt) and [RecipeIntentRouter.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt), wired [RecipeRegistry.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt) to consult it first, and taught [UserGoal.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/UserGoal.kt) to parse `[INTENT:...]`. Existing recipe directives still work as fallback. I also updated [PromptCatalog.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt) so canonical deterministic flows use `[INTENT:<canonical>]`, while low-level visible UI recipes remain explicit recipe ids.

Added [RecipeIntentRouterTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/test/kotlin/com/handy/core/agent/RecipeIntentRouterTest.kt) covering every enum route, unknown intent, router-first registry behavior, and legacy fallback. AppRecipe `propose()` implementations and `RecipeRunner` were not changed. RCA entry added as `DL-072` in [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md).

Universal invariants confirmed: OS-1 foreground services untouched, OS-2 overlay/chat untouched, OS-3 capture untouched, OS-4 overlay Compose lifecycle untouched, OS-5 secure-capture handling untouched. The unrelated untracked `handy_macos_ref/` directory is still untouched.

Requested test output:

```text
$ JAVA_HOME=$HOME/.cache/codex-jdk17 PATH=$HOME/.cache/codex-jdk17/bin:$PATH ./gradlew :core:test :android-runtime:test :app:test --stacktrace

> Task :core:test
Model eval DuplicateTarget pass rate: 2/2 (100.0%)
Model eval HindiHinglish pass rate: 3/3 (100.0%)
Model eval IntentFirst pass rate: 3/3 (100.0%)
Model eval MarkIdSelection pass rate: 3/3 (100.0%)
Model eval NoContextHonesty pass rate: 2/2 (100.0%)
Model eval SecureWindow pass rate: 2/2 (100.0%)
Model eval SensitiveAction pass rate: 3/3 (100.0%)
Model eval ToolInjection pass rate: 1/1 (100.0%)
Pointer replay accuracy: all replay groups passed

> Task :android-runtime:test
> Task :app:test

BUILD SUCCESSFUL in 7s
125 actionable tasks: 21 executed, 104 up-to-date
```

`git diff --check` also passed.

Advanced Phase 4 / pre-S-recipes routing hardening — acceptance check: deterministic intent-to-recipe selection before overlapping S recipes land — status: PASS.
