# P-POLICY-1: UiActionIntent + turn-scoped ToolProvenance thr…

## Metadata

- Thread ID: `019e63a7-13d8-7131-b5d2-352f21aa9d3e`
- Created: 2026-05-26 15:09:14 IST
- Updated: 2026-05-26 15:45:58 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/26/rollout-2026-05-26T15-09-14-019e63a7-13d8-7131-b5d2-352f21aa9d3e.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `4744f20cead651482ecf33b1a984977cccb453fd`
- Messages exported: 30

## Brief Summary

This conversation focused on: Two related fixes that GPT's audit identified, treated as one because they share infrastructure: (a) Replace PolicyGuardedActionPerformer's synthesised OpenApp action with a UiActionIntent that carries semantic kind + user utterance + target label/role/markId… Likely related git changes: 33d0563 P-POLICY-1 — UiActionIntent + turn-scoped ToolProvenance threaded into recipes.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `4744f20cead651482ecf33b1a984977cccb453fd`
- Likely related commits:
  - `33d0563` 2026-05-26 15:46:42 IST [medium] P-POLICY-1 — UiActionIntent + turn-scoped ToolProvenance threaded into recipes. Files: DEBUG_LOG.md, README.md, android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/action/NoopActionPerformer.kt, android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt, android-runtime/src/main/kotlin/com/handy/runtime/llm/ClaudeLlmClient.kt, android-runtime/src/main/kotlin/com/handy/runtime/llm/GeminiCloudLlmClient.kt, android-runtime/src/main/kotlin/com/handy/runtime/llm/HandyToolRunner.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/llm/HandyToolRunnerProvenanceTest.kt, app/src/androidTest/kotlin/com/handy/app/agent/RecipeNativeActionExecutionTest.kt, app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt

## Conversation

### USER 2026-05-26T09:39:29.468Z

GOAL
Two related fixes that GPT's audit identified, treated as one because
they share infrastructure:

(a) Replace PolicyGuardedActionPerformer's synthesised OpenApp action
    with a UiActionIntent that carries semantic kind + user utterance
    + target label/role/markId so policy can reason about meaning.

(b) Make tool provenance turn-scoped (not single-bit, not cleared by
    safe tools) and thread it through the recipe execution path so
    actions triggered after an untrusted-tool turn cannot run.

VERIFIED PRE-CONDITIONS
- app/src/main/kotlin/com/handy/app/accessibility/PolicyGuardedActionPerformer.kt:59-69
  synthesises AssistantAction.OpenApp(packageHint=…) for every kind in
  ["tap","long_press","scroll"] and hardcodes sourceTrust = TRUSTED_RECIPE.
- android-runtime/.../llm/HandyToolRunner.kt:237-246
  mostRecentToolResultWasUntrusted is an AtomicBoolean, set by name in
  UNTRUSTED_TOOL_NAMES = {"web_search","github_search","fetch_page"},
  cleared on the next safe tool, cleared on read via getAndSet(false).
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
  flyToAndTap/flyToAndType always pass SourceTrust.TRUSTED_RECIPE in
  policyEngine.decide(...).
- DefaultActionPolicyEngine.kt:94-96 blocks UNTRUSTED_TOOL actions with
  ActionRisk.HIGH + reason "tool-suggestion-only". That's the right gate;
  we just need it to be reachable.

IMPLEMENTATION

(a) UiActionIntent

1. New action type:
   File: core/src/main/kotlin/com/handy/core/action/AssistantAction.kt (extend)
       sealed class AssistantAction { … existing variants … 

           data class UiAction(
               val kind: UiActionKind,
               val userUtterance: String?,   // null if not user-initiated (e.g. recipe-driven)
               val targetLabel: String?,
               val targetRole: String?,
               val targetMarkId: String?,
               val targetViewId: String?,
               val typedText: String? = null,  // only for TYPE
               val proposedPackage: String?,
           ) : AssistantAction()

           enum class UiActionKind { TAP, LONG_PRESS, SCROLL_UP, SCROLL_DOWN, SCROLL_LEFT, SCROLL_RIGHT, TYPE }
       }

