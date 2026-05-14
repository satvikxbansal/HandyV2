# Handy on Android — Build Plan v2

> **V2-SUPERSESSION BANNER (Phase 0 alignment).** Everything below describes the **V1 baseline** (tap-for-me deferred, Claude-only brain, `ChatActivity`-only chat surface, pixel pointing deferred, etc.) and remains valid as historical context + the architecture the V2 work builds on. For the **actual V2 scope** — overlay chat panel, bounded tap-for-me, BrainRouter + Gemini cloud + Gemini Nano, semantic pointing from the screen-text path, notification listener, clipboard features, Diagnostics, Quick Settings tile, Unified Buddy UX, four-color bubble taxonomy, action audit, cursorbuddy-android recipe borrowings, V3 fence — read [`Handy_Android_Build_Plan_V2_Scope.md`](Handy_Android_Build_Plan_V2_Scope.md). If this document and the V2 scope doc disagree on any V2 topic, **the V2 scope doc wins** and this document's wording must be treated as stale for that topic.

**Status:** Planning document. Supersedes v1. Read this end-to-end before writing any code.

**Scope of v1 (first build):** a bug-free, fast, Apple-class Android port of Handy with chat, voice (long-press widget), streaming responses, `[POINT]` semantic pointing via Accessibility, per-tool memory, screen capture, screen **text** reading via Accessibility tree, intent dispatch ("set a 10-minute timer"), and a pluggable brain abstraction (Claude in v1, Gemini/others later). Widget gesture model: tap = chat, long-press = voice, drag = snap to nearest edge. Web search is implemented but **off by default** until we evaluate.

**Deferred to v2 (historical note — now promoted; see V2 scope doc):** tap-for-me / do-it-for-me via `AccessibilityService.dispatchGesture`. The *seams* are built into v1 so turning the feature on later is an implementation, not a refactor.

---

## 1. Product quality bar (non-negotiables)

These are not aspirations. They are gating criteria for shipping v1.

- **Bug-free first build.** No known crashes, ANRs, or navigation dead-ends on a fresh install. Every bug fixed after the first build goes into `DEBUG_LOG.md` (see Section 14).
- **Voice is flawless.** Long-press → audible/haptic confirmation → partial transcripts stream within 300ms → release → final transcript sent. No cut-offs, no double-fire, no permission loops.
- **Animations are flawless.** 60 fps minimum on a Pixel 6a-class device, with no dropped frames during widget drag, snap, or chat bubble stream. All motion uses the same easing tokens.
- **Runs on the majority of phones in active use.** `minSdk 26` (Android 8.0) → covers ~96% of active Android devices in 2026. Target devices: Pixel 6a, Samsung A-series, Motorola Moto G, OnePlus Nord, Redmi Note — mid-range, not flagship-only.
- **No lags.** 16ms frame budget. No main-thread I/O, no main-thread JSON, no bitmap work on main, no cold-start > 800ms, no widget-tap-to-chat-visible > 250ms.
- **Apple-class UI.** Bold, confident typography; generous spacing; dark-first palette from the macOS `DesignSystem.swift`; considered motion; translucency and rounded corners everywhere; no Material "sharp-edge" default styles.

Every pull request against v1 is reviewed against this list. If any item regresses, the PR does not merge.

---

## 2. V1 scope — what's in, what's out, what's stubbed

### In v1 (must ship, must be bug-free)

- Foreground service that keeps Handy alive and owns the overlay widget.
- Floating widget with the exact gesture model: tap → chat, 400 ms long-press → voice (push-and-hold), drag → snap to nearest vertical edge (Y clamped, X snapped).
- Chat `Activity` with streaming Claude responses, per-tool memory, image attach, and settings.
- Onboarding flow for every permission (overlay, accessibility, mic, notifications, screen capture).
- Voice: Android `SpeechRecognizer` (on-device when available) with partial transcripts.
- TTS: Android `TextToSpeech` system voice.
- Screen capture via `AccessibilityService.takeScreenshot` (preferred) with `MediaProjection` fallback.
- Screen **text** extraction via `AccessibilityNodeInfo` tree walking. (New — see Section 6.)
- Intent dispatch for a starter set of actions (timer, alarm, open URL, open app, dial, maps query). (New — see Section 7.)
- Semantic `[POINT]` pointer via `AccessibilityNodeInfo.getBoundsInScreen()` — Strategy B only.
- Per-tool chat history (JSON file store, macOS-schema-compatible) keyed by app package + umbrella site label.
- `EncryptedSharedPreferences` for API keys.
- Pluggable LLM brain: `LlmClient` interface + `ClaudeLlmClient` implementation. (New — see Section 5.)
- Web search tools (Brave / Jina / GitHub) behind a setting that is **off by default**.
- `DEBUG_LOG.md` protocol enforced from day one.
- Comprehensive unit + instrumented tests.

### Promoted to V2 (see [`Handy_Android_Build_Plan_V2_Scope.md`](Handy_Android_Build_Plan_V2_Scope.md) for the authoritative spec)

The items below were listed here as "deferred to v2 with seams in v1". They are now in V2 scope. The V2 scope doc is the source of truth; the V1-era seams in this document remain the starting point for the implementation.

- **Tap-for-me** — V1 ships `NoopActionPerformer`; V2 adds `AccessibilityGestureActionPerformer` with confirmation policy, bounded catalog, and action audit (V2 scope §4). The V1 Hilt binding flip is the delivery mechanism; the surrounding rules in V2 are new.
- **Gemini / other brains** — V1 ships the `LlmClient` seam; V2 adds `GeminiCloudLlmClient`, a new `LocalGenAiClient` interface (Gemini Nano), and a `BrainRouter` (V2 scope §5). Gemini cloud is experimental, not default.
- **Expanded intent catalogue** — V1 ships ~9 intents; V2 adds SMS compose, calendar event, reminder, app-info / notification-settings / accessibility-settings / battery-optimization / Wi-Fi / Bluetooth deep links, share URL, start-navigation, system web search, open-by-indexed-app (V2 scope §4.2). Still dispatched through `dispatch_action` only.
- **Semantic `[POINT]` from screen-text path** — V1 supports pointing only after vision; V2 routes `[POINT]` from pure text path and from the new accessibility-marks path (V2 scope §7).
- **Tutor mode** — V1 keeps the prompt off by default; V2 enables it with bounded cadence + cooldown + local summarization (V2 scope §12). **Not** a continuous runner.

### Net-new in V2 (no V1 seam — see scope doc for contract)

- **Overlay chat panel** as the primary quick surface (V2 scope §2).
- **Unified Buddy UX** — the widget *is* the pointer; one overlay, seven-layer Glass Lens render, Bezier flight, sticky pointing until interaction/cancel/timeout, then return to dock (V2 scope §3).
- **Four-color bubble taxonomy** — yellow transcript / teal action-in-progress / green response / blue pointer label, with mutual-exclusion rules (V2 scope §3).
- **`RequestBudgeter` + `AccessibilityMarksProvider`** — smarter capture / grounding (V2 scope §6).
- **`HandyNotificationListenerService`** — narrow scope, no ambient autonomy (V2 scope §8).
- **Clipboard assist** — helpful, not ambient (V2 scope §9).
- **`DiagnosticsActivity`** — runtime state + audit tail (V2 scope §10).
- **Quick Settings tile + optional Assist entry** (V2 scope §11).
- **Action audit** — every performed action writes an `AuditEvent` (V2 scope §4.3).
- **Cursorbuddy-android recipe borrowings** — Glass Lens render, compact accessibility-marks JSON, node-first-gesture-second tap, cache-at-tap snapshot, IME choreography, voice auto-submit, `flagIncludeNotImportantViews`, glassmorphism-without-window-blur, per-app quick-prompt chips, `StateFlow` for accessibility connection (V2 scope §15).

### Explicitly out of v1

- Widget home-screen icon / Quick Settings tile / Assist replacement — v2.
- Wear OS / Android Auto — v3.
- Notification listener service — v2.
- Clipboard watcher — v2.
- On-device Gemini Nano fallback — v2.
- **Continuous guided workflow / multi-step runner** (the macOS-style
  "do the next thing, then the next" orchestrator). v1 is one-shot
  guidance only.
