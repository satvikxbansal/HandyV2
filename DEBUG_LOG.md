# Handy Android — DEBUG_LOG.md

Every bug fix from this point forward appends an entry **in the same
commit as the fix**, **before** the user is told the fix is complete.
Rules, template, and prevention-rule quality bar are in
`.cursor/rules/20-debug-log.mdc`.

Entries are append-only and sequential (DL-000, DL-001, DL-002, …). Do
not delete or edit an older entry; corrections append a new entry that
cross-references the one being superseded.

---

### DL-000 — Initial v1 green build

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Severity** | Informational |
| **File(s)** | (scaffold) |
| **Symptom** | — |
| **Root Cause** | — |
| **Fix** | Phase 0 → Phase 3 scaffold committed: module tree, Gradle toolchain (AGP 8.7.3, Kotlin 2.0.21, Gradle 8.10.2, JDK 17), `:core` pure-Kotlin domain (prompts, parsers, orchestrator, settings, interfaces), `:android-runtime` adapters (Claude SSE, STT on-device-first, TTS chunker, capture pipeline with OS-3 tiered branching, tree reader, pointer resolver, intent dispatcher + `LaunchableAppIndex`, `JsonHistoryStore`, `EncryptedKeyStore`, DataStoreSettings, `NoopActionPerformer`), and `:app` (all manifest components, `OverlayComposeHost`, gesture state machine, `ChatActivity`, `SettingsActivity`, `OnboardingActivity`, Apple-class theme). |
| **Prevention Rule** | Every subsequent DL-XXX entry lands in the same commit as its fix. Never tell the user "done" until the DL entry is written. |

---

### DL-001 — Gradle sync fails with "No locally installed toolchains match"

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Tags** | `#android #BuildToolchain #Gradle #JDK` |
| **Severity** | Build Error |
| **Environment** | macOS 15 / darwin 25.4.0 aarch64 • Android Studio (user install, first-run, no standalone JDK on PATH) • AGP 8.7.3 • Gradle 8.10.2 • Kotlin 2.0.21 |
| **File(s)** | `settings.gradle.kts`, `core/build.gradle.kts`, `android-runtime/build.gradle.kts`, `app/build.gradle.kts` |
| **Symptom** | Gradle sync fails with 4 errors + 1 warning on first open. Every error chain terminates in `org.gradle.jvm.toolchain.internal.NoToolchainAvailableException: Cannot find a Java installation on your machine matching this tasks requirements: {languageVersion=17, vendor=any vendor, implementation=vendor-specific} for MAC_OS on aarch64.` followed by `ToolchainDownloadFailedException: No locally installed toolchains match and toolchain download repositories have not been configured.` IDE reports `Project source sets cannot be resolved` and `Could not resolve all dependencies for configuration ':core:compileClasspath'`. |
| **Root Cause Category** | Config Drift |
| **Root Cause Context** | The original `settings.gradle.kts` didn't apply the `org.gradle.toolchains.foojay-resolver-convention` plugin. Every module declares `jvmToolchain(17)` (and `:core` additionally declares `java.toolchain.languageVersion.set(17)`), which is a Gradle **task-level** requirement — distinct from the JDK that runs Gradle itself. Android Studio's bundled JBR satisfies "run Gradle" but not "provide a JDK 17 toolchain for `:core:compileJava`." Without a provisioner plugin, Gradle can neither discover a matching JDK nor download one, so every compile task short-circuits before classpath resolution. The first confusion was conflating "Gradle JDK" (runtime) with "toolchain JDK" (per-task); they are configured in different places and serve different purposes. |
| **Fix** | Applied `id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"` in the top-level `plugins { }` block of `settings.gradle.kts`. The plugin auto-downloads an Adoptium JDK 17 into Gradle's user home on first sync and caches it for all subsequent builds. Module `build.gradle.kts` files intentionally unchanged — their `jvmToolchain(17)` declaration is the correct way to express the requirement, and Foojay is the correct way to satisfy it. |
| **Iterations** | 1 |
| **Prevention Rule** | When a Gradle module declares `jvmToolchain(N)` / `java.toolchain.languageVersion.set(N)`, `settings.gradle.kts` **must** also apply `id("org.gradle.toolchains.foojay-resolver-convention")` so the toolchain is provisionable on any machine that doesn't happen to have that JDK installed. **Why:** without the resolver, `NoToolchainAvailableException` fails sync on every fresh checkout / CI runner / dev with only Android Studio installed (DL-001). Do not "fix" this by removing the toolchain requirement — `jvmToolchain(N)` is what makes the build reproducible across JDK versions in the first place. |

