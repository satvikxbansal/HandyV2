# S4: Add Chrome omnibox flow

## Metadata

- Thread ID: `019e54a8-a3e2-7213-a9a6-f189c517899c`
- Created: 2026-05-23 17:16:38 IST
- Updated: 2026-05-23 17:28:58 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T17-16-38-019e54a8-a3e2-7213-a9a6-f189c517899c.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `5275e07ef66686b75bf2250d30ff664acdee4017`
- Messages exported: 11

## Brief Summary

This conversation focused on: Add an omnibox-typing subflow to the existing ChromeRecipe. Uses flyToAndType against the Chrome url_bar viewId. Keep all current ChromeRecipe paths intact. FILES TO KNOW - android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/Ch… Likely related git changes: 5275e07 S3: Add web search recipe; 50d0992 S4: Strengthen ChromeRecipe with omnibox typing.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `5275e07ef66686b75bf2250d30ff664acdee4017`
- Likely related commits:
  - `5275e07` 2026-05-23 17:16:33 IST [high] S3: Add web search recipe. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/WebSearchRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/WebSearchRecipeTest.kt, core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt, core/src/main/kotlin/com/handy/core/llm/AvailableTools.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt, core/src/test/kotlin/com/handy/core/prompts/PromptCatalogTest.kt
  - `50d0992` 2026-05-23 17:35:29 IST [high] S4: Strengthen ChromeRecipe with omnibox typing. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ChromeRecipe.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/ChromeRecipeTest.kt, app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt, core/src/test/kotlin/com/handy/core/prompts/PromptCatalogTest.kt

## Conversation

### USER 2026-05-23T11:46:40.956Z

Read the standing rules. Single-pass.

GOAL
Add an omnibox-typing subflow to the existing ChromeRecipe. Uses
flyToAndType against the Chrome url_bar viewId. Keep all current
ChromeRecipe paths intact.

FILES TO KNOW
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ChromeRecipe.kt
  (current shape)
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
  (confirm flyToAndType signature; you need to construct a
  SemanticPoint from a viewId only)
