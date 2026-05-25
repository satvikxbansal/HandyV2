# E1: Add replay regression evals

## Metadata

- Thread ID: `019e4e89-f77b-7192-9eb0-30a4686ec337`
- Created: 2026-05-22 12:45:25 IST
- Updated: 2026-05-22 13:09:07 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/22/rollout-2026-05-22T12-45-25-019e4e89-f77b-7192-9eb0-30a4686ec337.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `68c70057cd5cf2adc93574c4f4db3d5644590eda`
- Messages exported: 17

## Brief Summary

This conversation focused on: turn pointer + LLM behaviour into measured, regression-tested Likely related git changes: 68c7005 R3 + V2: Add treeHash, shopping recipes, and gesture guards; 7ab0827 E1: Add eval/replay framework, checks, and tests.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `68c70057cd5cf2adc93574c4f4db3d5644590eda`
- Likely related commits:
  - `68c7005` 2026-05-22 12:42:34 IST [low] R3 + V2: Add treeHash, shopping recipes, and gesture guards. Files: android-runtime/src/main/kotlin/com/handy/runtime/accessibility/LiveScreenGuard.kt, android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ShoppingRecipePack.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt, app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt, app/src/main/kotlin/com/handy/app/accessibility/PolicyGuardedActionPerformer.kt, app/src/main/kotlin/com/handy/app/chat/ChatConfirmationBroker.kt, app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/test/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformerTypeTextTest.kt
  - `7ab0827` 2026-05-22 13:09:45 IST [high] E1: Add eval/replay framework, checks, and tests. Files: DEBUG_LOG.md, core/build.gradle.kts, core/src/main/kotlin/com/handy/core/eval/ModelEval.kt, core/src/main/kotlin/com/handy/core/eval/RecordedResponseLlmClient.kt, core/src/main/kotlin/com/handy/core/eval/ResponseChecks.kt, core/src/main/kotlin/com/handy/core/screen/replay/SnapshotReplay.kt, core/src/test/kotlin/com/handy/core/eval/DuplicateTargetEvalTest.kt, core/src/test/kotlin/com/handy/core/eval/EvalTestSupport.kt, core/src/test/kotlin/com/handy/core/eval/HindiHinglishEvalTest.kt, core/src/test/kotlin/com/handy/core/eval/IntentFirstEvalTest.kt, core/src/test/kotlin/com/handy/core/eval/MarkIdSelectionEvalTest.kt, core/src/test/kotlin/com/handy/core/eval/NoContextHonestyEvalTest.kt

## Conversation

### USER 2026-05-22T07:15:41.177Z

Goal: turn pointer + LLM behaviour into measured, regression-tested
properties.

Files to touch (new):
- core/src/main/kotlin/com/handy/core/screen/replay/SnapshotReplay.kt
- core/src/test/kotlin/com/handy/core/screen/replay/SemanticPointerResolverReplayTest.kt
- core/src/test/resources/replay/<app>/screen_<n>.json (seed 20 apps;
  curated + ambiguous + secure + duplicate-label fixtures)
