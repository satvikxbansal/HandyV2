# R3: Gmail + WhatsApp + Chrome

## Metadata

- Thread ID: `019e4b36-8132-7c81-bd84-1e91de507ef7`
- Created: 2026-05-21 21:15:23 IST
- Updated: 2026-05-21 21:39:11 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/21/rollout-2026-05-21T21-15-23-019e4b36-8132-7c81-bd84-1e91de507ef7.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `892dd4db52eef172717c1666275302691e916f23`
- Messages exported: 14

## Brief Summary

This conversation focused on: three higher-value recipes; never send/post without explicit No likely related git commits were found for this thread. It may have been planning/review-only, debugging without a commit, or committed as part of a neighboring bundled thread.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `892dd4db52eef172717c1666275302691e916f23`
- Likely related commits: none found

## Conversation

### USER 2026-05-21T15:45:36.483Z

Goal: three higher-value recipes; never send/post without explicit
confirmation.

Files to touch (new):
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/GmailRecipe.kt
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/WhatsAppRecipe.kt
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ChromeRecipe.kt

Notes:
- GmailRecipe + WhatsAppRecipe must stop BEFORE Send. Sending is a
  separate explicit step with STRONG_HOLD confirmation.
- ChromeRecipe: open URL via intent (no need to type into address
  bar); summarize visible page via fetch_page; navigate within the
  page via marks.

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS. 

Acceptance:
- "Reply to John 'on my way'" → opens WhatsApp chat with John →
  fills draft → STOPS → asks "send?" → on STRONG_HOLD send.
- Recipe refuses to act inside an incognito Chrome tab if a
  setting "no_actions_in_incognito" is on (default on).

### ASSISTANT 2026-05-21T15:45:43.828Z

I’ll wire these in as new recipe classes and first map how the existing agent recipes express confirmations, holds, and app actions so the additions fit cleanly. After that I’ll build and inspect the touched code paths for crashy assumptions.

### ASSISTANT 2026-05-21T15:45:57.077Z

I found the recipe system already present under `android-runtime/.../recipes`, plus a few pending local changes in adjacent policy/runner files. I’m going to treat those as existing user work, read the contracts, and only make the smallest edits needed to add these recipes safely.

### ASSISTANT 2026-05-21T15:47:10.426Z

A small design wrinkle showed up: recipes can open apps, tap, type, and fire native intents, but they do not currently have a “tool call” command for `fetch_page` or a per-step “strong hold” override. I’m checking whether the existing confirmation path can express “draft now, send only after hold” without broad framework surgery.

### ASSISTANT 2026-05-21T15:57:23.472Z

I’m going to make two small framework additions before adding the recipe files: a per-step confirmation override so “Send” can require a real hold, and package-transition support so a recipe can open Gmail/WhatsApp/Chrome and then continue safely after the app switch.

### ASSISTANT 2026-05-21T15:59:01.257Z

Now I’m adding the three recipe files. Gmail will use a `mailto:` intent to create the draft in one safe step, WhatsApp will either deep-link by phone or use the visible search/chat UI by contact name, and Chrome will open URLs by intent or tap page marks without touching the address bar.

### ASSISTANT 2026-05-21T16:02:55.921Z

One more important safety detail: the current runner preflights every tap/type target against the current screen before the recipe starts. That blocks legitimate “open app, then tap the new app” recipes, so I’m adding a deferred preflight path that still does fresh policy checks when each future step is reached.

### ASSISTANT 2026-05-21T16:05:25.489Z

The recipes are in place and registered. I’m moving into the audit phase now: first static searches for suspicious API usage/imports, then focused unit tests, then a full build or the closest available Gradle verification task.

### ASSISTANT 2026-05-21T16:05:38.160Z

Gradle can’t start because this machine currently has no Java runtime on PATH (`Unable to locate a Java Runtime`). I’m going to look for an installed/bundled JDK before calling that a blocker, because the code still needs a real compile pass if one is available.

### ASSISTANT 2026-05-21T16:06:19.020Z

Found a cached JDK 17 under `~/.gradle/jdks`, so the earlier Java issue is recoverable without installing anything. I’m rerunning Gradle with `JAVA_HOME` pinned to that JDK and starting with the recipe/policy test slices.

### ASSISTANT 2026-05-21T16:06:48.867Z

