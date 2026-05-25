# FIX-A: Refine beta-blocked phrases

## Metadata

- Thread ID: `019e4f83-07e8-7ea3-bc82-56998d5f17ce`
- Created: 2026-05-22 17:17:27 IST
- Updated: 2026-05-22 17:28:53 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/22/rollout-2026-05-22T17-17-27-019e4f83-07e8-7ea3-bc82-56998d5f17ce.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `0498e619cd0d4fe18d6f3848ff83d9246379c245`
- Messages exported: 23

## Brief Summary

This conversation focused on: DefaultActionPolicyEngine.BETA_BLOCKED_TERMS currently contains Likely related git changes: df10120 Fix-A: Tighten BETA_BLOCKED_TERMS so legitimate recipes don't get refused.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `0498e619cd0d4fe18d6f3848ff83d9246379c245`
- Likely related commits:
  - `df10120` 2026-05-22 17:29:39 IST [high] Fix-A: Tighten BETA_BLOCKED_TERMS so legitimate recipes don't get refused. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt

## Conversation

### USER 2026-05-22T11:47:48.312Z

You are working on Handy Android (multi-module Gradle).

Goal: DefaultActionPolicyEngine.BETA_BLOCKED_TERMS currently contains
words ("delete", "remove", "transfer", "send money") that match
substrings inside ordinary recipe targets, causing R3 Gmail/WhatsApp
recipes and shopping recipes to be silently refused with reason
"beta-blocked" even when the user explicitly confirms.

Files to touch:
- android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt

Required changes:
- Replace BETA_BLOCKED_TERMS with two narrower lists:
  - UI_BETA_BLOCKED_PHRASES: only match phrases (whole-word, multi-token
    when relevant), restricted to surfaces that look financial / payment.
    Use: "buy now", "place order", "complete purchase", "pay now",
    "checkout", "send money", "transfer money", "wire transfer".
  - DELETE_HARD_BLOCKED_PHRASES: only block when the target text matches
    one of these *and* the action source trust is not TRUSTED_RECIPE.
    Use: "delete account", "close account", "factory reset".
- Update isBetaBlocked() to take the SourceTrust as a parameter, and:
  * If TRUSTED_RECIPE: only block on UI_BETA_BLOCKED_PHRASES (the
    payment/checkout surfaces) and DELETE_HARD_BLOCKED_PHRASES.
  * If anything else: keep current behaviour for UI_BETA_BLOCKED_PHRASES
    only.
- Move the URL-side beta-block (upi: + payment terms in URL) to a
  separate isPaymentUrl() check that runs regardless of sourceTrust.
- Add 3 new unit tests in DefaultActionPolicyEngineTest:
  * Gmail recipe step "Delete email" is allowed when SourceTrust =
    TRUSTED_RECIPE.
  * "delete account" target is blocked regardless of SourceTrust.
  * Shopping "Buy now" target is blocked with reason "beta-blocked".

Do NOT:
- Remove the payment/purchase block.
- Allow "send" as a standalone word (it's already not in the list;
  keep it that way).
- Touch the sensitive-field path (OTP / password / card) — that
  stays as-is.

RCA log entry (DEBUG_LOG.md):
- Title: "DL-### BETA_BLOCKED_TERMS over-matched ordinary recipe text"
- Root cause: Initial OPS1/P0 list used single-word terms that
  substring-match legitimate UI text inside Gmail/WhatsApp/Shopping
  recipes ("delete email", "remove from cart", "transfer photos").
  Recipes confirmed by the user were silently refused.
- Prevention rule: any blocklist that matches against UI text must
  use whole-word multi-token phrases, and must accept a SourceTrust
  parameter so TRUSTED_RECIPE flows can opt into narrower checks.

Tests: ./gradlew :core:test :android-runtime:test 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-22T11:47:54.464Z