- core/src/main/kotlin/com/handy/core/eval/*.kt (eval framework)
- core/src/test/kotlin/com/handy/core/eval/* (one suite per eval in
  §16: MarkIdSelection, NoContextHonesty, SecureWindow,
  DuplicateTarget, ToolInjection, SensitiveAction, HindiHinglish,
  IntentFirst)

Notes:
- The model eval suite uses a recorded-response fake LlmClient so
  the suite is deterministic in CI. Real-model evals run via a
  separate task that's manual / nightly.

Acceptance:
- ./gradlew :core:test prints "Pointer replay accuracy: X/Y" per
  app group with X/Y >= 0.95 on the seeded corpus.
- Model eval suite prints pass rates per eval; CI gate is a soft
  warning initially, hard fail after Phase 7 ships. 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-22T07:15:49.274Z

I’ll turn this into concrete repo changes and verify them, starting by mapping the current pointer/LLM test structure so the replay and eval pieces fit the project instead of floating beside it.

### ASSISTANT 2026-05-22T07:15:56.152Z

The current core module already has screen snapshots, flattened accessibility text, pointer markup parsing, secure-window handling, and deterministic LLM fakes in tests. I’m going to extend those seams with replay fixtures and eval specs rather than introduce a separate testing universe.

### ASSISTANT 2026-05-22T07:19:58.685Z

I found the Android runtime resolver already has the scoring rules we want to measure: exact mark IDs, view IDs, text/role fallback, duplicate-label ambiguity, low-confidence handling, and redacted diagnostics. I’m going to add a pure core replay version for resource fixtures, then wire Gradle test logging so the accuracy/pass-rate lines actually appear in `:core:test`.

### ASSISTANT 2026-05-22T07:25:22.725Z

The replay corpus is in place now: twenty app groups, including the secure and duplicate/ambiguous cases. Next I’m adding the eval harness as a small deterministic runner, so each prompt behavior test can be a normal JUnit suite with recorded model output and a printed pass-rate line.

### ASSISTANT 2026-05-22T07:28:51.722Z

The model eval suites are now added and each one reports a pass-rate line through the same tiny runner. I’ve also enabled standard test streams for the core module so those replay/eval metrics show up in the normal Gradle output.

### ASSISTANT 2026-05-22T07:29:08.372Z

The first `:core:test` attempt did not reach Gradle: macOS cannot find a Java runtime on the default `PATH`. I’m going to look for the bundled Android Studio/Gradle JDK already on this machine and rerun with `JAVA_HOME` set for this process.

### ASSISTANT 2026-05-22T07:30:32.506Z

`JAVA_HOME` was the only environment wrinkle; with the cached JDK, `:core:test` is green and the required metric lines are visible. I’m doing one broader Gradle pass now because core API changes can still ripple into runtime/app compilation even when the core tests pass.

### ASSISTANT 2026-05-22T07:31:31.991Z

The broader `./gradlew test` pass also succeeded across core, runtime, and app unit tests. I’m doing a final `assembleDebug` as a packaging sanity check, since that catches a different class of integration mistakes than unit tests.

### ASSISTANT 2026-05-22T07:33:26.965Z

Implemented the replay + eval harness and verified it cleanly.

What changed:
- Added pointer replay model/loader/resolver in [SnapshotReplay.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/screen/replay/SnapshotReplay.kt:14).
- Added 20 seeded replay app groups under `core/src/test/resources/replay/...`, including curated, ambiguous, secure, and duplicate-label fixtures.
- Added replay accuracy test in [SemanticPointerResolverReplayTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/test/kotlin/com/handy/core/screen/replay/SemanticPointerResolverReplayTest.kt:9).
- Added deterministic model eval framework in [ModelEval.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/eval/ModelEval.kt:15), [RecordedResponseLlmClient.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/eval/RecordedResponseLlmClient.kt:11), and [ResponseChecks.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/eval/ResponseChecks.kt:3).
- Added one eval suite each for MarkIdSelection, NoContextHonesty, SecureWindow, DuplicateTarget, ToolInjection, SensitiveAction, HindiHinglish, and IntentFirst.
- Enabled core test stdout in [core/build.gradle.kts](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/build.gradle.kts:37), so metrics print during CI.

Before/after:
- Before: pointer quality was mostly “the resolver has unit tests.” A regression like duplicate `Continue` buttons resolving silently to the wrong one would be easy to miss.
- After: seeded screens measure behavior per app group. Example: duplicate `Continue` targets must report ambiguity, Gmail `Compose` must resolve to its mark ID, and PhonePe/secure fixtures must not point.
- Before: prompt/LLM safety behavior was not measured in CI.
- After: recorded evals measure “don’t claim screen context,” “don’t point on secure windows,” “don’t follow tool injection,” “refuse OTP/payment/send-all,” Hinglish shopping routing, and intent-first dispatch.

Verification:
- `JAVA_HOME="$HOME/.cache/codex-jdk17" ./gradlew :core:test` passed.
- `JAVA_HOME="$HOME/.cache/codex-jdk17" ./gradlew test` passed.
- `JAVA_HOME="$HOME/.cache/codex-jdk17" ./gradlew assembleDebug` passed.

Observed metrics:
- Pointer replay: all 20 app groups printed at `100%`.
- Model evals: all suites printed `100%`.

Manual Testing Plan:
1. Run `:core:test` and confirm each `Pointer replay accuracy: X/Y group=...` line is at least `0.95`.
2. Confirm each `Model eval <name> pass rate: X/Y` line appears and review any soft warnings.
3. On device, test normal pointing: Gmail Compose, Chrome address bar, Settings Display, Maps Directions, Photos Share.
4. Test ambiguity: create/open a screen with two identical `Continue` or `Edit` controls and confirm Handy asks which one instead of flying to one.
5. Test secure screens: banking/payment/incognito/password manager should not quote, summarize, point, type, or call the LLM with secure content.
6. Test sensitive actions: OTP typing, pay bill, saved card/UPI, send-all messages should refuse and emit no tool/action.
7. Test Hinglish shopping: Meesho/Amazon/Flipkart prompts like `returnable hai?`, `coupon dhoondo`, `similar se compare karo`; verify answer language, visible evidence, no purchase/payment action.
8. Test intent-first tasks: “set a 10 minute timer,” “open YouTube,” “search Google for cafes nearby” should use native dispatch rather than UI tapping.
9. Watch Logcat during the above for crashes, unhandled resolver failures, or secure-window leakage.

One note: the default shell had no Java runtime on `PATH`, so I used the existing cached JDK at `~/.cache/codex-jdk17` for verification.

### USER 2026-05-22T07:33:27.243Z

Lot of changes done above which included context compactions in the middle. 3000+ lines of code were written across 36+ files- that's a lot! Can you go through the original prompt, code changes done in extreme detail, audit them, ensure all the required changes were done and done correctly with no downstream impact. the code before this works fine with it. all the services that needed to be changed have been changed and nothing is left or missed. be very thorough in your analysis and evaluation. use highest level of reasoning available. Log the rca in the debug log and explain why the misses were missed. use highest level of thinking possible and think deeply. Evaluate and understand very deeply. Be very thorough and detailed. use your highest level of intelligence to analyse the requirement, code generated and plug gaps. be very thorough and have highest attention to detail.

### ASSISTANT 2026-05-22T07:33:44.105Z

I’ll do this as a real audit pass rather than a victory lap: re-read the requirement, diff every generated file, look for contract mismatches and downstream risks, patch anything I find, rerun verification, and add an RCA entry to `DEBUG_LOG.md`.

### ASSISTANT 2026-05-22T07:34:07.688Z

The inventory shows the actual generated surface is 2,493 lines across 34 new/modified core files plus 20 replay JSON fixtures; `handy_macos_ref/` is untracked but not part of this change. I’m now auditing behavior, not just counts: fixture schema, scoring parity, test gates, and whether the acceptance text has any hidden “§16” gaps.

### ASSISTANT 2026-05-22T07:35:25.560Z

A few gaps are showing up in the audit, all test-quality rather than production-runtime crashes: curated groups should use the stricter §16 threshold, the corpus is missing an explicit small-target fixture from §16, and the intent eval’s tool-input checks are string-based instead of JSON-aware. I’m patching those now and then I’ll rerun the full checks.

### ASSISTANT 2026-05-22T07:36:13.658Z

The first patch caught a compile miss immediately: I added a JSON-aware helper but forgot the `LlmChunk` import. Fixing that now; this is exactly the kind of small integration miss this audit is meant to flush out before handoff.

### ASSISTANT 2026-05-22T07:37:19.175Z

The core gate is green again after the hardening patch, with `curated_basic` now at `4/4` because of the new small-target replay. I’m running the broader module tests and debug assembly one more time before writing the RCA, so the log entry has final validation rather than stale earlier results.

### ASSISTANT 2026-05-22T07:39:06.987Z

I completed the deep audit and plugged the gaps I found.

**Audit Result**
The original E1 requirement is now covered in the codebase:

| Requirement | Status |
|---|---|
| `SnapshotReplay.kt` replay framework | Done |
| `SemanticPointerResolverReplayTest.kt` | Done |
| 20 replay app groups | Done, still exactly 20 app groups |
| Curated + ambiguous + secure + duplicate-label fixtures | Done |
| §16 small-target fixture | Added during audit |
| Per-app replay output: `Pointer replay accuracy: X/Y` | Done |
| Replay gate `>= 0.95` | Done |
| Curated replay stricter `>= 0.99` from §16 | Added during audit |
| Eval framework under `core/eval` | Done |
| Recorded-response fake `LlmClient` | Done |
| One suite per eval: MarkIdSelection, NoContextHonesty, SecureWindow, DuplicateTarget, ToolInjection, SensitiveAction, HindiHinglish, IntentFirst | Done |
| Eval pass-rate output per suite | Done |
| Soft warning gate initially | Done |
| Downstream app/runtime build safety | Verified |

**Gaps Found And Fixed**
During the audit I found several non-production, test-hardening gaps:

1. The initial corpus did not include an explicit small-target replay case from §16.
   Fixed by adding [screen_2.json](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/test/resources/replay/curated_basic/screen_2.json:1).

2. Curated replay groups were using the general `0.95` gate.
   Fixed: curated groups now require `0.99`, real-app groups require `0.95` in [SemanticPointerResolverReplayTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/test/kotlin/com/handy/core/screen/replay/SemanticPointerResolverReplayTest.kt:69).

3. Intent-first evals matched raw JSON substrings.
   Fixed with JSON-aware checks in [ResponseChecks.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/eval/ResponseChecks.kt:120).

4. Metric formatting used default JVM locale.
   Fixed with `Locale.US` in [ModelEval.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/eval/ModelEval.kt:171) and [SnapshotReplay.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/screen/replay/SnapshotReplay.kt:179).

5. Replay fixture hygiene was too loose.
   Fixed by asserting unique screen IDs, non-empty groups/packages, and non-empty cases.

**RCA Logged**
I appended the RCA as `DL-065` in [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1073).

Why the misses happened: the first pass optimized for the explicit acceptance bullets, while a couple of stricter details lived in parent §16 text. The change was large and split across many files, so the first green test run proved the generated happy path, but not every parent-section sub-bullet. The new prevention rule is to trace every prompt bullet and parent-section bullet to a concrete assertion or fixture.

**Downstream Impact**
No production service wiring needed to change. The existing runtime resolver, orchestrator, and `LlmClient` seam already supported this work; this change adds measurement around them. I verified downstream impact anyway.

Verification passed:
- `JAVA_HOME=$HOME/.cache/codex-jdk17 ./gradlew :core:test`
- `JAVA_HOME=$HOME/.cache/codex-jdk17 ./gradlew test`
- `JAVA_HOME=$HOME/.cache/codex-jdk17 ./gradlew assembleDebug`
- `git diff --check`
- JSON syntax check for every replay fixture

Current metric highlights:
- Pointer replay: all 20 app groups printed, all at 100%.
- `curated_basic` is now `4/4` including the small-target case.
- All model eval suites printed `100.0%`.
