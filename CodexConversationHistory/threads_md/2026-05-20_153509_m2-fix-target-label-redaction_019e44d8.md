# M2: Fix target label redaction

## Metadata

- Thread ID: `019e44d8-a58d-7863-844e-e34ef5dc471e`
- Created: 2026-05-20 15:35:09 IST
- Updated: 2026-05-20 15:47:21 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/20/rollout-2026-05-20T15-35-09-019e44d8-a58d-7863-844e-e34ef5dc471e.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `796f32dc75b1d0abc3735e28858887421f18ee48`
- Messages exported: 14

## Brief Summary

This conversation focused on: close the privacy hole where TargetCandidate.label can carry Likely related git changes: 7145d1e M1 + M2 + M3: Add live screen guard and target redaction.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `796f32dc75b1d0abc3735e28858887421f18ee48`
- Likely related commits:
  - `7145d1e` 2026-05-20 16:32:26 IST [high] M1 + M2 + M3: Add live screen guard and target redaction. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/accessibility/LiveScreenGuard.kt, android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt, android-runtime/src/main/kotlin/com/handy/runtime/capture/ScreenCapturePipeline.kt, android-runtime/src/main/kotlin/com/handy/runtime/llm/ClaudeLlmClient.kt, android-runtime/src/test/kotlin/com/handy/runtime/accessibility/SemanticPointerResolverTest.kt, app/build.gradle.kts, app/src/androidTest/kotlin/com/handy/app/diagnostics/DiagnosticsActivityRedactionScreenshotTest.kt, app/src/androidTest/kotlin/com/handy/app/os/Os5SecureWindowTest.kt, app/src/androidTest/kotlin/com/handy/app/overlay/ManualTargetSelectorTest.kt, app/src/androidTest/kotlin/com/handy/app/pointing/MarkIdHandoffInvariantTest.kt, app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt

## Conversation

### USER 2026-05-20T10:05:20.951Z

Goal: close the privacy hole where TargetCandidate.label can carry
raw node text past the existing redactor.

Files to touch:
- android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt
  * In RuntimeCandidate.fromNode: pass text/desc through
    ScreenRedactor.redactText with context built from
    className/viewId/contentDescription, AND with isPassword=true when
    node.isPassword (already the case in AccessibilityMarksProvider).
  * In RuntimeCandidate.fromMark: marks are pre-redacted, but defend
    in depth — re-apply ScreenRedactor.redactMark.
  * When materialising TargetCandidate, ensure label/role/viewId
    pass through the redactor's diagnostics=true path (so emails /
    phones are masked in dev-only diagnostics dumps).
- app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt
  (audit rows show redacted text only; assert via screenshot test
   that REDACTED tokens appear).
- core/src/main/kotlin/com/handy/core/audit/AuditEvent.kt (no shape
  change; just make sure callers use the redacted target description
  — search AuditAction call sites and verify).

Acceptance:
- New tests in :android-runtime:
  * "password field never appears in ResolvedPointTarget.debugCandidates"
  * "OTP-like short code is masked when label context includes 'OTP'"
  * "card-like number passes Luhn → masked"
- Manual: open a login screen with a password field, ask "where do
  I tap to log in" → Diagnostics shows debugCandidates with
  [redacted] in the password row.

Tests:
- ./gradlew :android-runtime:test :core:test

### ASSISTANT 2026-05-20T10:05:29.100Z

I’ll trace the resolver and redactor paths first, then patch the narrow privacy boundary and add the requested runtime tests. After that I’ll check the audit/diagnostics callers so we don’t leave a second path carrying raw labels.

### ASSISTANT 2026-05-20T10:05:42.566Z

I see the resolver file already has local modifications in this worktree, so I’m going to read that area carefully and layer the privacy fix on top instead of flattening anything. I’m also checking the redactor’s exact API because the context and diagnostics flags matter here.

### ASSISTANT 2026-05-20T10:06:11.953Z

The redactor already has the rules we need: password redaction, Luhn-backed card masking, context-sensitive short-code masking, and a diagnostics-only email/phone pass. The hole is the resolver’s live-node path and the diagnostic materialization path, exactly where you pointed.

### ASSISTANT 2026-05-20T10:08:10.403Z

