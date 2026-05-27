# P-STT2: Sarvam Saarika STT (opt-in cloud, Indic + Hinglish…

## Metadata

- Thread ID: `019e637b-939a-7da3-a3df-d70688a0b2af`
- Created: 2026-05-26 14:21:43 IST
- Updated: 2026-05-26 15:07:46 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/26/rollout-2026-05-26T14-21-43-019e637b-939a-7da3-a3df-d70688a0b2af.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `0101729ae72f4c296ad34a9bdcb897bd56bc7895`
- Messages exported: 41

## Brief Summary

This conversation focused on: Add an opt-in Sarvam Saarika v2 STT provider for high-quality Indic and code-mix Hinglish transcription. Cloud-only, gated by explicit user consent in settings. The original macOS Handy used Sarvam; this brings parity. Does NOT replace AndroidSttClient — adds… Likely related git changes: 0101729 P-STT1: STT mode, language, MAX_RESULTS=3, confidence, Andr…; 4744f20 P-STT2: Sarvam Saarika STT (opt-in cloud, Indic + Hinglish premium path).

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `0101729ae72f4c296ad34a9bdcb897bd56bc7895`
- Likely related commits:
  - `0101729` 2026-05-26 14:21:30 IST [medium] P-STT1: STT mode, language, MAX_RESULTS=3, confidence, Andr…. Files: DEBUG_LOG.md, android-runtime/build.gradle.kts, android-runtime/src/main/kotlin/com/handy/runtime/di/RuntimeModule.kt, android-runtime/src/main/kotlin/com/handy/runtime/speech/AndroidSttClient.kt, android-runtime/src/main/kotlin/com/handy/runtime/speech/AndroidTtsClient.kt, android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt, android-runtime/src/test/kotlin/com/handy/runtime/speech/AndroidSttClientTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/speech/TtsChunkerTest.kt, app/src/main/AndroidManifest.xml, app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt, app/src/main/kotlin/com/handy/app/diagnostics/DiagnosticsActivity.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt
  - `4744f20` 2026-05-26 15:08:55 IST [high] P-STT2: Sarvam Saarika STT (opt-in cloud, Indic + Hinglish premium path). Files: DEBUG_LOG.md, PRIVACY_POLICY.md, README.md, android-runtime/src/main/kotlin/com/handy/runtime/di/RuntimeModule.kt, android-runtime/src/main/kotlin/com/handy/runtime/speech/MicAudioRecorder.kt, android-runtime/src/main/kotlin/com/handy/runtime/speech/SarvamSttClient.kt, android-runtime/src/main/kotlin/com/handy/runtime/speech/SwitchingSttClient.kt, android-runtime/src/main/kotlin/com/handy/runtime/storage/DataStoreSettings.kt, android-runtime/src/test/kotlin/com/handy/runtime/speech/SarvamSttClientHttpTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/speech/SwitchingSttClientTest.kt, app/src/main/kotlin/com/handy/app/chat/ChatActivity.kt, app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt

## Conversation

### USER 2026-05-26T08:51:47.752Z

GOAL
Add an opt-in Sarvam Saarika v2 STT provider for high-quality Indic and
code-mix Hinglish transcription. Cloud-only, gated by explicit user
consent in settings. The original macOS Handy used Sarvam; this brings
parity. Does NOT replace AndroidSttClient — adds a switcher.

VERIFIED PRE-CONDITIONS
- SttClient interface accepts a Flow<SttEvent>. Suitable for a
  non-streaming HTTP STT if we batch one utterance per session
  (long-press → release → upload → emit Final).
- Sarvam Saarika v2 batch endpoint: POST https://api.sarvam.ai/speech-to-text
    multipart with `file` (audio) and `model=saarika:v2`,
    optional `language_code`, `with_timestamps=false`.
    Response: { "request_id", "transcript", "language_code", "diarized_transcript" }
    Streaming endpoint exists too (`speech-to-text-translate`) but for
    simplicity v1 of this provider does batch upload after release.

IMPLEMENTATION