---

### DL-002 — `StrictMode.penaltyDeath()` kills the app on `ChatActivity` launch

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Tags** | `#android #StrictMode #Hilt #EncryptedSharedPreferences` |
| **Severity** | Runtime Crash |
| **Environment** | Android API 35 (Pixel 7 emulator) • Kotlin 2.0.21 • AGP 8.7.3 • Hilt 2.53 • androidx.security-crypto 1.1.0-alpha06 • `BuildConfig.DEBUG = true` |
| **File(s)** | `app/src/main/kotlin/com/handy/app/HandyApplication.kt` |
| **Symptom** | Tapping "Open Handy" in onboarding (or tapping the floating widget) flashes the chat screen for <100ms, then the process dies silently with no user-visible stack. Re-launch returns the user to the onboarding screen ("Handy needs a few permissions") every time, creating the illusion that permissions aren't persisting. |
| **Root Cause Category** | Config Drift |
| **Root Cause Context** | `HandyApplication.installStrictMode()` used `StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().penaltyDeath()`. When `ChatActivity` is created, Hilt lazily constructs the `SingletonComponent` graph needed by `ChatViewModel`: `ClaudeLlmClient` → `EncryptedKeyStore` (which calls `EncryptedSharedPreferences.create(...)` → reads the Keystore master key from disk on the main thread) and `JsonHistoryStore` (whose constructor calls `File(filesDir, "handy/history").mkdirs()` on the main thread). Both are legitimate main-thread disk reads that `detectAll()` flags — and `penaltyDeath()` escalates that flag to `SIGTERM` on the process. The misconception was treating `penaltyDeath()` as "extra safety in debug"; in practice it's a policy that only makes sense once every singleton's constructor is provably off-main, which is not yet true. |
| **Fix** | Removed `.penaltyDeath()` from the thread policy; the policy still emits `penaltyLog()` so violations show up in logcat for future refactoring. Class-level doc in `HandyApplication.kt` updated to explain the trade-off and when death can be re-enabled. |
| **Iterations** | 1 |
| **Prevention Rule** | In `Application.onCreate`, set `StrictMode` to `penaltyLog()` only until every singleton in the Hilt graph is proven off-main (documented or covered by a lint check). Never combine `detectAll()` with `penaltyDeath()` while any `EncryptedSharedPreferences`, `SharedPreferences`, `File(...).mkdirs()`, or `Room` call runs on the main thread — Hilt constructs singletons lazily on the thread of the first injection site, which is almost always the main thread. **Why:** `penaltyDeath` turns every legitimate-but-unoptimised disk read into a process kill, and Hilt's lazy instantiation makes it indistinguishable from a user-facing crash (DL-002). |

---