I’ll make the policy change and tests, then run the requested Gradle tasks. I’ll first inspect the policy engine, its tests, and the RCA log so the edits land cleanly in the existing style.

### ASSISTANT 2026-05-22T11:48:03.291Z

I found the policy engine already has the over-broad beta scan in both URL and tap target paths, plus a long existing test file around `SourceTrust`. Next I’m reading the relevant blocks so I can make the smallest patch and avoid disturbing the sensitive-field logic.

### ASSISTANT 2026-05-22T11:48:33.461Z

There’s one small ambiguity in the wording: one bullet says the hard delete phrases are blocked only when trust is not `TRUSTED_RECIPE`, while the required test says “delete account” is blocked regardless of `SourceTrust`. I’m following the test and product-safety intent: account deletion/reset remains blocked for trusted recipes too.

### ASSISTANT 2026-05-22T11:50:22.878Z

I’m about to patch the policy in one place: split the phrase lists, add whole-phrase matching, keep URL payment blocking separate, and add the three regression tests alongside the existing policy tests.

### ASSISTANT 2026-05-22T11:51:29.149Z

The code patch is in place now. I’m adding the RCA entry as `DL-067`, then I’ll compile/test so any Kotlin or behavior mistakes get caught before I hand this back.

### ASSISTANT 2026-05-22T11:52:09.272Z

The RCA entry is appended. I’m doing a quick local audit of the diff now, especially around the new regex helpers and the policy order, before running Gradle.

### ASSISTANT 2026-05-22T11:52:52.690Z

The diff now has one explicit URL-payment regression and the new shopping target test owns the `Buy now` case, which keeps the suite a bit cleaner. I’m running the focused policy test first, plus a whitespace check, then I’ll run the full requested `:core:test :android-runtime:test`.

### ASSISTANT 2026-05-22T11:53:00.885Z

The whitespace check passed. The focused Gradle run hit the local shell’s missing default Java runtime, so I’m switching to the repo’s cached JDK path that prior builds used on this machine and rerunning.

### ASSISTANT 2026-05-22T11:53:17.973Z

Gradle’s aggregate `:android-runtime:test` task doesn’t accept `--tests`, so I’m using the concrete unit-test task for the focused run and will still run the exact aggregate command the user requested afterward.

### ASSISTANT 2026-05-22T11:53:35.924Z

The focused policy test passed after compiling `android-runtime`. I’m running the requested full test command now; if it comes back clean I’ll do one app compile pass as an extra crash/build sanity check.

### ASSISTANT 2026-05-22T11:53:51.715Z

The requested `:core:test :android-runtime:test` command passed cleanly. I’m doing the extra `:app:compileDebugKotlin` pass now so the app-side dependency graph sees the changed runtime code too.

### ASSISTANT 2026-05-22T11:54:34.245Z

One small cleanup before final validation: the trust-aware branch was explicit but duplicated. I’m tightening that and adding plural URL terms (`payments`, `purchases`) to keep the URL payment/purchase guard conservative without broadening UI text matching.

### ASSISTANT 2026-05-22T11:54:45.873Z

That cleanup is in. I’m rerunning the requested tests and app compile once more after the final edit, so the final status reflects the actual patch.

### ASSISTANT 2026-05-22T11:55:04.193Z

Final validation is green: requested tests passed and the app debug Kotlin compile passed too. I’m updating the RCA validation row to reflect the actual commands, then I’ll do one final diff/status check.

### ASSISTANT 2026-05-22T11:55:29.640Z

The diff check is clean. I see an unrelated untracked `handy_macos_ref/` directory that I’m leaving alone. I’m going one step further with `:app:assembleDebug`, and if a device/emulator is attached I’ll do a basic launch crash smoke too.

### ASSISTANT 2026-05-22T11:55:45.660Z

`assembleDebug` passed. The shell doesn’t have `adb` on `PATH`, so I can’t do a device launch smoke from here without discovering SDK tools; I’ll check the repo’s `local.properties` for the SDK path before deciding whether that’s available.

