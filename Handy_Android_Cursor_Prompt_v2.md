# Handy for Android — Cursor Prompt & Rules (v2)

> This file is **the single source of truth for how Cursor should drive the Handy Android build**. It is designed to be pasted directly into Cursor's chat (or used to seed `.cursor/rules/*.mdc`). It references `Handy_Android_Build_Plan_v2.md` (sibling file) as **required reading** — Cursor must read and internalise that plan before writing a single line of code.
>
> **V2 addendum (2026-04-24).** The original Section 1 below (pasted prompt) and the Execution Order it embeds describe the **V1 baseline** — Phase 0 through Phase 4 ending with a shipped V1. That content is preserved unchanged as historical baseline. The **V2 Execution Phases section that follows this banner (Section 0 below)** supersedes the V1 Phase 0–4 order for all V2 work. Where the V1 prompt and the V2 Execution Phases disagree, **V2 wins** and the V1 text is stale for that topic.
>
> **Required reading for V2 work (in order):**
>
> 1. [`Handy_Android_Build_Plan_V2_Scope.md`](Handy_Android_Build_Plan_V2_Scope.md) — authoritative V2 scope doc. Read end-to-end before touching V2 code. If this file and the V2 scope doc disagree, the scope doc wins.
> 2. [`Handy_Android_Build_Plan_v2.md`](Handy_Android_Build_Plan_v2.md) — V1 baseline architecture. The V2 work builds on these modules and interfaces.
> 3. [`./Handy V1 (macOS app)/`](Handy V1 (macOS app)) — behavioural source of truth for ported prompts, strings, bubble rules, and motion tokens.
> 4. [`.cursor/rules/10-handy-project-guardrails.mdc`](.cursor/rules/10-handy-project-guardrails.mdc) — guardrails with the V1 rules plus a new "V2 extensions" section and "Anti-patterns observed in `cursorbuddy-android-main`" block.
> 5. [`.cursor/rules/20-debug-log.mdc`](.cursor/rules/20-debug-log.mdc) — DEBUG_LOG protocol.
> 6. [`DEBUG_LOG.md`](DEBUG_LOG.md) — all Prevention Rules from DL-000 onwards.
> 7. [`DESIGN_NOTES.md`](DESIGN_NOTES.md) — running design log including SDK divergence note, Unified Buddy decision, bubble taxonomy, cursorbuddy licensing discipline.
> 8. [`cursorbuddy-android-main/`](cursorbuddy-android-main) — AGPL-3.0 reference implementation of the V3 product class. We port **recipes only** (scope doc §15). Anything that looks like porting their `TutorialEngine` / `TutorialControls` / `ClaudeAIPlanner` pixel-bounds pointing is a V3 feature being smuggled in — stop and revert.
>
> Three top-level sections below:
>
> 0. **V2 Execution Phases** (this addendum) — the phase order for V2 work.
> 1. **The Cursor Prompt** (V1 baseline — shipped) — preserved for historical context.
> 2. **The Cursor Rules** — pointer summary of the authoritative files on disk.

---

## 0. V2 Execution Phases (supersedes V1 Phase 0–4 for V2 work)

Every V2 coding turn must open with the "Think Before Coding" block described in `.cursor/rules/00-karpathy-think-before-coding.mdc` AND name the phase below it is advancing. The phase, slice, acceptance check, files to touch, and interfaces NOT to change must all be declared before code is written.

### Phase 0 — Source-of-truth alignment (docs + rules only)

**Slice:** no runtime code. Land the V2 scope doc, the V2 banner on the V1 plan, the V2 execution phases in this cursor prompt, the V2 extensions in the guardrails rule, and the V2 entries in DESIGN_NOTES.

**Acceptance:**

- [`Handy_Android_Build_Plan_V2_Scope.md`](Handy_Android_Build_Plan_V2_Scope.md) exists with all 15 V2 prompt subsections, the Unified Buddy state machine, the four-color bubble taxonomy, the `LocalGenAiClient` allowed / forbidden task lists, the action audit schema, the V3 fence, and the cursorbuddy borrowings (§15 of the scope doc, ten numbered recipes + Do-NOT-borrow anti-patterns).
- V1 plan has a V2-supersession banner and a "Promoted to V2" pointer section.
- This cursor prompt has Section 0 (V2 Execution Phases).
- `.cursor/rules/10-handy-project-guardrails.mdc` has a `## V2 extensions` section whose every bullet maps to a named subsection in the scope doc.
- `DESIGN_NOTES.md` has four new entries: Unified Buddy, Bubble Taxonomy, SDK Divergence, and cursorbuddy AGPL-3.0 recipes-not-source discipline.
- Zero `.kt` / manifest changes in this phase.

### Phase 1 — Overlay chat panel (primary quick surface)

**Slice:** new Compose overlay panel, IME-focus choreography per cursorbuddy recipe #5, expand-to-`ChatActivity` path, lifecycle stability (rotation / multi-window / overlay revocation / app switch), quick-prompt chips per cursorbuddy recipe #9, voice auto-submit 300 ms grace per cursorbuddy recipe #6, glassmorphism palette + no-backdrop-blur per cursorbuddy recipe #8.

**Acceptance:**

- Tap widget → overlay chat panel opens in ≤ 250 ms.
- Typing commits characters (instrumented test).
- IME resizes the panel (`SOFT_INPUT_ADJUST_PAN`); does not resize the whole screen.
- Rotation does not crash; panel either recreates or dismisses cleanly.
- Idle widget keeps the OS-2 idle flag set before panel open and after panel dismiss.
- Voice final result auto-submits after 300 ms grace.
- Quick-prompt chips render based on cached package from cursorbuddy recipe #4.
- No `RenderEffect.createBlurEffect` or `FLAG_BLUR_BEHIND` on the panel window.
- `OverlayComposeHost` reused; no second Compose host class is created.

### Phase 2 — Smarter capture + grounding + Unified Buddy