- **Tap-for-me / do-it-for-me** via `AccessibilityService.dispatchGesture`
  (v2; seams only in v1).
- **Gemini or any non-Claude brain** (v2; `LlmClient` seam only in v1).
- **Pixel-coordinate `[POINT]`** (Strategy A). v1 is semantic-pointer
  only.
- **Overlay-hosted chat panel.** v1 chat lives in `ChatActivity`; no
  `ChatPanelOverlayService`.

---

## 3. Architecture at a glance

Three Gradle modules. The boundaries matter because they make the v2 features drop in without surgery.

```
Handy/
├─ core/                          # Pure Kotlin. No android.*, no androidx.*
│   ├─ llm/                       # LlmClient interface + shared types (LlmRequest, LlmChunk, ToolDefinition)
│   ├─ prompts/                   # PromptCatalog (chat, voice, tutor) + addendums (screen-text, intent-tool, web-search)
│   ├─ search/                    # Brave / Jina / GitHub search contracts (off by default)
│   ├─ history/                   # ChatHistoryStore interface, ConversationTurn
│   ├─ parsing/                   # AssistantMarkupParser (PointParser + SpokenTagParser + IntentCallParser)
│   ├─ tool/                      # ToolContext, UmbrellaSiteLabels
│   ├─ action/                    # AssistantAction sealed class, ActionPerformer interface
│   ├─ intent/                    # IntentKeywordHints, IntentResult sealed type
│   ├─ screen/                    # ScreenSnapshot (vision + text), ScreenReader interface, ScreenInputRouter
│   ├─ orchestrator/              # ConversationOrchestrator (router + prompt builder + stream consumer)
│   └─ model/                     # ChatMessage, ChatTurn, ImagePart, Settings enums, LoadingVerbs, WebSearchStatusText
│
├─ android-runtime/               # Android adapters / wrappers. No UI, no Compose,
│                                 # and NO manifest-declared components in v1.
│   ├─ llm/                       # ClaudeLlmClient (OkHttp SSE)
│   ├─ capture/                   # ScreenCapturePipeline (API 34+ takeScreenshotOfWindow → API 30–33 takeScreenshot → API 26–29 MediaProjection). Returns CaptureResult.
│   ├─ accessibility/             # AccessibilityTreeReader, SemanticPointerResolver (pure logic; the Service class itself lives in :app)
│   ├─ speech/                    # AndroidSttClient (createOnDeviceSpeechRecognizer on API 31+)
│   ├─ tts/                       # AndroidTtsClient (system TextToSpeech)
│   ├─ storage/                   # EncryptedKeyStore, DataStoreSettings, JsonHistoryStore (macOS-schema-compatible JSON files)
│   ├─ intent/                    # AndroidIntentDispatcher (AssistantAction → Intent, returns IntentResult), LaunchableAppIndex
│   ├─ action/                    # NoopActionPerformer (v1) — seam for v2
│   └─ di/                        # Hilt modules binding :core interfaces to impls
│
└─ app/                           # Owns all manifest components and all Compose UI.
    ├─ HandyApplication.kt
    ├─ service/                   # AssistantForegroundService (foregroundServiceType="specialUse|microphone")
    │                             # MediaProjectionCaptureService (foregroundServiceType="mediaProjection")
    ├─ accessibility/             # HandyAccessibilityService class (delegates to :android-runtime readers/resolvers)
    ├─ overlay/                   # FloatingWidgetOverlayService + gesture state machine + snap + OverlayComposeHost
    ├─ pointer/                   # PointerOverlayController wiring
    ├─ chat/                      # ChatActivity, ChatViewModel (the single chat surface in v1)
    ├─ settings/                  # SettingsActivity
    ├─ onboarding/                # OnboardingActivity (permissions + disclosure + keys)
    ├─ widget/                    # Floating-widget Compose visuals
    └─ theme/                     # DesignSystem.kt + HandyTheme
```

Two rules we enforce:

1. **Purity of `:core`.** If you want to type `import android.` inside
   `core/`, stop. Move that code into `:android-runtime` behind an
   interface defined in `:core`. This keeps the brain swappable, the
   intent catalogue extensible, and the tap-for-me feature a
   one-class addition in v2.
2. **Manifest ownership.** Every `<service>`, `<activity>`,
   `<receiver>`, or `<provider>` is declared in `:app`'s manifest and
   the class that implements it lives in `:app`. `:android-runtime`
   hosts Android wrappers, adapters, and pure Android-aware logic
   only — it never owns a manifest component in v1. This gives us a
   single module in which to reason about lifecycle, foreground-service
   types, overlay flags, and the Accessibility service declaration.

---

## 4. Tech stack (locked for v1)

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin 1.9.x | Standard |
| UI | Jetpack Compose + Material3 (minimal) | Apple-class theming over Material primitives |
| DI | Hilt | Best Android support, less ceremony than Dagger |
| Async | Coroutines + Flow | `Flow<LlmChunk>` for streaming |
| Network | OkHttp 4 + `okhttp-sse` | SSE streaming for Claude |
| JSON | kotlinx.serialization | Multiplatform-friendly, fast |
| DB/Storage | DataStore Preferences + JSON files | Simple; Room overkill for v1 |
| Secrets | `EncryptedSharedPreferences` + Android Keystore | Keychain equivalent |
| Animation | `androidx.compose.animation` + `androidx.dynamicanimation` (SpringAnimation for snap) | 60fps physics |
| Speech (STT) | `SpeechRecognizer.createOnDeviceSpeechRecognizer` on API 31+ when available; else `createSpeechRecognizer`. `EXTRA_PREFER_OFFLINE` only as a hint. | On-device first, all calls main-thread, `destroy()` on teardown |
| Speech (TTS) | `android.speech.tts.TextToSpeech` | Local, fast |
| Min SDK | 26 (Android 8.0) | ~96% coverage |
| Target SDK | 35 (Android 15) | Play Store requirement as of late-2025/2026 |
| Compile SDK | 35 | Matches target |
| Compatibility smoke | API 36 (Android 16) | CI-only smoke; NOT a v1 shipping target |
| Java | 17 | Stable |
| Build | Gradle Kotlin DSL + version catalogs | Modern standard |

Nothing exotic. Every dependency here is a first-party Google / Square library.

---

## 4.5 Android OS constraints — the five that will break v1 if ignored

Five platform behaviours turn into silent app-kills or hallucinated LLM output if we are not deliberate about them. They are pulled out here because they cut across several sections below (foreground services, overlays, screen capture, accessibility) and because the Cursor rules in `10-handy-project-guardrails.mdc` reference them by number (OS-1..OS-5).

**OS-1 — Foreground service types (manifest crash + Android 15 start rules).** On API 34+, starting a foreground service without an explicit `android:foregroundServiceType` in the manifest throws a fatal `MissingForegroundServiceTypeException` on first launch. Because `targetSdk 35` is required, this applies to every FGS in Handy, with the added Android 15 wrinkle that background-initiated FGS starts are more restricted than before.

- `AssistantForegroundService` declares `android:foregroundServiceType="specialUse|microphone"`. The manifest requests `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, and `FOREGROUND_SERVICE_MICROPHONE`.
- The `specialUse` entry includes a `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" />` explanation string ("on-screen AI assistant overlay that must remain visible across apps").
- The MediaProjection fallback service (screen-capture path) declares its own `android:foregroundServiceType="mediaProjection"` and requests `FOREGROUND_SERVICE_MEDIA_PROJECTION`.
- `startForeground()` fires within 5 s of `onStartCommand()`; the notification channel is created at `Application.onCreate`, never lazily.
- **Android 15 `SYSTEM_ALERT_WINDOW` background-start exemption.** If we rely on this exemption to start the FGS from a background context, the overlay must **already be visible** in `WindowManager` at the moment the start happens. If the widget is not currently attached, route the start through a user-initiated broadcast or a brief foreground activity rather than assuming the exemption covers us.

**OS-2 — Idle-widget overlay stays non-focusable; typed chat lives in `ChatActivity`.** In v1 the floating widget is the only overlay that hosts interactive chrome. A tap on the widget launches `ChatActivity` (its own window, normal IME handling), so the historical "remove `FLAG_NOT_FOCUSABLE` when the chat panel opens" dance does not apply.