2. Wire PolicyGuardedActionPerformer.guard() to build a UiAction:
   File: app/src/main/kotlin/com/handy/app/accessibility/PolicyGuardedActionPerformer.kt
   Replace the synthesised OpenApp with:
       val action = AssistantAction.UiAction(
           kind = when (kind) {
               "tap" -> UiActionKind.TAP
               "long_press" -> UiActionKind.LONG_PRESS
               "type_text" -> UiActionKind.TYPE
               "scroll_up" -> UiActionKind.SCROLL_UP
               "scroll_down" -> UiActionKind.SCROLL_DOWN
               "scroll_left" -> UiActionKind.SCROLL_LEFT
               "scroll_right" -> UiActionKind.SCROLL_RIGHT
               else -> error("unknown kind: $kind")
           },
           userUtterance = null,   // not available at this seam yet; future enhancement
           targetLabel = (target as? TapTarget.AtNode)?.text,
           targetRole = (target as? TapTarget.AtNode)?.role,
           targetMarkId = (target as? TapTarget.AtNode)?.markId,
           targetViewId = (target as? TapTarget.AtNode)?.viewId,
           typedText = text,
           proposedPackage = target.packageNameOrNull() ?: grounding.toolContext.packageName,
       )
   Also: stop hardcoding TRUSTED_RECIPE. The caller's sourceTrust must
   flow in. PolicyGuardedActionPerformer is called from
   AgentSessionController (recipe path, TRUSTED_RECIPE), from
   BuddyFlightDriver (tap-for-me path, TRUSTED_USER unless tool-tainted),
   and from HandyToolRunner.dispatch_action (TRUSTED_USER unless
   tool-tainted). Add a `sourceTrust: SourceTrust` parameter to each
   ActionPerformer method overload, default = TRUSTED_USER for
   backwards compat, and thread it from each caller.

3. Policy engine semantic checks:
   File: android-runtime/.../action/DefaultActionPolicyEngine.kt
   Add a new private function `isUiActionDestructive(action: UiAction): Boolean`
   that checks BOTH:
     - target label/desc contains UI_BETA_BLOCKED_PHRASES (existing logic
       moved here), AND
     - new: action.userUtterance (if non-null) matches DESTRUCTIVE_UTTERANCE_REGEX
       (e.g. \b(send|pay|buy|delete|transfer|confirm|submit|order|book)\b).
   When destructive: return strong-hold confirmation.

   Add to the `when` branch in `decide(action, ...)`:
       is AssistantAction.UiAction -> {
           // Existing target-text checks still apply via .isBetaBlocked.
           // Add semantic check on userUtterance.
           if (isUiActionDestructive(action)) return strongHold("destructive-intent")
           // Recipe-driven scrolls/taps that DIDN'T come from a user
           // utterance still pass through risk=MEDIUM with normal
           // confirmation (matches current behaviour).
           …
       }

(b) Turn-scoped ToolProvenance

1. New data class:
   File: core/src/main/kotlin/com/handy/core/llm/ToolProvenance.kt
       data class ToolProvenance(
           val turnId: String,
           val usedUntrustedTools: Set<String> = emptySet(),
           val untrustedDomains: List<String> = emptyList(),
           val containsActionLikeInstruction: Boolean = false,
       ) {
           val isUntrusted: Boolean get() = usedUntrustedTools.isNotEmpty()
       }

2. Rewrite HandyToolRunner provenance:
   File: android-runtime/.../llm/HandyToolRunner.kt
   Replace `mostRecentToolResultWasUntrusted: AtomicBoolean` with a
   per-turn accumulator keyed by `turnId`:
       private val provenanceByTurn = ConcurrentHashMap<String, ToolProvenance>()

   - When a tool returns, MERGE into the turn's provenance:
         provenanceByTurn.compute(turnId) { _, prev ->
             val base = prev ?: ToolProvenance(turnId)
             if (name in UNTRUSTED_TOOL_NAMES && result is ToolResult.Ok) {
                 base.copy(
                     usedUntrustedTools = base.usedUntrustedTools + name,
                     untrustedDomains = base.untrustedDomains + extractDomains(result),
                     containsActionLikeInstruction = base.containsActionLikeInstruction ||
                         result.bodyContainsActionLikeInstruction(),
                 )
             } else base
         }
     Safe tools do NOT clear untrusted state.
   - Expose `currentTurnProvenance(turnId): ToolProvenance?` for callers.
   - Clear the entry only when the turn finalises (orchestrator emits
     AssistantTurnFinalized) — wire via a small `onTurnEnd(turnId)` hook.

