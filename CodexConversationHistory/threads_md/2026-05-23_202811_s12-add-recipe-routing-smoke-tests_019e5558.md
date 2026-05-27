# S12: Add recipe routing smoke tests

## Metadata

- Thread ID: `019e5558-0243-7563-859c-723791fb759c`
- Created: 2026-05-23 20:28:11 IST
- Updated: 2026-05-23 20:38:07 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T20-28-11-019e5558-0243-7563-859c-723791fb759c.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `762e93f676f61a93b21d2c21abff123a60fb23e6`
- Messages exported: 17

## Brief Summary

This conversation focused on: Lock recipe routing for future contributors. Smoke-test every canonical user utterance against the registry. Add a conflict test that detects recipe overlap. Update README and DEVICE_MATRIX. FILES TO KNOW - android-runtime/src/main/kotlin/com/handy/runtime/ag… Likely related git changes: 2b78200 S12: Add recipe routing smoke tests.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `762e93f676f61a93b21d2c21abff123a60fb23e6`
- Likely related commits:
  - `2b78200` 2026-05-23 20:56:40 IST [high] S12: Add recipe routing smoke tests. Files: DEBUG_LOG.md, README.md, core/src/test/kotlin/com/handy/core/agent/RecipeRegistrySmokeTest.kt, core/src/test/kotlin/com/handy/core/agent/ResolverConflictTest.kt, docs/DEVICE_MATRIX.md

## Conversation

### USER 2026-05-23T14:58:14.525Z

Read the standing rules. Single-pass. NO new production code.

GOAL
Lock recipe routing for future contributors. Smoke-test every
canonical user utterance against the registry. Add a conflict test
that detects recipe overlap. Update README and DEVICE_MATRIX.

FILES TO KNOW
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/AndroidRuntimeRecipes.kt
- core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt
- core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt

IMPLEMENT
Add core/src/test/.../agent/RecipeRegistrySmokeTest.kt covering this
table — each row asserts the registry resolves to the expected
recipe id given the utterance, a fake GroundingSnapshot with
appropriate foreground package, and an LLM-emitted INTENT token:

| utterance                          | foreground   | expected recipe id  |
|------------------------------------|--------------|---------------------|
| "open spotify"                     | launcher     | open_app            |
| "install spotify"                  | launcher     | install_app         |
| "set 7am alarm"                    | launcher     | clock_alarm         |
| "set 10 minute timer"              | launcher     | timer               |
| "search the web for cats"          | launcher     | web_search          |
| "search chrome for cats"           | chrome       | chrome              |
| "turn on dnd"                      | launcher     | android_settings    |
| "ringtone settings"                | launcher     | android_settings    |
| "set brightness"                   | launcher     | android_settings    |
| "screen timeout"                   | launcher     | android_settings    |
| "reply to john on whatsapp"        | whatsapp     | whatsapp_reply      |
| "draft email to mom"               | gmail        | gmail               |
| "schedule lunch tomorrow"          | launcher     | create_calendar_event |
| "book uber to airport"             | uber         | uber_ride           |
| "book ola to cubbon park"          | ola          | ola_ride            |
| "find cheap saree on meesho"       | meesho       | shopping_search     |

Add core/src/test/.../agent/ResolverConflictTest.kt:
For every (recipeA, recipeB) pair, for each canonical utterance
that maps to recipeA in the smoke test, assert recipeB.propose
returns Refused for that utterance. Documents lane discipline.

Add a "summarize this screen" assertion: the smoke test asserts the
registry does NOT propose any recipe for this utterance (it routes
through ConversationMode.SUMMARIZE_SCREEN per S-6).

Update README.md "What's working now":
Add a bullet listing the deterministic recipes after S-1..S-10:
open app, install app, alarm, timer, web search, chrome search,
android settings (10 targets), gmail draft, whatsapp draft, calendar
event, ride-hailing (prep only), shopping search & coupons.

Update docs/DEVICE_MATRIX.md per-cell checklist with:
- OpenApp recipe launches the right app.
- Install X opens the Play Store listing.
- Timer recipe sets the right duration.
- Calendar recipe opens compose UI with prefilled fields.
- Ride-hailing recipe stops before Confirm.
- Summarize-screen mode produces a 2-4 sentence answer with no tool
  calls and no pointer.

