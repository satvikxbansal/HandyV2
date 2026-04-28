---
name: Pointer Landing Accuracy
overview: Keep LLM pointing semantic and move the offset logic into Android runtime so the widget lands near the intended button without looking closer to a neighboring control.
todos:
  - id: landing-math
    content: Refine Android pointer landing math with edge-aware target-affinity scoring.
    status: completed
  - id: diagnostics
    content: Add concise logs for target bounds, candidate kind, final landing, and pointer angle.
    status: completed
  - id: bubble-polish
    content: Optionally adjust bubble placement for bottom-nav targets after landing is correct.
    status: completed
  - id: verify
    content: Run lints, whitespace check, and compile when Java runtime is available.
    status: completed
isProject: false
---

# Pointer Landing Accuracy

## Diagnosis
The screenshots show the pointer landing between bottom-nav items, so visually it reads closer to `Photos` / `Contacts` than the intended `Search` / `Voicemail`. This is a runtime landing problem, not a prompt problem.

The current Android logic in [`app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt`](app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt) picks the nearest non-overlapping widget rectangle around the target bounds. That avoids covering the button, but for dense bottom navigation it can select a nearby slot whose widget center is closer to an adjacent tab label/icon.

macOS does not ask the model for fake coordinates. It resolves the actual target, then offsets the visual cursor locally before animating:

```517:526:handy_macos_ref/Handy/Services/CompanionCursorManager.swift
private func startNavigatingToElement(screenLocation: CGPoint) {
    pointingReturnWorkItem?.cancel()
    pointingReturnWorkItem = nil

    let targetInSwiftUI = convertScreenPointToSwiftUI(screenLocation)
    let offsetTarget = CGPoint(x: targetInSwiftUI.x + 8, y: targetInSwiftUI.y + 12)
    let clampedTarget = CGPoint(
        x: max(20, min(offsetTarget.x, screenFrame.width - 20)),
        y: max(20, min(offsetTarget.y, screenFrame.height - 20))
    )
```

## Recommendation
Do not change the prompt to request shifted coordinates. Keep `[POINT]` targeting the actual control, then compute a visual landing position in Android that satisfies three constraints:

- The widget rect must not overlap the target bounds.
- The pointer glyph/tip should aim back at the target center.
- For bottom nav / top nav rows, prefer landing above the intended item and horizontally centered on that item, rather than beside it where it can read as adjacent.

## Implementation Plan
- Update [`BuddyFlightDriver.kt`](app/src/main/kotlin/com/handy/app/overlay/BuddyFlightDriver.kt):
  - Replace the current simple nearest-candidate scoring in `chooseLandingPosition` with target-aware candidates.
  - Detect dense nav rows using target position and dimensions:
    - bottom-edge targets: prefer `above target, center aligned`
    - top-edge targets: prefer `below target, center aligned`
    - left/right edge targets: prefer side placement but keep vertical center aligned
  - Add a `targetAffinityPenalty` so candidates that drift horizontally toward neighboring controls score worse.
  - Keep hard non-overlap with target bounds, using a small expanded target rect margin so the widget never hugs the tappable area too tightly.
- Keep `angleFromWidgetToTarget(...)` as the final pointer orientation source so the hand points back to the real target center.
- Add/keep diagnostic logs around landing:
  - target bounds
  - chosen candidate kind (`bottom-above-center`, `side-right`, fallback, etc.)
  - final landing rect
  - pointer angle
- Leave [`PromptCatalog.kt`](core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt) unchanged unless logs later show the model is pointing at the wrong semantic element. Prompt changes would make grounding worse because the model should not be responsible for overlay geometry.
- Optional polish after the landing fix: improve bubble placement in [`FloatingWidgetOverlayService.kt`](app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt) so for bottom-nav targets the bubble prefers above the pointer and does not stretch across adjacent labels.

## Validation
- Manual cases:
  - Photos bottom nav: `Search` should land visually above/near Search, not closer to Photos.
  - Phone bottom nav: `Voicemail` should land above/near Voicemail, not closer to Contacts.
  - Top-left menu button should land near but not cover the menu.
  - Corner controls should clamp on screen while still pointing at the actual target.
- Automated/focused checks:
  - Add small unit tests if `chooseLandingPosition` can be extracted to a pure helper without expanding scope too much.
  - Run `ReadLints`, `git diff --check`, and `:app:compileDebugKotlin` if Java runtime is available.