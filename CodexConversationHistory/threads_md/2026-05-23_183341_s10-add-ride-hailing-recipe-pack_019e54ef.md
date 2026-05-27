# S10: Add ride-hailing recipe pack

## Metadata

- Thread ID: `019e54ef-2f6c-7711-9d02-cfa347560f52`
- Created: 2026-05-23 18:33:41 IST
- Updated: 2026-05-23 18:49:01 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T18-33-41-019e54ef-2f6c-7711-9d02-cfa347560f52.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `6c2a51cf3b26bfb9f6e984a74e4d59c6b6910d4a`
- Messages exported: 12

## Brief Summary

This conversation focused on: A recipe pack that opens Uber / Ola / Rapido, searches for the destination, lets the user see the cheapest option, and STOPS BEFORE the final "Confirm Ride" tap. Same pattern as WhatsApp recipe stopping before Send. THIS RECIPE PACK NEVER TAPS A CONFIRM/REQUE… Likely related git changes: 762e93f S10: Ride-hailing recipe pack (Uber + Ola + Rapido).

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `6c2a51cf3b26bfb9f6e984a74e4d59c6b6910d4a`
- Likely related commits:
  - `762e93f` 2026-05-23 18:51:47 IST [high] S10: Ride-hailing recipe pack (Uber + Ola + Rapido). Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/RideHailingRecipePack.kt, android-runtime/src/main/kotlin/com/handy/runtime/storage/LearnedAllowlistStore.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RideHailingRecipePackTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt, app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt, core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt, core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt, core/src/main/kotlin/com/handy/core/agent/RecipeStep.kt

## Conversation

### USER 2026-05-23T13:03:56.995Z

Read the standing rules. Single-pass.

GOAL
A recipe pack that opens Uber / Ola / Rapido, searches for the
destination, lets the user see the cheapest option, and STOPS BEFORE
the final "Confirm Ride" tap. Same pattern as WhatsApp recipe
stopping before Send.

THIS RECIPE PACK NEVER TAPS A CONFIRM/REQUEST/BOOK BUTTON. The final
tap is the user's — that's a payment action and must remain
user-initiated. The recipe's last in-chat message must say "Ready to
go — tap Confirm in {app} when you're ready."

FILES TO KNOW
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/WhatsAppRecipe.kt
  (mirror the multi-step + stop-before-send pattern exactly)
- android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ShoppingRecipePack.kt
  (mirror the pack structure)
- android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt
  (verify ride-hailing packages are NOT in ActionAppPolicy.STATIC_DENYLIST
  and that BETA_BLOCKED_TERMS / UI_BETA_BLOCKED_PHRASES already cover
  "request" / "confirm" / "book" / "place order" — these are the
  belt-and-suspenders against you accidentally adding a Confirm step)
- android-runtime/src/main/kotlin/com/handy/runtime/storage/LearnedAllowlistStore.kt
  (verify ride apps will NOT be auto-allowed for gesture fallback —
  add them to the exclusion list if such a list exists; otherwise
  document the recipe's per-step `allowGestureFallback = false`)
- core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt
  (MAX_STEPS = 6; each recipe must fit)

App packages:
- Uber: com.ubercab
- Ola: com.olacabs.customer
- Rapido: com.rapido.passenger

IMPLEMENT
Add android-runtime/.../recipes/RideHailingRecipePack.kt:

object RideHailingRecipePack {
    fun defaultRecipes() = listOf(
        UberRideRecipe, OlaRideRecipe, RapidoRideRecipe,
    )
}

Each recipe shares the same shape via a small helper. Steps (4-5):
  1. NativeAction OpenApp(packageName) allowPackageChangeAfter=true.
  2. Tap "Where to?" / destination field. Multi-matcher fallback:
     viewId suffix containing "destination" or "search" or "where",
     OR content description "Where to?" / "Search destination",
     OR role=edittext role-only match.
  3. TypeText destination string.
  4. Tap first search result row (text matches destination tokens,
     OR position-first-list-item via viewId suffix "list_item_0").
  5. (Optional) Tap a card matching "Auto" / "UberGo" / "Mini" /
     "Bike" based on cheapest-class hint, ONLY if a stable
     identifier exists. Mark this step `sensitive = true` with
     `confirmationOverride = STRONG_HOLD`. If no stable identifier,
     OMIT step 5 and let the user pick.

  ABSOLUTELY DO NOT add any step targeting "Confirm" / "Request" /
  "Book" / "Choose" / payment buttons. Add an inline comment in code
  citing this rule.

In propose():
- arg "destination" / "to" / "place" or text after "book a cab to"
  / "uber to" / "ride to".
- Refuse missing destination with "missing-destination".
- Refuse if foreground package is not the recipe's package AND
  recipe is responsible for opening it (step 1 handles that).

Add at the end of the runner's success path (in AgentSessionController
or wherever the recipe-completed message is emitted), if the recipe
id is one of the three: append a chat message "Ready to go — tap
Confirm in ${app} when you're ready." Use the existing message-
posting path; don't invent a new one.