**Slice:** `RequestBudgeter` (`:core`), `AccessibilityMarksProvider` per cursorbuddy recipe #2 (`:android-runtime`), semantic point-from-text path, `LaunchableAppIndex` hardening (`PACKAGE_ADDED / REMOVED / REPLACED` refresh; no `QUERY_ALL_PACKAGES`), diagnostics plumbing foundations, Glass Lens render per cursorbuddy recipe #1, Bezier flight (macOS math + cursorbuddy's `DecelerateInterpolator(2f)` + `OvershootInterpolator(1.5f)` pulse) + return + four-color bubble renderer, `StateFlow<AccessibilityConnectionState>` per cursorbuddy recipe #10.

**Acceptance:**

- A `[POINT]` from a pure-text request flies the Unified Buddy to a resolved semantic node, arrives in ≤ 1.4 s, dwells 3–5 s with the blue label bubble, returns to dock.
- Glass Lens golden-image test passes for Docked / Listening / Thinking / Flying / Pointing / Acting / Speaking.
- A screen-text-only request to Claude includes the `<screen_ui>` block but no image.
- `FLAG_SECURE` test window classifies as `SecureWindow` and the orchestrator injects the "can't see this screen" system message without calling `LlmClient` with an image (OS-5).
- `AccessibilityConnectionState` flips from `NeverConnected` / `Disconnected` to `Connected` on `onServiceConnected` without polling.
- `AccessibilityMarksProvider` emits ≤ 50 nodes; password fields appear with `password: true` and empty / redacted `text`.

### Phase 3 — Tap-for-me

**Slice:** Hilt binding flips `ActionPerformer` → `AccessibilityGestureActionPerformer`; node-first / gesture-second recipe #3; cache-at-tap rule #4; confirmation policy (scope §4.1); bounded action catalog (scope §4.2); action audit (scope §4.3); teal lens bubble + `PanelActionConfirming` panel state.

**Acceptance:**

- "Tap Send" on a clickable node fires `ACTION_CLICK`; no `dispatchGesture` fires; `AuditEvent.userConfirmed = false` (benign one-step).
- "Share this link" (destructive) shows the panel confirmation chip; only on tap does the action fire; `AuditEvent.userConfirmed = true`.
- Disabled button → `PerformResult.Failed("node not clickable")`; no gesture; audit captures the failure reason.
- Stale node (user switched screens between request and resolve) → re-resolve once, else fail; no guessing.
- WebView low-quality tree → fall back to pointer, no brute-force gesture.
- Hilt binding test proves the V2 binding is wired; V1 `NoopActionPerformer` is only bound when `settings.tapForMeEnabled = false`.

### Phase 4 — Brains expansion

**Slice:** `GeminiCloudLlmClient` (:android-runtime); `LocalGenAiClient` interface (:core); `GeminiNanoLocalGenAiClient` (:android-runtime) with availability gating; `BrainRouter` (:core); provider + local-AI + privacy-mode settings surface; experimental labeling in Settings UI.

**Acceptance:**

- Provider toggle in Settings swaps brain end-to-end without app restart.
- Shared `LlmClient` contract test runs green against both Claude and Gemini cloud.
- `BrainRouter` routes `SUMMARIZE_TEXT` to Nano when `LocalAvailability.Available`; falls back to cloud on `Unsupported` / `Downloading` / `TemporarilyUnavailable`.
- `preferLocalWhenPossible = true` routes eligible text-only tasks to Nano first; non-eligible tasks still go cloud.
- Gemini cloud provider has an "experimental" tag in Settings and is not default.

### Phase 5 — System surfaces

**Slice:** `HandyQuickSettingsTileService` with configurable tile action (open panel / start voice / open full chat); launcher / home icon polish (adaptive + themed + monochrome); optional Assist entry (`HandyAssistIntentService`).

**Acceptance:**

- Tile add-flow works on API 33+ via `TileService.requestAddTileService`.
- Launcher icon opens `ChatActivity` directly after first launch.
- Assist entry is off by default; when enabled, voice invocation routes to Handy; when disabled, restores OEM default without a reboot.

### Phase 6 — Notification listener + clipboard

**Slice:** `HandyNotificationListenerService` (scope §8) with narrow features; `ClipboardAssist` (scope §9) gated by visible Handy state; in-app disclosures + consent for both; Diagnostics expansion.

**Acceptance:**

- User enables notification access via the in-app disclosure.
- Recent-notifications summary works; `RemoteInput` reply requires confirmation + writes an audit event.
- Clipboard summarize works from a visible panel.
- Clipboard does NOT auto-process when a clip looks like a password / OTP.
- Disabled → permissions revoked cleanly; no residual background work.

### Phase 7 — Tutor mode enablement

**Slice:** bounded tutor mode per scope §12; cadence + cooldown logic; screen-text-heavy tutoring; local summarization path where applicable.

**Acceptance:**

- Tutor fires at most one nudge per 60 s.
- Suspends under battery-saver / thermal throttling.
- Stops on app switch or user interrupt.
- **Does not** chain multi-step actions.

### Phase 8 — Hardening / policy / beta

**Slice:** Play disclosure package for V2 (tap-for-me explicit, notification listener explicit, no `QUERY_ALL_PACKAGES`); policy pass; device matrix (Pixel 6a / Samsung A / Moto G / OnePlus Nord / Redmi Note); performance pass (cold start, drag fps, panel open latency, Bezier flight fps); `DEBUG_LOG.md` hardening.

**Acceptance:**

- DEBUG_LOG tail silent for ≥ 1 week.
- All scope §13 policy deliverables present.
- Play internal testing beta live.

---

## 1. The Cursor Prompt (paste into Cursor)

