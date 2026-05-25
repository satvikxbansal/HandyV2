# P1: Migrate splash to design tokens

## Metadata

- Thread ID: `019e5858-f6c3-7b20-9331-296392aae848`
- Created: 2026-05-24 10:28:05 IST
- Updated: 2026-05-24 10:34:50 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/24/rollout-2026-05-24T10-28-05-019e5858-f6c3-7b20-9331-296392aae848.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `e6d67f942d054cbe608d7e13e9aa4223fedab092`
- Messages exported: 14

## Brief Summary

This conversation focused on: The splash you already shipped (app/src/main/kotlin/com/handy/app/ onboarding/SplashScreen.kt) hard-codes its own colors and timings. Migrate it to read from the new HandyDesign tokens shipped in P-0 so the rest of the onboarding inherits… Likely related git changes: e6d67f9 Add missing handoff drawable assets; 297d0c7 Migrate splash to HandyDesign tokens.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `e6d67f942d054cbe608d7e13e9aa4223fedab092`
- Likely related commits:
  - `e6d67f9` 2026-05-24 10:26:02 IST [medium] Add missing handoff drawable assets. Files: DEBUG_LOG.md, app/src/main/res/drawable/ic_lucide_camera.xml, app/src/main/res/drawable/ic_lucide_timer.xml, app/src/main/res/drawable/ic_phosphor_mic.xml, app/src/main/res/drawable/ic_phosphor_send.xml
  - `297d0c7` 2026-05-24 10:34:02 IST [medium] Migrate splash to HandyDesign tokens. Files: DEBUG_LOG.md, app/src/main/kotlin/com/handy/app/onboarding/SplashScreen.kt

## Conversation

### USER 2026-05-24T04:58:26.015Z

Read the universal rules. Single-pass. Read → implement → test → commit.

GOAL
The splash you already shipped (app/src/main/kotlin/com/handy/app/
onboarding/SplashScreen.kt) hard-codes its own colors and timings.
Migrate it to read from the new HandyDesign tokens shipped in P-0
so the rest of the onboarding inherits the same accent values, and
align the breath animation with the design canvas:

  scenes-onboarding.jsx defines Ring with handy-breath animation:
    @keyframes handy-breath {
      0%, 100% { transform: scale(1);    opacity: var(--ring-opacity, 0.2); }
      50%      { transform: scale(1.06); opacity: calc(var(--ring-opacity, 0.2) * 1.4); }
    }
  Duration 3.6s, staggered 0/0.6/1.2s.

Your current implementation already does scale 1→1.06 and alpha
base→base*1.4 with 3.6s, FastOutSlowInEasing, RepeatMode.Reverse,
0/600/1200ms offsets — keep those. The only changes:

FILES TO READ FIRST (in full)
- app/src/main/kotlin/com/handy/app/onboarding/SplashScreen.kt
- handy-new-design-handoff/project/src/scenes-onboarding.jsx (SplashScreen
  + SplashMark + Ring functions, lines 44–165)
- app/src/main/kotlin/com/handy/app/design/HandyDesignTokens.kt (P-0)
- app/src/main/kotlin/com/handy/app/design/HandyDesignType.kt (P-0)
- app/src/main/res/values/strings.xml (find splash_title,
  splash_subtitle, splash_built_by_line — keep their strings,
  re-verify the existing text is "Handy" / "Your on-screen copilot"
  / "Built with love by Satvik Bansal")

CHANGES TO MAKE (in SplashScreen.kt only — no other file changes)

A) Replace the file-local Color constants
   (SplashPageBg, SplashAccent, SplashAccentDark, SplashAccentInk,
    SplashTextPrimary, SplashTextSecondary, SplashTextMuted)
   with references to HandyDesign.Colors:
     SplashPageBg        → HandyDesign.Colors.PageBg
     SplashAccent        → HandyDesign.Colors.Accent
     SplashAccentDark    → HandyDesign.Colors.AccentDeep
     SplashAccentInk     → HandyDesign.Colors.AccentInk
     SplashTextPrimary   → HandyDesign.Colors.TextPrimary
     SplashTextSecondary → HandyDesign.Colors.TextSecondary
     SplashTextMuted     → HandyDesign.Colors.TextMuted

   Pixel parity: HandyDesign.Colors.Accent is exactly #D97757, same
   value the splash already uses, so the rendered splash is byte-
   identical after this swap.

B) Wrap the SplashScreen Box body in HandyDesignTheme { ... } so
   downstream LocalHandyDesignColors readers work if anyone else
   imports a primitive (defensive).

