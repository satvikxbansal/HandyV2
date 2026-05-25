# V2: Add shopping voice prompts

## Metadata

- Thread ID: `019e4dfd-2933-7a72-8bfb-8c9892db4e46`
- Created: 2026-05-22 10:11:37 IST
- Updated: 2026-05-22 10:23:37 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/22/rollout-2026-05-22T10-11-37-019e4dfd-2933-7a72-8bfb-8c9892db4e46.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `2ee317e1f3308c66bf6429818d0f69e98ed15cb9`
- Messages exported: 14

## Brief Summary

This conversation focused on: domain-scoped Hindi voice shopping on Meesho/Amazon/Flipkart. No likely related git commits were found for this thread. It may have been planning/review-only, debugging without a commit, or committed as part of a neighboring bundled thread.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `2ee317e1f3308c66bf6429818d0f69e98ed15cb9`
- Likely related commits: none found

## Conversation

### USER 2026-05-22T04:42:17.201Z

Goal: domain-scoped Hindi voice shopping on Meesho/Amazon/Flipkart.

Files to touch:
- core/src/main/kotlin/com/handy/core/prompts/QuickPromptCatalog.kt
  (add Meesho/Amazon/Flipkart quick prompts in Hindi + English)
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  (shopping-mode addendum: ask if returnable, find coupons, compare)
- (new) android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ShoppingRecipePack.kt

Acceptance:
- On a Meesho product page, panel shows shopping prompts; "compare
  with similar" calls fetch_page + summarises.

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS. 

Be very careful and detailed in your work. ensure you are thorough and comprehensive in your evaluation and implementation of everything

### ASSISTANT 2026-05-22T04:42:25.258Z

I’ll trace the existing prompt and recipe patterns first, then wire the shopping behavior in the same style so we don’t surprise the runtime. After that I’ll run the relevant build/tests and audit the diff for any sharp edges.

### ASSISTANT 2026-05-22T04:42:38.694Z

I see the worktree already has unrelated edits, including an existing change in `PromptCatalog.kt`. I’ll treat those as user work and make the smallest compatible edits around them rather than trying to “clean” the tree.

### ASSISTANT 2026-05-22T04:42:56.768Z

The recipe registry is split a little unusually: the runtime pack object lives inside `ClockRecipe.kt`, not a separate file. I’m checking that registration path now so the new shopping pack is actually discoverable by agent mode, not just sitting unused.

### ASSISTANT 2026-05-22T04:43:53.892Z

The acceptance has a subtle tension: native apps expose package names, while `fetch_page` needs a URL. I’m checking whether Handy already derives browser/site labels or URLs so the shopping mode can be scoped cleanly instead of pretending a native product screen always has a fetchable link.

### ASSISTANT 2026-05-22T04:45:44.147Z

I’ve got the shape now: quick prompts need a shopping category, the prompt addendum needs to be active only on Meesho/Amazon/Flipkart surfaces, and the runtime recipe pack should support safe shopping search/coupon discovery while leaving comparison to `fetch_page` as requested. I’m going to make those edits and add focused regression tests around the prompt/catalog behavior and recipe registration.

### ASSISTANT 2026-05-22T04:47:54.552Z

One important fix: for Meesho/Amazon/Flipkart opened in Chrome, the foreground package is still Chrome. Handy already keeps a browser site label, so I’m threading that into quick-prompt categorization; otherwise the native apps would work but `meesho.com` in a browser would still show generic browser chips.

### ASSISTANT 2026-05-22T04:49:18.795Z

The code is now wired and covered at the unit-test level. I’m doing a static diff pass first, then I’ll run the focused Gradle test/build tasks so we catch Kotlin/compiler issues rather than relying on eyeballing.

### ASSISTANT 2026-05-22T04:50:11.144Z

The diff check is clean. I’m running the focused JVM/unit suites now: `:core:test`, `:android-runtime:testDebugUnitTest`, and `:app:testDebugUnitTest`. The app test module is worth including because the panel prompt selection lives there.

### ASSISTANT 2026-05-22T04:50:22.306Z

Gradle couldn’t start because the shell can’t see a Java runtime. I’m checking for the bundled Android Studio JBR next; if it’s present, I’ll run the same tasks with `JAVA_HOME` pointed at it.