### DL-003 — Foreground service crashes with `FOREGROUND_SERVICE_TYPE_MICROPHONE` before `RECORD_AUDIO` is granted

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Tags** | `#android #ForegroundService #Permissions #ApiLevel14` |
| **Severity** | Runtime Crash |
| **Environment** | Android API 35 • targetSdk 35 • `AssistantForegroundService` declared as `foregroundServiceType="specialUse|microphone"` |
| **File(s)** | `app/src/main/kotlin/com/handy/app/service/AssistantForegroundService.kt` |
| **Symptom** | On "Open Handy", when onboarding has NOT already granted `RECORD_AUDIO`, `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE or FOREGROUND_SERVICE_TYPE_MICROPHONE)` throws `android.app.ForegroundServiceStartNotAllowedException: Starting FGS with type microphone ... requires permissions: RECORD_AUDIO`. Kills the hosting process. Secondary: we also called `ContextCompat.startForegroundService(…, FloatingWidgetOverlayService)` on a service that never calls `startForeground(…)`, so Android's 5-second rule fires and kills the widget service independently. |
| **Root Cause Category** | API Change |
| **Root Cause Context** | Android 14 (API 34) added a rule: declaring `FOREGROUND_SERVICE_TYPE_MICROPHONE` at `startForeground()` time requires the `RECORD_AUDIO` runtime permission to already be granted, even if you're not recording yet. We requested the microphone FGS type up-front at lifecycle-anchor start, well before voice capture ever runs — so it tripped on fresh installs / reduced-mode users who haven't granted the mic. The second half of the bug (`startForegroundService` on a service that never foregrounds) was a mis-used API — `startForegroundService` promises to become foreground within 5s, and we had no intent of doing so for the widget overlay. |
| **Fix** | `AssistantForegroundService.onStartCommand` now starts with only `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`; the microphone type is reserved for a future upgrade call that the voice controller will issue on push-to-talk start (at which point `RECORD_AUDIO` is guaranteed granted). Widget overlay service is now started via regular `startService(…)` — it lives inside the already-foregrounded parent's process. `Timber.w` wraps both calls so a bad launch degrades instead of crashing. |
| **Iterations** | 1 |
| **Prevention Rule** | Never declare `FOREGROUND_SERVICE_TYPE_MICROPHONE` / `_CAMERA` / `_LOCATION` / `_HEALTH` in the `startForeground(…)` bitmask until the runtime permission that type requires is **currently granted**. Upgrade the FGS type by re-calling `startForeground(id, notification, newType)` the moment the session that needs it begins, and downgrade when it ends. Pair it with: any service started via `Context.startForegroundService(…)` **must** call `Service.startForeground(…)` within 5 seconds; if you don't intend to, start it with `startService(…)` instead. **Why:** mismatches on either end throw unrecoverable exceptions on API 34+ (DL-003). |

---

### DL-004 — Second floating icon: Android Accessibility shortcut bubble triggered by wrong service flags

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Tags** | `#android #AccessibilityService #Overlay` |
| **Severity** | Logic Bug |
| **File(s)** | `app/src/main/res/xml/accessibility_service_config.xml` |
| **Symptom** | After enabling Handy's Accessibility service, a second greyed Android-robot icon appears floating on the screen alongside Handy's orange widget. Tapping it opens a system panel, not Handy. |
| **Root Cause Category** | Config Drift |
| **Root Cause Context** | The XML config declared `accessibilityFlags="flagRequestFilterKeyEvents \| flagReportViewIds \| flagRetrieveInteractiveWindows \| flagRequestTouchExplorationMode \| flagIncludeNotImportantViews"`. Two of those are inappropriate for Handy: (a) `flagRequestTouchExplorationMode` is reserved for TalkBack-style services that intercept all touches to drive a screen reader — setting it makes Android surface its Accessibility-shortcut bubble system-wide so users can toggle the service fast; (b) `flagRequestFilterKeyEvents` is nonsensical when paired with `canRequestFilterKeyEvents="false"`. Handy reads text, points, and dispatches intents — it has no business requesting either flag. |
| **Fix** | `accessibility_service_config.xml` now declares only `flagReportViewIds \| flagRetrieveInteractiveWindows \| flagIncludeNotImportantViews`. Those three are the minimum needed for `getWindows()` on API 30+, `takeScreenshotOfWindow(…)` on API 34+, and viewId-suffix matching in `SemanticPointerResolver`. The config's header comment now enumerates which flags are intentionally NOT set and why. |
| **Iterations** | 1 |
| **Prevention Rule** | When writing an `accessibility-service` XML config, include only flags whose behaviour Handy actually needs. Never set `flagRequestTouchExplorationMode` unless the service is a screen reader; never set `flagRequestFilterKeyEvents` / `flagRequestAccessibilityButton` without matching the corresponding `canRequest…="true"`. **Why:** Android treats those flags as "the user needs a system-level shortcut to control this service" and overlays a persistent bubble on top of every app, which looks to users like a bug in ours (DL-004). |

