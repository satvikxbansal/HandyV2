# Remove inferSemanticPoint fallback

## Metadata

- Thread ID: `019e4580-9897-7052-bd9c-e6e09a115dd7`
- Created: 2026-05-20 18:38:36 IST
- Updated: 2026-05-20 18:41:14 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/20/rollout-2026-05-20T18-38-36-019e4580-9897-7052-bd9c-e6e09a115dd7.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `9549a8659d3a0242cebe62879eb44a231b1b6ebd`
- Messages exported: 8

## Brief Summary

This conversation focused on: OverlayChatPipeline.inferSemanticPoint still constructs ad-hoc Likely related git changes: 7145d1e M1 + M2 + M3: Add live screen guard and target redaction; e07fa9b Some fixes.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `9549a8659d3a0242cebe62879eb44a231b1b6ebd`
- Likely related commits:
  - `7145d1e` 2026-05-20 16:32:26 IST [high] M1 + M2 + M3: Add live screen guard and target redaction. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/accessibility/LiveScreenGuard.kt, android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt, android-runtime/src/main/kotlin/com/handy/runtime/capture/ScreenCapturePipeline.kt, android-runtime/src/main/kotlin/com/handy/runtime/llm/ClaudeLlmClient.kt, android-runtime/src/test/kotlin/com/handy/runtime/accessibility/SemanticPointerResolverTest.kt, app/build.gradle.kts, app/src/androidTest/kotlin/com/handy/app/diagnostics/DiagnosticsActivityRedactionScreenshotTest.kt, app/src/androidTest/kotlin/com/handy/app/os/Os5SecureWindowTest.kt, app/src/androidTest/kotlin/com/handy/app/overlay/ManualTargetSelectorTest.kt, app/src/androidTest/kotlin/com/handy/app/pointing/MarkIdHandoffInvariantTest.kt, app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt
  - `e07fa9b` 2026-05-20 18:41:29 IST [low] Some fixes. Files: DEBUG_LOG.md, README.md, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/test/kotlin/com/handy/app/overlay/OverlayPresenterFsmTest.kt

## Conversation

### USER 2026-05-20T13:08:40.082Z

Goal: OverlayChatPipeline.inferSemanticPoint still constructs ad-hoc
SemanticPoint targets when the LLM omits a [POINT:...] tag. These
ad-hoc targets bypass the M1 markId-preserving path. Either delete
the heuristic (it was a v1 fallback) or route its output through
buildTapTargetForResolved so the resolver/performer can still apply
the new package/window guard.

Files to touch:
- app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt

Required changes (preferred — delete):
- Remove inferSemanticPoint() and the call site that uses it; only
  fly when pointing.semantic is non-null from the parser.
- Remove the "menu / drawer / hamburger" keyword list and the
  TOP_LEFT_MENU_MAX_Y constant.
- Make sure tests still pass (no test in the repo depends on this
  heuristic).

Alternative (keep behavior but route through guard):
- Keep inferSemanticPoint() but have it construct a SemanticPoint
  with markId from the matched AccessibilityMark, and let the rest
  of the path flow through unchanged.

RCA log entry:
- "DL-### inferSemanticPoint bypassed M1 markId path".
- Root cause: v1 heuristic for top-left menus survived M1 because
  the diff focused on flyToAndTap signature. The heuristic only
  fires when the LLM omits a [POINT:...], which is rare with the
  current prompts, so behavior is silent.
- Prevention rule: when refactoring an action-target plumbing
  contract, grep for every SemanticPoint(...) constructor call and
  audit whether each path threads through the new guard.

Tests: ./gradlew :app:testDebugUnitTest

### ASSISTANT 2026-05-20T13:08:48.703Z

I’ll make the targeted cleanup in `OverlayChatPipeline.kt`, then run the requested debug unit tests. First I’m going to inspect the current call path and any nearby RCA/log convention so the edit lands cleanly.

### ASSISTANT 2026-05-20T13:09:00.709Z

The file has exactly the old fallback path: `pointing?.semantic ?: inferSemanticPoint(...)`, plus helper constructors that synthesize `SemanticPoint`. I’m removing that whole heuristic and then I’ll append the next DL entry, since the repo’s debug log is append-only and the prompt gives the RCA text.

### ASSISTANT 2026-05-20T13:09:39.875Z

The code path is now narrowed to parser-emitted semantic points only. I’m adding the RCA as `DL-057`, following the existing table format at the end of `DEBUG_LOG.md`.

### ASSISTANT 2026-05-20T13:09:59.781Z

Next I’m doing a quick sweep for any leftover fallback symbols and then running the requested Gradle unit test task. If the local JDK setup grumbles, I’ll report that plainly and retry with the repo’s known JDK path only if needed.

### ASSISTANT 2026-05-20T13:10:17.446Z

The direct Gradle invocation hit the machine’s missing default Java runtime, so the code hasn’t been exercised yet. I’m checking the repo’s previously used local JDK path and retrying the same test task with `JAVA_HOME` set.

### ASSISTANT 2026-05-20T13:10:41.046Z

The unit suite passed with the local JDK path. I’m updating the DL-057 validation line from “pending” to the actual passing command so the log does not lie quietly, which is always where logs start causing trouble.

### ASSISTANT 2026-05-20T13:11:14.096Z

Implemented the preferred delete path.

Changed [OverlayChatPipeline.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt:211) so buddy flight only uses `pointing?.semantic` from the parser. Removed `inferSemanticPoint()`, the menu/drawer/hamburger fallback keywords, ad-hoc `SemanticPoint(...)` construction, `normalize()`, and `TOP_LEFT_MENU_MAX_Y`.

Added the RCA entry as [DL-057](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:941): `inferSemanticPoint bypassed M1 markId path`.

Validation:
`git diff --check` passed.
`./gradlew :app:testDebugUnitTest` could not start without a default Java runtime, then passed with:
`JAVA_HOME=/tmp/codex-jdk17/Contents/Home PATH=/tmp/codex-jdk17/Contents/Home/bin:$PATH ./gradlew :app:testDebugUnitTest`

Note: the worktree already had unrelated dirty files (`README.md`, `OverlayPresenter.kt`, `OverlayPresenterFsmTest.kt`, `handy_macos_ref/`); I left those alone.