```
ROLE
You are a senior Android engineer building "Handy" — a production-grade,
always-available on-device AI assistant for Android, ported from the
existing macOS SwiftUI app. You are meticulous, surgical, and you never
ship code you have not reasoned about.

REQUIRED READING (read these BEFORE writing or editing any code; re-read
the relevant sections each time you start a new feature)

  1. ./Handy_Android_Build_Plan_v2.md
       The full v2 architecture, scope, modules, interfaces, gesture
       state machine, screen-reading design, intent dispatch, Apple-
       class UI system, and 10-week rollout plan. This is your
       blueprint. Do not deviate from it without writing a short
       design note in DESIGN_NOTES.md and getting explicit approval in
       the chat first.

  2. ./Handy/                (the existing macOS Swift project)
       The behavioural reference. This is where months of prompt
       tuning, UX strings, and conversation-shape decisions live.
       Before inventing any new assistant behaviour — a system prompt,
       a loading verb, a status-line string, an error message, a
       chat-history shape, a TTS/text split, a web-search heuristic,
       a tool-use contract — grep this tree first. If the concept
       already exists and is platform-agnostic, port it
       character-for-character. Only adapt the parts that are
       genuinely macOS-specific (menu-bar references, Command key
       shortcuts, pixel-coordinate pointing).

       Specific files you must read before writing their Android
       counterparts:
         - Handy/Services/HandyManager.swift
             * `chatSystemPrompt`, `voiceSystemPrompt`,
               `tutorModeSystemPrompt` (three distinct prompts —
               not two).
             * `webSearchPromptAddendum(hasBraveKey:)` — dynamic
               addendum that changes based on which search keys
               the user has configured.
             * `loadingVerbs` — the curated 34-string "thinking"
               vocabulary shown during request latency.
             * `webSearchStatusText` map — "Searching the web…",
               "Searching GitHub…", "Reading page…", "Looking
               things up…" — keyed off the LLM's tool_use name.
             * `introPrefix` behaviour — first-turn-only prefix
               on the assistant's response.
         - Handy/Services/ClaudeAPIService.swift
             * SSE parsing, tool-use loop, error taxonomy
               (`ClaudeAPIError.noAPIKey`, `.httpError`,
               `.invalidResponse`, `.networkError`) and their
               user-facing `errorDescription` strings.
         - Handy/Utilities/PointParser.swift
             * `stripPointTags`, `extractSpokenPart`,
               `clampVoiceSpokenForTTS(maxChars = 420)`,
               `clampVoiceSpokenForOverlay(maxChars = 110)`,
               `truncateAtSentenceBoundary`. Port the exact caps
               and boundary logic; do not round numbers.
         - Handy/Models/ChatMessage.swift
             * `ChatMessage`, `MessageRole`, `ConversationTurn`
               — the on-disk JSON schema. Android's
               `ChatHistoryStore` must read/write the same
               JSON so histories round-trip between platforms
               in the future.
         - Handy/Models/AppSettings.swift
             * `AssistantMode` (.helpOnly / .tutor),
               `STTProvider`, `TTSProvider`, `SarvamVoice` and
               their display strings + descriptions. Port enum
               raw values verbatim.
         - Handy/Services/WebSearchService.swift
             * Brave / Jina / GitHub call shapes and the
               source-citation contract.
         - Handy/Services/TTSService.swift and
           Handy/Services/SpeechRecognitionService.swift
             * Error strings and authorization-denied paths.
         - Handy/DesignSystem.swift
             * Colour, spacing, typography, motion tokens.
         - Handy/Views/ChatInterfaceView.swift
             * Chat-bubble layout contract — which roles render,
               which fields are hidden, how streaming deltas look.

  3. ./debugging.mdc
       The DEBUG_LOG protocol you will follow for every bug fix once v1
       is green. The Android version is defined below in the rules.

  4. https://github.com/forrestchang/andrej-karpathy-skills
       The four skills you will embody: Think Before Coding, Simplicity
       First, Surgical Changes, Goal-Driven Execution. These are
       enforced via ./.cursor/rules/*.mdc — do not fight them.

GOAL

Deliver v1 of Handy for Android that is:

  * Completely bug-free on first build-and-run.
  * Well-structured (three Gradle modules: :core, :android-runtime,
    :app) with no cross-module leaks.
  * Fast on mid-range phones (Pixel 6a / Snapdragon 6-gen class),
    no dropped frames on the floating widget drag, no ANRs.
  * Voice capture + TTS that "just works" end-to-end.
  * Animations and illustrations matching the macOS app's character
    (idle breathing, listening pulses, thinking ring, speaking waves,
    typewriter text reveal, glass-card chat bubbles).
  * Web search wired but OFF by default (settings toggle, same UX
    contract as macOS).
  * UI that mimics Apple's visual class — bold Inter typography,
    restrained colour system, high-contrast dark theme, 8/12/16/24 dp
    spacing rhythm, spring-based motion, glass-morphic panels.

NON-GOALS FOR v1 (seams prepared only — see plan §7, §8, §9)

  * Tap-for-me / do-it-for-me via AccessibilityService.dispatchGesture.
    Ship NoopActionPerformer in v1. Do not wire the real one.
  * Gemini / alternate LLM brains. Ship ClaudeLlmClient as the single
    binding. The LlmClient interface must allow a future one-class
    addition with zero refactors.
  * Strategy A (pixel-coordinate pointing). Only Strategy B
    (semantic pointer via AccessibilityService) is in scope.

SCOPE FENCE

Anything not listed in §2 (In scope for v1) of the build plan is out of
scope. If a feature "feels useful", that is not a reason to add it.
Write it as a TODO in DESIGN_NOTES.md under "v2 candidates" and move
on.

OS CONSTRAINTS — MUST HANDLE IN V1 (read the detailed rules in
./.cursor/rules/10-handy-project-guardrails.mdc, section "Android OS
constraints". These are non-negotiable for a bug-free first build.)

  OS-1  Foreground services on API 34+ crash if the manifest does not
        declare an explicit foregroundServiceType. Because targetSdk
        is 35, every FGS needs one. AssistantForegroundService must
        declare foregroundServiceType="specialUse|microphone" and the
        manifest must include FOREGROUND_SERVICE_SPECIAL_USE and
        FOREGROUND_SERVICE_MICROPHONE. Any service that owns a
        MediaProjection session must declare
        foregroundServiceType="mediaProjection". On Android 15, if
        we rely on the SYSTEM_ALERT_WINDOW background-start exemption
        the overlay must already be visible in WindowManager at the
        moment the start happens — otherwise route the start through
        a user-initiated path.

  OS-2  v1 has no chat-panel overlay. The floating widget is the only
        overlay and stays FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN |
        FLAG_LAYOUT_NO_LIMITS at all times. Tapping the widget starts
        ChatActivity via Intent(FLAG_ACTIVITY_NEW_TASK); typed chat
        lives in that Activity with normal IME handling. Do NOT add a
        ChatPanelOverlayService. An instrumentation test proves that
        typing into ChatActivity commits characters.

  OS-3  The capture pipeline branches in three tiers:
        (a) API 34+  → AccessibilityService.takeScreenshotOfWindow
            (activeWindowId) — cheaper, respects per-window secure
            flags, no projection consent.
        (b) API 30–33 → AccessibilityService.takeScreenshot.
        (c) API 26–29 → MediaProjection fallback.
        Guard each accessibility call with @RequiresApi on the private
        method and hide it behind an SDK-agnostic public entry point.
        The pipeline always returns a CaptureResult sealed type; no
        raw Bitmap crosses an orchestration boundary. When using the
        MediaProjection fallback, register a MediaProjection.Callback,
        handle onStop(), release the VirtualDisplay + Surface
        immediately, and clear capture state so no further frames
        are produced.

  OS-4  Compose requires a LifecycleOwner, ViewModelStoreOwner, and
        SavedStateRegistryOwner. Overlays run inside a Service's
        WindowManager — no Activity is providing those owners.
        Create an OverlayComposeHost that owns a LifecycleRegistry and
        SavedStateRegistryController, attaches both via
        setViewTreeLifecycleOwner / setViewTreeSavedStateRegistryOwner
        / setViewTreeViewModelStoreOwner, and moves the lifecycle to
        RESUMED before calling setContent { }. Reuse it for every
        overlay (in v1: the floating widget).

  OS-5  Banking apps, password managers, and incognito browser tabs
        set FLAG_SECURE. Both takeScreenshot/takeScreenshotOfWindow
        and MediaProjection return all-black frames for those
        surfaces, and Claude will hallucinate confidently on a black
        image. Capture results are a sealed type
        (Bitmap / SecureWindow / NotPermitted / Unsupported / Failed).
        Primary detection of SecureWindow comes from the capture API
        itself — official failure codes, and a black-frame heuristic
        for the MediaProjection path. windowInfo.isSecure is NOT a
        required source of truth: treat it as an optional hint that
        may promote a Bitmap to SecureWindow, but never let a false
        value override a secure classification from the capture path.
        On SecureWindow, never call the LLM with an image; inject a
        system chat message saying "I can't see this screen — the app
        is marked secure. Try from a non-secure screen or paste the
        text." Accessibility text-reading honours the same
        classification.

EXECUTION ORDER

Do not skip or re-order these phases. Each phase ends with a green
build + the acceptance checks named in the plan.

  Phase 0  Repo scaffold
    - Create root Gradle project with three modules: :core,
      :android-runtime, :app. :android-runtime owns adapters /
      wrappers only and declares NO manifest components. :app owns
      every manifest-declared component and all Compose UI.
    - Configure minSdk 26, targetSdk 35, compileSdk 35, Kotlin 2.0+,
      AGP 8.5+, JDK 17. Add a CI smoke lane for API 36 (Android 16);
      do NOT make API 36 the shipping target.
    - Wire Hilt, Compose BOM, KSP, ktlint, detekt, JUnit5, Turbine,
      MockK. No other dependencies yet.
    - Commit: "Phase 0: module scaffold + toolchain".
    - Acceptance: `./gradlew build` green on a clean checkout.

  Phase 1  :core domain
    - Port the pure-Kotlin domain from plan §4:
        models (ChatMessage, ChatHistory, Tool), LlmClient interface +
        LlmRequest/LlmChunk, SttClient / TtsClient interfaces,
        AssistantAction sealed class, ActionPerformer interface +
        TapTarget / PerformResult + NoopActionPerformer (default),
        ScreenTextSnapshot / UiNode / ScreenInputRouter,
        ConversationOrchestrator (the "brain"), PromptCatalog
        (chatSystemPrompt + voiceSystemPrompt +
        tutorModeSystemPrompt + webSearchPromptAddendum — all four
        copied verbatim from macOS HandyManager.swift, with ONLY the
        platform adaptations listed in
        10-handy-project-guardrails.mdc §"macOS prompt reuse".
        Plus two NEW Android-only addendums:
        `screenTextPromptAddendum(snapshot)` and
        `intentToolPromptAddendum()` — see that rule for the exact
        wording contract), LoadingVerbs (port the 34-string array
        verbatim), WebSearchStatusText map (verbatim tool-name →
        status-line mapping).
    - ZERO Android imports in this module. Enforce via detekt
      configuration + a unit test that scans classpath for
      android.* references.
    - Write unit tests for every pure function and every branch of
      ScreenInputRouter.
    - Commit: "Phase 1: :core domain + orchestrator + tests".
    - Acceptance: `./gradlew :core:test` green, 90%+ line coverage on
      :core/src/main.

  Phase 2  :android-runtime adapters
    - Implement the Android side of the :core interfaces
      (per plan §5). :android-runtime hosts adapters / wrappers
      only — NO manifest-declared components and NO Compose:
        ClaudeLlmClient (OkHttp + SSE),
        AndroidSttClient — on API 31+ use
          SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
          when isOnDeviceRecognitionAvailable(context) returns true;
          otherwise SpeechRecognizer.createSpeechRecognizer(context).
          All recognizer calls run on the main thread; destroy() on
          teardown. EXTRA_PREFER_OFFLINE is only a hint. Manifest
          <queries> for android.speech.RecognitionService.
        AndroidTtsClient (TextToSpeech),
        AccessibilityTreeReader (reads AccessibilityNodeInfo tree,
        produces ScreenTextSnapshot; honours OS-5 SecureWindow via
        the capture pipeline's classification — NOT via
        windowInfo.isSecure as the required source of truth),
        ScreenCapturePipeline (OS-3 three-tier order:
          API 34+ → takeScreenshotOfWindow,
          API 30-33 → takeScreenshot,
          API 26-29 → MediaProjection with a registered
          MediaProjection.Callback, onStop() releasing the
          VirtualDisplay + Surface immediately; returns the
          CaptureResult sealed type, never a raw Bitmap),
        SemanticPointerResolver (Strategy B only — see plan §8),
        AndroidIntentDispatcher (AssistantAction → Intent; returns
          IntentResult: Dispatched / ChooserShown /
          NeedsConfirmation / NoHandler / Failed),
        LaunchableAppIndex (built at startup from launcher-resolved
          apps; refreshed on ACTION_PACKAGE_ADDED/REMOVED/REPLACED;
          NO per-request fuzzy PackageManager walks and no
          QUERY_ALL_PACKAGES permission),
        EncryptedKeyStore (EncryptedSharedPreferences + Android
          Keystore),
        DataStoreSettings (DataStore<Preferences> for app settings),
        JsonHistoryStore (JSON files on disk, atomic write via
          temp-file-then-rename, per-tool cap enforced at write,
          macOS-schema-compatible; NO Room in v1).
    - The Android classes that actually extend Service /
      AccessibilityService / Application / Activity live in :app
      (Phase 3). Adapters in :android-runtime receive them as
      injected dependencies.
    - Provide Hilt @Binds for every interface. Bind
      ActionPerformer → NoopActionPerformer for v1.
    - No UI in this module.
    - Commit: "Phase 2: :android-runtime adapters".
    - Acceptance:
        * `./gradlew :android-runtime:test` green.
        * Integration test that round-trips a mocked Claude SSE
          stream through ClaudeLlmClient → LlmChunk flow.
        * Unit test proves ScreenCapturePipeline picks the right
          path on simulated Build.VERSION.SDK_INT = 28, = 31, and
          = 34 (OS-3).
        * Unit test proves MediaProjection teardown releases the
          VirtualDisplay + Surface synchronously on onStop() and
          on explicit cancel (OS-3 lifecycle).
        * Unit test proves SecureWindow classification: (a) fires
          when the capture API itself returns a secure failure
          code, (b) fires when a >99%-black bitmap is returned by
          the MediaProjection path, (c) does NOT rely on
          windowInfo.isSecure as the only signal (OS-5).
        * JsonHistoryStore test proves per-tool segregation, the
          per-tool cap, atomic write, and round-trip compatibility
          with a golden macOS JSON fixture.

  Phase 3  :app UI, services, and manifest components
    - All manifest-declared components live in :app. Build Compose
      UI and the owning Android components as plan §10, §11, §12
      describe:
        HandyApplication (installs StrictMode in debug; creates
            notification channels at onCreate — NOT lazily),
        OnboardingActivity (in-app Accessibility disclosure with
            explicit consent + deep-link; reduced mode when
            declined),
        ChatActivity (the SINGLE chat surface in v1 — typed chat,
            streaming Claude responses, per-tool history via
            JsonHistoryStore),
        SettingsActivity (brain + voice + TTS + web-search toggle
            off by default),
        FloatingWidgetOverlayService (TYPE_APPLICATION_OVERLAY,
            uses OverlayComposeHost per OS-4; overlayFlagsFor()
            returns the idle-widget flag set only, per OS-2;
            tapping the widget starts ChatActivity via an explicit
            Intent with FLAG_ACTIVITY_NEW_TASK — do NOT build a
            ChatPanelOverlayService),
        HandyAccessibilityService (delegates to the
            :android-runtime AccessibilityTreeReader and
            SemanticPointerResolver; the XML config declares
            canRetrieveWindowContent=true, canTakeScreenshot=true,
            a sensible accessibilityEventTypes, and
            notificationTimeout; serviceInfo adds
            FLAG_RETRIEVE_INTERACTIVE_WINDOWS and
            FLAG_REPORT_VIEW_IDS),
        AssistantForegroundService (manifest-declared
            foregroundServiceType="specialUse|microphone" per OS-1;
            owns audio capture + TTS lifecycle; respects Android 15
            SYSTEM_ALERT_WINDOW background-start rules per OS-1),
        MediaProjectionCaptureService (manifest-declared
            foregroundServiceType="mediaProjection"; owned only when
            the capture pipeline needs the API 26-29 fallback per
            OS-3).
    - Implement the gesture state machine from plan §10 exactly:
        ACTION_DOWN → 400ms long-press timer →
        ACTION_MOVE (scaledTouchSlop) → DRAG or LONG_PRESS →
        ACTION_UP → tap | voice-submit | snap-to-edge.
      Use SpringAnimation (STIFFNESS_LOW, DAMPING_RATIO_MEDIUM_BOUNCY)
      for snap. Do NOT invent new gestures.
    - Implement all animations from plan §12:
        idle breathing (1.0 → 1.04 → 1.0 over 2.6s ease-in-out,
        looped), listening pulse rings (3 staggered, 1.2s each),
        thinking ring (continuous rotation, gradient stroke),
        speaking waves (3 bars, phase-shifted sine),
        typewriter reveal (~24 chars/sec with blinking caret).
      Match the macOS visuals, do not redesign them.
    - Apply the Apple-class UI system (plan §11):
        Inter font, typography scale, colour tokens, 8dp spacing
        grid, glass-card surfaces with RenderEffect blur on API 31+.
    - Commit in two parts:
        "Phase 3a: manifest components + overlay + gesture state
         machine"
        "Phase 3b: ChatActivity + onboarding + animations + Apple-
         class theme".
    - Acceptance:
        1. Fresh install → onboarding shows the in-app Accessibility
           disclosure + explicit consent step, then asks for
           SYSTEM_ALERT_WINDOW + RECORD_AUDIO + AccessibilityService.
           Declining Accessibility leaves the user in a usable
           reduced mode (chat + voice work; screen reading / pointing
           do not).
        2. Widget draggable, snaps to nearest edge on release, tap
           opens ChatActivity, long-press starts voice capture with
           an immediate haptic confirmation.
        3. A full Help-Only conversation (voice → STT → Claude → TTS)
           completes end-to-end on a physical device.
        4. No dropped frames during drag on a Pixel 6a
           (verify via `adb shell dumpsys gfxinfo`).
        5. AssistantForegroundService starts on Android 15 without
           MissingForegroundServiceTypeException (OS-1). Verified on
           API 30, API 35, and API 36 (smoke) emulator images.
        6. Typing into the ChatActivity input commits characters and
           Claude streaming is visible within the frame budget; the
           widget overlay's idle flags remain intact throughout
           (OS-2). Verified by an instrumentation test that types
           into the input and asserts the committed text, and
           separately asserts that no ChatPanelOverlayService is
           present in the manifest.
        7. Overlay Compose hosts mount and unmount cleanly with no
           "ViewTreeLifecycleOwner not found" or
           SavedStateRegistry errors in logcat across 10 open/close
           cycles (OS-4).
        8. A mocked `CaptureResult.SecureWindow` from the pipeline
           (or a real `FLAG_SECURE` test Activity via
           `setFlags(FLAG_SECURE, FLAG_SECURE)`) produces the
           injected "I can't see this screen — the app is marked
           secure" system message instead of a hallucinated LLM
           response (OS-5). The orchestrator asserts `LlmClient` was
           never called with an image in the secure case.

  Phase 4  Hardening + DEBUG_LOG bootstrap
    - Create DEBUG_LOG.md at project root and initialise with DL-000
      ("Initial v1 green build" — severity Informational).
    - From this point on, every bug fix — build error, compile-
      warning-treated-as-error, runtime crash, ANR, flaky test, or
      logic bug — appends a DL-XXX entry per the Android debugging
      rule below, in the SAME commit as the fix, BEFORE you tell me
      the fix is done.
    - Install StrictMode (thread + VM policies) in HandyApplication
      under BuildConfig.DEBUG.
    - Set up baseline profiles for ChatActivity and the floating-
      widget overlay service.
    - Land the Play / policy deliverables listed in plan §18a: the
      in-app Accessibility disclosure + consent step, the privacy
      policy text, the Play Console declaration draft, and the
      review-artifact checklist.
    - Add instrumentation tests covering:
        * Widget gesture state machine (every transition).
        * Snap-to-edge correctness at both vertical edges.
        * JsonHistoryStore per-tool segregation, per-tool cap,
          atomic write, and round-trip against a golden macOS JSON
          fixture.
        * SemanticPointerResolver fallback chain
          (text+role → desc → viewId suffix → fuzzy Levenshtein ≤ 2).
        * Intent dispatch happy paths for each AssistantAction,
          including the NeedsConfirmation behaviour on destructive
          actions.
        * ScreenInputRouter routing heuristic over the fixed prompt
          fixture set.
        * Accessibility onboarding happy path AND declined-path
          reduced mode.
        * ChatActivity typing smoke (IME commits characters,
          streaming UI does not jank).
        * STT/TTS round-trip smoke using the AndroidSttClient /
          AndroidTtsClient adapters.
        * LaunchableAppIndex: built at startup, refreshed on a
          simulated ACTION_PACKAGE_ADDED broadcast, no
          QUERY_ALL_PACKAGES permission required.
        * OS-1: AssistantForegroundService starts successfully on
          API 35 with declared foregroundServiceType, plus a smoke
          start on API 36.
        * OS-2: typing in ChatActivity commits characters and the
          floating-widget overlay stays on idle flags throughout;
          no ChatPanelOverlayService exists.
        * OS-3: ScreenCapturePipeline takes the
          takeScreenshotOfWindow path on API 34+, the
          takeScreenshot path on API 31, and the MediaProjection
          path on API 28; MediaProjection teardown releases
          VirtualDisplay + Surface synchronously.
        * OS-4: OverlayComposeHost survives 10 mount/unmount
          cycles with no Compose lifecycle errors.
        * OS-5: a mocked `CaptureResult.SecureWindow` from the
          capture pipeline causes the orchestrator to inject the
          "secure screen" system message without ever calling
          `LlmClient` with an image; a second test covers the
          black-frame path on the MediaProjection fallback.
    - Commit: "Phase 4: hardening + DEBUG_LOG bootstrap".
    - Acceptance: `./gradlew connectedAndroidTest` green on a
      Pixel 6a API 35 emulator image, plus an API 36 compatibility
      smoke run.

PROCESS PROTOCOL FOR EVERY TURN

  1. Before writing code, state in the chat:
       - Which phase you are in.
       - Which plan section(s) you are implementing this turn.
       - Any DEBUG_LOG.md Prevention Rules that apply.
       - Your one-paragraph plan for this change.
     (This is the "Think Before Coding" Karpathy rule — enforced.)

  2. Keep the change surgical. Touch only the files the plan says
     this phase touches. If you find yourself "while I'm here"
     refactoring an unrelated file, stop and revert.

  3. Write or update tests in the same commit as the code.

  4. Before announcing "done", run:
       ./gradlew ktlintCheck detekt test :app:assembleDebug
     and report the exact commands + summary output.

  5. If a fix took more than one iteration, append a DL-XXX entry to
     DEBUG_LOG.md in the same commit as the fix — not after.

  6. Never disable a test, never `@Suppress` a warning, and never
     use `!!` to "make it compile". If you're tempted, stop and
     reason about why the types are wrong.

OUTPUT STYLE IN CHAT

  * Concise. No filler.
  * Show the exact file paths + diff-like snippets of changes, not
    entire files unless you just created them.
  * End each turn with (a) what phase/section you completed, (b)
    the acceptance checks you ran, (c) the next action.

START HERE

  * Read Handy_Android_Build_Plan_v2.md end to end.
  * Read the six Cursor rule files in ./.cursor/rules/ (listed
    in section 2 of this prompt).
  * Re-read the "Android OS constraints (v1 must-handle)" section
    in 10-handy-project-guardrails.mdc and be able to name OS-1
    through OS-5 from memory.
  * Reply with: "Ready. Proceeding with Phase 0 — repo scaffold.
    Here is my plan: ... and here is how I'll respect OS-1..OS-5
    in the phases they apply to: ..." and wait for my go-ahead
    before writing files.
```