---

### DL-005 — Onboarding shows "Allow" every launch because it never reads real system state

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Tags** | `#android #Onboarding #Permissions #StateSync` |
| **Severity** | Logic Bug |
| **File(s)** | `app/src/main/kotlin/com/handy/app/onboarding/OnboardingViewModel.kt`, `app/src/main/kotlin/com/handy/app/onboarding/OnboardingActivity.kt` |
| **Symptom** | Every app launch lands on the "Handy needs a few permissions" screen with every step reset to "Allow" / "Open settings", even when the user granted all of them on a previous run. Compounds with DL-002/003 — when the app crashes on "Open Handy", the relaunch re-starts onboarding, making the permission loop feel inescapable. |
| **Root Cause Category** | Logic Error |
| **Root Cause Context** | The original `OnboardingViewModel` tracked permission state purely as in-memory `Boolean` fields that defaulted to `false` and were only set when the user tapped the in-app buttons. Actual system state — `ContextCompat.checkSelfPermission`, `Settings.canDrawOverlays`, `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` — was never queried. Result: the checklist was a record of "what the user clicked this session", not "what Android currently permits". A launch after permissions had already been granted showed identical UI to a fresh install. There was also no auto-forward to `ChatActivity` when everything was already set up. |
| **Fix** | `OnboardingViewModel` is now an `AndroidViewModel`, and `refreshFromSystem()` reads `RECORD_AUDIO`, `POST_NOTIFICATIONS` (on API 33+), `Settings.canDrawOverlays`, and the `ENABLED_ACCESSIBILITY_SERVICES` string for `HandyAccessibilityService`. `OnboardingActivity.onResume` calls `refreshFromSystem()` so state stays correct after the user bounces back from Settings. Added `OnboardingUiState.minimallyReady`; when it's true, a `LaunchedEffect` in the composable short-circuits straight to `ChatActivity`. Added an explicit `POST_NOTIFICATIONS` step to the checklist (Android 13+). `OnboardingActivity.goToChat(…)` only starts `AssistantForegroundService` when overlay + notifications are actually granted. |
| **Iterations** | 1 |
| **Prevention Rule** | Any onboarding / permission-gating screen **must** derive its UI state from real system queries (`checkSelfPermission`, `canDrawOverlays`, `ENABLED_ACCESSIBILITY_SERVICES`), not from in-memory session flags, and must refresh in `onResume` (or the equivalent Compose `LifecycleEventObserver`). Persist "disclosure acknowledged" to DataStore so it isn't re-shown, but **never** persist permission-grant booleans — always read them fresh from the OS. Pair it with: when every requirement is already satisfied, auto-forward and `finish()` the onboarding activity so it doesn't become a modal wall. **Why:** permission state can change any time (user revokes, app updated, device transferred) and session flags will disagree with reality (DL-005). |

---