C) Replace local typography literals with HandyDesignType references
   where applicable:
     The wordmark uses font 76sp / weight 600 / letterSpacing -0.035em.
     The TYPE_KT spec only goes up to Display (32sp) — splash needs
     its own bigger value. Keep it as a one-off override:
       HandyDesignType.Display.copy(
         fontSize = 76.sp, lineHeight = 76.sp,
         fontWeight = FontWeight.SemiBold,
         letterSpacing = (-0.035).em,   // currently 0.sp — fix
       )
   The current code sets `letterSpacing = 0.sp` — that is wrong vs
   the JSX (`-0.035em`). Fix it.

   The tagline currently uses `letterSpacing = 0.sp`. JSX is
   `-0.005em`. Fix:
       letterSpacing = (-0.005).em

   The credit's `letterSpacing = 1.8.sp` ≈ 0.18em on 10sp. Keep as is.

D) The credit is currently uppercased via `.uppercase(Locale.ROOT)` AND
   styled via HandyType.Overline.copy. Replace HandyType with
   HandyDesignType.Overline.copy(...). Keep the uppercase call (JSX
   does textTransform: uppercase).

E) SPLASH_ADVANCE_MS is currently 5_000L. Keep 5_000L per the previous
   commit you already shipped — do NOT change.

F) ic_hand_palm_fill is the right drawable. Confirm it stays.

G) Add a single short kdoc above SplashScreen saying which tokens it
   now reads from (so the next person grep-ing "Color(0xFFD9..."
   doesn't think there are two sources of truth).

DON'T DO
- Don't change any animation timing, easing, repeatMode, or offset.
- Don't change the layer order (PageBg → amber wash → top vignette
  → mark → wordmark → tagline → bottom credit).
- Don't touch SplashHandGlowHalo's API 31 vs <31 branch.
- Don't add or remove the Modifier.noRippleClickable advance gesture.

SELF-VERIFY
1. ./gradlew :app:assembleDebug — green.
2. ./gradlew :app:test :app:lint
3. Install and run on a Pixel 9 (or emulator). Visually confirm:
   - Same warm amber wash centred at 50% × 58%.
   - Same three breathing rings at 320/240/170dp.
   - Same hand disc with linear gradient #D97757 → #C76547 + the
     12% white inset stroke.
   - Wordmark is tighter (because letter-spacing -0.035em was missing
     before). If anything else shifted, you broke pixel parity — back
     out the letter-spacing change ONLY (everything else should be
     visually identical).

STOP CONDITIONS (revert + report only)
- HandyDesignType is not yet present (P-0 failed). Stop.
- ic_hand_palm_fill drawable no longer exists. Stop.