- `overlayFlagsFor(mode)` is kept as a single function for forward-compatibility, but in v1 `OverlayMode` has one public value (`IdleWidget`) returning `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`.
- The widget overlay is hidden while `ChatActivity` is foreground and restored on close.
- An instrumentation test must assert that typing into the `ChatActivity` input actually commits characters — we don't trust manual testing for IME behaviour.
- **Do not add a `ChatPanelOverlayService`** or any overlay that hosts a text input. If a future product decision needs one, it ships post-v1 and brings its own full IME test matrix.

**OS-3 — Screenshot API tiers + MediaProjection lifecycle.** With `minSdk 26`, a single capture call site cannot assume any particular screenshot API. The capture pipeline branches in this exact order:

1. **API 34+ (Android 14+)** — prefer `AccessibilityService.takeScreenshotOfWindow(activeWindowId, …)`. Cheaper, respects per-window secure flags, no projection consent.
2. **API 30–33** — use `AccessibilityService.takeScreenshot(Display.DEFAULT_DISPLAY, …)`.
3. **API 26–29** — use `MediaProjection` fallback.

- Each accessibility call is annotated `@RequiresApi` on the private method that actually invokes it, hidden behind an SDK-agnostic public entry point.
- The pipeline always returns a `CaptureResult` sealed type (see OS-5 for the shape). No raw `Bitmap` crosses an orchestration boundary.
- **MediaProjection lifecycle discipline.** Register a `MediaProjection.Callback`; in `onStop()` (or when capture is cancelled), immediately release the `VirtualDisplay` and the backing `Surface`/`ImageReader`, and clear any cached capture state so no further frames are produced. Leaking the virtual display keeps sending black frames to the LLM long after the user has stopped the session.
- A unit test must verify the API branch picks the right path on simulated `Build.VERSION.SDK_INT = 28`, `= 31`, and `= 34`.

**OS-4 — Compose inside a `WindowManager` overlay.** `ComposeView` expects a `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner`. Services don't supply any of those. An `OverlayComposeHost` class does:

- Owns `LifecycleRegistry` (driven through `CREATED → STARTED → RESUMED` on attach, reversed on detach).
- Owns `SavedStateRegistryController` (call `performAttach()` then `performRestore(null)` before the lifecycle leaves `INITIALIZED`).
- Owns `ViewModelStore`.
- Attaches all three via `setViewTreeLifecycleOwner`, `setViewTreeSavedStateRegistryOwner`, `setViewTreeViewModelStoreOwner` **before** `setContent { }`.
- `release()` drives the lifecycle to `DESTROYED` before `windowManager.removeView(root)`.
- In v1 only the floating widget overlay uses this host; any future pointer-chrome overlay reuses it.

**OS-5 — Secure / unusable capture.** Banking, password managers, DRM video, and incognito browsers set `FLAG_SECURE`. Both `takeScreenshot`/`takeScreenshotOfWindow` and `MediaProjection` return all-black buffers for those surfaces; sending a black image to the LLM produces confident hallucinations, which is worse than a clear error.

- The capture pipeline never hands raw bitmaps to the LLM. It hands a `CaptureResult`:

```kotlin
sealed class CaptureResult {
    data class Bitmap(val bitmap: android.graphics.Bitmap) : CaptureResult()
    data object SecureWindow : CaptureResult()
    data object NotPermitted : CaptureResult()
    data object Unsupported : CaptureResult()
    data class Failed(val reason: String) : CaptureResult()
}
```

- **Primary detection (required)** comes from the capture API itself: official `takeScreenshot`/`takeScreenshotOfWindow` failure codes (for example `ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS` and `ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR`), and — for the `MediaProjection` path — a cheap "is the bitmap essentially all-black" sampler (16-pixel stride; >99% of samples within 3 of RGB(0,0,0) → classify as secure / unusable).
- `windowInfo.isSecure` from `AccessibilityService.getWindows()` is **not a required source of truth** and must not be the only signal. Treat it as an optional hint only: if the capture API did not already classify the frame as secure, a `true` value may promote a `Bitmap` result to `SecureWindow`, but a `false` value never overrides a secure classification from the capture path.
- On `SecureWindow`, the orchestrator never calls `LlmClient` with an image. It injects a system chat turn:

  > I can't see this screen — the app is marked secure (e.g. banking, incognito, or password manager). Ask me again from a non-secure screen, or paste the text you want help with.

- The accessibility text path (`readScreenText`) consumes the same pipeline: if the capture channel flags the active window as secure, or if a tree walk returns no usable text while the capture path reports `SecureWindow`, return the same injected message rather than a possibly-partial node list.

These five are called out individually so that every section below can assume they are handled.

---

## 4.6 Sources of truth — what to port from the macOS app

The macOS Handy repository (`./Handy/`) is checked in alongside this
plan. It is the **product spec** for every assistant-facing string,
prompt, style rule, and UX contract. Before writing any new piece of
assistant behaviour in Android, grep this tree first. If the concept
already exists, port it; do not redesign it.

The table below is the authoritative mapping. A feature is not done
until the Android code reads or reproduces the macOS source.

| macOS symbol (in `./Handy/…`)                                       | Android destination                                                                   | Port rule                                                                                                  |
|---------------------------------------------------------------------|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `HandyManager.swift → chatSystemPrompt`                             | `core/prompts/PromptCatalog.chatSystemPrompt`                                         | Verbatim except allowed platform adaptations below.                                                        |
| `HandyManager.swift → voiceSystemPrompt`                            | `core/prompts/PromptCatalog.voiceSystemPrompt`                                        | Verbatim except allowed platform adaptations below.                                                        |
| `HandyManager.swift → tutorModeSystemPrompt`                        | `core/prompts/PromptCatalog.tutorModeSystemPrompt`                                    | Verbatim except allowed platform adaptations below.                                                        |
| `HandyManager.swift → webSearchPromptAddendum(hasBraveKey:)`        | `core/prompts/PromptCatalog.webSearchAddendum(hasBraveKey)`                           | Verbatim.                                                                                                  |
| `HandyManager.swift → loadingVerbs` (34 strings)                    | `core/ui/LoadingVerbs.kt`                                                             | Verbatim.                                                                                                  |
| `HandyManager.swift → webSearchStatusText` tool-name mapping        | `core/ui/WebSearchStatusText.kt`                                                      | Verbatim.                                                                                                  |
| `HandyManager.swift → introPrefix` first-turn behaviour             | `core/brain/IntroPrefix.kt`                                                           | Verbatim.                                                                                                  |
| `PointParser.stripPointTags / extractSpokenPart / clampVoiceSpokenForTTS (420) / clampVoiceSpokenForOverlay (110) / truncateAtSentenceBoundary` | `core/parsing/AssistantMarkupParser.kt`                                               | Verbatim logic + the exact `maxChars` constants. Extend to parse **semantic** pointer form as well (Strategy B). |
| `ChatMessage.swift → ChatMessage / MessageRole / ConversationTurn`  | `core/model/ChatMessage.kt` + `core/history/ChatHistoryStore.kt`                      | JSON-schema-compatible. Future cross-platform sync must work without a migration.                          |
| `AppSettings.swift → AssistantMode / STTProvider / TTSProvider / SarvamVoice` | `core/model/Settings.kt`                                                              | Enum raw values + display descriptions verbatim.                                                           |
| `ClaudeAPIService.swift → ClaudeAPIError` taxonomy + `errorDescription` | `core/llm/LlmError.kt` + `android-runtime/…/ClaudeLlmClient.kt`                         | Verbatim error strings for the platform-agnostic cases; relocalise the two permission-flow strings (screen-recording, speech-recognition) for Android's settings paths. |
| `WebSearchService.swift` Brave / Jina / GitHub calls + citation rule | `core/search/{BraveSearch,JinaReader,GithubSearch}.kt`                                | Verbatim call shapes, headers, and the "briefly mention your source naturally" contract.                   |
| `TTSService.swift` + `SpeechRecognitionService.swift` error strings | `android-runtime/tts/AndroidTtsClient.kt` + `android-runtime/speech/AndroidSttClient.kt` | Verbatim error copy; relocalise permission-denied copy for Android settings paths.                         |
| `DesignSystem.swift` colour / spacing / typography / motion tokens  | `app/theme/DesignSystem.kt`                                                           | Verbatim numeric values; substitute Inter for SF Pro; scale sp/dp appropriately (see §11).                 |
| `ChatInterfaceView.swift` bubble layout contract                    | `app/chat/ChatPanel.kt` Compose                                                       | Verbatim visual contract (which roles render, which fields are hidden, streaming-delta behaviour).         |

