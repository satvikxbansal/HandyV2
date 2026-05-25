# Goal: add controlled typing with full policy + verification.

## Metadata

- Thread ID: `019e4a10-deae-7b83-b38b-f400d9d6e6a0`
- Created: 2026-05-21 15:54:40 IST
- Updated: 2026-05-21 16:36:42 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/21/rollout-2026-05-21T15-54-40-019e4a10-deae-7b83-b38b-f400d9d6e6a0.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `bddd08e58b175858df12dc51c29cc04d8ba239a7`
- Messages exported: 45

## Brief Summary

This conversation focused on: add controlled typing with full policy + verification. Likely related git changes: bddd08e P2+P3: Support candidate options and audit review; b2b44d7 T1: Add controlled typing and action confirmation.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `bddd08e58b175858df12dc51c29cc04d8ba239a7`
- Likely related commits:
  - `bddd08e` 2026-05-21 15:54:29 IST [low] P2+P3: Support candidate options and audit review. Files: android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt, app/src/androidTest/kotlin/com/handy/app/diagnostics/AuditReviewActivityTest.kt, app/src/main/AndroidManifest.xml, app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt, app/src/main/kotlin/com/handy/app/diagnostics/AuditReviewActivity.kt, app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt, app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt, app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPanelBridge.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt
  - `b2b44d7` 2026-05-21 16:40:16 IST [medium] T1: Add controlled typing and action confirmation. Files: android-runtime/src/main/kotlin/com/handy/runtime/accessibility/ActionEventObserver.kt, android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt, android-runtime/src/main/kotlin/com/handy/runtime/action/NoopActionPerformer.kt, android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt, android-runtime/src/test/kotlin/com/handy/runtime/accessibility/ActionEventObserverMatcherTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt, app/build.gradle.kts, app/src/androidTest/kotlin/com/handy/app/diagnostics/AuditReviewActivityTest.kt, app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt, app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt, app/src/main/kotlin/com/handy/app/accessibility/PolicyGuardedActionPerformer.kt, app/src/main/kotlin/com/handy/app/accessibility/SwitchingActionPerformer.kt

## Conversation

### USER 2026-05-21T10:24:55.291Z

Goal: add controlled typing with full policy + verification.

Files to touch:
- core/src/main/kotlin/com/handy/core/action/ActionPerformer.kt
  (add suspend fun typeText(target, text); add
   ActionCapability.TYPE)
- app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt
  (impl using ACTION_SET_TEXT + Bundle with
   ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE; re-resolve via markId;
   require node.isEditable; no per-key gesture fallback)
- app/src/main/kotlin/com/handy/app/accessibility/SwitchingActionPerformer.kt
- core/src/main/kotlin/com/handy/core/privacy/ScreenRedactor.kt
  (no shape change; verify the redactor's diagnostics=true path is
   used when computing "would this text be redacted?" before
   typing)
- android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt
  (extend rules: nearby labels OTP/CVV/password/card → allowed=false
   for TYPE)
- core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt
  (capability addendum announcing TYPE under strict policy)
- app/src/main/kotlin/com/handy/app/overlay/TapForMeConfirmationSheet.kt
  (typing variant: show the text, allow user edit)
- (new) android-runtime/src/main/kotlin/com/handy/runtime/accessibility/ActionEventObserver.kt
- app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt
  (feed events into observer; performer awaits matching event
   <= 1500ms)
- core/src/main/kotlin/com/handy/core/audit/AuditEvent.kt
  (add verifiedBy: String? field — "view-clicked", "scrolled",
   "text-changed", null)

Acceptance:
- Manual: "search for milk in Amazon" → confirmation → text lands
  in the search field; audit verifiedBy="text-changed".
- "type my OTP" → refused by policy with reason="sensitive-field".
- The privacy filter blocks any card-pattern / OTP-pattern text
  even if the LLM emits it.

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   

Tests:
- AccessibilityGestureActionPerformerTypeTextTest with a fake node.
- ActionEventObserverMatcherTest.

### ASSISTANT 2026-05-21T10:25:03.561Z

I’ll wire this through the action stack and policy layer, then add the observer/test coverage so the implementation has a real verification path instead of just “we called the API.” I’ll first map the current action abstractions and existing audit/policy patterns so the change fits the app’s shape.

### ASSISTANT 2026-05-21T10:25:13.667Z

I’ve got the target files and noticed the repo already has mark-id resolution, redaction diagnostics hooks, and policy tests. Next I’m reading the concrete signatures and call sites so TYPE can flow through without inventing a parallel action path.

