# R1: Add policy-gated recipe runner

## Metadata

- Thread ID: `019e4a3a-d080-7c90-ab1c-700a873fe94f`
- Created: 2026-05-21 16:40:28 IST
- Updated: 2026-05-21 16:56:03 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/21/rollout-2026-05-21T16-40-28-019e4a3a-d080-7c90-ab1c-700a873fe94f.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `b2b44d79fb1275209b81e443ebd47859b51f292d`
- Messages exported: 13

## Brief Summary

This conversation focused on: deterministic, policy-gated, per-step verified multi-step. Likely related git changes: b8f91bd R1: Add agent recipe system and progress UI.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `b2b44d79fb1275209b81e443ebd47859b51f292d`
- Likely related commits:
  - `b8f91bd` 2026-05-21 17:07:12 IST [high] R1: Add agent recipe system and progress UI. Files: app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt, app/src/main/kotlin/com/handy/app/overlay/AgentProgressBubble.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt, core/src/main/kotlin/com/handy/core/agent/AppRecipe.kt, core/src/main/kotlin/com/handy/core/agent/RecipePlan.kt, core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt, core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt, core/src/main/kotlin/com/handy/core/agent/RecipeStep.kt, core/src/main/kotlin/com/handy/core/agent/UserGoal.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt, core/src/test/kotlin/com/handy/core/agent/RecipeRunnerTest.kt

## Conversation

### USER 2026-05-21T11:11:05.612Z

Goal: deterministic, policy-gated, per-step verified multi-step.

Files to touch (new):
- core/src/main/kotlin/com/handy/core/agent/UserGoal.kt
- core/src/main/kotlin/com/handy/core/agent/AppRecipe.kt
- core/src/main/kotlin/com/handy/core/agent/RecipePlan.kt
- core/src/main/kotlin/com/handy/core/agent/RecipeStep.kt
- core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt
- core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt
- app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt
- app/src/main/kotlin/com/handy/app/overlay/AgentProgressBubble.kt

Files to touch:
- app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt
  (when assistant says "use recipe X with args Y", call
   RecipeRegistry → propose → policy.decide each step → user
   approves plan + each sensitive step → RecipeRunner.run())
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  (agent-mode addendum: model picks recipe + args, NEVER emits
   raw executable plan)

Hard rules in RecipeRunner:
- max 5 steps, abort on package change, sensitive step requires
  per-step confirmation, every step re-captures, every step calls
  policy.decide on the fresh snapshot.

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS. 

Acceptance:
- Unit tests for the runner with a fake recipe, fake performer,
  fake policy.
- A plan emitted via "trick" prompt (claiming Pay → Tap Pay) is
  refused by policy.

### ASSISTANT 2026-05-21T11:11:13.471Z

I’ll wire this end to end and verify it rather than just sketching it. I’m going to first map the existing agent, overlay, policy, and test patterns so the new recipe layer fits the app instead of sitting beside it awkwardly.

### ASSISTANT 2026-05-21T11:11:23.647Z

The repo already has a guarded one-step action path, plus screen snapshots and a presenter FSM. I’m reading those now so the recipe runner can reuse the existing policy surface and confirmation style instead of inventing a second safety system.

### ASSISTANT 2026-05-21T11:13:39.953Z

The existing tap-for-me flow already rechecks policy at the performer layer, but multi-step needs a stricter outer loop: validate the recipe plan, dismiss the panel so fresh captures see the target app, then re-capture and re-authorize before each individual step. I’m going to keep the model’s role deliberately small: choose a named recipe and JSON args, never choose executable steps.

### ASSISTANT 2026-05-21T11:15:37.616Z

I’ve got the shape now. I’m adding the pure Kotlin recipe contract and runner first, with dynamic target resolution against each fresh snapshot, then I’ll hook the overlay through a small app controller and verify with focused runner tests plus compile/test tasks.

### ASSISTANT 2026-05-21T11:22:08.444Z

The core runner and app bridge are in place now. Next I’m compiling the pure Kotlin pieces first; that usually catches the shape mistakes early before the Android compile adds Hilt and Compose noise.

### ASSISTANT 2026-05-21T11:22:35.070Z

The first compile caught two Kotlin sharp edges in the runner: early returns inside an expression-body helper, and a sealed-class property name collision. I’m tightening those now and rerunning the same focused tests.