### Allowed platform adaptations for the three system prompts

The only transforms permitted when porting the prompt bodies:

| macOS text                                       | Android text                                                  |
|--------------------------------------------------|---------------------------------------------------------------|
| "on **macos**" / "on macos only"                 | "on **android**" / "on android only"                          |
| "menu bar" (Handy's home)                        | "floating widget" (Handy's home)                              |
| "open handy's chat from the menu bar"            | "tap the handy widget to open the full chat"                  |
| keyboard shortcut references (command/option/control) | physical-keyboard-optional; prefer gestures ("tap the widget", "long-press to speak"). Keep shortcuts if the user asks specifically. |
| "windows/linux" platform disclaimer              | drop, or replace with "android phones and tablets"            |
| pixel-coordinate `[POINT:x,y:label]` instructions | semantic `[POINT:role=…;text=…]` / `[POINT:viewId=…]` / `[POINT:desc=…]` instructions (Strategy B, §8) |

Every other word, rule, exemplar dialogue, and stylistic choice
(all lowercase, no emojis, never say "simply" or "just",
"plant a seed" closing, "don't read out code verbatim") is
preserved exactly. If a line needs changing for a reason not on
this table, it's a design decision — flag it to the user in
DESIGN_NOTES.md, do not change it unilaterally.

### New Android-only prompt addendums (no macOS counterpart)

Two addendums are added on the Android side because the new
capabilities (accessibility-tree screen reading, intent dispatch)
did not exist on macOS:

1. **`screenTextPromptAddendum(snapshot)`** — appended when
   `ScreenInputMode ≠ VisionOnly` and a `ScreenTextSnapshot` is
   present. Tells the LLM to treat the flattened tree as the
   primary source of truth for on-screen text and to reference
   `text` / `contentDescription` / `viewId` when pointing.
2. **`intentToolPromptAddendum()`** — always present when the
   `dispatch_action` tool is enabled. Tells the LLM to prefer the
   tool for well-defined actions (timer, alarm, open app, dial,
   maps, share) over verbal instructions, and to ask for
   confirmation only on destructive actions.

Both addendums are appended at the END of the base system prompt
so they never interleave with user-tuned content.

---

## 5. The brain — pluggable LLM abstraction

**V1 runs Claude. V2 can swap in Gemini without touching call sites.** This is a hard requirement of the `core/llm/` design.

### The interface

```kotlin
// core/llm/LlmClient.kt
interface LlmClient {
    /** Stream a chat completion. Emits text deltas, tool calls, and a terminal Done event. */
    fun streamChat(request: LlmRequest): Flow<LlmChunk>

    /** Short identifier for logging + settings display ("claude-sonnet-4", "gemini-2.5-flash", …). */
    val modelId: String
}

data class LlmRequest(
    val systemPrompt: String,
    val messages: List<ChatTurn>,
    val images: List<ImagePart> = emptyList(),
    val screenText: ScreenTextSnapshot? = null,   // for screen-reading path
    val tools: List<ToolDefinition> = emptyList(),
    val maxTokens: Int = 2048,
)

sealed class LlmChunk {
    data class Text(val delta: String) : LlmChunk()
    data class ToolCall(val id: String, val name: String, val inputJson: String) : LlmChunk()
    data class Done(val stopReason: String) : LlmChunk()
    data class Error(val throwable: Throwable) : LlmChunk()
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)
```

### Why this shape

- `Flow<LlmChunk>` is provider-neutral. Claude's SSE `content_block_delta` and Gemini's `generate_content_stream` both fit.
- Tool calls are a structured event, not inline text. The chat pipeline knows when to pause and execute (search, intent dispatch) without regex-parsing stream content.
- `screenText` is first-class input. When it is non-null and `images` is empty, we save API cost (see Section 6).
- `modelId` is a single string, surfaced in settings.

### V1 implementation: `ClaudeLlmClient`

- OkHttp `EventSource` against `https://api.anthropic.com/v1/messages`.
- Translates `LlmRequest` → Anthropic body: system prompt, messages, images as `image/jpeg` base64, tools with Anthropic tool schema.
- Parses SSE events: `content_block_start`, `content_block_delta`, `content_block_stop`, `message_stop`, `tool_use`.
- Emits `LlmChunk.Text`, `LlmChunk.ToolCall`, `LlmChunk.Done`.
- Reads key via injected `EncryptedKeyStore`.

### V2: `GeminiLlmClient` (seam only in v1)

- Hits `generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent`.
- Same `Flow<LlmChunk>` shape.
- Gemini tool-use protocol (function declarations → function_call parts) maps cleanly.
- Images: Gemini accepts inline base64 JPEG the same way.
- Enabled via `AppSettings.llmProvider` enum: `CLAUDE`, `GEMINI` (latter hidden until v2).

### Settings surface for brains

- `AppSettings.llmProvider: LlmProvider = CLAUDE` (only CLAUDE selectable in v1).
- `AppSettings.claudeModelOverride: String?` — defaults to `claude-sonnet-4-20250514`.
- `AppSettings.geminiModelOverride: String?` — hidden in v1.

### Contract tests

Write a JVM-level contract test that any `LlmClient` must pass:

- Empty user message → emits `Text` deltas → terminal `Done`.
- Single image + text → emits text grounded in image.
- Tool registered → model emits a `ToolCall` chunk before `Done`.
- Network error → emits `Error` chunk and closes the flow.

In v2 when adding `GeminiLlmClient`, running the same contract test is the acceptance bar.

---

## 6. Screen reading — the zero-vision path

This is the cost-and-latency feature. When the user asks "what does this email say?" or "summarize this article," we do **not** need to send a screenshot. We walk the Accessibility tree, extract the text, and feed it to the LLM as structured prose. That's milliseconds instead of a round-trip screenshot + JPEG + vision token cost.

### Data shape

```kotlin
// core/screen/ScreenSnapshot.kt
data class ScreenTextSnapshot(
    val packageName: String,
    val windowTitle: String?,
    val timestamp: Long,
    val root: UiNode,
)

data class UiNode(
    val role: String,               // Button, EditText, TextView, WebView, …
    val text: String?,              // AccessibilityNodeInfo.text
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val boundsInScreen: IntRect,
    val children: List<UiNode>,
    val clickable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
)
```

### Extraction

`HandyAccessibilityService` exposes:

```kotlin
suspend fun readScreenText(maxDepth: Int = 20, maxNodes: Int = 400): ReadResult

sealed class ReadResult {
    data class Snapshot(val snapshot: ScreenTextSnapshot) : ReadResult()
    data object SecureWindow : ReadResult()      // per OS-5
    data object NoActiveWindow : ReadResult()
}
```

BFS from `rootInActiveWindow`. Secure-window handling follows OS-5: the tree walker does **not** rely on `windowInfo.isSecure` as the required source of truth. Instead, it consumes the same `CaptureResult` channel as the screenshot path — if the capture pipeline has classified the active window as `SecureWindow`, the reader short-circuits with a `SecureWindow` result and injects the canonical "I can't see this screen" system message. A `true` value of `windowInfo.isSecure` on API 30+ is an additional hint that may promote a suspicious tree walk to `SecureWindow`; a `false` value never overrides a secure classification from the capture path.

Skip nodes with no text, no contentDescription, and no interesting role. Cap depth and total node count to protect memory.

### Serialization for the LLM

The prompt layer has two presentations:

- **Compact for LLM** — a YAML-ish flattened tree where each visible text node gets one line: `[Button] "Send" (id/send_btn) @ 120,600-280,660`. This is what goes into `LlmRequest.screenText` (when used, passed as a structured `<screen_ui>` block in the system prompt addendum).
- **Debug** — pretty-printed JSON, only in debug builds.

Token-efficient: a typical Gmail screen flattens to ~1–3KB of text. Compared to a 1280×720 JPEG that the vision model must process, this is an order of magnitude cheaper and faster.

