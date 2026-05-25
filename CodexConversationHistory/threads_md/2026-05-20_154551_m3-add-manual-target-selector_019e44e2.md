# M3: Add manual target selector

## Metadata

- Thread ID: `019e44e2-6fe2-7ff2-80d4-872202cadca4`
- Created: 2026-05-20 15:45:51 IST
- Updated: 2026-05-20 16:08:59 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/20/rollout-2026-05-20T15-45-51-019e44e2-6fe2-7ff2-80d4-872202cadca4.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `796f32dc75b1d0abc3735e28858887421f18ee48`
- Messages exported: 17

## Brief Summary

This conversation focused on: when semantic resolution fails or the buddy points wrong, the Likely related git changes: 7145d1e M1 + M2 + M3: Add live screen guard and target redaction; 9549a86 F1: Viewport-aware buddy flight, stale cancel & FSM.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `796f32dc75b1d0abc3735e28858887421f18ee48`
- Likely related commits:
  - `7145d1e` 2026-05-20 16:32:26 IST [high] M1 + M2 + M3: Add live screen guard and target redaction. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/accessibility/LiveScreenGuard.kt, android-runtime/src/main/kotlin/com/handy/runtime/accessibility/SemanticPointerResolver.kt, android-runtime/src/main/kotlin/com/handy/runtime/capture/ScreenCapturePipeline.kt, android-runtime/src/main/kotlin/com/handy/runtime/llm/ClaudeLlmClient.kt, android-runtime/src/test/kotlin/com/handy/runtime/accessibility/SemanticPointerResolverTest.kt, app/build.gradle.kts, app/src/androidTest/kotlin/com/handy/app/diagnostics/DiagnosticsActivityRedactionScreenshotTest.kt, app/src/androidTest/kotlin/com/handy/app/os/Os5SecureWindowTest.kt, app/src/androidTest/kotlin/com/handy/app/overlay/ManualTargetSelectorTest.kt, app/src/androidTest/kotlin/com/handy/app/pointing/MarkIdHandoffInvariantTest.kt, app/src/main/kotlin/com/handy/app/accessibility/AccessibilityGestureActionPerformer.kt
  - `9549a86` 2026-05-20 17:57:23 IST [high] F1: Viewport-aware buddy flight, stale cancel & FSM. Files: DEBUG_LOG.md, app/build.gradle.kts, app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt, app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt, app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/main/kotlin/com/handy/app/widget/BezierFlightController.kt, app/src/test/kotlin/com/handy/app/overlay/BuddyFlightLandingGeometryTest.kt, app/src/test/kotlin/com/handy/app/overlay/OverlayPresenterFsmTest.kt, core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt

## Conversation

### USER 2026-05-20T10:17:25.998Z

Goal: when semantic resolution fails or the buddy points wrong, the
user can long-press the widget and tap the intended target; Handy
records that node and uses it.

Files to touch:
- (new) app/src/main/kotlin/com/handy/app/overlay/ManualTargetSelector.kt
- app/src/main/kotlin/com/handy/app/widget/WidgetContent.kt
  (long-press-while-pointing → enter ManualTargetSelector mode)
- app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt
  (when selector is active, listen for TYPE_VIEW_CLICKED outside
   our own package and capture the node via event.source; recycle
   carefully)
- app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt
  (resumeWithManualTarget(node): re-fly to the new bounds, audit
   "manual-fallback")
- core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt
  (add a ManualTargetSelection state to the FSM seed; full FSM
   lands in F1)

UX:
- While the sticky pointer is up, a small "Wrong one?" chip appears.
- Tapping the chip dims the overlay, shows "Tap the right one"
  text + waves a small ring around the user's first touch.
- Audit row recorded: AuditAction.ManualSelect.

Acceptance:
- Manual: open a Chrome WebView page where semantic resolution
  fails → "Wrong one?" chip → tap → buddy hops to user's choice.
- Audit log shows AuditAction.ManualSelect, no other taps fire.

Tests:
- New instrumented test in app/src/androidTest for the selector.

Do NOT:
- Persist or learn from manual selections in this prompt (that
  belongs to a future prompt under H7-style allowlists, scoped
  carefully).

### ASSISTANT 2026-05-20T10:17:31.612Z

I’ll trace the current overlay, widget, accessibility, and audit flow first so the selector lands in the existing shape instead of bolting on a second control path.

### ASSISTANT 2026-05-20T10:17:40.836Z