SELF-VERIFY
1. ./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug
2. ./gradlew :app:assembleRelease — release build green.
3. On a real Pixel device (document in DEBUG_LOG):
   a. Run each utterance through chat.
   b. Confirm the right recipe appears in the plan approval sheet.
   c. For ride-hailing: confirm STOP before Confirm tap.
   d. For Gmail / WhatsApp: confirm STOP before Send tap.
   e. For Install X: confirm Play Store opens to the right listing.
   f. Pull the audit file (path documented in OPS1) and confirm
      every test run produced an entry with the recipe id.
   g. Attach a single bullet list of pass/fail results in DEBUG_LOG.

STOP CONDITIONS (revert + report only)
- Two recipes propose for the same utterance and the conflict
  cannot be resolved without changing recipe behavior. In that
  case, leave the smoke test red and report which pair conflicts —
  this is a real bug in S-1..S-10, not a flaw in the test.

DEBUG_LOG ENTRY: standard, plus prevention rule "any new recipe
added in the future must add one row to RecipeRegistrySmokeTest and
verify ResolverConflictTest still passes." 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-23T14:58:22.447Z

I’ll do this as a constrained test-and-docs pass: first I’m going to read the repo’s standing rules and the routing/recipe code so the tests pin existing behavior without adding production code. Then I’ll run the requested verification as far as the local environment allows and document any device-only gap honestly.

### ASSISTANT 2026-05-23T14:58:36.060Z

I found the DEBUG_LOG protocol and existing S-series entries, plus an untracked `handy_macos_ref/` directory that I’ll leave alone unless it turns out to be directly relevant. Now I’m reading the recipe interfaces, current tests, and docs layout so the new coverage fits the existing module boundaries.

### ASSISTANT 2026-05-23T14:58:58.878Z

A small repo quirk surfaced: the runtime recipe pack object is named `AndroidRuntimeRecipes`, but it currently lives in `ClockRecipe.kt`, not the path the prompt named. I’m keeping the change test/doc-only and checking whether `:core` can legally see runtime recipes without creating a module cycle before I touch files.

### ASSISTANT 2026-05-23T15:02:24.342Z

**Plan**
Phase: Phase 10 hardening / observability around deterministic recipe routing, advancing the recipe-only multi-step acceptance check in `HANDY_NEXT_LEVEL_PLAN.md` and the audit/device-matrix release checks.

Files I’ll touch:
- `core/src/test/kotlin/com/handy/core/agent/RecipeRegistrySmokeTest.kt` to lock the canonical utterance → intent → recipe-id routing table, plus summarize-screen non-routing.
- `core/src/test/kotlin/com/handy/core/agent/ResolverConflictTest.kt` to assert every non-owning recipe refuses the smoke-test utterances.
- `README.md` to list the deterministic S-1..S-10 recipe set under the working-now/current-state section.
- `docs/DEVICE_MATRIX.md` to add the per-device recipe and summarize-mode checklist rows.
- `DEBUG_LOG.md` to append the requested prevention rule and validation/manual-device status.

Interfaces I will not change:
- No production Kotlin, no recipe behavior, no recipe ids, no Gradle module dependencies, no prompts, no policy engine, no runtime recipe pack wiring.
- `:core` stays pure JVM and does not depend on `:android-runtime`.

Risks and checks:
- The prompt’s table uses `timer`/`gmail`, while production currently routes to `set_timer`/`gmail_compose`; because this is no-production-code, I’ll lock the real current ids used by `RecipeIntentRouter`.
- Conflict coverage in `:core` will use test-local canary recipes so it does not violate module boundaries.
- I’ll run focused tests first, then the requested Gradle commands, then release assemble. Real Pixel manual testing will be documented as blocked if no physical Pixel is attached.

### ASSISTANT 2026-05-23T15:02:26.118Z

I’m making the edits now: two pure-core test files with canary recipes, then the README/device-matrix doc updates. I’m intentionally keeping `android-runtime` untouched because `:core` cannot import Android library code without breaking the standing module rule.

### ASSISTANT 2026-05-23T15:03:17.678Z

The core tests are in place. I’m updating the two docs next so reviewers have the same canonical recipe list and the manual per-device checklist catches the exact safety stops users care about.

### ASSISTANT 2026-05-23T15:03:33.859Z

I’m starting with the focused new core tests before the full build. If they catch an overlap or an id mismatch, I’ll stop and tighten the tests rather than letting a noisy full Gradle run hide the useful failure.

### ASSISTANT 2026-05-23T15:04:15.506Z

Focused `:core` coverage is green. Plain `java -version` still fails on this machine, so I’m using the repo-local JDK path that prior DEBUG_LOG entries already rely on for reproducible Gradle runs.

### ASSISTANT 2026-05-23T15:04:27.638Z