---

## 2. The Cursor Rules (drop into `.cursor/rules/`)

Create a `.cursor/rules/` directory at the repo root and add the five `.mdc` files below. These are **always-applied** rules; Cursor will load them on every turn. They are deliberately tight — they encode the Karpathy skills and the Handy-specific guardrails that the prompt above depends on.

### 2.1 `.cursor/rules/00-karpathy-think-before-coding.mdc`

```markdown
---
description: Karpathy Skill 1 — Think Before Coding
globs: ["**/*"]
alwaysApply: true
---

# Think Before Coding

Before you write or edit any file, output a short "Plan" block in the
chat containing:

1. The user-visible goal of this change (one sentence).
2. The files you will touch and why (bullet list).
3. The interfaces you will NOT change (to prove you understand the
   blast radius).
4. The risks and how you will catch them (tests, manual checks).

Only after the Plan block is posted may you write code.

## Why

Rushing to code produces tangled diffs, hidden coupling, and bugs that
appear two commits later. Ten seconds of planning prevents an hour of
debugging.

## How to apply

* Applies to every code-writing turn, including "tiny" fixes.
* Skip only if the user explicitly says "just do it" or the change is
  purely cosmetic (e.g., fixing a typo in a comment).
* If the plan reveals the change is bigger than you thought, say so
  and ask to split it before touching code.
```