### The routing decision — vision, text, or both?

A small `ScreenInputRouter` decides per-request:

```kotlin
sealed class ScreenInputMode {
    object VisionOnly : ScreenInputMode()
    object TextOnly : ScreenInputMode()
    object Both : ScreenInputMode()
}

fun chooseScreenInput(userMessage: String, settings: AppSettings): ScreenInputMode {
    val visualKeywords = listOf("where is", "click", "point", "show me", "highlight", "color", "layout", "design", "image", "photo", "picture", "icon", "diagram")
    val textualKeywords = listOf("what does", "summarize", "read", "email", "article", "message", "translate", "copy this", "what is this", "explain this")
    val looksVisual = visualKeywords.any { userMessage.contains(it, ignoreCase = true) }
    val looksTextual = textualKeywords.any { userMessage.contains(it, ignoreCase = true) }
    return when {
        looksVisual && !looksTextual -> ScreenInputMode.VisionOnly
        looksTextual && !looksVisual -> ScreenInputMode.TextOnly
        else -> ScreenInputMode.Both      // when unsure, send both — cheap insurance
    }
}
```

The heuristic is small on purpose. We will iterate after measuring real usage. A future `BrainRouter` can use an on-device classifier.

### System-prompt addendum

When `screenText` is non-null, append to the system prompt:

```
screen text (from accessibility): the user is in package {packageName}; here is the visible UI tree in flattened form. Use this as your primary source; do not hallucinate UI that is not listed. If you need to point at an element, reference its viewId or text.

<screen_ui>
{compact serialization}
</screen_ui>
```

### Privacy

Screen-text extraction is gated by the same Accessibility consent that the user has already granted. No text leaves the device until the user types/speaks a message. Nothing is cached to disk.

---

## 7. Intents & deep linking

Users say things in natural language. The assistant turns them into `Intent`s when it can. We do not want Claude "telling the user how to tap through the Clock app" — we want Handy to *do it*, via `Intent`, without any UI traversal.

### The action model

```kotlin
// core/action/AssistantAction.kt
sealed class AssistantAction {
    data class StartTimer(val seconds: Int, val label: String?) : AssistantAction()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String?) : AssistantAction()
    data class OpenUrl(val url: String) : AssistantAction()
    data class OpenApp(val packageHint: String) : AssistantAction()          // resolves via PackageManager
    data class DialNumber(val number: String) : AssistantAction()            // does not auto-call
    data class MapsSearch(val query: String) : AssistantAction()
    data class ComposeEmail(val to: String?, val subject: String?, val body: String?) : AssistantAction()
    data class ShareText(val text: String) : AssistantAction()
    data class WebSearchIntent(val query: String) : AssistantAction()
    // v2 extension point — add here without touching dispatcher
}
```

### How the LLM triggers actions

Register a single tool with the LLM: `dispatch_action`. Its input schema is a discriminated union of the actions above. Every provider (Claude, Gemini) knows how to call tools.

System-prompt addendum when intents are enabled:

```
intents: you have a tool called `dispatch_action` that fires native android intents (start a timer, set an alarm, open a url, compose an email, etc). when the user asks for something that maps cleanly to one of these, call the tool instead of giving text instructions. if you call the tool, your spoken/written reply should be a one-line confirmation ("timer set for 10 minutes").
```

### The dispatcher

```kotlin
// android-runtime/intent/AndroidIntentDispatcher.kt
class AndroidIntentDispatcher(
    private val appContext: Context,
    private val launchableApps: LaunchableAppIndex,
) {
    fun dispatch(action: AssistantAction): IntentResult = when (action) {
        is AssistantAction.StartTimer -> fireTimer(action)
        is AssistantAction.SetAlarm   -> fireAlarm(action)
        is AssistantAction.OpenUrl    -> fireViewUrl(action.url)
        is AssistantAction.OpenApp    -> fireLaunchApp(action.packageHint)
        is AssistantAction.DialNumber -> fireDialer(action.number)            // NeedsConfirmation
        is AssistantAction.MapsSearch -> fireMaps(action.query)
        is AssistantAction.ComposeEmail -> fireEmail(action)
        is AssistantAction.ShareText  -> fireShare(action.text)                // NeedsConfirmation
        is AssistantAction.WebSearchIntent -> fireWebSearch(action.query)
    }
}
```

Every dispatch returns a structured `IntentResult`:

```kotlin
sealed class IntentResult {
    data class Dispatched(val component: ComponentName?) : IntentResult()
    data object ChooserShown : IntentResult()
    data class NeedsConfirmation(val reason: String) : IntentResult()
    data object NoHandler : IntentResult()
    data class Failed(val reason: String) : IntentResult()
}
```

Destructive actions (placing a call, sending a message, sharing content) always return `NeedsConfirmation` so the UI can require an explicit user tap before the real intent fires. Non-destructive one-step actions (timer, alarm, open URL, open app, maps search, web search) dispatch directly and return `Dispatched` or `ChooserShown`.

### `LaunchableAppIndex` — no naive fuzzy package resolution

`OpenApp` is the only action that needs to resolve a natural-language name to a concrete package (e.g. "gmail" → `com.google.android.gm`). We do **not** do this with a per-request fuzzy `PackageManager` walk — that is slow, defeats package-visibility restrictions on `targetSdk 35`, and makes every conversation touch `PackageManager`.

Instead, `:android-runtime` ships a `LaunchableAppIndex` built at app start from

```
packageManager.queryIntentActivities(
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
    0,
)
```

The index stores `(label, package, component, iconUri)` tuples and exposes a single `resolve(hint: String): ResolvedApp?` entry point that matches on label and common aliases. It refreshes on `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REMOVED` / `ACTION_PACKAGE_REPLACED` (registered dynamically). We do **not** request `QUERY_ALL_PACKAGES`; the manifest `<queries>` block lists only the Intents we actually need.

### The starter catalogue (v1)

| Action | Intent | Notes |
|---|---|---|
| StartTimer | `AlarmClock.ACTION_SET_TIMER` with `EXTRA_LENGTH` | Works on every stock clock app |
| SetAlarm | `AlarmClock.ACTION_SET_ALARM` | |
| OpenUrl | `Intent.ACTION_VIEW` + `Uri.parse` | Http/Https + deep-link schemes |
| OpenApp | `packageManager.getLaunchIntentForPackage(LaunchableAppIndex.resolve(hint))` | Index-backed resolution (see above); no per-request fuzzy PackageManager walk |
| DialNumber | `Intent.ACTION_DIAL` with `tel:` URI | We do NOT auto-call; user must press call |
| MapsSearch | `geo:0,0?q=<query>` | Opens in user's default maps app |
| ComposeEmail | `Intent.ACTION_SENDTO` with `mailto:` | Pre-fills to/subject/body |
| ShareText | `Intent.ACTION_SEND` with `text/plain` | Shows system share sheet |
| WebSearchIntent | `Intent.ACTION_WEB_SEARCH` | Falls back to Google/default search engine |

### Safety rail

Dispatcher never fires an action without the LLM explicitly calling `dispatch_action`. No regex-based inference from free text. This is a design choice, not a limitation — it keeps the user in control and makes logs auditable.

### V2 extension

Adding a new action type is one enum entry + one `when` branch. No call sites change. Tap-for-me in v2 is not an intent — it uses `ActionPerformer` (Section 9).

---

## 8. Pointing at elements — Strategy B only

Per your v2 decision: the pixel-coordinate pointer is dropped. Only semantic pointing ships.

### LLM contract

Claude, when pointing is useful, emits one of the canonical semantic forms:

```
[POINT:role=<role>;text=<exact visible text>]
[POINT:viewId=<resource id suffix>]
[POINT:desc=<contentDescription>]
[POINT:none]
```

The model chooses the attribute it has highest confidence in. `role` is one of `button`, `link`, `textfield`, `image`, `checkbox`, `switch`, `tab`, `menuitem`. The system prompt is updated accordingly — copy `HandyManager.swift`'s `chatSystemPrompt` but replace the pixel-form `[POINT:x,y:label]` spec with the four semantic forms above (exact wording in `10-handy-project-guardrails.mdc` → "Pointing contract"). The parser accepts the legacy pixel form as a read-only compatibility path so macOS test fixtures still round-trip, but **the runtime never emits it**.