### DL-006 — API-key field rejects paste (no explicit Paste affordance, unreliable long-press on emulator)

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Tags** | `#android #Compose #UX #TextField #Clipboard` |
| **Severity** | Logic Bug |
| **Environment** | Compose BOM 2024.12.01 • Android Emulator API 35 (Pixel 7) • host-clipboard passthrough default |
| **File(s)** | `app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt` |
| **Symptom** | User tried to paste a Claude API key into the Settings → Claude API key field, using the emulator's soft-keyboard long-press. No paste menu appeared, and the host-clipboard passthrough did not surface the Mac clipboard text either. With no other affordance, the user couldn't enter the key — effectively blocking the entire chat flow. |
| **Root Cause Category** | Logic Error |
| **Root Cause Context** | The `PasswordField` composable used `OutlinedTextField` with `PasswordVisualTransformation()` and no `keyboardType` / `singleLine` / trailing-icon affordances. Two problems stacked: (a) `PasswordVisualTransformation` makes the Android IME suppress the selection / paste popup on long-press in some OEM keyboards including the emulator's Gboard variant; (b) the emulator's host-clipboard bridge is best-effort and routinely drops content on first focus. Without an explicit Paste button, the user has no reliable path. There was no "show key" toggle either, so even if they got value in, they couldn't visually verify it was correct. |
| **Fix** | `PasswordField` now renders a trailing-icon `Row` with two `IconButton`s: (1) **Paste** — reads from `LocalClipboardManager.current.getText()?.text` and populates the field directly, bypassing the IME path entirely; (2) **Show/Hide** — toggles between `PasswordVisualTransformation` and `VisualTransformation.None` so the user can verify the key before tapping Save. The field is now `singleLine = true` with `KeyboardType.Password`, `autoCorrectEnabled = false`, and `imeAction = Done`. |
| **Iterations** | 1 |
| **Prevention Rule** | Every Compose text field that accepts a pasteable credential (API key, OTP, token, password) **must** expose an explicit in-field **Paste** IconButton backed by `LocalClipboardManager`, in addition to the normal long-press affordance. Pair it with a show/hide toggle when the field uses `PasswordVisualTransformation`. **Why:** long-press paste is unreliable on emulators + some Gboard variants + some IMEs (DL-006), and masked fields without a visibility toggle leave the user unable to verify what they entered. |

---

### DL-007 — "Save" on API-key field gives no confirmation, making users think it failed

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Tags** | `#android #Compose #UX #Feedback #Snackbar` |
| **Severity** | Logic Bug |
| **Environment** | Compose BOM 2024.12.01 • Material3 • Android API 35 |
| **File(s)** | `app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt`, `app/src/main/kotlin/com/handy/app/settings/SettingsViewModel.kt` |
| **Symptom** | User pastes a Claude API key, taps Save, and the field appears to "go blank" — no toast, no banner, no visible state change other than the masked placeholder that looks identical to an unfilled placeholder. User asks "is it actually saving?" with no way to tell from the UI. |
| **Root Cause Category** | Logic Error |
| **Root Cause Context** | Two overlapping gaps: (a) `SettingsViewModel.setKey(...)` quietly wrote to `EncryptedSharedPreferences` with no observable side effect beyond an update to `claudeKeyMasked` — which was only rendered as the field's *placeholder*, visually indistinguishable from "you haven't typed anything yet"; (b) `PasswordField` correctly scrubs the raw value from its local `remember { mutableStateOf }` after commit (security-first — never retain plaintext credentials in UI state), but combined with (a) the result looked exactly like a no-op. There was also no way to clear a previously-saved key if it was wrong. |
| **Fix** | `SettingsViewModel` now exposes a `MutableSharedFlow<String> messages` (replay=0, drop-oldest buffer) and `setKey` emits a string like "Claude API key saved" / "Claude API key cleared" after committing. `SettingsActivity.onCreate` collects the flow in a `LaunchedEffect` and calls `SnackbarHostState.showSnackbar(...)`. The `PasswordField` composable has been renamed / reworked into `CredentialField`, which (a) renders a persistent "✓ Saved — sk-••••XXXX" `SavedBadge` above the text field whenever a key is on disk, (b) disables the Save button when the text field is empty, (c) renames Save → Update when a key is already present, and (d) exposes an additional "Remove" TextButton that commits an empty string (which the VM interprets as `KeyStore.remove`). |
| **Iterations** | 1 |
| **Prevention Rule** | Any UI action that writes to persistent storage **must** produce a visible confirmation — either a Snackbar, an inline "Saved" chip, or both — and the confirmation **must** be derived from an observable VM event (a SharedFlow with `replay=0`, not a StateFlow reset flag) so rotations do not replay the toast. Pair it with: credential fields that wipe their raw-value state after commit **must** render a separate, unambiguous "saved on disk" badge (masked preview, icon, distinct from the empty placeholder). **Why:** without explicit confirmation the user cannot tell a successful save from a silent no-op, which is especially damaging for credentials where the next action (sending a chat) fails opaquely if the key wasn't actually stored (DL-007). |