The Android runtime recipe/policy tests passed. The core-only test run collided with the other Gradle invocation in the Kotlin incremental cache, so I’m stopping the daemon and rerunning verification sequentially to avoid a false failure.

### ASSISTANT 2026-05-21T16:07:07.466Z

Focused tests are clean now, and whitespace checks passed. I’m running the broader Android compile path next, including the app module, because the new setting toggle touches Compose UI and needs an actual app build.

### ASSISTANT 2026-05-21T16:07:41.607Z

The broad verification passed: app debug build plus core and android-runtime unit tests are green. I’m doing one final source audit now for accidental broad changes, unresolved imports, and the exact file list changed.

### ASSISTANT 2026-05-21T16:09:11.097Z

Implemented the three recipes and the safety plumbing around them.

**What Changed**
Before, Handy had lower-level recipes like tap/type/search plus Clock/Settings/Maps. Messaging/email flows either needed manual work or generic UI actions, and recipes had no way to force a strong hold on a final “Send”.

After:
- [GmailRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/GmailRecipe.kt:17): opens a Gmail draft through a `mailto:` intent, fills recipient/subject/body, then pauses at `Send Gmail message?` with `STRONG_HOLD`.
- [WhatsAppRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/WhatsAppRecipe.kt:17): opens WhatsApp, searches a contact or uses `wa.me` when phone is provided, fills the draft, then pauses at `Send WhatsApp message?` with `STRONG_HOLD`.
- [ChromeRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ChromeRecipe.kt:14): opens URLs by intent, taps visible page marks for navigation, and refuses summary mode so the assistant uses `fetch_page`.
- [RecipeStep.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeStep.kt:14) and [RecipeRunner.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt:28): added per-step confirmation overrides and deferred preflight after app-switching native actions.
- [DefaultActionPolicyEngine.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt:56): blocks actions in Chrome Incognito when `no_actions_in_incognito` is on, defaulting on via [Settings.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/model/Settings.kt:170).

Practical example: “Reply to John ‘on my way’” can now become a deterministic WhatsApp flow: open WhatsApp, find John, type `on my way`, stop before Send, then only send if the user performs the strong hold.

**Manual Test Plan**
1. Install/run debug build, enable Tap-for-me/action disclosure, and confirm Settings has “Block Chrome Incognito actions” on.
2. WhatsApp contact path: ask `Reply to John 'on my way'`; approve recipe; verify it opens WhatsApp, searches John, opens the chat, fills the draft, then stops at a strong-hold Send confirmation.
3. WhatsApp decline path: cancel the strong-hold sheet and verify no message is sent and the draft remains.
4. WhatsApp send path: repeat and complete the strong hold; verify exactly one message sends to the selected John.
5. WhatsApp ambiguity path: test with two similar John contacts; expected result is refusal/target-not-found/ambiguity rather than sending to the wrong chat.
6. Gmail path: ask for an email with `to`, `subject`, and body; verify Gmail draft opens prefilled, then strong-hold is required before Send.
7. Gmail decline/send: cancel once to verify unsent draft, then hold once to verify a single send.
8. Chrome open: ask to open `example.com`; verify it opens via intent and does not type into the address bar.
9. Chrome summary: ask to summarize a page URL; verify the assistant uses `fetch_page` behavior, not Chrome UI typing/actions.
10. Chrome marks: on a normal tab with visible marks, ask to tap a page link/button; verify mark-based navigation works.
11. Incognito safety: open Chrome Incognito, ask for any Chrome/UI action; verify refusal with incognito action block. Toggle setting off and confirm the block is lifted.
12. Regression safety: verify secure windows still refuse, tap-for-me mute still blocks UI actions, and missing Gmail/WhatsApp handlers fail safely.

**Verification**
Passed:
- `git diff --check`
- `JAVA_HOME=... ./gradlew :core:test --tests com.handy.core.agent.RecipeRunnerTest`
- `JAVA_HOME=... ./gradlew :android-runtime:testDebugUnitTest --tests com.handy.runtime.agent.recipes.RuntimeRecipePackTest --tests com.handy.runtime.action.DefaultActionPolicyEngineTest`
- `JAVA_HOME=... ./gradlew :app:assembleDebug :core:test :android-runtime:testDebugUnitTest`
- `JAVA_HOME=... ./gradlew :app:testDebugUnitTest`

The current worktree had pre-existing unrelated dirty files; I left those alone.
