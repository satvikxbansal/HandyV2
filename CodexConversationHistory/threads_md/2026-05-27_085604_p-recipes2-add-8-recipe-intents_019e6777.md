# P-RECIPES2: Add 8 recipe intents

## Metadata

- Thread ID: `019e6777-ca01-73a3-a591-500c59dab6ec`
- Created: 2026-05-27 08:56:04 IST
- Updated: 2026-05-27 10:21:07 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/27/rollout-2026-05-27T08-56-04-019e6777-ca01-73a3-a591-500c59dab6ec.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `0229ca70aed1f56fbd4b768d63022ba5ffcf5625`
- Messages exported: 43

## Brief Summary

This conversation focused on: Add 8 new recipes that significantly broaden Handy's helpfulness without expanding the risk surface. Each is intent-first, draft-only, or guide-only. Every recipe ships with fixtures from P-RECIPES-1. DEPENDS ON - P-RECIPES-1 (contract tests + verifiers + sid… Likely related git changes: 1c88770 P-RECIPES2: Add 8 recipe intents.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `0229ca70aed1f56fbd4b768d63022ba5ffcf5625`
- Likely related commits:
  - `1c88770` 2026-05-27 11:05:24 IST [high] P-RECIPES2: Add 8 recipe intents. Files: DEBUG_LOG.md, README.md, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/CalculatorRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/CalendarEventRecipeV2.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ContactsRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/FilesRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/FoodDeliveryRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/NotesRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/PhotosRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/YouTubeRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt

## Conversation

### USER 2026-05-27T03:26:33.000Z

GOAL
Add 8 new recipes that significantly broaden Handy's helpfulness without
expanding the risk surface. Each is intent-first, draft-only, or
guide-only. Every recipe ships with fixtures from P-RECIPES-1.

DEPENDS ON
- P-RECIPES-1 (contract tests + verifiers + side-effect classification).

VERIFIED PRE-CONDITIONS
- Existing AndroidRuntimeRecipes.defaultRecipes() adds 11 recipes plus
  the Shopping and Ride packs.
- RecipeIntentRouter.INTENT_TO_RECIPE_ID maps canonical intents to recipe
  ids. New intents must be added there.
- Canonical intents are defined in core/.../agent/RecipeIntent.kt.

IMPLEMENTATION (one recipe each — fully concrete)

1. YouTubeRecipe
   File: android-runtime/.../agent/recipes/YouTubeRecipe.kt
   Intents:
     - YOUTUBE_SEARCH ("play X on YouTube", "find X video")
     - YOUTUBE_OPEN_CHANNEL ("open <channel> channel")
   Side effect: OPENS_EXTERNAL_UI.
   Steps:
     - NativeAction(OpenUrl("https://www.youtube.com/results?search_query=…"))
       (Chrome / YouTube app handles the URL.)
   BLOCKED in fixtures: "like this video", "subscribe to <channel>",
   "comment <text>".

2. NotesRecipe
   File: android-runtime/.../agent/recipes/NotesRecipe.kt
   Intent: CREATE_NOTE ("take a note: buy milk", "remind me to call mom").
   Side effect: DRAFT_ONLY.
   Steps:
     - NativeAction(ShareText(text=note, mimeType="text/plain"))
       — opens the system share sheet; Keep, Notion, Obsidian, etc. all
       accept.
   Verifier: package change after share.

3. ContactsRecipe
   File: android-runtime/.../agent/recipes/ContactsRecipe.kt
   Intents:
     - OPEN_CONTACT ("open <name>'s contact")
     - PREPARE_CALL ("call <name>" → opens dialer with prefilled number,
       NO auto-dial)
     - PREPARE_SMS ("text <name> <message>" → opens SMS draft with
       prefilled recipient + body)
   Side effect: PREPARE_CALL / PREPARE_SMS = DRAFT_ONLY (no send/dial).
   - Use ContactsContract to resolve name → contact_uri (URI only, no
     phone number leaked to LLM).
   - PREPARE_CALL emits Intent(ACTION_DIAL, tel:<resolved>) — never
     ACTION_CALL.
   - Multiple matches → recipe refuses with reason "ambiguous-contact"
     and shows a chip "Which one? Rohan S, Rohan B …". (Reuses the
     existing candidate-options UX.)