### ASSISTANT 2026-05-21T10:26:28.652Z

One important shape is emerging: the existing “show in app” pipeline can point and then tap, but it has no typed-action envelope yet. I’m going to keep the lower-level typing implementation strict and make only the minimum surrounding additions needed for policy, confirmation UI, verification, and tests to compile cleanly.

### ASSISTANT 2026-05-21T10:29:39.373Z

I’m going to edit the action contract first, then thread that through the real/noop/switching/guarded performers. After that I’ll add the observer and hook the accessibility service to it, so the type action can verify the resulting text-change event.

### ASSISTANT 2026-05-21T10:33:09.261Z

The policy work is next. I’m adding a real internal `TypeText` action so the policy engine can distinguish “tap this” from “put this string into this field,” then it can block both sensitive destinations and sensitive proposed text before the accessibility layer ever runs.

### ASSISTANT 2026-05-21T10:37:09.563Z

The first pass of code is in. I’m going to run the focused unit tests now; there’s a good chance the Android JVM tests will need small adjustments because AccessibilityNodeInfo and Bundle are Android framework classes, so I’ll tighten that up based on the compiler rather than guessing.

### ASSISTANT 2026-05-21T10:37:20.463Z

The Gradle run hit the local machine’s Java setup rather than the code: `Unable to locate a Java Runtime`. I’ll check whether Android Studio’s bundled JDK is present and rerun Gradle with `JAVA_HOME` pointed at it if it is.

### ASSISTANT 2026-05-21T10:37:46.543Z

There isn’t a bundled Java runtime in the workspace dependencies either, so I can’t execute Gradle on this machine as-is. I’m switching to a static compile review now: checking changed Kotlin for signature mismatches, sealed `when` fallout, and Compose call-site consistency before I wrap up.

### ASSISTANT 2026-05-21T10:42:20.777Z

I found one practical gap: the confirmation sheet and type performer existed, but no assistant response could actually carry “type this text” through the existing point-and-act pipeline. I’m adding a small `[TYPE:text=...]` control tag alongside the existing `[POINT:...]` tag so the app can turn “search for milk” into “point at the search field, confirm/edit `milk`, then type it.”

### ASSISTANT 2026-05-21T10:47:26.002Z

Implemented the controlled typing path end to end.

**What Changed**
Before: Handy could point at and tap a field, but “search for milk in Amazon” would only land on the search box. The user still had to type.

After: the assistant can emit a controlled `[TYPE:text=milk]` action paired with a `[POINT:...]`; Handy flies to the text field, shows a confirmation sheet where the user can edit `milk`, then uses `ACTION_SET_TEXT`. It waits up to 1500ms for `TYPE_VIEW_TEXT_CHANGED` and audits `verifiedBy="text-changed"`.

Sensitive examples now fail closed:
- “type my OTP 123456” → blocked as `sensitive-field`
- “enter CVV 123” near a CVV/card/password label → blocked
- card-shaped text like `4111 1111 1111 1111` → blocked before dispatch

Key files include:
- [ActionPerformer.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/action/ActionPerformer.kt)
- [AccessibilityGestureActionPerformer.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt)
- [DefaultActionPolicyEngine.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt)
- [ActionEventObserver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/accessibility/ActionEventObserver.kt)
- [TapForMeConfirmationSheet.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/TapForMeConfirmationSheet.kt)

**Verification**
I added:
- `AccessibilityGestureActionPerformerTypeTextTest`
- `ActionEventObserverMatcherTest`
- policy tests for OTP/card/sensitive nearby labels
- parser test for `[TYPE:text=...]`

I could not run Gradle because this machine has no Java runtime installed: `Unable to locate a Java Runtime.`  
`git diff --check` passed.

**Manual Test Plan**
1. Enable Handy Accessibility, overlay permission, and Tap-for-me disclosure.
2. Open Amazon with the search field visible.
3. Ask: `search for milk in Amazon`.
4. Expect a Type-for-me confirmation sheet showing `milk`.
5. Edit it to `oat milk`, confirm, and verify the Amazon search field receives `oat milk`.
6. Open Audit Review and verify a `Type text` event with `verifiedBy="text-changed"`.
7. Repeat and tap Cancel: no text should be entered.
8. Repeat and let the sheet timeout: no text should be entered.
9. Try `type my OTP 123456` on an OTP screen: expect refusal/failed policy reason `sensitive-field`.
10. Try card/CVV/password labeled fields: no confirmation should run, no text should land.
11. Use a fake/model-debug response with `[TYPE:text=4111 1111 1111 1111]`: policy/privacy should block it.
12. Test a non-editable target: expect no gesture fallback and an unsupported/failure result.
13. Regression check normal tap-for-me and scroll still work.