3. Thread provenance into ConversationOrchestrator:
   File: core/.../ConversationOrchestrator.kt
   - When emitting AssistantTurnFinalized, include `provenance: ToolProvenance?`
     fetched from ToolRunner.
   - Add `provenance` to the data class.

4. Pass provenance into recipe + flight paths:
   File: app/.../overlay/OverlayChatPipeline.kt
   - Capture `event.provenance` in the AssistantTurnFinalized branch.
   - When calling agentSessionController.runIfRecipeRequested(...), pass
     provenance.
   File: app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt
   - Add `provenance: ToolProvenance?` parameter to runIfRecipeRequested.
   - When calling policyEngine.decide(...) for each step's deferred check,
     pass sourceTrust = if (provenance?.isUntrusted == true) UNTRUSTED_TOOL else step.policySourceTrust().
   File: app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
   - Add `provenance: ToolProvenance?` parameter to flyTo / flyToAndTap /
     flyToAndType.
   - When deciding policy, pass sourceTrust = if (provenance?.isUntrusted == true) UNTRUSTED_TOOL else TRUSTED_USER.
   - Plumb from OverlayChatPipeline (which already has the provenance).

5. Policy engine UNTRUSTED_TOOL handling (already partially there at line 94-96):
   File: DefaultActionPolicyEngine.kt
   - Tighten: when UNTRUSTED_TOOL, ALL side-effecting actions are denied
     with risk=HIGH + reason="tool-suggestion-only".
   - Informational actions (WebSearchIntent — opening a URL after
     user confirmation) remain allowed but require ConfirmationLevel.STRONG_HOLD.

ACCEPTANCE
- Action triggered by an LLM turn that called fetch_page → policy denies
  with "tool-suggestion-only", even if the recipe path is the executor.
- Action triggered by a user-typed turn that did NOT call any untrusted
  tool → policy decides normally.
- "Buy milk" said into the panel → assistant says "I drafted a note in
  Keep". Tap-Send button on Gmail (label = "Send") in the same flow is
  fine because no untrusted tools involved.
- An LLM turn that called web_search then synthesised a UiAction.TAP
  with userUtterance="send the form" → policy emits strong-hold + denies.
- The single-bit clearing bug is gone: web_search → safe_tool → action
  still treats the action as UNTRUSTED_TOOL.
- Generic taps where target text is "Continue" but userUtterance is
  "send the email" → strong-hold confirmation required, not normal.

TESTS
- DefaultActionPolicyEngineTest (extend):
  - UiAction(kind=TAP, userUtterance="pay now") → strong-hold.
  - UiAction(kind=TAP, userUtterance="cancel") → normal.
  - UiAction with provenance.isUntrusted=true → denied.
  - Provenance with usedUntrustedTools={web_search, fetch_page} → denied.
  - Multi-tool turn web_search → safe_tool → action → still denied.
- HandyToolRunnerProvenanceTest (new):
  - Two-tool turn, untrusted then safe, action → ToolProvenance.isUntrusted = true.
  - Concurrent turns A and B, each calling tools → provenance keyed
    correctly per turnId.
  - onTurnEnd(A) clears A's entry; B's persists.
- AgentSessionControllerTest:
  - Recipe step with provenance.isUntrusted=true → policy denied,
    progress bubble shows "policy refused …", no execution.
- BuddyFlightDriverTest:
  - flyToAndTap with provenance.isUntrusted=true → policy denied,
    pointer dismissed.