4. FilesRecipe
   File: android-runtime/.../agent/recipes/FilesRecipe.kt
   Intents:
     - FILES_SEARCH ("find file <name>")
     - FILES_OPEN ("open <file>")
   Side effect: OPENS_EXTERNAL_UI.
   Uses ACTION_GET_CONTENT / ACTION_OPEN_DOCUMENT intents.

5. PhotosRecipe
   File: android-runtime/.../agent/recipes/PhotosRecipe.kt
   Intents:
     - PHOTOS_OPEN ("open Photos / Gallery")
     - PHOTOS_SHARE_CURRENT (only when the user is already viewing a
       photo and explicitly asks "share this photo" — recipe inspects
       the foreground app, refuses otherwise)
   Side effect: PHOTOS_OPEN = OPENS_EXTERNAL_UI; SHARE = DRAFT_ONLY (system
   share sheet, user picks recipient).
   BLOCKED: "delete this photo", "delete all".

6. CalculatorRecipe
   File: android-runtime/.../agent/recipes/CalculatorRecipe.kt
   Intent: CALCULATE ("what's 23% of 4500", "compute 12*47").
   Two paths:
     - Direct: parse the expression locally via a tiny safe parser
       (only +, -, *, /, %, parentheses). Answer returned in the chat;
       no recipe execution.
     - Open: "open calculator" → ACTION_MAIN + CATEGORY_APP_CALCULATOR.
   Side effect: NONE.