- android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt
  (confirm Chrome's url_bar is NOT detected as a password context —
  it shouldn't be, but verify)

Constants:
- Chrome omnibox viewId = `com.android.chrome:id/url_bar`. Match by
  viewId suffix `url_bar` only (covers Brave, Edge, Vivaldi, etc.).
- Chromium families: com.android.chrome, com.brave.browser,
  com.microsoft.emmx, com.vivaldi.browser.

IMPLEMENT
- ChromeRecipe.propose(): add a new branch for "search chrome for X"
  / "chrome search X" intents when foreground package is in the
  Chromium families set:
    Steps:
      1. (only if foreground is not Chrome) NativeAction
         OpenApp("com.android.chrome") allowPackageChangeAfter=true.
      2. Tap RecipeTarget.Node(viewId="url_bar", role="edittext").
      3. TypeText(target=RecipeTarget.Node(viewId="url_bar"), text=query).
      4. Tap RecipeTarget.Node(text="Go", role="button"). If this
         step fails to resolve, the runner already refuses
         gracefully; recipe must include a final fallback NativeAction
         OpenUrl("https://www.google.com/search?q="+enc(query)) so
         the user still gets results.
- ChromeRecipe KDoc: document the omnibox subflow + the fallback.
- Router: OPEN_CHROME_URL (already in S-11 enum) → ChromeRecipe.id
  for both URL and omnibox-search cases.
- WebSearchRecipe (from S-3) must remain the router default for
  generic "search the web" — only the explicit "chrome search"
  / "in chrome" phrasing should route here.

Tests:
- Extend ChromeRecipeTest:
  * "search chrome for cats" with Chrome foreground → omnibox plan
    with the steps above.
  * "search chrome for cats" with non-Chromium foreground → Refused
    ("not-chrome").
  * Existing URL-open path unchanged.

SELF-VERIFY
./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug

STOP CONDITIONS (revert + report only)
- BuddyFlightDriver.flyToAndType does NOT accept a SemanticPoint
  built from viewId alone — investigate signature before changing.

DEBUG_LOG ENTRY: standard. 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-23T11:46:49.714Z

I’ll do this as a single pass: first I’m reading the relevant recipe, flight driver, policy engine, and router/test shape so the changes land exactly where the existing code expects them.

### ASSISTANT 2026-05-23T11:47:02.053Z

So far the recipe is small and conservative: URL intents first, then one-node Chrome page navigation. I’ve confirmed the policy engine blocks typed text only through its sensitive-field heuristics, so I’m checking the exact target matching and runner path before editing.

### ASSISTANT 2026-05-23T11:50:48.362Z

I found `BuddyFlightDriver.flyToAndType(spec: SemanticPoint, ...)` and `SemanticPoint(viewId = "url_bar")` is valid because the parser model only requires one identifier. The policy check also treats `url_bar` as ordinary context, so I’m adding a regression test for that while I patch the Chrome recipe and prompt routing hint.

### ASSISTANT 2026-05-23T11:52:24.650Z

The recipe now keeps URL args first, then detects explicit Chrome omnibox searches, and only then falls back to URL extraction/navigation. I’m doing the first compile/test pass now because Kotlin will catch any contract mismatch quickly.

### ASSISTANT 2026-05-23T11:54:11.102Z

One more integration point matters: recipe `TypeText` normally goes through the raw action performer, while this Chrome path is supposed to use the Buddy flight typing UX. I’m adding a narrow app-side wrapper so only Chrome `url_bar` recipe typing calls `flyToAndType(SemanticPoint(viewId = "url_bar"))`; other recipe typing stays on the existing path.

### ASSISTANT 2026-05-23T11:55:29.322Z

The focused app/runtime/core pass is green now. I’m checking the diff and then I’ll run the full requested Gradle command, plus a quick whitespace audit, before writing the debug log entry.

### ASSISTANT 2026-05-23T11:56:17.016Z

Plain `java -version` still fails on this machine because macOS has no default runtime on PATH, so the exact Gradle task set would fail before reaching the repo. I’m running the requested task set with the repo-local JDK that prior entries use, then I’ll record that explicitly.

### ASSISTANT 2026-05-23T11:56:34.069Z

The first full run hit the same intermittent Android lint FIR-resolution crash in `CoexistenceSmokeTests.kt` that’s already documented in the repo history; it is not pointing at the files changed here. I’m rerunning the same full command because previous entries show this lint crash clears on retry, and I want the final state proven clean.

### ASSISTANT 2026-05-23T11:57:02.397Z

The retry passed the full requested task set: tests, lint, and debug APK assembly. I’m doing the last sanity sweep now: status, whitespace, and, if a device is available, a quick install/launch crash smoke.

### ASSISTANT 2026-05-23T11:58:58.731Z

Implemented the Chrome omnibox subflow.

**What Changed**
- [ChromeRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ChromeRecipe.kt:16) now handles explicit Chrome search intents like `search chrome for cats` / `chrome search cats`.
- It keeps existing URL-open and visible-mark navigation paths intact.
- It targets the omnibox by `viewId` suffix `url_bar`, types the query, taps `Go`, and includes a final Google search URL fallback.
- [AgentSessionController.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt:329) now routes only Chrome `url_bar` recipe typing through `BuddyFlightDriver.flyToAndType(SemanticPoint(viewId = "url_bar"))`.

Before: “search chrome for cats” had no deterministic Chrome omnibox flow and could fall back to generic web search behavior.

After: from Chrome, Handy focuses the Chrome address bar, types `cats`, taps `Go`, and has `https://www.google.com/search?q=cats` as fallback. From Brave/Edge/Vivaldi, it first opens stable Chrome, then does the same flow. From Gmail/Notes/etc., it refuses with `not-chrome`.

**Tests And Verification**
- Added [ChromeRecipeTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/ChromeRecipeTest.kt:20) covering Chrome foreground, non-Chromium refusal, Chromium-family open-Chrome prelude, and unchanged URL-open behavior.
- Added policy coverage proving Chrome `url_bar` is not treated as a password context.
- Updated prompt guidance so generic “search the web” stays on `web_search`.

Final validation passed:
`JAVA_HOME=/Users/satvik.bansal/.cache/codex-jdk17 PATH=/Users/satvik.bansal/.cache/codex-jdk17/bin:$PATH ./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug --stacktrace`

Also installed and launched the debug app on `emulator-5554`; launch returned `Status: ok`, process stayed alive, and fresh logcat had no crash markers.

**Manual Test Plan**
1. Open Chrome, ask: `search chrome for cats`; confirm Handy focuses the omnibox, types `cats`, taps `Go`, and lands on results.
2. Open Chrome, ask: `chrome search best dosa nearby`; verify spaces encode correctly and results load.
3. Open Brave/Edge/Vivaldi, ask the same; verify Handy opens Chrome first, then uses the omnibox.
4. Open Gmail/Notes, ask: `search chrome for cats`; verify recipe refuses as `not-chrome`.
5. Ask generic: `search the web for cats`; verify it still routes to `web_search`, not ChromeRecipe.
6. Ask: `open example.com in chrome`; verify the existing single `OpenUrl("https://example.com")` path still runs.
7. Test Chrome incognito with existing setting enabled; verify action policy still blocks.
8. Test cancellation at the typing confirmation; verify no text is typed.
9. Test missing `Go` affordance/keyboard variation; verify the fallback Google URL step is present in the plan.
10. Re-run logcat crash sweep after each device test.

Note: there is an unrelated untracked `handy_macos_ref/` directory in the worktree; I left it untouched.