There are already local modifications, including in `BuddyFlightDriver.kt` and a few accessibility/runtime files. I’ll work with those as the current baseline and keep the new manual selection changes narrowly scoped.

### ASSISTANT 2026-05-20T10:18:06.002Z

The current sticky pointer already dismisses on app touches, so the capture path has to get first dibs on the click event and suppress that dismissal while selector mode is active. I’m also checking how audits are written so the manual row matches the existing diagnostics trail.

### ASSISTANT 2026-05-20T10:21:19.778Z

I’ve got the shape now. I’m going to add a small selector controller with a non-touchable dim layer, wire the sticky-pointer chip and long-press entry into the widget service, and route external click events through the accessibility service before the sticky pointer dismiss path.

### ASSISTANT 2026-05-20T10:21:24.712Z

First I’m adding the core vocabulary: a `ManualTargetSelection` overlay mode and the `ManualSelect` audit action, so diagnostics and the presenter can speak the same language as the selector.

### ASSISTANT 2026-05-20T10:22:13.109Z

Now I’m adding the selector itself. The dimming view is deliberately non-touchable, so the user’s tap still reaches the target app and the accessibility event can give us the selected node.

### ASSISTANT 2026-05-20T10:23:27.565Z

Next I’m teaching the flight driver how to accept a manually selected accessibility node: copy its bounds into a new flight, write a `ManualSelect` audit row, and recycle the node after the hop is scheduled.

### ASSISTANT 2026-05-20T10:24:14.352Z

The “Wrong one?” chip lives with the widget visuals so it stays visually consistent with existing bubble chips, while the service will host it in a separate touchable overlay.

### ASSISTANT 2026-05-20T10:24:33.574Z

Now I’m wiring the two user entry points: the touchable chip while pointing, and a widget long-press that starts manual selection instead of voice when the buddy is already sticky-pointing.

### ASSISTANT 2026-05-20T10:26:15.470Z

I’m moving the manual capture check ahead of the existing sticky-pointer dismissal, so the first external click in selector mode becomes the chosen node instead of just dismissing the pointer.

### ASSISTANT 2026-05-20T10:27:00.048Z

I’m adding a small instrumented selector test around the core capture behavior: start selector, hand it a real `AccessibilityNodeInfo`, verify it records bounds/package, and verify own-package clicks are ignored.

### ASSISTANT 2026-05-20T10:27:34.304Z

The implementation is in place. I’m running the Kotlin compile path now; that usually catches the Android API sharp edges around accessibility nodes and overlay constructors quickly.

### ASSISTANT 2026-05-20T10:27:46.530Z

The local Gradle compile is blocked because this machine doesn’t have a Java runtime available to the shell. I’ll still do a static pass for likely Kotlin/API issues and fix anything obvious before wrapping.

### ASSISTANT 2026-05-20T10:29:36.352Z

One subtlety popped up in the static pass: the sticky-pointer safety timeout could fire during manual selection. I’ve guarded that path so selector mode keeps ownership until the user picks a target or cancels.

### ASSISTANT 2026-05-20T10:30:23.881Z

I’m also covering the true resolver-failure case: instead of dropping straight back to idle, the presenter now keeps the buddy in a manual-fallback pointing state so the “Wrong one?” chip is available even when no reliable target was found.

### ASSISTANT 2026-05-20T10:38:59.707Z

Implemented the manual target fallback flow.

What changed:
- Added [ManualTargetSelector.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/ManualTargetSelector.kt): dim overlay, “Tap the right one” prompt, pulse ring, external click capture via `AccessibilityEvent.source`.
- Wired selector capture in [HandyAccessibilityService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt) before sticky-pointer dismissal.
- Added the touchable “Wrong one?” chip and long-press-while-pointing entry in [FloatingWidgetOverlayService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt) and [WidgetContent.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/widget/WidgetContent.kt).
- Added `resumeWithManualTarget(...)` in [BuddyFlightDriver.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt), including re-flight to selected bounds and `AuditAction.ManualSelect` audit rows with `manual-fallback`.
- Added FSM/audit seed updates in core, plus [ManualTargetSelectorTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/androidTest/kotlin/com/handy/app/overlay/ManualTargetSelectorTest.kt).

Verification:
- `git diff --check` passed.
- I could not run Gradle compile/tests because the shell has no Java runtime installed: `Unable to locate a Java Runtime`.

I also left the pre-existing dirty worktree changes intact.