Update:
- AndroidRuntimeRecipes.defaultRecipes() — add the pack.
- RecipeIntentRouter — BOOK_RIDE → pick the recipe matching the
  arg/foreground package, falling back to UberRideRecipe.
- PromptCatalog: intent example "book a cab to airport →
  [INTENT:book_ride]" plus a one-line rule: "for book_ride, Handy
  will prepare the booking but the user must tap Confirm/Request
  themselves."
- docs/ACTION_POLICY.md: add the ride-hailing pack to the
  per-recipe table (setup steps NORMAL, cheapest-pick STRONG_HOLD,
  ride confirmation BLOCKED by recipe design).
- docs/SECURITY_MODEL.md: add a new threat row "Ride-hailing app
  UI drift breaks the recipe" with mitigation "multi-matcher
  targets + graceful Refused + quarterly smoke."

Tests:
- RideHailingRecipePackTest:
  * Each recipe proposes a plan with NO confirm-class step (assert
    that no step's command text contains any of: "confirm",
    "request", "book", "choose", "place order").
  * Refused on missing destination.
  * expectedPackage on every step equals the recipe's package.
  * Cheapest-pick step (when present) is sensitive=true and
    confirmationOverride=STRONG_HOLD.
- DefaultActionPolicyEngineTest: even if a step were synthetically
  injected targeting "Confirm UberGo", the engine refuses it via
  BETA_BLOCKED_TERMS / UI_BETA_BLOCKED_PHRASES.

SELF-VERIFY
./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug
Manual smoke (required, document outcome in DEBUG_LOG):
- On a real device with Uber installed and signed in to a test
  account, run "book a cab to {nearby place}". Confirm Handy stops
  before the Confirm tap and the chat says "Ready to go — tap
  Confirm in Uber when you're ready." Take a screenshot, attach
  path to DEBUG_LOG.

STOP CONDITIONS (revert + report only)
- The ride-hailing apps' UI cannot be navigated via stable matchers
  from accessibility nodes at all — the destination field has no
  detectable viewId, content description, or text. In that case
  ship with just step 1 (open the app) and document the limitation;
  do not invent fragile coordinate taps.

DEBUG_LOG ENTRY: standard, plus prevention rule "any recipe whose
final step would be a payment / confirm / send / delete action
MUST end one step before that action and post a chat message
telling the user to complete the final tap in-app." 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-23T13:04:06.599Z

I’ll read the relevant recipe, policy, runner, and docs paths first so the implementation follows the existing shape instead of inventing a parallel one. Then I’ll patch the pack, routing, completion message, tests, and docs in one pass and run the requested verification.

### ASSISTANT 2026-05-23T13:04:19.897Z

I found the core hooks: runtime recipes are appended in `AgentSessionController`, `MAX_STEPS` is 6, and `BOOK_RIDE` already exists as an intent enum. Next I’m reading the recipe DSL and completion-message flow so the new pack lands in the existing lane.

### ASSISTANT 2026-05-23T13:04:57.859Z

The existing target resolver only supports one exact selector at a time, so the ride recipe needs a small, general extension there to express “try these stable selectors, then fall back to edit text.” I’m keeping that scoped to `RecipeTarget.Node` rather than adding coordinates or app-specific hacks.

### ASSISTANT 2026-05-23T13:08:27.400Z

**Plan**
Phase 4 / action-policy recipe runner work. Goal: add deterministic ride-hailing recipes that open Uber/Ola/Rapido, search a destination, optionally select a stable ride class, and always stop before any booking/payment confirmation.

Files I’ll touch and why:
- `core/.../RecipeStep.kt`: add narrowly-scoped target alternatives/contains matching so ride fields/results can use stable multi-matchers without coordinates.
- `android-runtime/.../RideHailingRecipePack.kt` and `ClockRecipe.kt`: add and register the three ride recipes.
- `core/.../RecipeIntentRouter.kt` and `PromptCatalog.kt`: route `book_ride` by app/foreground package and teach the prompt the stop-before-confirm rule.
- `AgentSessionController.kt`: post the final “Ready to go…” message on successful ride recipe completion through the existing presenter response path.
- `DefaultActionPolicyEngine.kt`, `LearnedAllowlistStore.kt`: add the requested belt-and-suspenders blocks and prevent learned gesture fallback for ride packages.
- tests plus `docs/ACTION_POLICY.md`, `docs/SECURITY_MODEL.md`, `DEBUG_LOG.md`: cover the behavior and document the safety posture.