### ASSISTANT 2026-05-21T11:22:55.010Z

The focused core tests pass now, including the policy refusal case for the “Pay” trick prompt. I’m moving up to the Android app compile next, where Hilt constructor wiring and Compose API mismatches will show up.

### ASSISTANT 2026-05-21T11:23:13.239Z

App Kotlin compilation is green, including Hilt generation for the new controller and the progress bubble overlay. I’m running the broader unit suite and assembling debug now so we catch resource, packaging, and JVM test regressions too.

### ASSISTANT 2026-05-21T11:23:29.328Z

The broad JVM tests and debug assemble are green. I’m checking whether there’s an attached emulator; if there is, I’ll install and do a quick launch/crash smoke check rather than stopping at compile-only confidence.

### ASSISTANT 2026-05-21T11:23:51.781Z

There is an emulator attached, so I’m doing the extra smoke check: install the debug APK, launch the app’s entry activity, then inspect logcat for fresh AndroidRuntime crashes.

### ASSISTANT 2026-05-21T11:24:41.287Z

Install and launch smoke passed: the app process stayed alive and fresh logcat had no `AndroidRuntime` / fatal exception entries. I’m doing one last static sweep over the touched files for awkward safety gaps and then I’ll wrap it with the practical test plan you asked for.

### ASSISTANT 2026-05-21T11:26:03.436Z

Implemented the deterministic recipe path end to end.

**What Changed**
Before: Handy could point at or tap a single visible element, but multi-step UI work was still effectively conversational. For example, “search YouTube for lo-fi” would lead to guidance or a single pointer, not a deterministic run.

After: the model may only say `use recipe X with args {...}`. The app parses that, asks `RecipeRegistry` to build a deterministic plan, checks policy, asks the user to approve, then `RecipeRunner` re-captures and re-checks policy before every step. Example: a safe search recipe can focus a search field, type a query, and submit, while a “tap Pay” trick is refused by policy before execution.

Core files added include [RecipeRunner.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt), [RecipeRegistry.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRegistry.kt), and the recipe model files. App integration is in [AgentSessionController.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt), [OverlayChatPipeline.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt), and [AgentProgressBubble.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/AgentProgressBubble.kt).

**Safety Gates**
- Max 5 steps enforced in `RecipeRunner`.
- Package changes abort the run.
- Every step re-captures before execution.
- Every step calls `policy.decide` on the fresh snapshot.
- Every step captures again after execution and verifies.
- Sensitive steps require per-step confirmation.
- The prompt now tells the model to pick recipe + args only, never raw executable plans.

**Validation Done**
- `:core:test --tests RecipeRunnerTest --tests PromptCatalogTest` passed.
- `:app:compileDebugKotlin` passed.
- `:core:test :android-runtime:test :app:testDebugUnitTest :app:assembleDebug` passed.
- `:app:lintDebug` passed.
- `:app:installDebug` passed on `emulator-5554`.
- Launched `com.handy.app.onboarding.OnboardingActivity`; app stayed alive and fresh logcat had no `AndroidRuntime` / fatal crash entries.

**Manual Testing Plan**
1. Safe recipe path: on a search screen, ask Handy to search for a harmless query. Confirm the raw recipe directive is not shown to the user, the plan sheet appears, progress bubble appears, and the search completes.
2. Policy refusal: on a checkout/payment screen, try “tap Pay” or a prompt-injection style request. Expected: no tap, policy refusal shown.
3. Sensitive confirmation: use a visible “Delete”/“Remove” control. Expected: plan approval plus per-step confirmation; canceling stops execution.
4. Package-change abort: approve a plan, then switch apps before the first step. Expected: recipe aborts, no action fires.
5. Per-step freshness: change screen layout/rotate/open keyboard mid-run. Expected: stale target or package/snapshot mismatch stops execution.
6. Ambiguous target: show two identical buttons and request one by label. Expected: policy refuses ambiguous target.
7. Gate closed: disable tap-for-me/action disclosure. Expected: recipe plan may be proposed, but execution is refused by policy/gate.
8. Safe typing: type benign text into a normal search/text field. Expected: user can review/edit typed text, then text appears.
9. Secret typing: try OTP/password/card/CVV text or fields. Expected: policy refuses.
10. Overlay UX: drag widget while progress bubble is visible; verify the bubble follows/clamps and disappears after completion/failure.