### 2.2 `.cursor/rules/01-karpathy-simplicity-first.mdc`

```markdown
---
description: Karpathy Skill 2 — Simplicity First
globs: ["**/*"]
alwaysApply: true
---

# Simplicity First

Prefer the simplest solution that satisfies the spec. Reach for
abstractions only when you have at least two concrete call-sites that
prove the abstraction pays for itself.

## Concrete rules for this repo

* No new interface unless the build plan names it OR a second
  implementation is imminent in the same PR.
* No new Gradle module unless the build plan names it.
* No reflection, no annotation processors outside Hilt/KSP. (Room is
  out of v1 — see the persistence rule in
  `10-handy-project-guardrails.mdc`.)
* No DSL, no custom operators, no "clever" one-liners. Boring Kotlin.
* If you're writing a generic `<T>` function and T is only ever one
  type in this codebase, delete the generic.
* If the plan says "data class", it's a data class. Not a sealed
  hierarchy, not a builder.

## Why

Handy's macOS twin has shipped to users. Every cleverness tax Android
charges delays reaching parity. Boring code is fast code and is cheap
to replace.

## How to apply

* On every new file, ask: "would a senior Android engineer joining
  tomorrow understand this in under 60 seconds?" If not, simplify.
* When tempted to add an extension point "just in case", don't. Leave
  a TODO(v2) comment instead.
```

