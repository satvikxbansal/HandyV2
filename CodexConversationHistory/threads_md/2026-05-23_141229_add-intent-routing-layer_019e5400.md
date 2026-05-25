# Add intent routing layer

## Metadata

- Thread ID: `019e5400-0c63-7aa1-a079-bb413025da06`
- Created: 2026-05-23 14:12:29 IST
- Updated: 2026-05-23 14:13:03 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T14-12-29-019e5400-0c63-7aa1-a079-bb413025da06.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `93754259560473853c03421ed3f1e0ba7089d498`
- Messages exported: 4

## Brief Summary

This conversation focused on: Introduce a small intent-routing layer so 6 new recipes (S-1..S-10) Likely related git changes: 9a3b522 Add deterministic open app recipe.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `93754259560473853c03421ed3f1e0ba7089d498`
- Likely related commits:
  - `9a3b522` 2026-05-23 14:22:07 IST [medium] Add deterministic open app recipe. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/OpenAppRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/intent/LaunchableAppIndex.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/OpenAppRecipeTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt, app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt, core/src/main/kotlin/com/handy/core/agent/RecipeIntent.kt, core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt, core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt, core/src/main/kotlin/com/handy/core/agent/UserGoal.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt

## Conversation

### USER 2026-05-23T08:42:36.260Z

You are working on Handy Android (multi-module: :core, :android-runtime, :app).
Repo guardrails live under .cursor/rules/. Read the standing rules
above this prompt and follow them. Do not pause for approval.

GOAL
Introduce a small intent-routing layer so 6 new recipes (S-1..S-10)
do not muddy registry order. The LLM emits [INTENT:<canonical>] and
the router resolves to exactly one recipe.

FILES TO KNOW (read fully before changing anything)
- core/src/main/kotlin/com/handy/core/agent/UserGoal.kt
- core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt
- core/src/main/kotlin/com/handy/core/agent/AppRecipe.kt
- core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/AndroidRuntimeRecipes.kt

Grep before touching:
- Every caller of RecipeRegistry.propose to make sure your signature
  change is backward-compatible.
- Every reference to UserGoal.requestedRecipe — confirm format.

IMPLEMENT
Add:
- core/src/main/kotlin/com/handy/core/agent/RecipeIntent.kt with enum:
    OPEN_APP, SET_ALARM, SET_TIMER, WEB_SEARCH, INSTALL_APP,
    OPEN_SETTING, CREATE_CALENDAR_EVENT, DRAFT_GMAIL, DRAFT_WHATSAPP,
    OPEN_CHROME_URL, SHOPPING_SEARCH, SHOPPING_FIND_COUPONS, BOOK_RIDE.
    Each carries `val canonical: String` (lowercase snake).
- core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt with:
    fun routeOrNull(goal: UserGoal, recipes: List<AppRecipe>): Pair<RecipeIntent, AppRecipe>?
  Logic:
    * Parse [INTENT:<token>] from goal source text (or a new
      UserGoal.requestedIntent field — add it).
    * Map token → RecipeIntent.
    * Map RecipeIntent → recipe id via a static table; look up the
      recipe in the passed-in list.
    * Return the pair, or null on miss.
- core/src/test/kotlin/com/handy/core/agent/RecipeIntentRouterTest.kt:
    * Each enum value resolves to exactly one recipe.
    * Unknown token → null.
    * No [INTENT:] in text → null (falls back to legacy walk).

Change:
- UserGoal.kt: add `val requestedIntent: String? = null` and a
  helper to populate it from "[INTENT:foo]". Keep
  `requestedRecipe` working unchanged.
- RecipeRegistry.propose: first call RecipeIntentRouter.routeOrNull;
  if non-null, call only that recipe's propose. If null, fall back
  to current walk-in-order behavior.
- PromptCatalog: replace the existing recipe-emission instruction
  with the new INTENT instruction. List all canonical tokens in
  the prompt for the LLM. Keep the legacy [RECIPE:] hint parsing
  alive (router accepts both) so no current eval breaks.

SELF-VERIFY
1. ./gradlew :core:test :android-runtime:test :app:test
2. ./gradlew :app:lint :app:assembleDebug
3. Confirm all 10 universal invariants.
4. If any test fails, fix until green or revert.

STOP CONDITIONS (revert + report only)
- UserGoal.requestedRecipe is not where the LLM directive lands
  today — your assumption was wrong; you need to be told the real
  parsing path.
- An existing test asserts the legacy [RECIPE:] format in a way that
  the router refactor cannot preserve without a behavior change.

DEBUG_LOG ENTRY
Title: "DL-### RecipeIntentRouter to prevent recipe ambiguity ahead
of 6 new recipes."
Body: explain the registry-walk problem and the new router contract.
Prevention rule: every new recipe family must register a RecipeIntent
value before its recipe class lands.

### ASSISTANT 2026-05-23T08:42:45.114Z

I’ll read the repo guardrails and the six named files first, then grep the two call/reference points before making any edits. After that I’ll thread the router in as a narrow compatibility layer and run the requested Gradle checks.

### ASSISTANT 2026-05-23T08:42:59.444Z

The worktree is already dirty in exactly the files this task touches, so I’m going to treat those as existing local changes and inspect them carefully before editing. I’m also checking the rule files now so the implementation matches the project’s own conventions instead of just the prompt.

### USER 2026-05-23T08:43:03.084Z

<turn_aborted>
The user interrupted the previous turn on purpose. Any running unified exec processes may still be running in the background. If any tools/commands were aborted, they may have partially executed.
</turn_aborted>