### The resolver

```kotlin
// android-runtime/pointer/SemanticPointerResolver.kt
suspend fun resolve(pointSpec: SemanticPointSpec): AccessibilityNodeInfo? {
    val root = accessibility.rootInActiveWindow ?: return null
    // 1. exact text match with role filter
    val byText = root.findByTextAndRole(pointSpec.label, pointSpec.role)
    if (byText != null) return byText
    // 2. contentDescription match
    val byDesc = root.findByContentDescription(pointSpec.label)
    if (byDesc != null) return byDesc
    // 3. viewId suffix match (if LLM knew the id from screenText path)
    if (pointSpec.viewIdHint != null) {
        val byId = root.findByViewIdSuffix(pointSpec.viewIdHint)
        if (byId != null) return byId
    }
    // 4. fuzzy text match (Levenshtein ≤ 2, case-insensitive)
    return root.findByFuzzyText(pointSpec.label, maxDistance = 2)
}
```

### The overlay

Once a node is found, read `node.getBoundsInScreen(rect)` and animate the pointer overlay to `rect.centerX, rect.centerY`. Overlay window is `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_TOUCHABLE`. Animation is `SpringAnimation` on both axes with `DAMPING_RATIO_MEDIUM_BOUNCY`, matching the motion tokens.

### Why this is better

- Survives UI reflow. The screenshot is a frozen pixel grid; the tree is live.
- Works in landscape/portrait switches between the question and the answer.
- Gives us a robust path to tap-for-me in v2: we already have the `AccessibilityNodeInfo`; v2 just adds `dispatchGesture(GestureDescription)` on top.

---

## 9. Tap-for-me — v2 seam, not v2 implementation

We do **not** ship any gesture dispatch in v1. We do ship the interfaces.

```kotlin
// core/action/ActionPerformer.kt
interface ActionPerformer {
    suspend fun tap(target: TapTarget): PerformResult
    suspend fun longPress(target: TapTarget): PerformResult
    suspend fun scroll(direction: ScrollDirection, target: TapTarget?): PerformResult
    val capabilities: Set<ActionCapability>
}

sealed class TapTarget {
    data class AtScreenPoint(val x: Int, val y: Int) : TapTarget()
    data class AtNode(val semantic: SemanticPointSpec) : TapTarget()
}

enum class ActionCapability { TAP, LONG_PRESS, SCROLL, SWIPE }
```

### V1 implementation

```kotlin
// android-runtime/action/NoopActionPerformer.kt
class NoopActionPerformer : ActionPerformer {
    override suspend fun tap(target: TapTarget) = PerformResult.Unsupported("enable tap-for-me in settings — v2")
    // …
    override val capabilities: Set<ActionCapability> = emptySet()
}
```

Hilt module binds `ActionPerformer` to `NoopActionPerformer` in v1. The chat pipeline already checks `capabilities` before offering "Do it for me" buttons — in v1 they never appear. In v2, swap the Hilt binding and the buttons appear automatically.

### V2 implementation (prewired reference only)

```kotlin
// v2 — do not ship in v1
class AccessibilityGestureActionPerformer(
    private val service: HandyAccessibilityService,
) : ActionPerformer {
    override suspend fun tap(target: TapTarget): PerformResult {
        val (x, y) = resolveTargetToScreenPoint(target) ?: return PerformResult.NotFound
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = suspendCancellableCoroutine<Boolean> { cont ->
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) { cont.resume(true) }
                override fun onCancelled(g: GestureDescription) { cont.resume(false) }
            }, null)
        }
        return if (dispatched) PerformResult.Ok else PerformResult.Failed
    }
    override val capabilities = setOf(ActionCapability.TAP, ActionCapability.LONG_PRESS, ActionCapability.SCROLL, ActionCapability.SWIPE)
}
```

The point is: v1 doesn't have this class. v2 adds this file + flips a Hilt binding. That's it.

---

## 10. Floating widget — the gesture model

Unchanged from v1 plan, fully specified here for the prompt.

### Window

In v1 the floating widget is the **only** overlay that hosts
interactive chrome. A tap launches `ChatActivity` (its own window,
normal IME handling) — there is no chat-panel overlay. We still keep
`overlayFlagsFor(mode)` as a single choke-point so forward-compatible
modes (pointer chrome, etc.) can be added without refactoring the
widget service (OS-2).

```kotlin
enum class OverlayMode { IdleWidget }   // v1: only idle widget is hosted in WindowManager

fun overlayFlagsFor(mode: OverlayMode): Int = when (mode) {
    OverlayMode.IdleWidget ->
        LayoutParams.FLAG_NOT_FOCUSABLE or
            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            LayoutParams.FLAG_LAYOUT_NO_LIMITS
}

val params = WindowManager.LayoutParams(
    LayoutParams.WRAP_CONTENT,
    LayoutParams.WRAP_CONTENT,
    LayoutParams.TYPE_APPLICATION_OVERLAY,
    overlayFlagsFor(OverlayMode.IdleWidget),
    PixelFormat.TRANSLUCENT,
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = savedX; y = savedY
}

fun onWidgetTap() {
    appContext.startActivity(
        Intent(appContext, ChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    // widget overlay hides itself while ChatActivity is foreground and restores on close.
}
```

The widget's Compose view is hosted by an `OverlayComposeHost`
(OS-4) that supplies `LifecycleOwner`, `SavedStateRegistryOwner`,
and `ViewModelStoreOwner`. Never call `setContent { }` directly on
a `ComposeView` inflated into `WindowManager` — it will crash with
"ViewTreeLifecycleOwner not found".

### Visuals (match macOS)

- Idle: hand icon, thin amber outline.
- Touch-down: outline turns white, subtle scale-up to 1.05 (150 ms, ease-out).
- Listening: waveform replaces hand; color turns blue; subtle pulse.
- Thinking: spinner (rotating arc).
- Drag: widget follows finger 1:1; amber outline stays.
- Snap: spring to nearest edge.

All motion tokens live in `DesignSystem.kt` so they can be tuned in one place.

### Gestures (exact state machine)

```
ACTION_DOWN
 → record (downX, downY), startX = windowParams.x, startY = windowParams.y
 → start 400ms long-press timer
 → outline: amber → white (150ms)

ACTION_MOVE (while not yet dragging)
 → dx = rawX - downX; dy = rawY - downY
 → if hypot(dx, dy) > scaledTouchSlop:
     cancel long-press timer
     enter DRAG state
     (if long-press had fired, cancel voice session gracefully)

ACTION_MOVE (dragging)
 → windowParams.x = clamp(startX + dx, 0, screenW - widgetW)
 → windowParams.y = clamp(startY + dy, statusBarH, screenH - widgetH - navBarH)
 → windowManager.updateViewLayout(...)

LONG-PRESS TIMER FIRES (not dragging)
 → haptic: HapticFeedbackConstants.LONG_PRESS
 → VoiceController.start()
 → widget visual: idle → waveform

ACTION_UP:
  if DRAG:
    snapToNearestEdge()          // spring, persist
  else if long-press fired:
    VoiceController.stopAndSubmit()  // ripple → spinner → chat opens with streaming
  else:
    openChatActivity()           // slide-in from widget side
  outline: white → amber (150ms)
```

### Snap animation

```kotlin
fun snapToNearestEdge() {
    val centerX = windowParams.x + widgetWidth / 2
    val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - widgetWidth
    SpringAnimation(FloatValueHolder(windowParams.x.toFloat())).apply {
        spring = SpringForce(targetX.toFloat())
            .setStiffness(SpringForce.STIFFNESS_LOW)
            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
        addUpdateListener { _, v, _ ->
            windowParams.x = v.toInt()
            windowManager.updateViewLayout(widgetView, windowParams)
        }
        addEndListener { _, _, _, _ -> settings.saveWidgetPosition(windowParams.x, windowParams.y) }
        start()
    }
}
```

### Visibility rules

- Hidden while `ChatActivity` is foreground.
- Hidden while a full-screen video is detected (optional for v1: observe `TYPE_WINDOW_STATE_CHANGED` with `FLAG_FULLSCREEN`).
- Restored on chat close.