### 2.3 `.cursor/rules/02-karpathy-surgical-changes.mdc`

```markdown
---
description: Karpathy Skill 3 — Surgical Changes
globs: ["**/*"]
alwaysApply: true
---

# Surgical Changes

One change per commit. One concern per file edit. If you find unrelated
issues, write them down in DESIGN_NOTES.md — do not fix them in the
current diff.

## Hard rules

1. The diff for a single task must touch only the files that task
   requires. "While I'm here" refactors are a bug.
2. Formatting-only changes live in their own commit, never mixed with
   logic.
3. Renames live in their own commit, never mixed with logic.
4. When fixing a bug, change the minimum lines needed. Do not
   "improve" surrounding code.
5. If a change spans more than ~200 added lines or more than 5 files,
   stop and split the task before continuing.

## Why

Big diffs hide bugs, break git bisect, and make review impossible.
Handy must be maintainable by a small team for years; every diff is a
future archaeological dig site.

## How to apply

* Before `git add`, run `git diff --stat` and sanity-check the file
  count.
* If the stat is surprising, split it.
```

### 2.4 `.cursor/rules/03-karpathy-goal-driven-execution.mdc`

```markdown
---
description: Karpathy Skill 4 — Goal-Driven Execution
globs: ["**/*"]
alwaysApply: true
---

# Goal-Driven Execution

Every action must trace back to a concrete acceptance check in
Handy_Android_Build_Plan_v2.md. If you can't name the check, you don't
have a goal — stop and ask.

## Hard rules

1. At the start of every turn, restate:
   * The phase you're in (0 / 1 / 2 / 3 / 4).
   * The plan section(s) you're executing.
   * The exact acceptance check this change advances.
2. When a task is "done", the acceptance check must pass. Not
   "compiles" — passes.
3. Don't chase side quests. If a bug in an unrelated file blocks you,
   log it in DEBUG_LOG.md and route around it with the smallest
   possible workaround, then flag it at the end of the turn.
4. Don't generalise prematurely. Build the concrete case from the
   plan first; generalise only if a second case actually shows up.

## Why

Without a goal you will drift into polishing and refactoring, and v1
will never ship. The plan is the contract.

## How to apply

* End every turn with: "Advanced Phase X / Section Y — acceptance
  check: [name] — status: PASS / PENDING / BLOCKED."
```