1. Settings additions:
   File: core/src/main/kotlin/com/handy/core/model/Settings.kt
       enum class SttProvider {
           ANDROID,           // existing
           SARVAM_SAARIKA,    // new
       }
       (SttProvider already exists with ANDROID only — add SARVAM_SAARIKA.)

2. Audio recorder:
   File: android-runtime/src/main/kotlin/com/handy/runtime/speech/MicAudioRecorder.kt
   - Records raw PCM into a memory buffer (no disk) when the user holds
     long-press.
   - Encodes to WAV on stop.
   - 16kHz mono, 16-bit PCM (Sarvam preferred).
   - Enforces max session = 30s; refuses to upload anything longer.
   - Uses AudioRecord; respects RECORD_AUDIO permission already gated by
     VoiceController.

3. Implement SarvamSttClient:
   File: android-runtime/src/main/kotlin/com/handy/runtime/speech/SarvamSttClient.kt
       class SarvamSttClient @Inject constructor(
           private val recorder: MicAudioRecorder,
           private val keyStore: KeyStore,
           private val settings: DataStoreSettings,
           private val httpClient: OkHttpClient,
           private val json: Json,
       ) : SttClient {
           override val isOnDeviceAvailable: Boolean = false

           override fun listen(): Flow<SttEvent> = callbackFlow {
               val key = keyStore.get(EncryptedKeyStore.KEY_SARVAM)
                   ?: run {
                       trySend(SttEvent.Error("Add a Sarvam API key in Settings.", false))
                       close(); return@callbackFlow
                   }
               recorder.start()
               trySend(SttEvent.BeginningOfSpeech)
               awaitClose {
                   recorder.stop()
                   // Upload synchronously; can't stream partials with the batch endpoint.
                   uploadBlocking(key, recorder.consumeWavBytes())
                       .fold(
                           onSuccess = { tr -> trySend(SttEvent.Final(tr, isOnDevice = false)); close() },
                           onFailure = { e -> trySend(SttEvent.Error(e.message ?: "Sarvam STT failed", true)); close() },
                       )
               }
           }
           override fun stopListening() { /* signals awaitClose */ }
           override fun release() { recorder.release() }
       }
   - HTTP timeouts: 15s upload.
   - On 401: clear key + emit error.
   - The UX trade-off: Sarvam batch STT means no live partial transcripts.
     That's OK for short turns. Document in settings: "Sarvam transcribes
     after you release the press. No live preview."

4. SwitchingSttClient:
   File: android-runtime/src/main/kotlin/com/handy/runtime/speech/SwitchingSttClient.kt
   Same pattern as SwitchingTtsClient. Defaults to AndroidSttClient.
   Switches when:
       settings.sttProvider == SARVAM_SAARIKA && keyStore.get(KEY_SARVAM) != null

5. DI rewire:
   File: android-runtime/.../RuntimeModule.kt
   Change provideSttClient to return SwitchingSttClient with both arms.

