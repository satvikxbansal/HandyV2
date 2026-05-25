# S6: Route summarize screen prompt

## Metadata

- Thread ID: `019e54ba-2277-79d3-bc73-16c4371d7cc0`
- Created: 2026-05-23 17:35:45 IST
- Updated: 2026-05-23 17:51:04 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T17-35-45-019e54ba-2277-79d3-bc73-16c4371d7cc0.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `50d09928b1e714d3a6960e6154fdfc669066204a`
- Messages exported: 13

## Brief Summary

This conversation focused on: Wire the existing "Summarize this screen" quick-prompt to a special non-tool, non-pointer turn that bypasses the recipe runner and tool layer. No new gesture, no new compose screen — just routes the existing chip differently. FILES TO KNOW… Likely related git changes: f31c22a S6: "Help me read this" mode.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `50d09928b1e714d3a6960e6154fdfc669066204a`
- Likely related commits:
  - `f31c22a` 2026-05-23 18:08:11 IST [high] S6: "Help me read this" mode. Files: DEBUG_LOG.md, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelContent.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPanelBridge.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/test/kotlin/com/handy/app/overlay/OverlayPresenterFsmTest.kt, core/src/main/kotlin/com/handy/core/orchestrator/ConversationOrchestrator.kt, core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt, core/src/main/kotlin/com/handy/core/prompts/QuickPromptCatalog.kt, core/src/test/kotlin/com/handy/core/orchestrator/ConversationOrchestratorTest.kt

## Conversation

### USER 2026-05-23T12:06:01.669Z

Read the standing rules. Single-pass.

GOAL
Wire the existing "Summarize this screen" quick-prompt to a special
non-tool, non-pointer turn that bypasses the recipe runner and tool
layer. No new gesture, no new compose screen — just routes the
existing chip differently.

FILES TO KNOW
- core/src/main/kotlin/com/handy/core/prompts/QuickPromptCatalog.kt
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
- core/src/main/kotlin/com/handy/core/orchestrator/ConversationOrchestrator.kt
- app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt
- app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelContent.kt
- app/src/main/kotlin/com/handy/app/screen/ScreenContextBuilder.kt

Grep "Summarize this screen" — find every place it's defined or
matched as a quick prompt. Trace its current routing.

IMPLEMENT
- PromptCatalog: add a new SUMMARIZE_SCREEN_PROMPT:
    "you are handy in summarize mode. the user asked you to read
     and summarize what's currently visible on their screen. respond
     in 2-4 short sentences, in plain language. do not point at
     anything ([POINT:none]). do not use tools. do not emit any
     RECIPE or INTENT tokens."
- ConversationOrchestrator: add an optional `mode: ConversationMode`
  parameter (default NORMAL). New enum ConversationMode { NORMAL,
  SUMMARIZE_SCREEN }. When SUMMARIZE_SCREEN:
    * Use SUMMARIZE_SCREEN_PROMPT.
    * Force tools = emptyList() regardless of caller's tools.
    * Skip recipe-routing in the downstream AgentSessionController.
- QuickPromptCatalog: mark "Summarize this screen" with a sentinel
  or a strongly-typed QuickPrompt variant that the panel pipeline
  can detect. Avoid string compare in the pipeline.
- OverlayChatPipeline: when the user taps that quick prompt, call
  orchestrator with ConversationMode.SUMMARIZE_SCREEN.

Tests:
- ConversationOrchestratorTest: SUMMARIZE_SCREEN mode → uses the new
  prompt, empty tools, no recipe routing.
- QuickPromptCatalogTest: the sentinel for "Summarize this screen" is
  set and reachable.

SELF-VERIFY
./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug

STOP CONDITIONS (revert + report only)
- AgentSessionController does its own tool-list construction
  separately and the SUMMARIZE_SCREEN bypass cannot be threaded
  through cleanly — that needs a small refactor of how mode flows.

DEBUG_LOG ENTRY: standard, plus the prevention rule "any new
'fast mode' that bypasses recipes/tools must be expressed as a
ConversationMode value, not a string compare in the pipeline." 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-23T12:06:09.592Z

I’ll trace the quick prompt path first, including the standing rules and any existing debug-log convention, then make the smallest threaded change through the catalog, pipeline, and orchestrator. After that I’ll run the requested Gradle verification and audit anything that shakes loose.

### ASSISTANT 2026-05-23T12:06:19.043Z

I found the repo’s debug-log protocol and the affected files. There’s also an untracked `handy_macos_ref/` directory already present; I’ll leave it alone and keep the change scoped to the Android project files.

### ASSISTANT 2026-05-23T12:06:45.492Z

Standing rules are read. The matching prevention rules I’m carrying forward are: prompt/tool changes need end-to-end tests, recipe-routing changes must stay explicit, quick prompts are V2 recipe #9, and the new fast path must be mode-shaped rather than a pipeline string check.

### ASSISTANT 2026-05-23T12:08:24.324Z

Here’s the small plan before editing, per the repo rules.

Phase: V2 overlay chat panel work. Plan sections: scope §2 overlay panel, §7 semantic pointing guardrails, §15 recipe #9 quick prompts. Acceptance check advanced: tapping the quick prompt sends a screen-summary answer without tools, recipes, or pointer flight.