7. FoodDeliveryRecipe
   File: android-runtime/.../agent/recipes/FoodDeliveryRecipe.kt
   Intents:
     - FOOD_SEARCH ("find biryani on Swiggy")
     - FOOD_TRACK_ORDER ("track my Zomato order")
   Side effect: OPENS_EXTERNAL_UI.
   Steps:
     - NativeAction(OpenUrl) → deep links per app (e.g. swiggy://…) if
       installed, else web URL.
   BLOCKED: "place the order", "confirm payment", "pay".

8. CalendarRecipeV2 (replaces / supplements CalendarEventRecipe)
   File: android-runtime/.../agent/recipes/CalendarEventRecipeV2.kt
   Improvements over v1:
     - Better recipient/attendee parsing.
     - Strong-hold on final save.
     - Refuses recurring rules until user confirms ("every Tuesday at
       4pm — should I make this repeat?").
   Keep CalendarEventRecipe v1 for backwards compat; switch the intent
   router to v2.

9. Update RecipeIntentRouter:
   File: core/.../agent/RecipeIntentRouter.kt
   Add intents to INTENT_TO_RECIPE_ID for each new recipe.

10. Update AndroidRuntimeRecipes.defaultRecipes():
    File: android-runtime/.../agent/recipes/ClockRecipe.kt (the
    AndroidRuntimeRecipes object lives there).
    Append the 8 new recipes to the list.

ACCEPTANCE
- "Take a note: buy milk" → share sheet appears with the text.
- "Find biryani on Swiggy" → Swiggy or browser opens to a biryani search.
- "Call Mom" → dialer opens with Mom's number filled, user taps Call.
- "What's 23% of 4500?" → answer in chat ("1035"). No recipe.
- "Order biryani" without "find" → recipe refuses with
  "i-can-search-but-cant-order".
- "Delete this photo" → recipe refused; bubble explains.
- Multi-match contact → candidate chips appear.

TESTS
- One contract test per new recipe with 3 fixtures.
- Manual: each on Pixel + Samsung; CalculatorRecipe especially needs
  unit tests for the expression parser.

DO NOT
- Add payment, purchase, checkout, place-order, send-message,
  delete behaviour to any recipe.
- Use ACTION_CALL (only ACTION_DIAL).
- Hardcode third-party coordinates.
- Auto-confirm; everything pauses at the system UI step.

UPDATE
- DEBUG_LOG.md, RECIPE_SWEEP_MATRIX.md, README recipe inventory. 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS

### ASSISTANT 2026-05-27T03:26:43.170Z

I’ll trace the existing recipe contracts, intent routing, and P-RECIPES-1 fixtures first, then wire the new recipes into the same shape instead of inventing a parallel path. After that I’ll run the relevant tests/build checks and tighten anything that shakes loose.

### ASSISTANT 2026-05-27T03:27:00.887Z

The repo already has the P-RECIPES-1 pieces: recipe resource fixtures, side-effect expectations, and the intent router. I’m going to read the exact contract APIs now so the new recipes participate in the same verifier path as the existing ones.

### ASSISTANT 2026-05-27T03:32:18.579Z

I’ve got the shape now. A couple of important existing constraints popped out: destructive native actions like share/SMS/dial currently need a confirmed-dispatch path inside recipes, and ambiguous choices only have a chip UI for candidate options, so I’m going to thread those through carefully while keeping the actual new recipes draft-only/open-only.

### ASSISTANT 2026-05-27T03:43:24.219Z

The main runtime pieces are now in place: new action types, Android intent dispatchers, local contact resolution, recipe routing, and default-pack registration. Next I’m adding the P-RECIPES-style fixtures and focused tests so we can catch the edge cases instead of trusting the wiring by inspection.

### ASSISTANT 2026-05-27T03:46:44.688Z

Fixtures and focused unit tests are down now, including the calculator parser cases and contact ambiguity labels. I’m going to run the core/runtime test slice before touching docs, because compiler feedback here will be more useful than polishing prose too early.

### ASSISTANT 2026-05-27T03:46:58.023Z

The first Gradle attempt hit the local shell’s missing Java runtime, which this repo has worked around before with a bundled/cached JDK. I’m checking the known JDK paths and will rerun with `JAVA_HOME` pinned.

### ASSISTANT 2026-05-27T03:47:44.619Z

Focused core/runtime tests are green with the repo-local JDK. I’m going to compile the app next because the production injection path and overlay chip tweak live there, and that’s where Hilt/Compose issues tend to show up.

### ASSISTANT 2026-05-27T03:48:13.066Z

App compile is clean too; only existing accessibility announcement deprecation warnings showed up. I’m moving to the requested documentation updates now, then I’ll do the larger validation sweep and installation/crash check.

### ASSISTANT 2026-05-27T03:51:52.559Z

I’ve got the implementation already in place and am doing the last pass now: full validation, crash/build checks where the local setup allows, then I’ll update the debug log with the exact evidence.

### ASSISTANT 2026-05-27T03:52:05.245Z

Whitespace checks are clean. I’m kicking off the full Gradle sweep now with the repo-local JDK so we catch compile, unit, lint, and packaging issues in one pass.

### ASSISTANT 2026-05-27T03:52:46.800Z

The full sweep found one real compile issue in app tests: `AgentSessionController` now needs the Android context for contact lookup, and the unit test still constructs it with the old signature. I’m patching that instead of papering over it.

### ASSISTANT 2026-05-27T03:53:36.507Z

That compile fix took; the full run has passed the test compile point and is now finishing lint/report tasks. Next I’ll do an install-and-launch smoke check if `adb` sees a device.

### ASSISTANT 2026-05-27T03:53:52.544Z

There is an emulator attached (`emulator-5554`), so I’m going one step beyond the build: installing the debug APK, launching the app, and checking logcat for startup crashes.

### ASSISTANT 2026-05-27T03:54:16.019Z

The emulator smoke test is clean: debug APK installed, app launched cold into `OnboardingActivity`, process stayed alive, and the focused crash scan found no fatal exception, ANR, or forced finish for Handy. I’m writing that evidence into the debug log now.

### ASSISTANT 2026-05-27T03:55:12.512Z

The documentation pass is in and the final whitespace check is clean. I’m doing one last code audit around the new dispatcher actions and recipe refusal paths, because these are exactly the places that can compile fine but surprise you on-device.

### ASSISTANT 2026-05-27T03:57:17.755Z

The audit caught an important non-crashy behavior gap: the recipe execution gate still only knew the older verbs, so prompts like “Call Mom,” “Take a note,” “share this photo,” “track my Zomato order,” and the calculator answer path could be ignored before the new recipes ever proposed. I’m tightening that gate and adding a regression so the acceptance examples actually reach the new safe recipes.

### ASSISTANT 2026-05-27T03:58:18.065Z

I patched the gate and added a regression for the calculator acceptance path: “What’s 23% of 4500?” can now produce the local answer even though it is not an execution request. I’m running the focused tests first, then I’ll repeat the full build/smoke because this touched core/app routing.

### ASSISTANT 2026-05-27T03:59:23.484Z

Focused regressions are green. I’m rerunning the full Gradle sweep now so the final state reflects the execution-gate fix too, not just the earlier implementation.

### ASSISTANT 2026-05-27T04:00:04.369Z

Full validation is through core evals, app/runtime unit tests, and APK assembly; lint is the remaining stretch. The late gate fix is behaving cleanly so far.

### ASSISTANT 2026-05-27T04:00:15.988Z

The full sweep passed after the gate fix. I’m reinstalling the fresh APK and doing one more emulator launch/crash scan so the runtime smoke reflects the exact final build.

### ASSISTANT 2026-05-27T04:01:46.588Z

Implemented P-RECIPES-2 end to end.

**What Changed**
Before, Handy could handle the older recipe set, but prompts like “Take a note: buy milk,” “Call Mom,” “Find biryani on Swiggy,” or “What’s 23% of 4500?” either needed generic guidance or could miss the deterministic low-risk path.

After this change, Handy has 8 new low-risk recipe families:

- YouTube search/channel handoff, with like/subscribe/comment refused.
- Notes share-sheet draft.
- Contacts open/dialer/SMS draft, never `ACTION_CALL`.
- Files picker/document handoff, no mutation/read/upload.
- Photos/Gallery open and current-photo share only from a photo viewer.
- Calculator local arithmetic answers or Calculator app open.
- Food delivery search/tracking, no order/payment/checkout.
- Calendar v2 event draft with attendees and recurrence refusal.

I also updated router/default registration, native actions, dispatcher support, prompt/tool schemas, candidate chips for ambiguous contacts, calculator answer handling, docs, and 24 new JSON fixtures.

**Important Audit Fix**
I caught and fixed a subtle routing gap: the execution gate still only knew the older verbs, so acceptance prompts like “Call Mom,” “Take a note,” “share this photo,” “track my Zomato order,” and “order biryani” could be ignored before the new recipes proposed/refused. That gate now covers the new safe verbs, and calculator answer-only prompts can respond without treating arithmetic as automation.

**Key Files**
- [ClockRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt)
- [AndroidIntentDispatcher.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt)
- [RecipeIntentRouter.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt)
- [AgentSessionController.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt)
- [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md)
- [RECIPE_SWEEP_MATRIX.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/docs/qa/RECIPE_SWEEP_MATRIX.md)

**Validation**
Passed:

```bash
git diff --check
JAVA_HOME=/Users/satvik.bansal/.cache/codex-jdk17 PATH=/Users/satvik.bansal/.cache/codex-jdk17/bin:$PATH ./gradlew --no-daemon :core:test :android-runtime:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
```

Also installed the final debug APK on `emulator-5554`, launched `com.handy.android/com.handy.app.onboarding.OnboardingActivity`, confirmed the process stayed alive with PID `25574`, and logcat showed no `FATAL EXCEPTION`, ANR, force-finish, or Handy process-death entry.

**Manual Test Plan**
Run this on Pixel and Samsung before release:

1. Fresh install, grant overlay/accessibility, grant contacts only for contacts tests.
2. Seed contacts: `Mom`, `Rohan S`, `Rohan B`, and one SMS-capable contact.
3. Happy paths:
   - “Take a note: buy milk” opens share sheet with exact text.
   - “Find biryani on Swiggy” opens Swiggy or web search.
   - “Call Mom” opens dialer prefilled, no call placed.
   - “Text Mom hello” opens SMS draft, no send.
   - “Open Rohan’s contact” opens contact UI.
   - “What’s 23% of 4500?” answers `1035` in chat.
   - “Open calculator” opens Calculator.
   - YouTube search/channel, Files picker, Photos open/share-current, Calendar v2 attendee draft, food tracking.
4. Blocked paths:
   - “Order biryani,” “confirm payment,” “like this video,” “subscribe,” “delete this photo,” “delete file,” unsupported calculator functions.
   - Verify refusal appears and no external state changes.
5. Ambiguity:
   - “Call Rohan” with two matches shows candidate chips and does not open dialer.
6. Permission/fallback:
   - Deny contacts permission and verify contacts recipe refuses cleanly.
   - Test food/YouTube with apps installed and absent to verify deep link/web fallback.

### USER 2026-05-27T04:01:46.810Z

That was a long lengthy task! Lots of changes done above which included context compactions in the middle. 2000+ lines of code were written across 52+ files- that's a lot of code and it needs a deep deep thorough audit - functional as well as non-functional! the task was also stuck in the middle so we need to deepl review the work done. The Recipes especially are critical in Handy! Can you go through the original prompt, code changes done in extreme detail, audit them, ensure all the required changes were done and done correctly with no downstream impact. the code before this works fine with it - no suspicious lines of codes for app crashes, or faulty wirings. all the services that needed to be changed have been changed and nothing is left or missed. ensure the ui is also proper with correct spacings, colours, fonts and elements. be very thorough in your analysis and evaluation. use highest level of reasoning available. Log the rca in the debug log and explain why the misses were missed. use highest level of thinking possible and think deeply. Evaluate and understand very deeply. Be very thorough and detailed. use your highest level of intelligence to analyse the requirement, code generated and plug gaps. be very thorough and have highest attention to detail. carefully analyse all the code - new and old and all dependencies too. the latest commit is of the lengthy changes above.

### ASSISTANT 2026-05-27T04:01:54.695Z

Absolutely. I’m going to treat this as a safety audit, not a rubber stamp: compare the latest recipe commit against the original prompt, trace the wiring from intent selection through registry, policy, dispatcher, UI chips, fixtures, tests, docs, and then patch anything that looks even slightly wrong.

### ASSISTANT 2026-05-27T04:02:25.054Z

The first thing I found: the recipe expansion is currently dirty/uncommitted on top of `HEAD` (`b4f0310 Audit`), not actually inside the latest commit. I’m auditing the dirty P-RECIPES-2 surface as the change under review and keeping the unrelated pre-existing `handy_macos_ref/` alone.

### ASSISTANT 2026-05-27T04:02:56.911Z

I’m tracing the most failure-prone chain now: recipe proposal → plan policy → execution dispatcher → verifier/audit. One early red flag is contacts: phone numbers are intentionally resolved locally, but I need to make sure they do not leak into prompts, audit strings, progress copy, or LLM-facing tool schemas.

### ASSISTANT 2026-05-27T04:09:38.646Z

The audit has found real gaps, not just paperwork: recipe disabling was not actually preventing recipe prompts/runs in the overlay, `open_contact` needed URI validation to avoid becoming an arbitrary VIEW escape hatch, contacts lacked an OPEN_CONTACT fixture, and a few blocked-term checks were too narrow or too broad. I’m patching those now.

### ASSISTANT 2026-05-27T04:14:53.530Z

I also found a prompt conflict: the direct-action addendum was still using “call mom” as a `dispatch_action` example, which competes with the new ContactsRecipe and cannot resolve names safely. I’ve changed that to explicit numbers only and told the model to use recipe intents for contact-name flows.

### ASSISTANT 2026-05-27T04:15:42.918Z

The first focused run caught a compile issue in the calendar intent hardening: Android exposes the event MIME type in practice, but not as the constant I used on this SDK. I replaced it with the platform MIME literal and am rerunning.

### ASSISTANT 2026-05-27T04:38:17.437Z

I’m picking up the audit exactly where the compaction left off: the safety and wiring fixes are mostly in place, and one focused test is telling us there’s still a recipe-toggle propagation bug to chase. I’ll fix that first, then rerun the focused suite before widening to the full build and debug-log RCA.

### ASSISTANT 2026-05-27T04:38:36.970Z

The orchestrator itself does pass `agentRecipesEnabled` through, so the failing test is probably exposing a race or fixture issue rather than a pure core bug. I’m reading the overlay request path now to see whether the real setting value is being mixed with a second turn or a fallback default.

### ASSISTANT 2026-05-27T04:39:38.194Z

The single failing overlay test is still running in isolation; I’m using it as a narrow signal so we don’t bury a real app-state bug under a full test log. While it runs, the code path looks logically correct, so I’m prepared for either a test-fixture race or another layer injecting the recipe prompt.

### ASSISTANT 2026-05-27T04:41:28.835Z

That failure turned out to be a useful wording bug: the recipe block was absent, but the direct-action addendum still said “agent-mode recipes” even when recipes were disabled. I’m tightening the prompt so the model gets different contact-name guidance depending on whether recipes are actually available.

### ASSISTANT 2026-05-27T04:42:41.588Z

Good, the narrow prompt issue is fixed and the focused suite has moved past compilation into runtime tests. If that comes back green, I’ll widen the audit to static safety scans and then the full Gradle lane.

### ASSISTANT 2026-05-27T04:43:05.850Z

Static checks show no `ACTION_CALL`, and `git diff --check` is clean. I’m doing another pass over the recipe implementations themselves now, focusing on unsafe actions, resolver behavior, intent construction, and whether the tests/fixtures really cover the exact blocked phrases from the original prompt.

### ASSISTANT 2026-05-27T04:44:53.024Z

I found one Android 11+ package-visibility gap: the food recipe can decide Swiggy/Zomato is installed, but the dispatcher may still fail to resolve `swiggy://` or `zomato://` without explicit query visibility. I’m adding narrow package/scheme queries so installed apps get the deep link path and non-installed cases still fall back to web.

### ASSISTANT 2026-05-27T04:46:31.214Z

The broader UI sweep found inherited negative letter-spacing in onboarding/chat/design primitives, not just the recipe chip surface. Since this audit explicitly includes UI quality and the guardrail is clear, I’m normalizing those remaining negative values to `0.em` rather than leaving tight typography that can clip under OEM font scaling.

### ASSISTANT 2026-05-27T04:47:17.343Z

Focused validation is recompiling the app manifest and UI after the package-visibility and typography fixes. So far it’s past the runtime recipe tests and into app test compilation, which is the right place to catch manifest/schema or Compose type mistakes.

### ASSISTANT 2026-05-27T04:47:35.021Z

The focused suite is green after the audit fixes. I’m moving to the full lane now: core tests, runtime tests, app tests, lint, and debug APK assembly in one serial Gradle invocation so KSP/generated caches don’t get trampled.

### ASSISTANT 2026-05-27T04:47:59.695Z

The full build has assembled the debug APK and is in lint/test wrap-up now. No compile break or manifest rejection has shown up, including from the new content/deep-link queries.

### ASSISTANT 2026-05-27T04:48:37.085Z

Full validation is green. I’m going to do the crash smoke as well: install the built debug APK, launch Handy, and scan fresh logcat for process death, ANR, or AndroidRuntime crashes.

### ASSISTANT 2026-05-27T04:49:07.357Z

Crash smoke is clean: the app installed, cold-launched, and the Handy process stayed alive. I’m grabbing one screenshot of the launched surface now as a quick visual sanity pass after normalizing typography.

### ASSISTANT 2026-05-27T04:51:07.696Z

Deep audit is complete. I found and fixed several real gaps, then reran focused validation, full build/lint/tests, and emulator crash smoke.

**Audit Fixes**
- Recipes toggle now truly disables recipe prompting and recipe execution in both full chat and overlay, while keeping normal direct actions gated separately.
- Direct-action prompt no longer references recipe mode when recipes are off, and avoids unsafe contact-name dispatch unless an exact number/contact URI is already known.
- `open_contact` is now dispatcher-hardened: only `content://com.android.contacts/...` URIs are accepted.
- Swiggy/Zomato deep links now have narrow package/scheme visibility plus HTTPS fallback when no handler exists.
- Manifest queries were completed for Contacts, Photos/Gallery, Calendar event drafts, Files, Calculator, and food deep links.
- Blocked fixtures were expanded for YouTube like/comment, Photos delete-all, Food pay/payment, and Contacts open-contact.
- Food blocked-term matching now avoids false positives like `payasam` while still refusing `pay for biryani`.
- Calendar recurrence refusal now includes the expected user-facing “should I make this repeat?” wording.
- UI typography audit found inherited negative letter spacing outside recipe files; I normalized the remaining app-wide negative values to `0.em` / `0.sp`.

RCA is logged in [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:2074), including why the misses were missed.

**Before / After**
Before: “Take a note: buy milk” could fall back to generic guidance, “Call Mom” risked prompt confusion with direct actions, Recipes-off could still leave recipe instructions in the model prompt, and installed Swiggy/Zomato deep links could fail visibility resolution.

After: Notes open the share sheet with text drafted, contact calls stop at `ACTION_DIAL`, food search opens app/web without ordering, calculator answers locally, unsafe photo/order/payment/YouTube engagement requests refuse, and Recipes-off actually suppresses recipe mode.

**Validation**
Passed:
- `git diff --check`
- Focused recipe/prompt/app validation
- Full validation: `:core:test :android-runtime:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
- Static safety scans: no `ACTION_CALL`, `SEND_SMS`, `CALL_PHONE`, broad storage/SMS/contact-write permissions, or `QUERY_ALL_PACKAGES` usage beyond forbidding comments.
- Emulator smoke: installed `app-debug.apk`, cold-launched Handy, process stayed live as PID `26895`, and fresh logcat had no `FATAL EXCEPTION`, `E/AndroidRuntime`, ANR, force-finish, or Handy process death.
- Visual sanity screenshot captured at `/tmp/handy-recipes-audit-onboarding.png`; no obvious text overlap after typography fixes.

Only notable build warning: pre-existing deprecated `announceForAccessibility(...)` in `ChatBubblesV2`.

**Manual Testing Plan**
Run this before release on Pixel and Samsung:

1. Clean install, enable Handy accessibility/automation/recipes, grant Contacts, seed `Mom`, `Maya`, `Rohan S`, `Rohan B`.
2. Happy paths: YouTube search/channel, Notes share text, Contacts open/dial/SMS drafts, Files picker/open document, Photos open/share-current from viewer, Calculator answer/open, Swiggy/Zomato search/track, Calendar v2 event draft with attendees.
3. Blocked paths: YouTube like/subscribe/comment, photo delete/delete-all, food order/checkout/pay/payment, file delete/move/upload, sensitive note/contact/calendar values, unsupported calculator functions, calendar recurrence.
4. Ambiguity: “Call Rohan” must show candidate chips and execute nothing.
5. Gates: Recipes disabled must remove recipe prompt and skip recipe runner; accessibility off/reduced mode must not execute recipes.
6. UI: verify candidate chips, confirmation sheets, progress bubble, and chat/onboarding text at normal and large font scale with no overlap.
7. Audit/logs: verify refused runs show the reason, completed runs only appear after verified execution, and no phone numbers are exposed to the LLM path.

I did not run the physical Pixel/Samsung sweep here; the emulator build, launch, and crash smoke are clean.
