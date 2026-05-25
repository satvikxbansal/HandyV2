# Fix-C: Align MAX_STEPS docs

## Metadata

- Thread ID: `019e4f95-25eb-76e3-8705-c3a676b7560b`
- Created: 2026-05-22 17:37:15 IST
- Updated: 2026-05-22 17:42:58 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/22/rollout-2026-05-22T17-37-15-019e4f95-25eb-76e3-8705-c3a676b7560b.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `df10120d6315dd88fe221e53985bd5a2d9cffbf9`
- Messages exported: 12

## Brief Summary

This conversation focused on: RecipeRunner.MAX_STEPS is 6; docs/ACTION_POLICY.md and Likely related git changes: 72b969e FIX-C: Align RecipeRunner MAX_STEPS with the docs.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `df10120d6315dd88fe221e53985bd5a2d9cffbf9`
- Likely related commits:
  - `72b969e` 2026-05-22 17:51:54 IST [high] FIX-C: Align RecipeRunner MAX_STEPS with the docs. Files: DEBUG_LOG.md, HANDY_NEXT_LEVEL_PLAN.md, core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt, docs/ACTION_POLICY.md

## Conversation

### USER 2026-05-22T12:07:17.517Z

Goal: RecipeRunner.MAX_STEPS is 6; docs/ACTION_POLICY.md and
HANDY_NEXT_LEVEL_PLAN.md both say 5. Decide one and update both.

Files to touch:
- core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt
- docs/ACTION_POLICY.md
- HANDY_NEXT_LEVEL_PLAN.md

Required changes (option A — keep 6, document why):
- Update both docs to say "Max 6 steps so multi-screen recipes
  (WhatsApp open → search → type → open → type → send) fit without
  artificial fragmentation."
- Add a const description on MAX_STEPS explaining the WhatsApp ceiling.

Required changes (option B — drop to 5):
- Split WhatsApp recipe into a 5-step "draft" plan (without final
  Send), and require a fresh user invocation for the Send step.
- This is the safer option but adds friction.

Pick option A unless you want the friction. Document either choice in
the runner's KDoc.

RCA log entry:
- Title: "DL-### RecipeRunner.MAX_STEPS drifted from docs"
- Root cause: WhatsApp recipe needs 6 steps; runner was bumped without
  updating the policy + plan docs. Reviewers and contributors will
  hit a contradiction.
- Prevention rule: when bumping a numeric safety constant, the
  same PR must update the matching value in ACTION_POLICY.md and
  HANDY_NEXT_LEVEL_PLAN.md.

Tests: none (configuration-only).
Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-22T12:07:27.351Z

I’ll take option A and audit the three named files plus nearby references so the constant, docs, and rationale line up cleanly. I’ll also run an appropriate verification command afterward, since even config-only changes can trip formatting or doc checks in Android repos.

### ASSISTANT 2026-05-22T12:07:47.508Z

I found the drift exactly where expected: `RecipeRunner.MAX_STEPS` is already `6`, while the policy docs still contain the old five-step recipe cap. I’m also checking the current WhatsApp recipe and `DEBUG_LOG.md` numbering before editing, because the requested RCA entry needs to land with the fix.

### ASSISTANT 2026-05-22T12:08:18.721Z

The live WhatsApp recipe already uses the six-step contact-search path and makes the sixth step sensitive with `STRONG_HOLD`, so option A matches the implementation. I’m going to update the docs broadly enough that there isn’t another stale “≤5” hanging around, then add the RCA as `DL-069` since `DL-068` is currently the latest log entry.

### ASSISTANT 2026-05-22T12:09:09.080Z

The first doc pass removed the stale `5` cap in the main recipe sections. One nuance I’m tightening now: the plan should contain the exact “Max 6 steps…” sentence with the same capitalization as the policy doc, so future grep-based reviews catch both.

### ASSISTANT 2026-05-22T12:09:41.957Z

The diff is now narrow: runner KDoc, both policy/plan docs, and the required `DL-069` RCA. I also see an existing untracked `handy_macos_ref/` directory in the worktree; I’m leaving it untouched and focusing verification on these changes.