6. Settings UI:
   File: app/src/main/kotlin/com/handy/app/settings/sections/BrainSection.kt
   Add provider picker (Android / Sarvam Saarika). When Sarvam picked:
   - Show the same Sarvam API key field as P-VOICE-2 (same key, same
     storage slot — reuse).
   - Caption: "Cloud transcription. Better Hindi and Hinglish; no live
     preview. Audio is sent to Sarvam."
   - First-time enable shows a one-tap consent sheet ("Send audio to
     Sarvam? Each session uploads up to ~30s of audio.").

PRIVACY
- Audio is never written to disk. The PCM buffer is freed after upload.
- Audit log records provider=sarvam-saarika, language, audio_ms,
  latency_ms, success/fail — never the transcript or the audio.

ACCEPTANCE
- ANDROID provider + AndroidSttClient continue working unchanged.
- SARVAM_SAARIKA + key + consent → long-press → release → spinner →
  transcript appears, then submits.
- SARVAM_SAARIKA + no key → friendly settings prompt, no upload.
- SARVAM_SAARIKA + airplane mode → graceful failure ("Sarvam needs
  internet — switch to Android STT or reconnect").
- Cancelling mid-press (no release) → no upload.
- Sessions > 30s are truncated; user sees a chip "Cut off at 30s".

TESTS
- SarvamSttClientHttpTest (MockWebServer):
  - 200 + transcript → SttEvent.Final.
  - 401 → SttEvent.Error("auth_failed").
  - >30s recording → truncated upload, transcript reflects only first 30s.
- SwitchingSttClientTest:
  - settings flow flips → routing updates.
- Manual:
  - Hinglish utterance via Sarvam — transcript noticeably better than
    Android STT on the same device.
  - Switch back to Android — no regressions.

DO NOT
- Persist audio.
- Auto-enable Sarvam. Default stays ANDROID.
- Send audio if RECORD_AUDIO is not granted.
- Re-prompt for consent every session — once is enough; revoke via
  settings.

UPDATE
- DEBUG_LOG.md, README, privacy policy (Sarvam cloud STT disclosed).
Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS

### ASSISTANT 2026-05-26T08:51:57.219Z

I’ll wire this in end to end: first I’m going to map the existing STT, settings, DI, key storage, and TTS switcher patterns so the Sarvam path lands in the app’s own architecture instead of feeling bolted on.

### ASSISTANT 2026-05-26T08:52:12.810Z

I found the existing Sarvam TTS plumbing and the settings model already has Sarvam language/voice concepts, which is good news: STT can reuse the same encrypted key slot and settings idioms. Next I’m reading the voice lifecycle and switcher patterns so `stopListening()` matches how long-press release actually closes the flow.

### ASSISTANT 2026-05-26T08:53:26.699Z

There’s a small requirements tension in the prompt: one line says “refuses to upload anything longer,” while acceptance says “sessions >30s are truncated.” I’m going to implement the privacy-preserving version that records at most 30 seconds in memory, uploads only that clipped WAV, and emits a user-visible non-transcript status event so the turn can still complete.

### ASSISTANT 2026-05-26T08:56:08.068Z

I have enough of the shape now. The main pieces I’m changing are: settings + consent state, an in-memory 16 kHz WAV recorder, Sarvam batch HTTP client with audit-safe telemetry, an STT switcher, settings UI disclosure/key controls, and focused unit coverage for HTTP/routing/recording limits.

### ASSISTANT 2026-05-26T08:56:21.712Z

I’m starting with the pure model layer so downstream code has stable names to compile against: new provider enum value, persisted consent bit, a transcript-safe STT notice event, and a speech-to-text audit action that stores metadata only.

### ASSISTANT 2026-05-26T08:59:01.382Z

Now I’m adding the cloud path itself. The client waits for the existing long-press release signal, stops the in-memory recorder, uploads a single WAV to Sarvam, clears the key on auth failure, and writes only provider/language/audio/latency metadata to the local audit.

### ASSISTANT 2026-05-26T09:01:03.629Z

The switcher is now in place and Android STT is still its own arm. I’m updating the voice controller next so Sarvam-specific runtime messages like “cut off at 30s” and “needs internet” can be shown without ever treating those messages as transcript text.

### ASSISTANT 2026-05-26T09:04:06.000Z

I’m moving into Settings now. The provider choice stays inside the Brain card, but Sarvam gets its own explicit consent path and reuses the existing encrypted Sarvam key so users do not manage two separate Sarvam secrets.

### ASSISTANT 2026-05-26T09:06:27.167Z

I’m adding tests against the seam that matters most: Sarvam HTTP with MockWebServer, including auth/key clearing and the 30-second clamp, plus switcher tests so flipping settings routes between Android and Sarvam without replacing the Android recognizer.

### ASSISTANT 2026-05-26T09:08:11.154Z

The code path is broad enough that I’m going to let the compiler complain early before I polish docs. First pass will be targeted Kotlin/test compilation around `:core`, `:android-runtime`, and `:app` so we can catch type/DI/Compose issues while the diff is still fresh.

### ASSISTANT 2026-05-26T09:08:49.127Z

The first compile pass got through the new runtime tests and then caught an exhaustive `when` in the audit UI, which is exactly the kind of downstream break I wanted surfaced early. I’m adding the new STT audit action to the diagnostics renderers now, still without exposing transcript/audio.

### ASSISTANT 2026-05-26T09:10:04.166Z

The implementation pieces are in place and compiling. I’m updating the user-facing docs now so the README and privacy policy say the same thing the code does: Android STT remains default, Sarvam STT is explicit opt-in, audio goes only after release, and logs/audit never store transcript or audio.

### ASSISTANT 2026-05-26T09:13:31.733Z

I’ve got the Sarvam path wired through the app and the targeted Kotlin/test fixes are already green. I’m doing the last sweep now: docs/debug log, full Gradle validation, and a smoke check for install/launch if an Android device or emulator is visible.

### ASSISTANT 2026-05-26T09:13:39.723Z

The workspace has the expected modified files plus the new Sarvam runtime/test files; `git diff --check` is clean. I’m finding the next debug-log slot now so the audit trail lands with the change instead of living only in my notes.

### ASSISTANT 2026-05-26T09:13:50.621Z

Next I’m running the broad verification pass, not just the new tests. This is the part that catches the unglamorous failures: lint exhaustiveness, generated resource references, Hilt wiring, and APK assembly.

### ASSISTANT 2026-05-26T09:14:39.319Z

Full Gradle validation passed, including lint and `assembleDebug`. I’m doing the runtime smoke next: check for a connected emulator/device, install the fresh APK if possible, launch the app, then scan fresh logcat for process-crash signatures.

### ASSISTANT 2026-05-26T09:14:48.644Z

The APK exists, but `adb` is not on the shell PATH. I’m checking the usual Android SDK locations before I mark runtime smoke as unavailable.

### ASSISTANT 2026-05-26T09:15:19.050Z

A device is online (`emulator-5554`), so I can do the crash smoke properly. I’ll install the freshly assembled debug APK, clear logcat, launch through the launcher intent, and then read only the fresh crash/error signatures.

### ASSISTANT 2026-05-26T09:16:13.424Z

Runtime smoke is clean: the rebuilt APK installs, launches, and the Handy process stays alive. The only fresh logcat noise is the repo’s known debug StrictMode disk-read logging; a stricter crash scan found no `FATAL EXCEPTION`, `E/AndroidRuntime`, ANR, force-finish, or Handy process death.

### ASSISTANT 2026-05-26T09:16:56.830Z

While reviewing the new tests, I spotted a couple of acceptance paths that were behaviorally implemented but not explicitly pinned: missing key and missing consent. I’m adding small MockWebServer assertions for those so the “no upload” guarantee stays locked.

### ASSISTANT 2026-05-26T09:17:43.430Z

The acceptance pins are in: missing key and missing consent now assert recorder never starts and no HTTP request is made; network failure asserts the exact offline guidance and audit reason. I’m rerunning the Sarvam STT test class first, then I’ll rerun the broad build once the debug log is appended.

### ASSISTANT 2026-05-26T09:18:20.454Z

One new test exposed a harmless race in the fake recorder assertion: cancellation can call `cancel()` from both the flow close path and the waiting upload coroutine, but neither path uploads. I’m tightening the assertion to the actual contract: recorder was cancelled, never stopped for upload, and no HTTP request was sent.

### ASSISTANT 2026-05-26T09:18:52.012Z

Targeted Sarvam STT tests are green again with the stricter no-upload coverage. I’m rerunning the full validation because the test file changed after the earlier successful full pass.

### ASSISTANT 2026-05-26T09:19:52.117Z

Full validation is green after the extra acceptance tests. I’m appending the required `DEBUG_LOG.md` entry now with the implementation summary, practical before/after impact, manual release plan, and the exact validation I ran.

### ASSISTANT 2026-05-26T09:21:19.502Z

The debug log is appended and the final whitespace check is clean. I’m doing one last status/stat pass so I can give you a tight close-out with the exact files and validation state.

### ASSISTANT 2026-05-26T09:22:25.448Z

Implemented the opt-in Sarvam Saarika v2 STT path end to end. Android STT remains the default and unchanged; Sarvam is only used after Settings consent plus a saved Sarvam key.

**What Changed**
- Added `SARVAM_SAARIKA` and consent persistence in [Settings.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/model/Settings.kt:39) and `DataStoreSettings`.
- Added in-memory WAV recording with 16 kHz mono PCM, no disk writes, and a 30s cap in [MicAudioRecorder.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/speech/MicAudioRecorder.kt:38).
- Added Sarvam batch upload, 401 key clearing, network-friendly errors, buffer zeroing, and transcript-free audit logs in [SarvamSttClient.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/speech/SarvamSttClient.kt:47).
- Added provider routing in [SwitchingSttClient.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/speech/SwitchingSttClient.kt:21) and rewired DI in [RuntimeModule.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/di/RuntimeModule.kt:198).
- Added Settings provider picker, Sarvam key reuse, revoke consent, and consent dialog in [BrainSection.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/settings/sections/BrainSection.kt:221) and [SettingsActivity.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt:451).
- Added `SttEvent.Notice` for the “Cut off at 30s” chip/status without polluting transcript text in [SttClient.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/speech/SttClient.kt:45).
- Updated README, privacy policy, and [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1981).

**Before / After**
Before: a Hinglish command like “panch minute ka timer lagao” or “Subah 7 baje alarm set kar do” depended entirely on Android SpeechRecognizer and could mishear the code-mix.

After: users who explicitly opt in can use Sarvam for better Hindi/Hinglish transcription. The flow is long-press -> release -> transcribing -> final transcript submits. No live preview on Sarvam because this v1 uses batch upload; Android STT still gives the existing behavior when selected.

**Manual Testing Plan**
1. Clean install: confirm provider is Android, Sarvam consent is off, and no Sarvam key is required.
2. Android regression: test long-press voice in chat and overlay with Auto, On-device only, and Network allowed.
3. Select Sarvam, dismiss consent, long-press: confirm no recording/upload and a consent prompt/error.
4. Select Sarvam, grant consent, no key: confirm friendly “Add a Sarvam API key in Settings.” and no upload.
5. Add real Sarvam key: speak English, Hindi, and Hinglish; confirm release triggers spinner/transcribing and one final submit.
6. A/B test same Hinglish phrase with Android vs Sarvam on the same device.
7. Hold beyond 30s: confirm “Cut off at 30s” and transcript only covers the capped audio.
8. Cancel mid-press: confirm no HTTP request.
9. Airplane mode: confirm “Sarvam needs internet — switch to Android STT or reconnect.”
10. Audit/privacy: confirm audit has provider/language/audio_ms/latency/success-fail only, never transcript/audio.
11. Revoke consent: confirm Sarvam cannot upload again until consent is re-granted.
12. Switch back to Android: repeat normal voice, cancel, mic-denied, chat mic, and overlay mic flows.

**Validation**
- `git diff --check` passed.
- Targeted Sarvam/Switching/Voice tests passed.
- Full validation passed: `:core:test :android-runtime:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Installed `app-debug.apk` on `emulator-5554`, launched `com.handy.android`, process stayed alive, and fresh logcat crash scan found no `FATAL EXCEPTION`, `E/AndroidRuntime`, ANR, force-finish, or Handy process death. Only the known debug StrictMode disk-read logs appeared.

I did not run a live real-key Sarvam Hinglish A/B call here because no Sarvam API key was available in the workspace; the HTTP path is covered with MockWebServer and the release manual plan above covers the real provider check.

### USER 2026-05-26T09:22:25.711Z

Lots of changes done above which included context compactions in the middle. 1700+ lines of code were written across 30+ files- that's a lot of code and it needs a deep deep thorough audit - functional as well as non-functional! The voice services especially are critical in Handy! Can you go through the original prompt, code changes done in extreme detail, audit them, ensure all the required changes were done and done correctly with no downstream impact. the code before this works fine with it - no suspicious lines of codes for app crashes, or faulty wirings. all the services that needed to be changed have been changed and nothing is left or missed. ensure the ui is also proper with correct spacings, colours, fonts and elements. be very thorough in your analysis and evaluation. use highest level of reasoning available. Log the rca in the debug log and explain why the misses were missed. use highest level of thinking possible and think deeply. Evaluate and understand very deeply. Be very thorough and detailed. use your highest level of intelligence to analyse the requirement, code generated and plug gaps. be very thorough and have highest attention to detail. carefully analyse all the code - new and old and all dependencies too.

### ASSISTANT 2026-05-26T09:22:44.903Z

I’ll treat this as a fresh audit pass, not as a rubber stamp. I’m going back through the original requirement against the diff and the surrounding voice/settings/DI code, then I’ll patch any gaps I find, rerun validation, and add a debug-log audit entry that names what was missed and why.

### ASSISTANT 2026-05-26T09:22:56.920Z

The first pass confirms the tree is exactly the Sarvam STT patch plus the pre-existing untracked `handy_macos_ref/`. I’m drilling into the runtime lifecycles now: recorder ownership, callbackFlow close paths, DI scoping, VoiceController stop/cancel behavior, and Settings state propagation.

### ASSISTANT 2026-05-26T09:24:14.173Z

A real issue surfaced: Sarvam batch STT can legitimately take longer than the old Android recognizer’s 2 second final-grace window, so the first implementation could cancel the upload before the final transcript arrived on a normal network. I’m going to add an explicit per-provider final timeout so Android keeps the fast behavior and Sarvam gets enough time for its 15s upload timeout.

### ASSISTANT 2026-05-26T09:26:47.266Z

I patched the timeout hole and added regression coverage so this cannot silently regress: Sarvam now advertises a 20s terminal-result window, Android keeps the default 2s, and the switcher/VoiceController delegate to the active provider. I also hardened Sarvam release/cancellation so service teardown can cancel an in-flight upload instead of letting it drift.

### ASSISTANT 2026-05-26T09:27:31.733Z

The next UI audit found a smaller but real future-value problem: this repo already has hidden `ASSEMBLY_AI` / `OPEN_AI` enum values, and the Sarvam settings rows treated every non-Android value as Sarvam. Runtime routing fell back to Android, but Settings could show the wrong cloud key/consent rows for a stale or future value. I’m normalizing the UI to “Sarvam only when explicitly Sarvam; everything else displays Android.”

### ASSISTANT 2026-05-26T09:29:01.295Z

One acceptance nuance also needed tightening: the cutoff notice is emitted after release, when the widget path has already moved from LISTENING to THINKING. The overlay presenter was ignoring voice notices outside LISTENING, so the full chat/panel could show the chip but the widget path could miss it. I’m allowing voice notices during the immediate THINKING drain as well.

### ASSISTANT 2026-05-26T09:29:48.140Z

The privacy audit found one memory hygiene gap worth fixing: upload bytes were zeroed, but the recorder’s internal PCM buffer relied on garbage collection after stop/cancel. It was never written to disk, but for a voice/privacy path we can do better. I’m replacing the recorder buffer with a wipeable in-memory buffer and zeroing the PCM copy after WAV encoding.

### ASSISTANT 2026-05-26T09:30:22.456Z

The requirement also asked that Sarvam STT audit success/failure without transcript/audio. Success and HTTP failures were covered, but pre-upload failures like missing consent/key were not logged. I’m adding zero-audio failure audit rows for those gated exits so the audit contract is consistent without weakening privacy.

### ASSISTANT 2026-05-26T09:33:11.668Z

The audit checklist is now in a better place: provider timeout, cancellation, memory wiping, pre-upload audit rows, hidden-provider UI fallback, and cutoff notice visibility are all patched and covered by targeted tests. I’m running the full build/lint/test suite again before I write the final audit RCA entry.

### ASSISTANT 2026-05-26T09:34:02.941Z

Full Gradle validation passed again after the audit fixes. I’m doing the final device smoke now with the rebuilt APK, then I’ll append the audit RCA entry to `DEBUG_LOG.md`.

### ASSISTANT 2026-05-26T09:34:34.376Z

Device smoke is clean with the rebuilt APK: install succeeded, launcher start succeeded, the Handy process stayed alive, and a strict crash scan found no fatal exception, AndroidRuntime error, ANR, force-finish, or process death. I’m appending the deep-audit RCA now.

### ASSISTANT 2026-05-26T09:37:14.302Z

I’ve completed the deep audit pass and the build/device verification; I’m just grabbing the exact anchors for the audit log and docs so the close-out points to the right places.

### ASSISTANT 2026-05-26T09:37:46.755Z

Deep audit is complete. I found and fixed several real issues in the Sarvam STT work, including the most important one: batch Sarvam uploads could have been cancelled by the old Android STT 2s final-result timeout. Sarvam now gets a longer provider-specific timeout, cancellation does not upload audio, missing consent/key paths do not record or upload, auth failures clear the key, audio buffers are wiped after use, and the UI now matches runtime routing instead of accidentally treating hidden/future providers as Sarvam.

I logged the RCA and audit hardening in [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:1993). Short version: the misses came from adapting an Android live-streaming STT flow to a batch-upload provider, plus context-compacted work hiding cross-layer timing/state edges. The fixed areas are concentrated around [SarvamSttClient.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/speech/SarvamSttClient.kt), [MicAudioRecorder.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/speech/MicAudioRecorder.kt), [SwitchingSttClient.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/speech/SwitchingSttClient.kt), [VoiceController.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/voice/VoiceController.kt), and [BrainSection.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/settings/sections/BrainSection.kt).

Before/after: before this, Handy always used Android STT, so code-mix commands like “kal subah 7 baje alarm laga do” depended on Android’s recognition quality. After this change, Android remains the default, but a user who explicitly consents and adds a Sarvam key can choose Sarvam Saarika for better Hindi/Hinglish transcription; audio uploads only after release, with no live preview, capped at 30s.

Validation passed:

```bash
JAVA_HOME=/Users/satvik.bansal/.cache/codex-jdk17 PATH=/Users/satvik.bansal/.cache/codex-jdk17/bin:$PATH ./gradlew --no-daemon :core:test :android-runtime:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
git diff --check
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

The full Gradle test/lint/assemble pass is clean. Emulator smoke launch was clean too: app process stayed alive and strict logcat scan found no `FATAL EXCEPTION`, `E/AndroidRuntime`, ANR, force-finish, or process-death signal.

Manual test plan to run on-device before release:

1. Android default: fresh install, do not enable Sarvam, long-press voice, confirm Android STT still returns live/normal results.
2. Sarvam no key: select Sarvam, deny or omit key, long-press, confirm friendly settings prompt and no network upload.
3. Sarvam consent: select Sarvam, accept one-tap consent, verify it does not re-prompt every session.
4. Sarvam success: add real key, speak Hinglish, release, verify spinner/transcribing state, final transcript appears and submits.
5. Airplane mode: Sarvam selected with key/consent, record and release, verify “Sarvam needs internet — switch to Android STT or reconnect”.
6. Cancel path: start press, cancel before release, verify no upload request is made.
7. Long session: hold past 30s, verify cutoff notice/chip appears and only first 30s uploads.
8. Switch back: choose Android again and verify no Sarvam UI/network behavior remains in the voice path.
9. Audit/privacy: verify audit shows provider/language/audio_ms/latency/success-fail only, never transcript or audio.
10. UI pass: settings spacing, consent dialog copy, key field, revoke consent, chat composer notice, overlay notice.

One thing I could not genuinely verify without a real Sarvam key and physical microphone flow is the live “Sarvam transcript noticeably better than Android” manual comparison. Everything local, mocked, linted, assembled, and emulator-smoked is clean.
