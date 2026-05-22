# Handy Coexistence Beta Test Pack

Run this pack before every beta. The goal is to prove Handy coexists with core
Android accessibility, display, locale, navigation, and windowing modes on at
least one Pixel device and one OEM device.

Related docs: [`DEVICE_MATRIX.md`](DEVICE_MATRIX.md),
[`PLAY_POLICY_MATRIX.md`](PLAY_POLICY_MATRIX.md), and
[`ACTION_POLICY.md`](ACTION_POLICY.md).

## What Changes

This change does not alter Handy runtime behavior. It adds the release gate the
team uses to catch coexistence regressions before a beta goes out.

Before this pack, a beta could pass the general smoke script while still missing
a real user setup. For example, a tester might verify the floating widget on a
Pixel with default English, gesture navigation, normal text size, and no screen
reader, then ship a build where Hindi locale wraps a permission row badly or
TalkBack focus skips the primary action.

After this pack, every beta has a single pass/fail table covering those modes on
Pixel and OEM hardware. For example, if Switch Access is enabled on a Samsung
device, the tester must prove they can still reach Handy's onboarding actions,
open chat, ask a screen question, and back out without an app crash or trapped
focus.

## Automated Subset

Run the deterministic instrumentation subset on every device lane:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.handy.app.CoexistenceSmokeTests
```

This subset covers:

- Accessibility-state listener wiring used when TalkBack or another
  accessibility service changes global accessibility state.
- RTL layout smoke for a representative Handy surface.
- Large-font layout smoke at 1.8x font scale for the same surface.

## Common Beta Script

Use this same app script for every row in the table unless the row setup says
otherwise.

1. Install the beta build and clear previous app state.
2. Launch Handy from the launcher.
3. Complete the in-app disclosure.
4. Verify permission rows are readable, reachable, and accurately reflect system
   state.
5. Enable the required permissions for the row, or explicitly choose reduced
   mode when the row is testing a denied/off state.
6. Open Handy chat.
7. Ask: "Summarize this screen".
8. Open Settings from Handy, then return to chat.
9. Open another ordinary app, summon Handy, and ask it to point at a visible
   non-destructive control.
10. Do not approve destructive or sensitive actions during coexistence testing.
11. Return to Handy and open Diagnostics if available.
12. Check `adb logcat` for `FATAL EXCEPTION`, ANR, permission, window-token, or
    foreground-service crashes.

Pass means the row's pass criteria are met on both the Pixel and OEM device.
Fail means any fail criterion occurs on either device. Record exact device model,
Android version, build number, and evidence in the notes.

## Manual Pass/Fail Table

Status values: `PASS`, `FAIL`, `BLOCKED`, `N/A`. A complete beta run fills every
Pixel and OEM status cell plus evidence notes.

| Case | Setup | Pass criteria | Fail criteria | Pixel status | OEM status | Evidence and notes |
|---|---|---|---|---|---|---|
| TalkBack off | Confirm TalkBack is disabled before launch. | Handy launches, onboarding and chat are reachable by touch, accessibility-off or reduced-mode copy is accurate, and no TalkBack-only affordance is required. | App blocks the user from reduced mode, shows stale accessibility-on state, loses widget/chat access, or crashes. |  |  |  |
| TalkBack on | Enable TalkBack, then launch Handy and run the common script with swipe/double-tap navigation. | Focus order reaches disclosure, permission rows, primary actions, chat input, send action, settings, diagnostics, and back navigation. Spoken labels are meaningful and no focus trap appears. | Any critical action is unreachable, unlabeled, read in a misleading order, trapped, or the app crashes while TalkBack is active. |  |  |  |
| Switch Access off | Confirm Switch Access is disabled and run the common script with normal touch. | App behaves the same as the baseline and does not require Switch Access-specific timing. | Baseline touch path fails, permission state is stale, or app crashes. |  |  |  |
| Switch Access on | Enable Switch Access with auto-scan or step-scan, then run the common script without direct touch where practical. | Scan order reaches disclosure, permission rows, chat input, send action, settings, and back navigation. Selection does not trigger unintended Tap-for-me actions. | Scan cannot reach a required control, selection causes an unintended action, focus loops indefinitely, or app crashes. |  |  |  |
| Large font | Set system font size to the largest available value, or at least 1.3x with `adb shell settings put system font_scale 1.3`. | Text remains readable, primary actions remain reachable by scroll, permission rows do not hide status/action controls, and chat input/send remain usable. | Text is clipped beyond recognition, buttons become unreachable, rows overlap, or app crashes. |  |  |  |
| Large display | Set display size to large in Settings, or use `adb shell wm density` to emulate a lower-density large-display layout and restore afterward. | Widget, onboarding, chat, settings, and diagnostics keep usable spacing. No essential control is off-screen without scroll. | Widget lands outside reachable bounds, controls overlap, scroll cannot reach content, or app crashes. |  |  |  |
| High contrast | Enable high contrast text or equivalent OEM contrast mode. | Text, chips, disabled/enabled states, and critical affordances remain distinguishable in onboarding, chat, settings, and confirmation surfaces. | Important state depends only on low-contrast color, text disappears, or app crashes. |  |  |  |
| Reduce motion | Disable animations through system accessibility/developer options (`animator_duration_scale`, `transition_animation_scale`, and `window_animation_scale` set to `0` where available). | Handy still opens, widget/chat transitions complete, Buddy or pointer states settle, and no spinner/animation-dependent step hangs. | Any workflow waits forever for animation, pointer state never completes, or app crashes. |  |  |  |
| Haptics off | Disable touch vibration/haptic feedback in system settings. | Long-press and tap workflows still work with visual feedback only; no action depends on vibration. | Long-press is ambiguous or broken without haptics, action confirmation is missed, or app crashes. |  |  |  |
| RTL | Enable force RTL layout direction, restart Handy, and run the common script. | Rows mirror correctly, leading/trailing controls remain visible, chat and settings navigation stay usable, and no text/control overlap appears. | Controls draw off-screen, row actions swap into confusing positions, text overlaps, or app crashes. |  |  |  |
| Hindi system locale | Change system language to Hindi (`hi-IN`), restart Handy, and run the common script. | App launches, dates/times/system strings do not crash formatting, English fallback copy remains readable, and permission/settings rows fit or scroll. | Locale change crashes startup, text formatting crashes, rows become unreachable, or critical copy is unreadable. |  |  |  |
| Gesture navigation | Enable gesture navigation and run the common script, including back gestures from onboarding, chat, settings, and diagnostics. | System back gestures do not conflict with Handy edges, widget drag remains usable, and chat/settings can be dismissed predictably. | Gesture back is swallowed unexpectedly, widget conflicts with system gesture areas, or app crashes. |  |  |  |
| 3-button navigation | Enable 3-button navigation and run the common script. | Bottom navigation bar does not cover Handy input/actions, widget can be dragged away from nav buttons, and back/home/recent do not corrupt state. | Bottom controls are hidden behind the nav bar, widget becomes trapped, state is corrupted after nav buttons, or app crashes. |  |  |  |
| Split screen | Put Handy and one ordinary app in split screen, then run chat, settings, and point-at-visible-control flows. | Layout recomposes to the smaller window, input remains usable with keyboard, widget/panel geometry stays inside visible bounds, and app resumes cleanly. | Content is clipped with no scroll, widget/panel draws outside the split window, keyboard covers send permanently, or app crashes. |  |  |  |
| Foldable | Use a foldable device/emulator. Run closed, open/tablet, and half-open posture where supported. | Handy survives posture changes, recomposes without stale coordinates, widget remains reachable, and pointer/overlay geometry updates after each fold state. | Crash on posture change, stale target coordinates after folding, widget appears on the wrong display area, or controls overlap. |  |  |  |

## End-to-End Manual Plan

### Device Setup

- Required devices: one Pixel and one OEM device. Prefer Samsung for OEM if
  available; otherwise use OnePlus, Oppo, Vivo, Realme, Xiaomi, or Motorola.
- Record model, Android version, build number, display size, font scale,
  navigation mode, locale, and enabled accessibility services.
- Start each row from a known state. Reset modified settings after each row if
  the next row depends on defaults.
- Keep one logcat capture per device:

```bash
adb logcat -c
adb logcat -v time > coexistence-DEVICE-CASE.log
```

### Build And Install

1. Build the app and test APK:

   ```bash
   ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
   ```

2. Install the beta build.
3. Clear app state before the first row on each device.
4. Run `CoexistenceSmokeTests` once on each device before manual rows.

### Manual Row Execution

For each row:

1. Apply the row setup and verify the setting visually or with `adb settings`
   when practical.
2. Run the common beta script.
3. Rotate once if the device supports it.
4. Background and foreground Handy once.
5. Lock and unlock the device once.
6. Capture a screenshot or short screen recording for any issue.
7. Stop logcat, scan for fatal crashes, ANRs, and permission failures.
8. Mark the row `PASS`, `FAIL`, `BLOCKED`, or `N/A`.

### Practical Crash Audit

Search logs for:

- `FATAL EXCEPTION`
- `ANR in com.handy.android`
- `BadTokenException`
- `ForegroundServiceStartNotAllowedException`
- `SecurityException`
- `WindowManager`
- `AccessibilityService`
- `IllegalStateException`

Any crash, ANR, or repeatable system exception in the tested flow is a beta
blocker until triaged.

### Exit Criteria

- `CoexistenceSmokeTests` pass on at least one device lane.
- Every manual table row has Pixel and OEM status filled in.
- Every `FAIL` has an issue link, logs, screenshots/video, and a release
  decision.
- No known crash or build error remains in the beta candidate.