- PolicyGuardedActionPerformerSemanticTest (new):
  - tap with target.text="Send" → semantic check on the target text
    triggers strong-hold (existing UI_BETA_BLOCKED_PHRASES).
  - tap with userUtterance="send the form" + benign target → triggers
    strong-hold via the new utterance check.

DO NOT
- Block legitimate read-only actions ("open this article", "summarise
  this page"). The firewall is on Android-side side effects only.
- Remove the existing UI_BETA_BLOCKED_PHRASES check.
- Change the default SourceTrust for recipes that DIDN'T involve tools
  — they stay TRUSTED_RECIPE.
- Persist ToolProvenance in chat history.

UPDATE
- DEBUG_LOG.md.
- README capability note: "When Handy uses web tools, it can summarise
  but cannot act on the page's instructions." 


Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS

### ASSISTANT 2026-05-26T09:39:38.880Z

I’ll trace the existing action/policy/provenance flow first, then make the smallest set of edits that lets the same trust signal reach recipes, flight taps, and direct tool-dispatched actions. After that I’ll run the relevant tests/build and patch whatever shakes out.

### ASSISTANT 2026-05-26T09:39:47.013Z

I found the expected Android/Kotlin files and one unrelated untracked folder already present. I’m leaving that alone and reading the policy/action seams now.

### ASSISTANT 2026-05-26T09:40:21.614Z

The current shape matches the audit: the policy engine already has the right denial reason, but `HandyToolRunner` collapses provenance into one mutable bit, and the UI performer disguises every tap as `OpenApp`. I’m going to add the semantic action model and then carry a per-turn provenance object through the orchestrator and overlay/recipe paths.

### ASSISTANT 2026-05-26T09:44:35.773Z

First edit pass: I’m adding the `UiAction`/`ToolProvenance` types and expanding the two core interfaces in a backwards-compatible way, so existing callers still compile while the new paths can pass explicit turn/source context.

### ASSISTANT 2026-05-26T09:46:41.245Z

The turn ID now goes into every tool-aware LLM request, and the orchestrator will emit a provenance snapshot with the finalized assistant turn while clearing runner state afterward. Next I’m replacing the runner’s single trust bit with a real per-turn accumulator.

### ASSISTANT 2026-05-26T09:47:12.930Z

The runner now preserves untrusted provenance across later safe/unknown tools in the same turn and keys it by turn ID. I’m moving into the policy engine now, where the new semantic action gets classified before anything reaches the Android-side executor.

### ASSISTANT 2026-05-26T09:47:53.497Z

The policy engine now understands `UiAction` directly: trusted destructive UI intent requires a strong hold, untrusted tool-tainted side effects are denied with `tool-suggestion-only`, and web-search handoff remains possible only behind a strong hold. I’m plumbing that provenance through overlay recipes and tap/type flights next.

### ASSISTANT 2026-05-26T09:52:16.274Z

I’ve added the core regressions for policy semantics, runner provenance, recipe refusal, and the policy-guarded performer’s new semantic payload. Time to let Gradle tell us what I missed; I’ll start with the unit-test modules before the full debug assemble.

### ASSISTANT 2026-05-26T09:52:27.709Z

Gradle stopped immediately because this machine doesn’t currently expose a Java runtime to the shell, so I can’t get compiler feedback yet. I’m going to do a static pass with `rg` and targeted Kotlin reads to catch signature/import issues, then I’ll try a build again if I find a local JDK path.

### ASSISTANT 2026-05-26T09:55:19.051Z

There is a repo-local JDK at `/Users/satvik.bansal/.cache/codex-jdk17`, so the earlier Java failure was just PATH/JAVA_HOME. I’m rerunning validation with that JDK now.

### ASSISTANT 2026-05-26T09:56:06.649Z

The main unit suites are green with the repo-local JDK. I’m doing the final packaging pass next (`diff --check`, lint/assemble) so we catch formatting/package-time errors too.

### ASSISTANT 2026-05-26T09:56:53.438Z

Packaging and lint passed cleanly, and `git diff --check` is clean. I’m checking whether an emulator/device is available for a quick install/launch crash smoke; if none is attached, I’ll call that out explicitly.

### ASSISTANT 2026-05-26T09:57:09.550Z

An emulator is attached. I’m installing the freshly built debug APK and launching it for a crash smoke, then I’ll scan logcat for `FATAL EXCEPTION`, `AndroidRuntime`, ANR, or process death.

### ASSISTANT 2026-05-26T09:57:54.330Z

The emulator smoke passed too: the freshly installed debug app launched, the Handy process stayed alive, and the crash-pattern scan found no `FATAL EXCEPTION`, `AndroidRuntime`, ANR, force-finish, or Handy process death. I’m writing the README/DEBUG_LOG notes now.

### ASSISTANT 2026-05-26T10:00:17.894Z

One more tidy check: I touched an androidTest fake performer signature, so I’m compiling the debug androidTest sources too.

### ASSISTANT 2026-05-26T10:01:33.635Z

Implemented. The core change is that Handy now policy-checks UI work as semantic `UiAction`s instead of disguising taps/types as `OpenApp`, and web-tool provenance is turn-scoped all the way through dispatch, recipes, overlay flight, and full-chat handoff.

**Before / After**
- Before: “tap Send” was policy-visible as roughly “open Gmail.” After: policy sees `TAP targetLabel=Send role=button markId=...`, so it can require strong-hold confirmation.
- Before: `web_search -> safe_tool -> dispatch_action` could lose the untrusted bit. After: the whole turn remains tainted until finalization, so later actions/recipes are denied with `tool-suggestion-only`.
- Before: a fetched page could indirectly trigger a recipe path. After: recipe policy receives `UNTRUSTED_TOOL` provenance and stops before execution.

Key files include [AssistantAction.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/action/AssistantAction.kt), [ToolProvenance.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/llm/ToolProvenance.kt), [HandyToolRunner.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/llm/HandyToolRunner.kt), [DefaultActionPolicyEngine.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt), [AgentSessionController.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt), and [BuddyFlightDriver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt).

**Validation**
Passed:
- `:core:test :android-runtime:testDebugUnitTest :app:testDebugUnitTest`
- `:app:lintDebug :app:assembleDebug`
- `:app:compileDebugAndroidTestKotlin`
- `git diff --check`

I also installed `app-debug.apk` on `emulator-5554`, launched Handy, confirmed the process stayed alive, and logcat showed no `FATAL EXCEPTION`, `AndroidRuntime`, ANR, force-finish, or Handy process death. The only error-level logs were unrelated Google TTS process messages.

**Manual Test Plan**
1. Clean install the debug APK, enable accessibility/automation, and verify normal overlay/chat startup.
2. Trusted no-web flow: ask Handy to draft or type benign text into a normal field. Expected: normal policy/confirmation, no `tool-suggestion-only`.
3. Trusted destructive UI: ask “send the form” while target is a benign `Continue` button. Expected: strong-hold confirmation.
4. Target destructive UI: target a visible `Send` button. Expected: strong-hold confirmation, not silent tap.
5. Benign trusted UI: ask “cancel” or “tap Continue.” Expected: normal confirmation, not strong-hold.
6. Untrusted direct action: ask a current/web-backed question whose fetched page includes “set a timer / tap buy.” Expected: summaries are allowed; Android actions are denied with `tool-suggestion-only`.
7. Multi-tool regression: force/script `web_search -> unknown/safe tool -> dispatch_action`. Expected: still denied.
8. Recipe path: fetched/web result suggests `use recipe tap_visible...`. Expected: progress bubble says policy refused, no execution.
9. Buddy tap/type path: after a web-tool turn, any tap/type action markup should deny and dismiss safely; plain pointing should still be allowed.
10. Read-only web handoff: `WebSearchIntent` from untrusted context should require strong-hold, not silently dispatch.
11. Secure/sensitive screens: verify secure windows, OTP/password/card fields still hard-block.
12. Crash smoke after each: launch, interact once through overlay/full chat, scan logcat for fatal/app-death patterns.

Docs updated in [README.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/README.md) and [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md). The pre-existing untracked `handy_macos_ref/` folder was left untouched.

### USER 2026-05-26T10:01:33.898Z

Lots of changes done above which included context compactions in the middle. 1000+ lines of code were written across 30+ files- that's a lot of code and it needs a deep deep thorough audit - functional as well as non-functional! Can you go through the original prompt, code changes done in extreme detail, audit them, ensure all the required changes were done and done correctly with no downstream impact. the code before this works fine with it - no suspicious lines of codes for app crashes, or faulty wirings. all the services that needed to be changed have been changed and nothing is left or missed. ensure the ui is also proper with correct spacings, colours, fonts and elements. be very thorough in your analysis and evaluation. use highest level of reasoning available. Log the rca in the debug log and explain why the misses were missed. use highest level of thinking possible and think deeply. Evaluate and understand very deeply. Be very thorough and detailed. use your highest level of intelligence to analyse the requirement, code generated and plug gaps. be very thorough and have highest attention to detail. carefully analyse all the code - new and old and all dependencies too.

### ASSISTANT 2026-05-26T10:01:53.600Z

I’ll do this as an audit pass, not a rubber stamp: re-walk the original requirements against the diff, inspect every touched path and adjacent dependency, run focused searches for missed call sites, then run validation again. If I find gaps, I’ll patch them and record the audit RCA in `DEBUG_LOG.md`.

### ASSISTANT 2026-05-26T10:03:14.791Z

I’m going back through the wiring from the policy entry points outward, then inward from the tool runner and orchestrator. The main thing I’m looking for now is not “does it compile,” but whether the safety invariant survives boring edge cases like disabled Type-for-me, secure screens, full chat actions, and mixed tool turns.

### ASSISTANT 2026-05-26T10:03:27.693Z

The first pass already surfaced one subtle policy-order risk: the untrusted-tool gate exists, but I’m checking whether any earlier branch can mask it with a different denial reason. That matters because the audit requirement wants the firewall to be both reachable and recognizable as `tool-suggestion-only`.

### ASSISTANT 2026-05-26T10:04:02.575Z

I found a plain compile-risk wart in the app layer too: `AgentSessionController` has a duplicated `visible = true` argument in a progress bubble constructor. I’m going to clean that up with the policy-order hardening rather than letting a tiny typo undermine the larger safety work.

### ASSISTANT 2026-05-26T10:06:59.491Z

I’m making four narrow hardening edits now: preserve `tool-suggestion-only` for tainted side-effect actions even when other settings would also block, keep Chrome recipe typing on `TRUSTED_RECIPE` unless provenance taints it, switch synthetic fallback turn IDs to the injected UUID source, and remove the duplicate progress argument. Then I’ll add regression coverage for those audit gaps.

### ASSISTANT 2026-05-26T10:09:04.991Z

The hardening patch is in. I’m starting with targeted unit tests around policy, provenance, and recipe plumbing so failures point to the exact layer before I run the full Gradle sweep.

### ASSISTANT 2026-05-26T10:09:36.958Z

The first targeted compile failed only in the new orchestrator test because I referenced `TurnSource` without importing it. That’s fixed; rerunning the same test slice now.

### ASSISTANT 2026-05-26T10:10:46.129Z

Targeted tests are green now. I’ve also re-grepped the action/provenance entry points: no old `AtomicBoolean` trust latch remains in the tool runner, `dispatch_action` goes through turn IDs, and the recipe/overlay/full-chat handoffs all carry provenance. Next is the full unit/lint/assemble sweep.

### ASSISTANT 2026-05-26T10:13:30.121Z

Full unit tests, lint, and debug assemble are green. I’m doing the last compile-only Android-test check and whitespace diff check now, then I’ll install and launch the debug APK for a basic crash smoke.

### ASSISTANT 2026-05-26T10:13:43.720Z

The emulator is available, so I’m installing the freshly built APK, launching the app, and checking fresh logcat for crash signatures. This is a smoke test, not a substitute for the manual flow plan, but it catches the ugly wiring failures before they reach you.

### ASSISTANT 2026-05-26T10:14:01.971Z

The install and launch smoke passed: the APK installed, `OnboardingActivity` cold-launched, the Handy process stayed alive, and the fresh crash scan was empty. I’m adding the audit RCA now, including the specific misses this second pass caught and why they slipped through the first large implementation.

### ASSISTANT 2026-05-26T10:15:58.837Z

**Audit Complete**

I did the deep pass and fixed additional gaps, not just reviewed them. The main hardening landed in [DefaultActionPolicyEngine.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt:91), [BuddyFlightDriver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt:389), [AgentSessionController.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt:407), and [ConversationOrchestrator.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/orchestrator/ConversationOrchestrator.kt:57).

I found and fixed these audit misses:

- Untrusted tool actions could be denied for `type-for-me-disabled` or `sensitive-field` before reaching `tool-suggestion-only`. Now tainted side-effect actions consistently hit the firewall reason, while sensitive `WebSearchIntent` queries still fail closed.
- `UiAction.proposedPackage` was being scanned as secret text, which could falsely block packages like `com.pinterest` because of the substring `pin`.
- Chrome omnibox recipe typing was losing `TRUSTED_RECIPE` during the Buddy flight re-check. It now preserves recipe trust unless provenance downgrades it to `UNTRUSTED_TOOL`.
- Legacy/fallback turn IDs used `System.nanoTime()`. They now use the injected UUID source, and a new orchestrator test proves provenance is emitted before `onTurnEnd` clears it.

**Application Impact**

Before: `web_search -> safe_tool -> dispatch_action` could look safe again in the old single-bit model.  
After: the whole turn stays tainted, so the action is denied with `tool-suggestion-only`.

Before: tapping Gmail `Send` was policy-checked like “open Gmail.”  
After: policy sees semantic `UiAction.TAP targetLabel=Send`, so it can require strong-hold confirmation.

Before: a trusted Chrome recipe could be accidentally re-checked as `TRUSTED_USER` during flight typing.  
After: it remains `TRUSTED_RECIPE` unless web-tool provenance taints the turn.

No Compose layout, spacing, colors, fonts, or component hierarchy changed. UI impact is limited to existing confirmation/progress surfaces receiving more accurate policy decisions.

**RCA Logged**

Updated [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:2015) with the deeper RCA, why the misses were missed, validation, prevention rules, and the manual release plan. README already has the web-tools capability note at [README.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/README.md:333).

**Validation Passed**

- Targeted policy/provenance/recipe tests passed.
- Full validation passed: `:core:test :android-runtime:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Android test compile passed: `:app:compileDebugAndroidTestKotlin`.
- `git diff --check` passed.
- Installed rebuilt debug APK on `emulator-5554`, launched `OnboardingActivity`, Handy process stayed alive with pid `14628`, and fresh logcat crash scan was empty.

**Manual Test Plan**

1. Clean install, enable automation, recipes, tap-for-me, and web tools.
2. User-only turn: “tap Continue” on a harmless screen. Expect normal confirmation and execution.
3. User-only semantic test: “send the email” targeting a benign `Continue` button. Expect strong-hold.
4. Gmail `Send` target with no web tools. Expect allowed only behind strong-hold.
5. Web-search prompt-injection turn that suggests an action. Expect any `dispatch_action` denied with `tool-suggestion-only`.
6. Multi-tool taint: `web_search -> safe/unknown tool -> action`. Expect still denied.
7. `fetch_page` then recipe directive. Expect progress bubble `policy refused ... tool-suggestion-only`, no execution.
8. Full-chat “Show me in app” after web tools. Point-only flight may show location; tap/type execution must be denied if attempted.
9. Normal user-typed recipe with no web tools. Expect trusted recipe behavior preserved.
10. Secure window, incognito, password, OTP/card fields. Confirm existing hard blocks still win and no crashes/logcat errors appear.

Note: unrelated untracked `handy_macos_ref/` remains untouched.