---

### DL-008 — Voice long-press produces no transcript: race between flow completion and stopAndAwaitFinal

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Tags** | `#android #STT #SpeechRecognizer #Coroutines #RaceCondition` |
| **Severity** | Logic Bug |
| **Environment** | Android API 35 Emulator (Pixel 7) • Compose BOM 2024.12.01 • SpeechRecognizer via Google cloud fallback |
| **File(s)** | `app/src/main/kotlin/com/handy/app/voice/VoiceController.kt` |
| **Symptom** | User long-presses the widget, speaks, releases — the widget briefly shows THINKING then returns to IDLE, but `ChatActivity` never receives a voice message. Logcat shows "Voice session produced no transcript" every time. |
| **Root Cause Category** | Race Condition |
| **Root Cause Context** | `VoiceController.start()` launched a coroutine that collected the `SttClient.listen()` flow and, **at the end of the launch block**, set `_state.value = State.IDLE`. On the Android emulator, `SpeechRecognizer` often errors immediately (ERROR_CLIENT when the Google app isn't properly initialised, or ERROR_NO_MATCH after a short silence timeout) — the flow emits `SttEvent.Error`, closes, the collect returns, and the launch block sets state to IDLE. All of this happens within ~200ms — well before the user's 1–2s hold-and-release. By the time `ACTION_UP` fires `stopAndAwaitFinal()`, that method checked `if (_state.value != State.LISTENING)` and short-circuited to `return null`, discarding whatever partial or final transcript had been buffered. Even when the recognizer *does* produce a Final, the state-reset race remains because the launch block is on `Dispatchers.Main.immediate` and runs immediately after the flow closes. |
| **Fix** | Removed the `_state.value = State.IDLE` line from the end of the collector launch block. State is now ONLY reset by `stopAndAwaitFinal()` or `cancel()` — the two explicit lifecycle methods — never by flow completion. `stopAndAwaitFinal` no longer bails when `state == IDLE`; it bails only when `collectJob == null` (meaning `start()` was never called). This decouples the "recognizer is done capturing" event from the "user released their finger" event. Also bumped the grace period from 1500ms to 2000ms, and added comprehensive `Timber.d` logging on every STT event and every lifecycle method so the next voice issue is diagnosable from a logcat paste alone. |
| **Iterations** | 1 |
| **Prevention Rule** | In a push-to-talk flow where a background producer (speech recognizer) races against a user gesture (finger-release), **never** let the producer's completion reset shared state that the gesture handler reads. The gesture handler owns the state transition; the producer writes to buffers only. Check for "never started" by testing whether the producer's `Job` reference is null, not by reading a state enum that may have been reset behind your back. **Why:** the recognizer can error or complete in <200ms, well before a typical 1–2s hold, turning every transcript into null (DL-008). |

---

### DL-009 — Chat surface parity with V1 `MessageBubbleView`

| Field | Value |
|-------|-------|
| **Date** | 2026-04-23 |
| **Tags** | `#android #ChatUI #Compose #ParityGap` |
| **Severity** | Logic Bug / UX Gap |
| **Environment** | Compose BOM 2024.12.01 • `:app` chat surface |
| **File(s)** | `app/src/main/kotlin/com/handy/app/chat/ChatActivity.kt`, `app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt` |
| **Symptom** | Every v1 chat turn rendered as a flat right- or left-aligned rectangle. No hand-icon avatar on the assistant side, no "You" pill on the user side, no per-bubble `h:mm a` timestamp, no 3 pulsing dots while the stream was live, no italic "web searched · github searched" caption above bubbles that had used tools. The header was the stock `TopAppBar` with only a Settings cog — no bold "Handy" label, no coloured status dot, no listening indicator. The tool-name bar that macOS uses to show the currently-attached app ("Gmail", "GitHub"…) was missing entirely. The loading strip emitted exactly one random verb at the start of a turn and never rotated. When the LLM errored, the half-finished streaming row was silently discarded — the user saw the spinner stop but no explanation. |
| **Root Cause Category** | Scope Gap |
| **Root Cause Context** | The Phase-3 chat scaffold used Material3 defaults (`TopAppBar`, flat `Surface` bubbles, `LoadingVerbChip` wired to a single event) as placeholders while the data model and orchestrator were being ported. The rendering contract from `Handy V1 (macOS app)/Handy/Views/ChatInterfaceView.swift` (avatars, streaming dots, italic tool label, status dot with halo, 3-bar listening animation, tool-name bar with editable override, rotating verbs every 2.5 s, error-row finalisation to "(response failed)") was deferred. DL-009 closes that gap without touching the orchestrator, LLM client, or data model — the existing `ChatMessage.searchToolsUsed`, `isStreaming`, and `timestampEpochMs` fields are finally rendered. `OrchestrationEvent.Error` now reconstructs a "failed turn" overlay (ASSISTANT bubble carrying the accumulated text with a "(response failed)" fallback, plus a `SYSTEM` bubble with the error) so nothing is silently swallowed. A per-turn rotation job in `ChatViewModel` replaces the single-shot `LoadingVerb` handling; it pauses when `loadingVerbFrozen` is true (Phase D will flip that flag when a tool call owns the strip). |
| **Fix** | Extended `ChatUiState` with `currentToolName`, `toolDetectionState`, `voiceState`, `pendingTranscript`, `localOverlay`, `loadingVerbFrozen`. Rebuilt `ChatActivity` with a custom `HandyHeaderBar` ("Handy" 20 sp extra-bold + `StatusDot` with pulsing halo + `ListeningBars` on listening), a new `ToolNameBar` (with IDLE / DETECTING / DETECTED / FAILED branches and an amber 3-dot trail on failure), a full-fidelity `MessageRow` (hand-icon avatar, "You" pill, italic `searchToolsLabel`, `StreamingDots`, selectable content via `SelectionContainer`, `h:mm a` timestamp), and a `ChatComposer` that swaps to a live-transcript `Text` whenever `voiceState == LISTENING`. The composer exposes `onVoiceStart` / `onVoiceStop` stubs that Phase B wires to `VoiceController`. `ChatViewModel.send` now drives a 2.5 s `verbRotationJob`; `OrchestrationEvent.Error` builds a two-bubble local overlay so failed turns render instead of vanishing. |
| **Iterations** | 1 |
| **Prevention Rule** | When you land a Phase-N scaffold that uses Material3 defaults for a screen whose visual contract is defined by an external spec (here: the macOS V1 app), file a same-commit DL entry listing every visual affordance in the spec that the scaffold omits, and link it to the parity ticket. The default-Material scaffold is not "done" even if it compiles and answers — "looks like V1 to a new user" is the acceptance criterion. Specifically: a `ChatMessage` model field (`searchToolsUsed`, `isStreaming`, `timestampEpochMs`) that is stored but never rendered is a silent contract violation; every persisted field must be either rendered or explicitly documented as "write-only for round-trip". **Why:** without this rule, the Phase-3 chat screen passed DL-000 acceptance while being unrecognisable as Handy (DL-009). |