### ASSISTANT 2026-05-22T11:56:03.678Z

A device is attached (`emulator-5554`), so I can do the basic install/launch crash smoke after all. I’m checking the launcher activity and then I’ll install the debug APK, launch, and scan fresh logcat for fatal crashes.

### ASSISTANT 2026-05-22T11:56:24.510Z

Install succeeded, but launching by namespace failed because the debug `applicationId` is different from the namespace. I’m checking the Gradle application id and will relaunch with the installed package name.

### ASSISTANT 2026-05-22T11:56:53.073Z

The emulator launch smoke came back clean: install succeeded, the real launch component started, and fresh logcat had no crash/error matches. I’m recording that in the RCA validation row.

### ASSISTANT 2026-05-22T11:57:38.485Z

I’m tightening the “regardless of source trust” test to include `UNTRUSTED_TOOL` too. For that one the policy may fail earlier as `tool-suggestion-only`, but it still proves the target cannot pass under any source trust.

### ASSISTANT 2026-05-22T11:57:48.322Z

That test now covers all three trust values. I’m rerunning the requested test command one last time after this test-only adjustment.

### ASSISTANT 2026-05-22T11:58:53.425Z

Implemented the policy narrowing in [DefaultActionPolicyEngine.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt:82), added the requested regressions in [DefaultActionPolicyEngineTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt:180), and appended RCA entry [DL-067](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1106).

**Behavior Change**
Before: recipe UI targets like `Delete email`, `remove from cart`, or `transfer photos` could match broad single-word substrings and get refused as `beta-blocked`, even after user confirmation.

After: UI matching is whole-phrase based. `Delete email` in a trusted Gmail recipe is allowed, while actual risky surfaces still block:
- Still blocked: `Buy now`, `Pay now`, `Checkout`, `Complete purchase`, `Send money`, `Transfer money`, `Wire transfer`.
- Still blocked: `Delete account`, `Close account`, `Factory reset`.
- Still blocked: `upi:` and payment/purchase URLs.
- Not added: standalone `send`.

Sensitive-field handling for OTP/password/card stayed intact.

**Validation**
Passed:
```bash
./gradlew :core:test :android-runtime:test --stacktrace
./gradlew :app:compileDebugKotlin --stacktrace
./gradlew :app:assembleDebug --stacktrace
git diff --check
```

Also installed `app-debug.apk` on `emulator-5554`, launched `com.handy.android/com.handy.app.onboarding.OnboardingActivity`, and fresh logcat had no `AndroidRuntime`, `FATAL EXCEPTION`, or Handy exception/error matches.

**Manual Test Plan**
1. Gmail trusted recipe: run a confirmed recipe step targeting `Delete email`; expect normal confirmation/action flow, not `beta-blocked`.
2. Gmail hard block: target `Delete account`; expect refused with `beta-blocked`.
3. Shopping safe text: target `Remove from cart`; expect not blocked by beta phrase policy.
4. Shopping purchase text: target `Buy now`, `Checkout`, `Place order`, `Complete purchase`, `Pay now`; expect `beta-blocked`.
5. WhatsApp/general recipe: target `Send` or `Transfer photos`; expect no beta block from standalone words.
6. WhatsApp/payments: target `Send money` or `Transfer money`; expect `beta-blocked`.
7. URL path: dispatch `upi://pay?...`, `/complete-purchase`, `/payments`; expect `beta-blocked` for all source trusts.
8. Non-payment URL: dispatch a normal URL without payment terms; expect existing non-UI URL behavior.
9. Sensitive fields: type OTP/password/card-like values; expect `sensitive-field`, confirming that path was untouched.
10. Smoke app: install debug APK, launch onboarding, verify no fatal logcat entries and no startup crash.

Note: there is an existing untracked `handy_macos_ref/` directory; I left it untouched.
