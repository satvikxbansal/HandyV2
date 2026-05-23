# Handy Android Device Matrix

This matrix is the release-regression scaffold. Each run cell is one concrete
device/API/navigation/locale/accessibility/display/windowing combination.

Status values:

- `PASS`: regression run completed with no release-blocking issue.
- `FAIL`: regression run completed and found a release-blocking issue.
- `BLOCKED`: cell cannot be run because the image/device or permission path is
  unavailable.
- `TBD`: not run yet.
- `ENV VERIFIED`: device state was verified with `adb`, but the full Handy
  regression script has not been executed for that exact cell.

## Required Axes

- Device families: Pixel, Samsung, BBK/OEM representative.
- APIs: 26, 29, 30, 33, 34, 36.
- Navigation: gesture-nav and 3-button.
- Locale: Hindi (`hi-IN`) plus default locale comparison.
- Accessibility: TalkBack off and TalkBack on.
- Display/windowing: phone, large display, split screen, foldable.

## Seed Cells Verified Locally

These two rows are the only cells available in the current local environment.
They are outside the requested release API set because the only installed/running
AVD is `Pixel_9_Pro` on API 35. Keep them as smoke seeds until API 34/36 images
or physical devices are available.

| Device family | Device/profile | API | Navigation | Locale | TalkBack | Display/windowing | Status | Evidence | Notes |
|---|---|---:|---|---|---|---|---|---|---|
| Pixel | Pixel_9_Pro AVD (`sdk_gphone64_arm64`) | 35 | gesture-nav | en-US | off | phone, fullscreen | ENV VERIFIED | `adb`: `ro.boot.qemu.avd_name=Pixel_9_Pro`, `ro.build.version.sdk=35`, `navbar.gestural=[x]`, `touch_exploration_enabled=0`, `wm size=1280x2856` | Local smoke baseline only; not a release-matrix substitute. |
| Pixel | Pixel_9_Pro AVD (`sdk_gphone64_arm64`) | 35 | 3-button | en-US | off | phone, fullscreen | ENV VERIFIED | `adb`: `navbar.threebutton=[x]`, `navigation_mode=0` after overlay switch | Local smoke baseline only; emulator restored to gesture-nav after verification. |

## Target Release Matrix

Each `TBD` below expands into both navigation modes, Hindi/default locale,
TalkBack on/off, and the relevant display/windowing variants. Add exact run
rows above as cells are executed.

| Device family | Representative target | API 26 | API 29 | API 30 | API 33 | API 34 | API 36 | Required variants still open |
|---|---|---|---|---|---|---|---|---|
| Pixel | Pixel emulator or physical Pixel | TBD | TBD | TBD | TBD | TBD | TBD | gesture-nav, 3-button, Hindi, TalkBack on/off, large display, split screen, foldable |
| Samsung | Galaxy emulator/physical device | TBD | TBD | TBD | TBD | TBD | TBD | gesture-nav, 3-button, Hindi, TalkBack on/off, large display, split screen, Galaxy Fold |
| BBK/OEM | OnePlus/Oppo/Vivo/Realme physical device | TBD | TBD | TBD | TBD | TBD | TBD | gesture-nav, 3-button, Hindi, TalkBack on/off, large display where available, split screen |

## Per-Cell Checklist

Use this checklist for every exact run row:

- App installs and launches.
- Onboarding permission state reflects Android system state.
- Widget appears, drags, snaps, and reopens chat/panel.
- `ChatViewModel` turn carries `TurnScreenContext` when accessibility/capture is
  available.
- `OverlayChatPipeline` turn carries `TurnScreenContext` from the panel snapshot
  or live builder.
- Accessibility pointer target resolves or fails closed.
- OpenApp recipe launches the right app.
- Install X opens the Play Store listing.
- Timer recipe sets the right duration.
- Calendar recipe opens compose UI with prefilled fields.
- Ride-hailing recipe stops before Confirm.
- Summarize-screen mode produces a 2-4 sentence answer with no tool calls and
  no pointer.
- No screenshot/image payload appears in crash or error logs.
- Reduced-mode behavior is clear when accessibility/capture permissions are
  missing.
- Split-screen/foldable/large-display geometry does not cover target controls.