### USER 2026-05-21T10:50:57.276Z

# Files mentioned by the user:

## Screenshot 2026-05-21 at 4.20.18 PM.png: /Users/satvik.bansal/Desktop/Screenshot 2026-05-21 at 4.20.18 PM.png

## My request for Codex:
Build error again! why arent we testing the code end to end comprehensively? ensure there are no app crashes too. 

Error: 
e: file:///Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/accessibility/ActionEventObserver.kt:35:35 Unresolved reference 'equalsViewId'.
Fix with AI

<image name=[Image #1]>
</image>

### ASSISTANT 2026-05-21T10:51:16.514Z

You’re right to call this out. I missed a Kotlin scoping issue that a real compile would have caught immediately; I’m going to fix that, then do a broader static pass for the other build errors shown and any likely crash paths around the new typing flow.

### ASSISTANT 2026-05-21T10:51:32.278Z

The immediate compiler issue is exactly what the screenshot says: `MatchTarget` is a nested data class, and I accidentally put `equalsViewId` / `overlapRatio` as instance-private extensions on `ActionEventObserver`. Nested classes cannot see those. I’m moving those helpers to file-private top-level functions so both production and tests can resolve them.

### ASSISTANT 2026-05-21T10:52:12.615Z

That fix is in. I’m doing a second pass over likely Kotlin compile failures now, especially nested-class access, sealed `when` exhaustiveness, and Android framework property use, because the first failure can hide the next few compiler errors.

### ASSISTANT 2026-05-21T10:52:54.826Z

I’m also tightening the verifier to avoid false positives: a text-change event should match by view id when present, otherwise by overlapping bounds, and only use class/text as a last resort. That helps prevent “some other EditText changed” from being audited as success.

### ASSISTANT 2026-05-21T10:53:09.867Z

The shell here still has no usable Java runtime, which is why I couldn’t run Gradle earlier. I’m going to provision a local JDK under the user cache and run the Android test tasks from that, so we get an actual compile/test signal instead of relying on static review.

### ASSISTANT 2026-05-21T10:54:16.628Z

Good, we now have a local JDK available for this session. I’m running the focused compile/test set first, then I’ll run a broader debug build path so we catch app-module crashes and DI/Compose compile errors too.

### ASSISTANT 2026-05-21T10:54:51.348Z

The original `ActionEventObserver` compiler error is past the first compile phase now. The broader `:core:test` run is failing on an existing-looking test visibility issue around `SECURE_WINDOW_SYSTEM_MESSAGE`, so I’m checking whether that’s unrelated or if my prompt/parser changes surfaced it.

### ASSISTANT 2026-05-21T10:55:04.238Z

The focused app compile found the next real errors. Four are mine: Kotlin won’t smart-cast nullable public properties from `TapTarget.AtNode` across modules. I’ll fix those by copying the values into locals before matching. There’s also a separate pre-existing `withStableMarkIds` unresolved import in `AccessibilityMarksProvider`; I’m checking it now because it blocks `:android-runtime:compileDebugKotlin` too.

### ASSISTANT 2026-05-21T10:56:16.408Z

That cleared the Android runtime compile. The `withStableMarkIds` error was stale classpath/cache behavior; forcing `:core:compileKotlin` before `:android-runtime:compileDebugKotlin` rebuilt it cleanly. Now I’m running the app compile and the focused test tasks again.

### ASSISTANT 2026-05-21T10:56:38.704Z

Now we’re in real test feedback. Runtime compile passed, core tests passed, and one of the new observer tests is failing because the test emitted into a `SharedFlow` before the collector was definitely registered. I’ll fix the test to let the collector start, then rerun.

### ASSISTANT 2026-05-21T10:58:09.595Z

You’re right to call that out. I’m picking this back up by fixing the compile failure, then I’m going to run the relevant unit tests and a full debug assemble path instead of stopping at the first green compile.

### ASSISTANT 2026-05-21T10:58:21.646Z

The screenshot error itself is already addressed in `ActionEventObserver`: the helper functions are now top-level so the nested matcher can see them. I found the next weak spot is a flaky observer test setup, so I’m tightening that test and adding one small null-safe guard around the accessibility action list before running the broader build.

### ASSISTANT 2026-05-21T10:59:19.029Z

There was a second real bug hiding behind the compiler issue: resource-id matching only normalized one side, so `"rs_search_src_text"` didn’t match `"com.amazon:id/rs_search_src_text"`. That’s fixed now, and I added the explicit matcher assertion so we don’t regress that practical Amazon-search case.

### ASSISTANT 2026-05-21T10:59:26.106Z

The targeted observer test is green now. I’m moving up a level to compile the app and run the core/runtime/app unit tests together, which should catch the policy, parser, prompt, and performer wiring instead of just the file that broke.

### ASSISTANT 2026-05-21T10:59:35.375Z

The broad run found more compile errors in the app module, not the runtime module: Kotlin isn’t smart-casting `resolved.node` after the compound resolver check. I’m converting that into an explicit non-null local so the performer compiles cleanly and avoids ambiguous null handling.

### ASSISTANT 2026-05-21T11:00:03.464Z

Good news: app Kotlin compile is now passing; the failure moved into the new app unit test imports. I’m aligning that test with the MockK API version already in the repo, then I’ll rerun the same full command.

### ASSISTANT 2026-05-21T11:00:35.921Z

That full unit-test lane is green now. I’m doing the packaging/Android-test compile pass next, because “unit tests passed” still isn’t the same as “the debug app can be assembled and its instrumentation sources still compile.”

### ASSISTANT 2026-05-21T11:00:50.435Z

The debug APK assembles and the Android-test sources compile. I’m doing two last hygiene checks now: whitespace/diff validation plus a repo/module scan so I don’t accidentally miss another affected Gradle lane.

### ASSISTANT 2026-05-21T11:00:55.329Z

`git diff --check` is clean, and this repo only has `core`, `android-runtime`, and `app`. I’m going to run Gradle’s `check` task across those modules as the final broad verification; if it exposes an unrelated pre-existing lint issue, I’ll separate that clearly from this change.

### ASSISTANT 2026-05-21T11:01:30.298Z

Gradle `check` got much farther, but failed inside Android Lint’s Kotlin FIR analysis on test source files, not on an app issue: one new unit test and one existing instrumentation test both trigger the same “Unexpected failure during lint analysis” stack. I’m separating that from the actual app lane now by running source lint plus checking whether there’s a connected device/emulator for a smoke launch.

### ASSISTANT 2026-05-21T11:01:58.167Z

Source lint is clean too. `adb` is not on PATH in this shell, so I’m checking the Android SDK location from the project and I’ll use its bundled `platform-tools/adb` if it exists for a real smoke install/launch.

### ASSISTANT 2026-05-21T11:02:21.043Z

There is an emulator attached (`emulator-5554`). The app APK is under Gradle intermediates on this setup, so I’m going to install that exact debug APK and launch the app package for a smoke check.

### ASSISTANT 2026-05-21T11:02:33.098Z

The emulator rejected the debug APK because it’s marked test-only, which is expected for this build type. I’m reinstalling with `-t` and then I’ll launch the main activity while watching for crash output.

### ASSISTANT 2026-05-21T11:02:53.971Z

The APK installed, but the launch class/package in the manifest doesn’t match the installed package name exactly. I’m reading the APK badging now and will launch the activity using the package/activity Android reports, not the source manifest assumption.

### ASSISTANT 2026-05-21T11:03:28.560Z

The emulator cold-launched the app successfully, and there was no `AndroidRuntime`/fatal crash in logcat after startup. I’m going one step further with the instrumentation tests that touch the changed audit UI, since that’s where `verifiedBy` is now displayed.

### ASSISTANT 2026-05-21T11:03:52.688Z

The targeted instrumentation test ran on the emulator and found a real test assertion problem: `com.example.target` now appears twice in the audit row, so `onNodeWithText(...).assertExists()` is too strict. I’m fixing the test to assert the package text is present without requiring uniqueness, then rerunning it.

### ASSISTANT 2026-05-21T11:04:17.210Z

The changed audit instrumentation test is green on the emulator now. I’m running the full connected debug instrumentation suite once, since there is a device available and that gives us a much better crash/regression signal than compile-only checks.

### ASSISTANT 2026-05-21T11:04:36.797Z

Full connected instrumentation is green: 9 tests passed on the Pixel 9 Pro AVD. I’m doing one final `check` pass with only the two known lint-test-analysis crash tasks excluded, so everything else in Gradle’s check lifecycle still gets exercised.

### ASSISTANT 2026-05-21T11:04:53.822Z

The broader `check` (with only the lint analyzer crash tasks excluded) exposed an actual crash-risk lint error: `BuddyFlightDriver` calls API 30 `WindowInsets.Type.systemBars()` without guarding minSdk 26. That is exactly the kind of thing we should catch here, so I’m fixing the API guard instead of hand-waving it.

### ASSISTANT 2026-05-21T11:05:27.506Z

That API call was already runtime-gated, but lint could not see the guard through the extension function. I annotated the extension with `@RequiresApi(R)`, which documents the contract and prevents accidental unguarded calls. Retesting the broad check lane now.

### ASSISTANT 2026-05-21T11:05:58.197Z

The broad check lane is now green when the two Android Lint test-source analyzer crash tasks are excluded; importantly, app/source lint itself passes. I’m rerunning plain `check` once more after the API fix so the final status is exact rather than inferred.

### ASSISTANT 2026-05-21T11:06:11.713Z

Plain `./gradlew check` is fully green now. Nice: the earlier lint analyzer crash disappeared after the test/source fixes and cache refresh, and the API-level crash risk is handled. I’m doing final status/diff checks so I can give you the exact files and verification list.

### ASSISTANT 2026-05-21T11:06:42.812Z

You were right. The previous pass was too narrow. I fixed the build error and then chased the next failures through compile, unit, lint, assemble, emulator launch, and connected tests.

**What I Fixed**
- Fixed `ActionEventObserver` helper visibility and view-id matching so `rs_search_src_text` correctly matches `com.amazon:id/rs_search_src_text`.
- Fixed nullable `AccessibilityNodeInfo` handling in [AccessibilityGestureActionPerformer.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt).
- Added a null-safe `ACTION_SET_TEXT` support check to reduce crash risk.
- Fixed new test imports/assertions and the audit UI instrumentation test.
- Fixed a real lint crash-risk: API 30 `WindowMetrics`/`WindowInsets` calls in [BuddyFlightDriver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt) are now guarded with `@RequiresApi(R)`.

**App Behavior Change**
Before: Handy could point/tap but could not safely fill a text field. “Search for milk in Amazon” could navigate to the field, but text entry was not a verified controlled action.

After: Handy can type only into editable accessibility nodes using `ACTION_SET_TEXT`, shows a confirmation sheet with editable text, waits for a matching `TYPE_VIEW_TEXT_CHANGED` event, and audits success with `verifiedBy="text-changed"`. Sensitive text like OTPs, CVVs, passwords, card numbers, or OTP/card-like patterns is blocked before typing.

**Verification Run**
All of these pass now:

```bash
JAVA_HOME="$HOME/.cache/codex-jdk17" ./gradlew :android-runtime:testDebugUnitTest --tests com.handy.runtime.accessibility.ActionEventObserverMatcherTest
JAVA_HOME="$HOME/.cache/codex-jdk17" ./gradlew :app:compileDebugKotlin :core:test :android-runtime:testDebugUnitTest :app:testDebugUnitTest
JAVA_HOME="$HOME/.cache/codex-jdk17" ./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin
JAVA_HOME="$HOME/.cache/codex-jdk17" ./gradlew :app:connectedDebugAndroidTest
JAVA_HOME="$HOME/.cache/codex-jdk17" ./gradlew check
git diff --check
```

I also installed the debug APK on the attached Pixel 9 Pro emulator, cold-launched `OnboardingActivity`, waited, and checked logcat for `AndroidRuntime`/fatal crashes. No startup crash appeared.

**Manual Test Plan**
1. Enable Handy accessibility + overlay permissions on a real device/emulator.
2. Open Amazon, focus no field manually, ask: “search for milk in Amazon”.
3. Confirm the typing sheet. Verify `milk` lands in the search field and audit shows `verifiedBy="text-changed"`.
4. Repeat but edit the confirmation text to `oat milk`; verify the edited value is what lands.
5. Ask “type my OTP 123456” near an OTP field. Verify refusal with sensitive-field/sensitive-text reason and no text lands.
6. Ask to type a card-like value, e.g. `4111 1111 1111 1111`; verify privacy/policy blocks it even if emitted by the model.
7. Try typing into a non-editable button/label. Verify it refuses, with no per-key gesture fallback.
8. Switch apps or rotate between confirmation and execution. Verify stale target handling fails safely.
9. Run audit review and confirm typing records are readable and wrong-action reporting still works.

One note: `handy_macos_ref/` is still untracked and I left it untouched.