---

## 11. Apple-class UI system

Direct translation of `DesignSystem.swift` with careful type scaling for Android DPI.

### Typography

We do not ship SF Pro (Apple-licensed). We use **Inter** (Google Fonts, free, geometric, SF-adjacent). Typography scale:

| Role | Size | Weight | Tracking |
|---|---|---|---|
| Display | 28 sp | 800 (Heavy) | -0.4 |
| Title | 20 sp | 700 (Bold) | -0.2 |
| Body | 15 sp | 400 (Regular) | 0 |
| Body-emphasis | 15 sp | 600 (SemiBold) | 0 |
| Caption | 12 sp | 500 (Medium) | +0.1 |
| Mono | 13 sp | 400 (JetBrains Mono) | 0 |

### Color palette (dark, primary)

| Token | Hex | Use |
|---|---|---|
| background | #0B0B0F | App background |
| surface | #141419 | Cards, chat bubbles |
| surfaceElevated | #1C1C22 | Inputs, elevated chrome |
| textPrimary | #F2F2F5 | Body copy |
| textSecondary | #9A9AA2 | Meta, captions |
| accent | #3B82F6 | Handy blue |
| accentMuted | #3B82F640 | Rings, glows |
| amber | #F59E0B | Widget outline idle |
| success | #10B981 | — |
| danger | #EF4444 | — |
| border | #2A2A31 | Dividers |

Light palette mirrors with the same ratios (background #F7F7F9, surface #FFFFFF, …).

### Motion tokens

| Token | Duration | Easing |
|---|---|---|
| quick | 150 ms | cubic-bezier(0.2, 0, 0, 1) |
| default | 250 ms | cubic-bezier(0.2, 0, 0, 1) |
| emphatic | 350 ms | cubic-bezier(0.3, 0, 0.2, 1) |
| spring-pop | — | SpringForce stiffness=380 damping=0.75 |
| spring-snap | — | SpringForce stiffness=LOW damping=MEDIUM_BOUNCY |

### Corners, spacing, elevation

- Radius tokens: sm=8dp, md=12dp, lg=16dp, xl=20dp.
- Spacing scale: 4, 8, 12, 16, 20, 24, 32, 40.
- Elevation: we use translucent overlays + 1dp hairline borders instead of shadows — mirrors macOS.

### Translucency

On API 31+, use `RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)` behind surfaces that sit over content. Gracefully degrade to a solid color on earlier devices.

### Haptics

- Tap confirmation: `HapticFeedbackConstants.CONFIRM` (API 30+), fallback to `VIRTUAL_KEY`.
- Long-press voice start: `LONG_PRESS`.
- Snap landing: 8 ms one-shot `VibrationEffect.createOneShot(8, DEFAULT_AMPLITUDE)`.

---

## 12. Voice — must be flawless

### Flow

1. Long-press on widget → immediate haptic confirmation → `VoiceController.start()`.
2. `VoiceController` acquires audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), sets mode, and constructs a `SpeechRecognizer`:
   - **API 31+ and `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` is true** → `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)`.
   - **Otherwise** → `SpeechRecognizer.createSpeechRecognizer(context)`.
   `EXTRA_PREFER_OFFLINE` may be set on the `RecognizerIntent` as a hint, but correctness does not depend on the OS honouring it. `EXTRA_PARTIAL_RESULTS=true` is always set. All `SpeechRecognizer` calls (`startListening`, `stopListening`, `cancel`, `destroy`) run on the **main thread** — `VoiceController` confines itself to `Dispatchers.Main.immediate`.
3. Partial transcripts stream to the widget's label bubble (overlay) quickly; final transcript on release.
4. Release → stop recognizer → restore audio mode → release focus → `destroy()` the recognizer → submit final transcript through the normal chat pipeline with `fromVoice = true`. An empty release (no transcript) is a graceful no-op — never a stuck spinner.
5. Response streams back; if it has `[SPOKEN]…[/SPOKEN]`, TTS speaks the clamped spoken portion only (420-char TTS cap, 110-char overlay cap — the exact `PointParser` numbers).

The manifest includes a `<queries>` block listing `android.speech.RecognitionService` so the system recognizer resolves reliably on `targetSdk 30+`.

### Failure modes we test

- User releases before recognizer returns anything → send empty, show nothing.
- `isOnDeviceRecognitionAvailable(context)` returns false or on-device construction throws → fall back to `createSpeechRecognizer`; UI shows a small "online" badge so the user sees we are using server recognition.
- User denied mic permission after install → long-press shows a one-tap "Fix permissions" banner.
- Recognizer errors (no-match, network) → silently restart up to 2 times; after that, surface a friendly error.
- Widget drag while listening → cancel voice (Section 10).
- Bluetooth headset attached → route through headset mic.

**Every commit that touches audio ships with at least one instrumentation test covering a round-trip** — this is the "if speech touches code, ship tests with it" rule.

### Test matrix

Physical QA on three devices before v1 ship: Pixel 6a (stock), Samsung A54 (One UI), Motorola Moto G Power (near-stock). All three have different mic subsystems and recognizer behaviors.

---

## 13. Web search — built, off, behind a flag

v1 ships the full `WebSearchService` port (Brave + Jina + GitHub) but with `AppSettings.webSearchEnabled = false` default. The toggle lives in Settings → Brain. The LLM does not receive the tool list unless the flag is on.

We build it now because it is cheap to do alongside the rest of the networking layer and we do not want to retrofit the tool protocol later. We leave it off because v1 quality matters more than v1 feature breadth — we want to evaluate vision/text/intent paths independently before adding search-calls-to-Claude.

---

## 14. Debugging protocol — `DEBUG_LOG.md`

Reuse the macOS `debugging.mdc` protocol character-for-character, retargeted at Kotlin.

### Location

`DEBUG_LOG.md` at the Android project root. Created on the first bug fix after v1 ships green.

### Rules

1. Every bug fix — build error, crash, ANR, logic bug, warning — appends one entry before telling the user "done."
2. Sequential numbering: DL-001, DL-002, ….
3. Append-only. Never delete or edit older entries. Corrections append a new entry referencing the old one.
4. Before writing code, skim `DEBUG_LOG.md` for Prevention Rules that apply.

### Entry template

```markdown
### DL-XXX — Short Title

| Field | Value |
|-------|-------|
| **Date** | YYYY-MM-DD |
| **Severity** | Build Error / Runtime Crash / ANR / Logic Bug / Warning |
| **File(s)** | `path/to/file.kt` |
| **Symptom** | What the user saw or what failed |
| **Root Cause** | Why it happened |
| **Fix** | What was changed |
| **Prevention Rule** | How to avoid this in the future |
```

This protocol is also mirrored into the Cursor rules (see the prompt document).

---

## 15. Performance budget

Hard targets enforced in CI where possible and in QA otherwise.

| Metric | Budget | How measured |
|---|---|---|
| Cold start → widget visible | ≤ 800 ms | Macrobenchmark |
| Widget tap → chat first frame | ≤ 250 ms | `reportFullyDrawn()` |
| Long-press → first partial transcript | ≤ 400 ms | Timestamp logs |
| Chat token → visible delta | ≤ 50 ms | Frame timing |
| JPEG encode 1024-edge screenshot | ≤ 40 ms | Unit perf test |
| Memory footprint idle | ≤ 110 MB | `dumpsys meminfo` |
| APK size | ≤ 25 MB | R8 + resource shrinking |

Enable baseline profiles for `ChatActivity` and the floating-widget overlay service. R8 full mode on release.

Additional hardening, enforced at build / run time:

- **StrictMode in debug builds.** `StrictMode.ThreadPolicy` with `detectAll().penaltyLog().penaltyDeath()` and `StrictMode.VmPolicy` with `detectAll().penaltyLog()` are installed in `HandyApplication.onCreate` under `BuildConfig.DEBUG`. Debug builds crash on main-thread disk/network and on leaked closables so these regressions surface during development rather than in production logs.
- **No main-thread I/O.** Network, disk, Accessibility tree walks, and JSON parsing all run on `Dispatchers.IO` or `Dispatchers.Default`.
- **No bitmap or JSON-heavy work on the main thread.** Screenshot compression, history reads/writes, and streaming JSON decode all run off-main.