DEBUG_LOG ENTRY (DL-###)
Title: "Splash migrated to HandyDesign tokens; tightened wordmark
+ tagline letter-spacing to match JSX (-0.035em / -0.005em)."
Body: tokens are now sourced from HandyDesign.Colors / HandyDesignType;
no behaviour change; letter-spacing fixed to match scenes-onboarding.jsx
lines 82–100. Prevention rule: when a screen ships with file-local
Color/TextStyle literals, the migration to shared tokens must be the
next prompt so the literals never get edited independently. 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-24T04:58:37.755Z

I’ll read the specified files first and verify the stop conditions before touching anything. Then I’ll keep the edits tightly scoped to `SplashScreen.kt`, run the requested Gradle checks, and commit only if the repo is green.

### ASSISTANT 2026-05-24T04:58:51.616Z

The stop-condition checks are good so far: `HandyDesignType` exists and `ic_hand_palm_fill.xml` is still present. I’m reading the rest of the splash file and locating the repo’s universal/debug-log docs before editing, since the debug entry needs to land in the right place.

### ASSISTANT 2026-05-24T04:59:22.873Z

I found the authoritative Cursor rule set and the DEBUG_LOG protocol. The relevant current prevention rule is the design-handoff checklist from DL-087, so I’m checking source tokens/drawables directly against the requested outputs before the splash edit.

### ASSISTANT 2026-05-24T04:59:55.325Z

**Plan**
Phase 0 / onboarding design-token migration. Acceptance check: splash render remains behaviorally identical while reading color/type tokens from the P-0 HandyDesign package, with only the requested JSX letter-spacing correction.

Files I’ll touch:
- `app/src/main/kotlin/com/handy/app/onboarding/SplashScreen.kt`: swap local literals to `HandyDesign.Colors` / `HandyDesignType`, wrap content in `HandyDesignTheme`, add the short KDoc, and preserve drawable/timing/layer order.
- `DEBUG_LOG.md`: append `DL-088` because the prompt explicitly asks for the same-commit log entry.

Interfaces I won’t change: `SplashScreen(onDone, modifier)`, `SplashHandGlowHalo` API 31/<31 behavior, advance gesture, animation timing/easing/repeat/offsets, string resources, and drawables.

Risks and catches: Compose `em` import/type mismatch and theme wrapping are compile-sensitive, so I’ll run `:app:assembleDebug`, `:app:test`, and `:app:lint`; then I’ll install/launch on any attached Pixel/emulator and inspect logcat for crashes.

### ASSISTANT 2026-05-24T05:00:04.210Z

I’m making the actual migration now: no layout surgery, just token references, theme scope, and the two letter-spacing corrections the JSX calls for. After that I’ll append the next sequential debug-log entry.

### ASSISTANT 2026-05-24T05:01:14.926Z

The source audit after the patch looks clean: no `Color(0x...)` literals or `HandyType` references remain in `SplashScreen.kt`, `SPLASH_ADVANCE_MS` is still `5_000L`, the ring timings/offsets are untouched, and the hand drawable remains `ic_hand_palm_fill`. I’m starting the requested Gradle verification now.

### ASSISTANT 2026-05-24T05:01:25.575Z

Plain Gradle hit the known host issue: there’s no system Java runtime on `PATH`. I’m rerunning the same checks with the repo-local JDK that prior verified entries used, so this still validates the Android build rather than the machine’s Java setup.

### ASSISTANT 2026-05-24T05:01:39.098Z

`:app:assembleDebug` is green with the repo-local JDK. Next up is the requested `:app:test :app:lint` pass, which should catch both Kotlin/Compose compile issues and lint regressions.

### ASSISTANT 2026-05-24T05:02:06.858Z

Both requested Gradle lanes are green now. I’m moving to the device smoke: install the debug APK, launch the onboarding splash, capture a screenshot/logcat, and verify the runtime didn’t crash.

### ASSISTANT 2026-05-24T05:02:40.002Z

The emulator screenshot shows the expected splash composition: warm amber wash low-center, three concentric rings around the 96dp hand disc, tightened “Handy” wordmark, accented “on-screen” tagline, and uppercase bottom credit. I’m letting it run past the 5-second advance and checking process/logcat for crashes.

### ASSISTANT 2026-05-24T05:03:05.627Z

The smoke run stayed healthy after the 5-second splash advance: app process is alive, logcat is clean for `AndroidRuntime`/Handy errors, and the next screen renders. I’m adding the validation details to the fresh DL-088 entry before the final diff/commit pass.

### ASSISTANT 2026-05-24T05:03:41.643Z

The final audit is clean: only `SplashScreen.kt` and `DEBUG_LOG.md` are modified; the untracked `handy_macos_ref/` directory was already present and I’m leaving it alone. I’m staging just the two intended files and committing the verified change.

### ASSISTANT 2026-05-24T05:04:50.457Z

Implemented and committed: `297d0c7 Migrate splash to HandyDesign tokens`.

Changed [SplashScreen.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/onboarding/SplashScreen.kt:65) to source splash colors from `HandyDesign.Colors`, wrap the splash in `HandyDesignTheme`, and use `HandyDesignType` for wordmark/tagline/credit. The animation timing, layer order, `5_000L` advance, no-ripple tap advance, glow branches, and `ic_hand_palm_fill` stayed intact. Added [DL-088](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1445).

Before: the splash had its own private orange/text values, so a future onboarding accent tweak could make later screens update while the splash silently stayed old. After: the splash inherits the same token source as the rest of onboarding. Practically, if `HandyDesign.Colors.Accent` is tuned later, the rings, hand disc, and “on-screen” accent move together. The visible intentional change is tighter wordmark/tagline spacing to match JSX.

**Verification**
- Plain `./gradlew :app:assembleDebug` is blocked on this host by missing system Java.
- With repo-local JDK: `:app:assembleDebug` passed.
- With repo-local JDK: `:app:test :app:lint` passed.
- Installed on `emulator-5554`, launched onboarding, visually confirmed splash and post-5s advance.
- Process stayed alive; filtered logcat had no `AndroidRuntime`, `ActivityTaskManager`, or `Handy` errors.
- Remaining untracked item: pre-existing `handy_macos_ref/`, left untouched.

**Manual Test Plan**
1. Fresh install or clear emulator app data, launch app, capture splash within 5 seconds.
2. Confirm exact strings: `Handy`, `Your on-screen copilot.`, `Built with love by Satvik Bansal`.
3. Check visual parity: dark page bg, amber wash around 50% x 58%, top vignette, rings at 320/240/170dp, hand disc gradient, white inset stroke.
4. Watch rings for at least 8 seconds: staggered breathing, no jump, no changed speed.
5. Tap splash early and verify it advances once only.
6. Relaunch and let timer advance naturally after 5 seconds.
7. Smoke on API 31+ and API 30 or lower to cover both glow-halo branches.
8. Check logcat after each run for crashes or Compose/runtime exceptions.

Advanced Phase 0 / onboarding token migration — acceptance check: PASS.

::git-stage{cwd="/Users/satvik.bansal/Desktop/Handy.android/HandyV2"}
::git-commit{cwd="/Users/satvik.bansal/Desktop/Handy.android/HandyV2"}