### ASSISTANT 2026-05-22T12:09:47.310Z

`git diff --check` is clean. The local shell does not have a system Java runtime on PATH, so I’m using the repo’s cached JDK at `~/.cache/codex-jdk17` for Gradle verification.

### ASSISTANT 2026-05-22T12:10:21.438Z

The full Gradle `build` is underway and has already passed `:core:test` plus the Android runtime unit-test phase. It’s now through app Kotlin/Java compilation and moving into app tests, lint, packaging, and release checks.

### ASSISTANT 2026-05-22T12:11:19.998Z

Full `./gradlew build` passed, including core/runtime/app tests, lint, debug packaging, R8, and release assembly. I’m doing the practical crash smoke now: check for a connected emulator/device, install the debug APK if available, launch the app, then scan fresh logcat for fatal runtime errors.

### ASSISTANT 2026-05-22T12:11:27.495Z

`adb` is not on PATH, but the debug APK was produced. I’m checking the standard Android SDK location next so I can still do the install/launch smoke instead of stopping at build-only verification.

### ASSISTANT 2026-05-22T12:12:14.848Z

The crash smoke passed: `:app:installDebug` installed on `emulator-5554`, `OnboardingActivity` launched with `Status: ok`, the process stayed alive, and a fresh logcat scan found no `AndroidRuntime` or `FATAL EXCEPTION` matches. I’m writing that into the RCA validation row now.

### ASSISTANT 2026-05-22T12:12:58.494Z

Implemented option A: kept `RecipeRunner.MAX_STEPS = 6` and documented why.

Changed:
- [RecipeRunner.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/RecipeRunner.kt:13): added runner KDoc and `MAX_STEPS` KDoc explaining the WhatsApp six-step ceiling.
- [docs/ACTION_POLICY.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/docs/ACTION_POLICY.md:97): updated recipe cap from five to six with the exact rationale sentence.
- [HANDY_NEXT_LEVEL_PLAN.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/HANDY_NEXT_LEVEL_PLAN.md:542): updated `RecipePlan` examples, hard rules, acceptance text, and clarified WhatsApp Send is the sixth explicit `STRONG_HOLD` step.
- [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1138): added `DL-069 — RecipeRunner.MAX_STEPS drifted from docs`.

Before: reviewers saw docs saying recipes were capped at 5 steps, while the app could run 6. A real WhatsApp contact-search flow could look “illegal” on paper even though the app supported it.

After: the code and docs agree on 6 steps, so this practical flow fits as one auditable recipe: open WhatsApp → search → type contact → open chat → type message → Send, with Send still gated by explicit `STRONG_HOLD`.

Verification passed:
- `git diff --check`
- `./gradlew build --no-daemon --stacktrace`
- `./gradlew :app:installDebug --no-daemon --stacktrace`
- Launched `OnboardingActivity` on `emulator-5554`; process stayed alive and fresh logcat had no `AndroidRuntime` / `FATAL EXCEPTION` matches.

Manual testing plan:
1. Fresh install, complete onboarding, enable required accessibility/action disclosure gates.
2. Ask: `Reply to John "on my way"` using a contact that requires search. Confirm the plan shows the six-step WhatsApp path and does not fragment into a second invocation.
3. Approve the plan, then cancel at the final Send hold. Expected: message remains drafted, nothing is sent, audit shows cancellation.
4. Repeat and complete the `STRONG_HOLD` Send. Expected: only the final hold sends; audit has all six step results.
5. Switch apps mid-recipe before Send. Expected: recipe aborts with package/screen-change protection.
6. Try a fake/debug 7-step recipe. Expected: `too-many-steps`.
7. Regression check shorter recipes: Clock alarm, Settings deep link, Maps preview, Gmail draft all still run normally under the six-step ceiling.
8. Safety negatives: ambiguous recipient, group/broadcast, WhatsApp payment/call/forward should refuse or require the existing policy path, never silently execute.