### ASSISTANT 2026-05-22T04:51:14.538Z

Found the local JBR under the user Applications folder, plus Gradle-managed JDKs. I’m rerunning Gradle with `JAVA_HOME` set explicitly so the build uses the same local runtime the project already points at.

### ASSISTANT 2026-05-22T04:51:38.622Z

Focused tests passed. I’m doing one more app-level build pass with `:app:assembleDebug` so we verify the actual debug APK path after the new runtime recipe file and prompt wiring.

### ASSISTANT 2026-05-22T04:51:47.645Z

Debug assembly is clean. I’m running `:app:lintDebug` as the last automated audit pass; lint is the thing most likely to catch Android-specific hazards that unit tests won’t.

### ASSISTANT 2026-05-22T04:53:37.582Z

Implemented the shopping mode path end to end.

**What Changed**
Before: Meesho/Amazon/Flipkart could look like generic app/browser context, so the panel might show generic chips like “Summarize this page”, and “compare with similar” was not strongly steered toward product-page evidence.

After: shopping surfaces get bilingual shopping chips and scoped shopping behavior:
- [QuickPromptCatalog.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/prompts/QuickPromptCatalog.kt:60) now detects Meesho, Amazon Shopping, Flipkart, and browser site labels like `Meesho`.
- [OverlayPresenter.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt:101) now passes the browser site label, so `meesho.com` in Chrome can show shopping prompts too.
- [PromptCatalog.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt:223) adds a shopping-mode addendum: returnability, coupons, compare, Hindi/Hinglish handling, and strong purchase/payment guardrails.
- [ShoppingRecipePack.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ShoppingRecipePack.kt:14) adds scoped recipes for product search and opening visible coupons/offers only.
- [ClockRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt:14) registers the shopping recipe pack.

Practical example: on a Meesho product page, the panel now offers “Returnable hai? / Is this returnable?”, “Coupon dhoondo / Find coupons”, “Similar se compare karo / Compare with similar”, and “Price sahi hai? / Is this a good price?”. If the user says “compare with similar”, the prompt tells the model to use `fetch_page` on the visible Meesho/Amazon/Flipkart product URL and summarize price, rating, delivery, returnability, coupons/offers, and a recommendation.

**Manual Test Plan**
1. Install the debug build, enable Accessibility + overlay, and enable Web Search in Handy settings. No Brave key is required for `fetch_page`, but Web Search must be enabled because that is the existing tool gate.
2. Open Meesho native app on a product page. Tap the Handy widget. Confirm greeting is shopping-specific and chips include returnability, coupons, compare, and price.
3. Repeat in Chrome on a `meesho.com` product URL. Confirm it does not show generic Chrome prompts.
4. Repeat quick-prompt smoke checks for Amazon Shopping and Flipkart native apps.
5. Open Chrome on `example.com`. Confirm shopping prompts do not appear.
6. Tap “Similar se compare karo / Compare with similar” on a visible Meesho product URL. Confirm the assistant calls `fetch_page`, then summarizes product evidence rather than giving generic advice.
7. Ask “returnable hai?” on a page with visible return/replacement text. Confirm it answers directly and points at the visible policy line/control.
8. Ask “coupon dhoondo”. Confirm it uses visible/fetched coupon evidence and does not invent coupon codes.
9. Ask unsafe requests: “buy now”, “add to cart”, “apply coupon”, “checkout”, “pay with UPI”, “change address”. Confirm Handy refuses to automate those and offers compare/summary/coupon/return help instead.
10. Ask “search cotton kurti for me” inside Meesho/Amazon/Flipkart. Confirm the shopping search recipe is proposed and only searches, with normal recipe confirmation.
11. Check Logcat during these flows for crashes, failed recipe proposals, or unexpected `dispatch_action` calls.

**Verification**
Passed:
- `JAVA_HOME="/Users/satvik.bansal/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :core:test :android-runtime:testDebugUnitTest :app:testDebugUnitTest`
- `JAVA_HOME="/Users/satvik.bansal/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`
- `JAVA_HOME="/Users/satvik.bansal/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:lintDebug`
- `git diff --check`

`lintDebug` completes successfully; it still reports existing non-fatal warnings in unrelated files. I left the pre-existing dirty worktree changes alone.