Interfaces I’m not changing: no new modules, no new action performer, no coordinate/pixel fallback, no free-form plan execution, no changes to `MAX_STEPS`.

Risks and checks: target matching could affect existing recipes, so I’ll keep defaults backward-compatible and run the requested full Gradle command. The ride flow risk is accidental confirm-class targeting, so tests will assert no confirm/request/book/choose/place-order command text and policy will deny synthetic “Confirm UberGo.”

### ASSISTANT 2026-05-23T13:15:55.573Z

The core pieces are now patched: ride recipes, routing, policy guardrails, and docs. I’m doing a quick diff/compile sanity pass next, because the target-matcher extension is the one place a small Kotlin signature mistake would show up fast.

### ASSISTANT 2026-05-23T13:16:01.961Z

The diff check is clean. I’m going to run focused core/runtime tests first so any recipe/router/policy issue is cheap to isolate before the full build-lint-assemble run.

### ASSISTANT 2026-05-23T13:16:15.401Z

Focused tests are green, including the new ride recipe assertions and policy denial for a synthetic “Confirm UberGo.” I’m moving to the full requested verification now.

### ASSISTANT 2026-05-23T13:16:53.311Z

The full requested Gradle command passed cleanly. I’m checking connected devices now for the required Uber manual smoke; if there isn’t a signed-in real device available, I’ll record that explicitly instead of pretending the smoke happened.

### ASSISTANT 2026-05-23T13:17:15.160Z

Only an emulator is attached (`emulator-5554`), not a real signed-in Uber device, so the required Uber manual smoke is blocked by environment. I’ll still install and launch the debug app on the emulator to catch startup crashes, then I’ll document the manual-smoke blockage in `DEBUG_LOG.md`.

### ASSISTANT 2026-05-23T13:17:55.567Z

Startup smoke on the emulator is clean too: install succeeded, the app process stayed alive, and a crash-pattern logcat scan returned no matches. I’m appending the required `DEBUG_LOG` entry now, including the real-device Uber smoke as blocked by environment rather than completed.

### ASSISTANT 2026-05-23T13:19:01.789Z

Implemented the ride-hailing recipe pack and guardrails.

Before: `book a cab to airport` had a canonical `book_ride` intent but no runtime recipe, so Handy could not safely prepare a ride flow.

After: Handy can route to Uber, Ola, or Rapido, open the app, fill the destination, select the first destination result, optionally tap a stable cheapest-class card, and then stop before the final ride confirmation. The final user-facing message is exactly:

`Ready to go — tap Confirm in Uber/Ola/Rapido when you're ready.`

Key files:
- [RideHailingRecipePack.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/RideHailingRecipePack.kt)
- [RecipeIntentRouter.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeIntentRouter.kt)
- [AgentSessionController.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt)
- [DefaultActionPolicyEngine.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt)

Verification passed:
- `git diff --check`
- focused recipe/router/policy/prompt tests
- `./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug`
- `:app:installDebug`, app launch on emulator, process stayed alive, crash-pattern logcat scan clean

Manual Uber real-device smoke was blocked: only `emulator-5554` was attached, Uber was not installed there, and no signed-in real Uber device was available. I documented that in [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md).

**Manual Test Plan**
1. Use a real Android device with Handy debug installed, Accessibility enabled, Tap-for-me enabled, and Uber/Ola/Rapido installed with test-safe accounts.
2. From a neutral app, ask `book a cab to airport`; verify Handy defaults to Uber, opens Uber, enters the destination, selects the first matching result, shows ride options, and stops before Confirm.
3. Repeat with `ola to airport` and `rapido to airport`; verify routing picks the named app.
4. Start while Rapido/Ola/Uber is foreground and ask `book a cab to nearby cafe`; verify foreground package routing wins when no app is named.
5. Test missing destination: `book a cab`; expect refusal `missing-destination`.
6. Test cheapest-class only with stable IDs in a controlled accessibility snapshot; verify the class step is `STRONG_HOLD`.
7. On each app, inspect that Handy never taps Confirm, Request, Book, Choose, payment, or saved-payment controls.
8. Confirm final chat text says `Ready to go — tap Confirm in {app} when you're ready.`
9. Capture screenshot after Handy stops before Confirm and attach its path to `DEBUG_LOG.md`.

Advanced Phase 4 / ActionPolicyEngine + deterministic recipes — acceptance check: bounded recipes re-check policy, stop before payment/confirm actions, and pass full build/test/lint — status: PASS, with real-device Uber smoke BLOCKED by environment.