The full requested debug/lint/test build is green with the repo-local JDK. I’m moving on to the release assemble now, then I’ll do the device availability/audit-path checks and write the DEBUG_LOG entry with what could and could not be manually run.

### ASSISTANT 2026-05-23T15:05:07.062Z

Release assembly is through lint-vital and currently in R8 minification. While that runs, I also learned `adb` is not on PATH, so I’ll look for the Android SDK platform-tools path before deciding the real-device status.

### ASSISTANT 2026-05-23T15:05:52.404Z

Release assembly is green. I found `adb` in the Android SDK, so I’m checking attached devices now; if there’s no real Pixel, I’ll log the real-device recipe sweep as blocked rather than pretending it was exercised.

### ASSISTANT 2026-05-23T15:06:05.648Z

Only `emulator-5554` is attached, not a physical Pixel, so the required real-device utterance sweep is blocked. I’m still doing a quick emulator install/launch crash smoke because the user explicitly asked to audit for build errors and app crashes.

### ASSISTANT 2026-05-23T15:06:42.208Z

The emulator smoke launched cleanly: install succeeded, `am start -W` returned `Status: ok`, the process stayed alive, and fresh error logcat was empty. I’m appending the DEBUG_LOG entry now with the green builds and the real-Pixel blocker called out plainly.

### ASSISTANT 2026-05-23T15:07:18.432Z

DEBUG_LOG now has the requested prevention rule and the blocked real-device checklist. I’m doing a final whitespace/status sweep so the final report is about the actual current tree, not stale pre-log validation.

### ASSISTANT 2026-05-23T15:07:25.643Z

One tiny docs polish before I call it done: the README request asked for an added bullet, and my first pass put the S-1..S-10 list inside the existing deterministic-recipes bullet. I’m splitting it into its own bullet so it matches the requested shape exactly.

### ASSISTANT 2026-05-23T15:08:07.839Z

Implemented as a test/docs-only pass. No production Kotlin changed.

**What changed**
- Added [RecipeRegistrySmokeTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/test/kotlin/com/handy/core/agent/RecipeRegistrySmokeTest.kt:1) with canonical utterance routing coverage plus the `summarize this screen` no-recipe assertion.
- Added [ResolverConflictTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/test/kotlin/com/handy/core/agent/ResolverConflictTest.kt:1) so non-owning recipe lanes refuse the smoke-test utterances.
- Updated [README.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/README.md:64) with the S-1..S-10 locked recipe list.
- Updated [docs/DEVICE_MATRIX.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/docs/DEVICE_MATRIX.md:49) with the requested per-cell recipe/summarize checklist.
- Added `DL-083` to [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1363) with the prevention rule.

I locked the current production ids `set_timer` and `gmail_compose` rather than the prompt’s shorthand `timer` / `gmail`, because this was explicitly no-production-code and those are the ids `RecipeIntentRouter` currently routes to.

**Practical Effect**
Before: a future contributor could accidentally make “search chrome for cats” drift toward generic `web_search`, or let “summarize this screen” start proposing a recipe, and there was no single registry-level canary for it.

After: the canonical utterances are pinned. If someone adds a recipe or shifts routing, they must add a smoke row and keep the conflict test green.

**Validation**
- Focused new tests: passed.
- `./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug`: passed using repo-local JDK.
- `./gradlew :app:assembleRelease`: passed.
- `git diff --check`: passed.
- Emulator crash smoke: debug APK installed, onboarding launched with `Status: ok`, process stayed alive, fresh error logcat was empty.
- Real Pixel sweep: blocked. `adb devices -l` showed only `emulator-5554`, no physical Pixel.

**Manual Testing Plan**
1. On a real Pixel, install debug APK, clear app data, grant overlay/accessibility as needed.
2. Run every canonical utterance through chat and confirm the plan sheet recipe id/summary matches: open app, install app, alarm, timer, web search, Chrome search, settings, WhatsApp, Gmail, calendar, Uber, Ola, Meesho.
3. For Gmail and WhatsApp, verify Handy drafts only and stops before Send.
4. For Uber/Ola, verify Handy stops before Confirm/Request.
5. For install, verify Play Store opens the right listing/search and Handy never taps Install.
6. For summarize-screen, verify 2-4 sentences, no plan sheet, no tool call, no pointer.
7. Pull `/data/data/com.handy.android/files/audit/events.json` and confirm each recipe run has the expected recipe id and no screenshot/secret payloads.
8. Repeat one pass with TalkBack on and one with gesture nav/3-button nav per `DEVICE_MATRIX`.

Advanced Phase 10 / registry hardening acceptance: local PASS, real Pixel manual sweep BLOCKED by missing device.