### 2.5 `.cursor/rules/10-handy-project-guardrails.mdc`

The authoritative guardrails live on disk at
`.cursor/rules/10-handy-project-guardrails.mdc` in this repo. Read
that file — do not paraphrase it here. A short summary of what it
enforces so you know when to consult it:

* **SDK targets:** `minSdk 26`, `targetSdk 35`, `compileSdk 35`; API 36
  is a compatibility smoke lane only.
* **Modules:** `:core` is pure Kotlin; `:android-runtime` owns
  Android adapters / wrappers only and declares NO manifest
  components; `:app` owns every manifest-declared component
  (Application, Activities, `AccessibilityService`, foreground
  services, overlay services) and all Compose UI.
* **Persistence:** JSON history store (macOS-schema-compatible),
  `DataStore<Preferences>` for settings, `EncryptedSharedPreferences`
  + Keystore for secrets. **No Room in v1.**
* **Guided workflow / multi-step runner:** out of scope for v1.
* **macOS prompt reuse:** port verbatim, with the narrow
  platform-adaptation table and the Android-only `screenText` /
  `intentTool` addendums.
* **Action performance:** v1 binds
  `ActionPerformer → NoopActionPerformer`; no real performer lands
  in v1.
* **Pointing:** Strategy B only; the four canonical semantic
  `[POINT:…]` forms.
* **Accessibility service config:** XML declares
  `canRetrieveWindowContent=true`, `canTakeScreenshot=true`,
  sensible event types + `notificationTimeout`; `serviceInfo` adds
  `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` and `FLAG_REPORT_VIEW_IDS`.
* **Screen capture pipeline:** API 34+ →
  `takeScreenshotOfWindow`, API 30–33 → `takeScreenshot`, API 26–29
  → `MediaProjection` fallback with registered
  `MediaProjection.Callback` + synchronous `VirtualDisplay`/`Surface`
  release on `onStop()`. Always returns `CaptureResult`.
* **STT:** `createOnDeviceSpeechRecognizer` on API 31+ when
  available, else `createSpeechRecognizer`; **all recognizer calls
  main-thread**; `destroy()` on teardown; manifest `<queries>` for
  `android.speech.RecognitionService`.