I’m going to patch three layers: resolver ingestion/debug materialization, audit-description formatting, and diagnostics display. The runtime tests will use deliberately raw fallback marks, which exercises the defense-in-depth mark path and the debug-candidate surface without needing a real Android accessibility tree in local JVM tests.

### ASSISTANT 2026-05-20T10:10:20.208Z

The resolver and diagnostics surfaces are patched now. I’ve also added the three requested `:android-runtime` tests and a Compose instrumentation screenshot-style check for the diagnostics row, so next I’m going to run the requested Gradle tests and fix whatever shakes out.

### ASSISTANT 2026-05-20T10:10:31.579Z

Gradle didn’t get as far as Kotlin: the machine can’t find a Java runtime via the normal `java` lookup. I’ll look for an installed JBR/JDK path next and rerun with `JAVA_HOME` if one is present.

### ASSISTANT 2026-05-20T10:12:04.238Z

There isn’t a JDK in the Android SDK or usual system locations. Homebrew has `openjdk@17` available but not installed; I’m going to avoid changing the machine globally unless needed, so I’ll finish local verification by inspecting compile-sensitive spots and report the Gradle block clearly.

### ASSISTANT 2026-05-20T10:13:08.554Z

I found a non-global path forward: instead of installing Homebrew Java, I’ll pull a temporary JDK under `/tmp` and point this one Gradle run at it. That keeps the machine setup untouched and gives us a real test result if the download cooperates.

### ASSISTANT 2026-05-20T10:13:42.397Z

The temporary JDK fetch is connected but hasn’t started transferring bytes yet, so this may be a network/CDN stall rather than a repo issue. I’ll give it a little longer, then fall back to reporting the blocked Gradle run if it stays at zero.

### ASSISTANT 2026-05-20T10:14:37.955Z

The direct temporary download stayed at zero bytes for 45 seconds, so I stopped it. Since Homebrew has a bottled JDK available in the user-local prefix, I’m going to try that route now; it’s the least messy way left to get Gradle moving.

### ASSISTANT 2026-05-20T10:15:17.401Z

Homebrew found the JDK bottle, but because this Homebrew lives under the user home directory instead of `/opt/homebrew`, it is warning that several dependencies may build from source. I’ll watch it closely; if it turns into a long toolchain build, I’ll stop and leave the test run marked blocked rather than burning the afternoon.

### ASSISTANT 2026-05-20T10:16:03.276Z

The Homebrew path has moved into building `gettext` from source and is quiet now, which is exactly the long-build risk. I’m giving it one last short window; if it doesn’t finish that dependency soon, I’ll stop the install attempt and keep the repo changes cleanly bounded.

### ASSISTANT 2026-05-20T10:17:21.522Z

Implemented the privacy closure.

Changed:
- [SemanticPointerResolver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt:393): live node text/desc now go through `ScreenRedactor`, with `isPassword = node.isPassword`.
- [SemanticPointerResolver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt:432): fallback marks are re-redacted via `redactMark`.
- [SemanticPointerResolver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt:308): `TargetCandidate` debug fields now use the diagnostics redaction path for label/role/viewId.
- [AccessibilityGestureActionPerformer.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt:285): audit `semanticTarget` descriptions are built from redacted fields.
- [DiagnosticsActivity.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt:263): audit rows defensively redact displayed target text.
- Added resolver tests in [SemanticPointerResolverTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/test/kotlin/com/handy/runtime/accessibility/SemanticPointerResolverTest.kt:51), including password, OTP, Luhn card, and diagnostics-only email/phone masking.
- Added the diagnostics screenshot-style Compose check in [DiagnosticsActivityRedactionScreenshotTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/androidTest/kotlin/com/handy/app/diagnostics/DiagnosticsActivityRedactionScreenshotTest.kt:25).

Audit shape stayed unchanged in `AuditEvent.kt`; I verified the `AuditAction` call sites and the app performer is the producer that needed the redacted description.

Verification:
- `git diff --check` passed.
- `./gradlew :android-runtime:test :core:test` could not run because the machine has no Java runtime available. I tried a temporary JDK download, but it stalled at 0 bytes; I also started a Homebrew JDK install, saw it enter source-build territory, and stopped it to avoid a long/global dependency change.