Files I’ll touch:
- `core/.../PromptCatalog.kt`: add the dedicated summarize-system prompt and a builder that only attaches screen context/failure notes.
- `core/.../ConversationOrchestrator.kt`: add `ConversationMode`, force empty tools in summarize mode, and use the summarize prompt.
- `core/.../QuickPromptCatalog.kt` plus `core/.../OverlayPanelState.kt`: make quick prompts typed, with a `SUMMARIZE_SCREEN` sentinel.
- `app/.../OverlayPresenter.kt`, `OverlayChatPanelContent.kt`, `OverlayPanelBridge.kt`, `OverlayChatPipeline.kt`: carry the typed chip through the existing panel submission path, call the orchestrator in summarize mode, and skip recipe/pointer handling for that mode.
- Tests in `ConversationOrchestratorTest`, `QuickPromptCatalogTest`, and likely one app presenter test for typed prompt assertions.
- `DEBUG_LOG.md`: append the required DL entry with your prevention rule.

Interfaces I won’t change: `LlmClient`, `ToolRunner`, recipe registry/runner APIs, `ScreenContextBuilder`, chat activity routing, and the action performer stack.

Risks and checks: the main risks are breaking existing string-rendered chips or accidentally leaving a tool/recipe path alive. I’ll catch those with focused unit tests first, then the requested full Gradle lane: `./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug`.

### ASSISTANT 2026-05-23T12:10:10.883Z

I’ve got the route now: overlay chips are currently plain strings, all chip taps become ordinary panel text, tools are still advertised, and after the response the pipeline always gives `AgentSessionController` a chance to parse recipe directives. I’m changing that route to carry a typed quick-prompt action alongside the display text.

### ASSISTANT 2026-05-23T12:14:04.034Z

The code now carries the quick-prompt action through the bridge and into the pipeline. I’m doing a focused compile/test pass next because this touched shared panel state types, so any stale string-list assumptions should show up quickly.

### ASSISTANT 2026-05-23T12:14:14.493Z

The focused core tests are green. I’m widening to the app tests now; that’s where the typed chip list and new callback path can reveal missed UI or presenter assumptions.

### ASSISTANT 2026-05-23T12:16:04.148Z

The focused Android path is green too. I’m running the full requested verification lane now; this is the one that matters for build/lint/crash-risk confidence before I write the debug-log entry.

### ASSISTANT 2026-05-23T12:16:41.923Z

The full Gradle lane passed. I’m doing one device-level smoke if an emulator is attached, then I’ll append the required `DEBUG_LOG.md` entry with the new prevention rule and run a final whitespace diff check.

### ASSISTANT 2026-05-23T12:18:10.385Z

The debug log is appended and the final whitespace check is clean. I’m doing one last local audit pass over the touched Kotlin now, mostly looking for accidental API awkwardness or any route that could still sneak a recipe/tool/pointer through summarize mode.

### ASSISTANT 2026-05-23T12:18:34.363Z

I found two small readability nits in the audit and cleaned them up: the nullable screen-text handling is explicit now, and the quick-prompt bridge builds the submission before emitting it. Since that touched Kotlin after the full pass, I’m rerunning the full requested lane.

### ASSISTANT 2026-05-23T12:21:04.004Z

Implemented the summarize-screen fast path.

What changed:
- Added `ConversationMode.SUMMARIZE_SCREEN` in [ConversationOrchestrator.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/orchestrator/ConversationOrchestrator.kt:50).
- Added the dedicated summarize prompt in [PromptCatalog.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt:32).
- Made quick prompts typed in [QuickPromptCatalog.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/prompts/QuickPromptCatalog.kt:25), with `Summarize this screen` carrying `SUMMARIZE_SCREEN`.
- Routed overlay chip taps through typed submissions in [OverlayPanelBridge.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayPanelBridge.kt:51) and [OverlayChatPipeline.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt:91).
- Appended `DL-080` to [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1315).

Before: tapping the overlay quick prompt was just normal chat text, so tools, recipe prompt addendums, recipe parsing, and pointer flight could all still be in play. For example, on a Gmail inbox it could answer with normal overlay behavior and still be eligible for a pointer; on a shopping page it could still get the shopping/tool addendum.

After: tapping `Summarize this screen` runs a special non-tool turn. It gets the visible screen context, uses the summarize-only prompt, sends `tools = emptyList()`, avoids tool-aware streaming, skips recipe routing, and skips pointer flight. Practical result: on Gmail it should say something like “this inbox shows a few recent emails, including receipts and team notes...” without pointing at Compose or opening recipe review.

Manual testing plan:
1. Open the overlay over an unknown app/screen and confirm the chip list includes `Summarize this screen`.
2. Tap it on Gmail, Settings, Chrome, and a shopping/product screen. Expect 2-4 short plain-language sentences.
3. Confirm no blue pointer flight, no `Review recipe` confirmation, no teal recipe/action progress, and no web-search status text.
4. Confirm the response does not include visible `RECIPE`, `INTENT`, `[POINT:role=...]`, or `[POINT:markId=...]` tokens.
5. Test a screen with tempting actions, like Clock or Settings. The summary should describe what is visible, not set timers, open settings, or tell the user where to tap.
6. Test secure/incognito/banking-style screens. The secure-window short-circuit should still avoid sending unusable context and show the existing secure-screen message.
7. Regression check normal overlay chips: `Show me around`, shopping coupon/compare chips, and ordinary typed panel questions should still use normal overlay behavior, including pointers/tools/recipes where appropriate.
8. Rotate the device or dismiss/reopen the panel during/after the summary to confirm no stuck streaming state.
9. Check logcat after each run for `AndroidRuntime`, `FATAL EXCEPTION`, `am_crash`, or Handy exceptions.

Verification passed:
- Focused tests for orchestrator/catalog/presenter.
- Full requested lane: `./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug` passed.
- `:app:installDebug` installed on `emulator-5554`; launch returned `Status: ok`; process stayed alive; logcat crash scan found no matches.
- `git diff --check` passed.

Advanced Phase V2 / Sections §2, §7, §15 recipe #9 — acceptance check: summarize quick prompt bypasses tools, recipes, and pointer routing — status: PASS.