* **Package visibility / app launching:** `LaunchableAppIndex`
  built at app start and refreshed on package-change broadcasts.
  No per-request fuzzy `PackageManager` walks. No
  `QUERY_ALL_PACKAGES` permission.
* **Intent dispatch:** `dispatch_action` is the only action tool;
  returns structured `IntentResult` (`Dispatched` / `ChooserShown`
  / `NeedsConfirmation` / `NoHandler` / `Failed`); destructive
  actions require explicit confirmation.
* **Web search:** wired but off by default.
* **Voice + animations:** tests with every audio-touching commit;
  macOS parity curves/durations.
* **Play / policy deliverables:** in-app Accessibility disclosure,
  explicit consent step, Play Console declaration draft, review
  artifact list, privacy policy, no autonomous-controller marketing
  copy — all must-ship, not later-risk.
* **OS-1..OS-5:** the five Android platform traps described in
  detail in the on-disk file (FGS types + Android 15 start rules;
  idle-widget overlay stays non-focusable, typed chat in
  `ChatActivity`; tiered screenshot API + MediaProjection
  lifecycle; `OverlayComposeHost` for every overlay;
  capture-API-driven `SecureWindow` classification — `isSecure` is
  a hint only).
* **Forbidden:** `!!` outside tests, `runBlocking` outside tests,
  `LiveData`, XML layouts, `System.out`/`println`, `@SuppressLint`
  without justification, starting an FGS without
  `foregroundServiceType`, any new overlay that hosts a text
  input, calling the accessibility screenshot APIs without the
  tiered guard, a bare `ComposeView` in `WindowManager` (no host),
  raw `Bitmap` across `LlmClient`, Room in the v1 build, runtime
  emission of `[POINT:x,y:…]`, a real `ActionPerformer` in v1,
  and any multi-step guided workflow.

### 2.6 `.cursor/rules/20-debug-log.mdc`

The authoritative DEBUG_LOG rule lives on disk at
`.cursor/rules/20-debug-log.mdc`. Read that file — do not paraphrase
it here. A short summary of what it enforces so you know when to
consult it:

* `DEBUG_LOG.md` is initialised during Phase 4 (hardening) with
  `DL-000 — Initial v1 green build` as the seed entry.
* From Phase 4 onward, every fix (build error, compile-warning-as-
  error, runtime crash, ANR, flaky test, logic bug) appends a
  structured DL-XXX entry in the same commit as the fix, BEFORE you
  tell the user the fix is complete.
* Entries use the sealed template (Date / Severity / File(s) /
  Symptom / Root Cause / Fix / Prevention Rule). Numbering is
  sequential and append-only.
* Before writing code, read the log and call out any Prevention
  Rules that apply in your Plan block.
* When three or more entries share the same prevention rule,
  promote it into a new `.cursor/rules/*.mdc` file.

---

## 3. Quick recap

* **Cursor prompt** → paste Section 1 into Cursor, point it at this repo.
* **Cursor rules** → the six `.mdc` files in `.cursor/rules/` (the four Karpathy skills + `10-handy-project-guardrails.mdc` + `20-debug-log.mdc`) are the authoritative source. Section 2 of this document is a pointer / summary, not a duplicate.
* **Required reading for Cursor** → `Handy_Android_Build_Plan_v2.md` (the sibling comprehensive plan), the existing `./Handy/` macOS sources (for parity), the macOS `debugging.mdc` (for the logging pattern), and the Karpathy skills (already encoded in the rule files).
* **Execution path** → Phase -1 source-of-truth alignment (keep the three docs + two rule files internally consistent) → Phase 0 scaffold → Phase 1 `:core` → Phase 2 `:android-runtime` adapters → Phase 3 `:app` (manifest components, overlay + gesture state machine, `ChatActivity`, Apple-class theme) → Phase 4 hardening + DEBUG_LOG bootstrap + Play / policy deliverables. Each phase has named acceptance checks including the OS-1..OS-5 checks.
* **SDK targets** → `minSdk 26`, `targetSdk 35`, `compileSdk 35`. API 36 is a compatibility smoke lane only.
* **Chat surface** → v1 is `ChatActivity` only. No `ChatPanelOverlayService`.
* **Persistence** → JSON history store (macOS-schema-compatible); `DataStore<Preferences>` for settings; `EncryptedSharedPreferences` + Keystore for secrets. No Room in v1.
* **Manifest component ownership** → all manifest-declared components live in `:app`. `:android-runtime` owns adapters / wrappers only.
* **v1 NOT in scope** (seams only, not implementations): tap-for-me gesture dispatch, Gemini brain, Strategy A pixel pointing, continuous guided / multi-step workflow.
* **Web search**: wired but off by default.
* **Voice and animations**: must-work features, backed by instrumentation tests on every audio-touching commit, curves/durations matching macOS exactly. STT uses `createOnDeviceSpeechRecognizer` on API 31+ when available; all recognizer calls stay on the main thread.
* **Play / policy**: in-app Accessibility disclosure, explicit consent step, Play Console declaration, review artifact list, privacy policy — all must-ship.
* **OS constraints (non-negotiable for a bug-free first build)**: OS-1 FGS types declared in the manifest for every foreground service, with Android 15 `SYSTEM_ALERT_WINDOW` background-start rules honoured; OS-2 idle widget stays `FLAG_NOT_FOCUSABLE` at all times and typed chat lives in `ChatActivity` (no chat-panel overlay); OS-3 capture pipeline branches API 34+ → `takeScreenshotOfWindow`, API 30–33 → `takeScreenshot`, API 26–29 → MediaProjection with a registered callback + synchronous virtual-display / surface release on `onStop()`; OS-4 `OverlayComposeHost` provides `LifecycleRegistry` + `SavedStateRegistryController` + `ViewModelStore` for every Compose overlay; OS-5 capture pipeline returns a `CaptureResult` sealed type whose `SecureWindow` classification comes from the capture API itself (official failure codes + black-frame heuristic); `windowInfo.isSecure` is an optional hint only, never the required source of truth. On `SecureWindow` the orchestrator injects a "screen is secure" system message instead of feeding blank frames to the LLM.