---

## 16. Testing strategy

### Unit (`:core`, JVM)

- `PointParser` round-trip on golden samples.
- `UmbrellaSiteLabels` on a large URL fixture (copied from macOS tests).
- SSE parser on canned Claude + canned Gemini responses.
- `ScreenInputRouter` heuristic on 30 sample prompts.
- `IntentCallParser` on LLM tool-use JSON fixtures.
- `LlmClient` contract test (runs against `ClaudeLlmClient` in v1, will run against `GeminiLlmClient` in v2).

### Instrumented (`:android-runtime`, `:app`)

- `EncryptedKeyStore` save/load/delete.
- `JsonHistoryStore` per-tool segregation, 100-turn cap.
- Widget gesture machine: tap, long-press, drag, snap — `ComposeTestRule` + `UiAutomator` for the overlay.
- Chat streaming end-to-end with a mocked `LlmClient`.
- Permission onboarding happy path.
- **OS-1**: `AssistantForegroundService` starts successfully on an API 35 emulator image with the declared `foregroundServiceType`, plus a smoke run on an API 36 image. A separate test asserts that when the widget overlay is not attached, the app does not attempt to rely on the `SYSTEM_ALERT_WINDOW` background-start exemption.
- **OS-2**: launch `ChatActivity` via the widget-tap path, type a known string into the chat input, assert the committed text matches; close `ChatActivity` and assert the widget overlay's `params.flags and FLAG_NOT_FOCUSABLE != 0` (the idle flag set remains intact). A second assertion forbids the existence of any overlay other than the floating widget (no `ChatPanelOverlayService`).
- **OS-3**: `ScreenCapturePipeline` takes the `takeScreenshotOfWindow` branch on a simulated API 34+ runtime, the `takeScreenshot` branch on a simulated API 31 runtime, and the `MediaProjection` branch on a simulated API 28 runtime (use `ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", n)` in Robolectric for unit-level coverage; use three emulator targets for a true connected test). A further test cancels the `MediaProjection` session and asserts the virtual display + surface are released synchronously.
- **OS-4**: `OverlayComposeHost` survives 10 mount/unmount cycles with no Compose lifecycle errors in logcat; `ViewModelStoreOwner`, `SavedStateRegistryOwner`, and `LifecycleOwner` are all present on the root view before `setContent { }` is invoked.
- **OS-5**: a mocked `CaptureResult.SecureWindow` from the pipeline causes the orchestrator to inject the "I can't see this screen" system message without invoking the mocked `LlmClient`. A second test covers the MediaProjection black-frame path on a simulated `FLAG_SECURE` window.

### QA script (manual, before every release)

One engineer runs through the script on all three QA devices:

1. Fresh install → onboarding end-to-end.
2. Tap widget → chat opens → ask "hi" → streams back.
3. Drag widget across screen → snap → reopen app → widget in saved position.
4. Long-press widget → say "what time is it" → final response speaks (if TTS on).
5. Say "set a 10-minute timer" → timer opens in Clock app.
6. Open Gmail email → ask "what does this email say" → response arrives without a visible screenshot flicker (text path).
7. Open Figma mobile → ask "point at the share button" → pointer flies to share button.
8. Toggle web search on → ask "latest Compose version" → Handy cites a source.

Any failure = blocker. Not ship-worthy.

---

## 17. Rollout plan

| Phase | What | Exit criteria |
|---|---|---|
| Week 1 | `:core` scaffolding, LlmClient interface + ClaudeLlmClient, prompts ported, parsers ported, contract test green | Unit tests pass on JVM |
| Week 2 | Foreground service, overlay widget, gesture machine, edge snap, settings persistence | Widget is draggable + snaps; gestures logged |
| Week 3 | Chat activity, streaming renderer, per-tool history, onboarding flow | End-to-end typed chat works |
| Week 4 | Screen capture (MediaProjection + Accessibility), `[POINT]` semantic resolver, pointer overlay | Pointing demo works |
| Week 5 | Screen-text extraction, ScreenInputRouter, system-prompt addendum | Gmail "what does it say" works text-only |
| Week 6 | Voice: long-press → start, partial transcripts, release → submit, TTS reply | Three-device QA clean |
| Week 7 | Intent dispatcher + starter catalogue of 9 actions + `dispatch_action` tool wiring | "Set 10-min timer" works |
| Week 8 | Polish: theming, haptics, motion tokens, accessibility onboarding, error UX, R8 | Performance budget met |
| Week 9 | Beta on real users (dogfood ~20); fix bugs into DEBUG_LOG.md | ≤ 2 open P1 bugs |
| Week 10 | Release candidate + Play Store listing + privacy policy | Signed, uploaded |

V2 work begins only after v1 is shipped and the DEBUG_LOG has ≤ 1 week of silence.

---

## 18. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Google flags accessibility use in Play review | Declare clearly: "Handy uses accessibility to read on-screen text and point at UI elements on the user's behalf, at the user's request." Provide video demo in Play Console. Do not declare tap-for-me until v2 (which may require additional review). |
| `SpeechRecognizer` cap on continuous recognition | Long-press is bounded (typical < 15s) — well under Google's limits. For longer sessions (tutor mode v2), plan for Whisper on-device. |
| Overlay window blocked by some OEMs on background apps | Foreground service + persistent notification is the documented pattern. Include OEM-specific guidance ("enable autostart on Xiaomi"). |
| Main-thread jank during screen-capture compression | Always on `Dispatchers.Default`; JPEG encode off the UI thread; pass bytes, not bitmaps. |
| LLM hallucinates screen contents when only text is sent | System prompt says "do not hallucinate UI not listed." ScreenInputRouter prefers Both when in doubt. |
| Provider swap (Claude → Gemini) regresses quality | Contract tests + snapshot tests on representative prompts. Keep Claude as default until regressions are resolved. |

---

## 18a. Play & Policy deliverables (treat as must-ship, not later-risk)

Play Store review for Accessibility-using apps has become strict.
v1 does not ship until each item below is in place. Each one is a
concrete artifact — not an intention — that lives in the repo or in
the Play Console draft.

- **Prominent in-app disclosure** for Accessibility Service use. Shown
  on first launch, in plain language, before the Accessibility
  deep-link. States what Handy reads on screen, what it does not,
  and links to the privacy policy. Not dismissable until acknowledged.
- **Explicit consent step** before the Accessibility settings
  deep-link is opened. The user must be able to decline and use a
  reduced mode with the toggle off (chat + voice still work; screen
  reading and semantic pointing do not).
- **Play Console Accessibility / permissions declaration** drafted
  against the actual v1 behaviour — no tap-for-me, no autonomous
  control; clearly describe read + pointing + intent dispatch.
- **Review artifact list** committed to the repo (video-free
  equivalents where Play requires uploads): screen recording of the
  consent flow; a canonical chat + pointing session; the
  Accessibility-off fallback experience.
- **Privacy policy** whose wording matches actual behaviour: what
  leaves the device (Claude API traffic, optional search API
  traffic), what doesn't (raw screenshots, accessibility tree
  content), retention (none on our side beyond transient session
  memory), and which third-party keys the user may configure.
- **Marketing copy discipline.** The Play listing, README, and any
  blog post never position Handy as an unrestricted autonomous
  controller of the phone. v1 is a read + point + dispatch
  assistant. That is what we claim and what we test.

A Phase 4 acceptance check re-reads this list and confirms every
item has a tangible artifact before the release candidate is built.

---

## 19. Summary

v1 is a focused, high-polish Android product. Three architectural bets power it and its future:

1. **`LlmClient` interface** — brains are a swap. Claude today, Gemini tomorrow, whatever ships next year.
2. **Screen reading via Accessibility** — not every query needs vision. We route cheaply when we can.
3. **Intent dispatch as a first-class tool** — when the user asks "start a timer," Handy *does*. It doesn't narrate.

Every v2 feature you listed (tap-for-me, richer intents, provider swap, richer screen reading) is an implementation drop-in, not a refactor. That is the entire point of this plan.

The Cursor prompt is in `Handy_Android_Cursor_Prompt_v2.md`. It references this document as required reading and enforces the Karpathy skills + the DEBUG_LOG protocol as Cursor rules.
