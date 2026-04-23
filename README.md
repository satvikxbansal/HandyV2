# Handy for Android

A production-grade on-screen AI assistant for Android, ported from the macOS
Handy app.

## Source of truth

Read these before writing or editing any code:

1. [`Handy_Android_Build_Plan_v2.md`](./Handy_Android_Build_Plan_v2.md) — the v1
   architecture, scope, modules, interfaces, OS-1..OS-5 handling, and 10-week
   rollout.
2. [`Handy_Android_Cursor_Prompt_v2.md`](./Handy_Android_Cursor_Prompt_v2.md) —
   the Cursor prompt and execution-phase definition.
3. [`.cursor/rules/`](./.cursor/rules/) — the canonical rule files. The four
   Karpathy skills, the Handy guardrails, and the DEBUG_LOG protocol live
   here; everything else in the repo refers back to these.
4. [`Handy-V2/MacOS App (Handy V1)/Handy/`](./Handy-V2) — the read-only
   macOS reference (system prompts, loading verbs, parsing rules, error copy,
   design tokens). Do not modify.

## Project layout

```
.
├─ core/                 # Pure Kotlin/JVM. No android.*, no androidx.*.
├─ android-runtime/      # Android adapters & wrappers only. No UI, no manifest components.
└─ app/                  # All manifest components + all Compose UI.
```

## Toolchain

| Tool        | Version                      |
|-------------|------------------------------|
| JDK         | 17                           |
| Kotlin      | 2.0.21                       |
| AGP         | 8.7.3                        |
| Gradle      | 8.10.2 (wrapper)             |
| Compose BOM | 2024.12.01                   |
| Hilt        | 2.53                         |
| OkHttp      | 4.12.0                       |
| minSdk      | 26                           |
| targetSdk   | 35 (Android 15)              |
| compileSdk  | 35                           |

API 36 (Android 16) is a compatibility smoke lane in CI — not a v1 shipping
target.

## Bootstrapping a fresh checkout

Requirements: JDK 17 on `JAVA_HOME` and the Android SDK with platform 35 +
build-tools 35.0.x installed (`sdkmanager "platforms;android-35" "build-tools;35.0.0"`).

```bash
./gradlew :core:test                       # Phase 1 unit tests
./gradlew :android-runtime:test            # Phase 2 unit tests
./gradlew :app:assembleDebug               # Phase 3 debug APK
./gradlew build                            # all modules
```

The Gradle wrapper JAR is checked in, so the first invocation works without a
system Gradle installation.

## Current phase status

| Phase | Scope                                                               | Status          |
|-------|---------------------------------------------------------------------|-----------------|
| -1    | Source-of-truth alignment across docs + rules                       | ✅ Done          |
| 0     | Module scaffold, toolchain, `HandyApplication`, `./gradlew build`   | 🟡 In progress  |
| 1     | `:core` domain, prompts, parsers, orchestrator, tests               | Pending         |
| 2     | `:android-runtime` adapters (Claude, STT, TTS, capture, intents)    | Pending         |
| 3     | `:app` UI + manifest components + Apple-class theme                 | Pending         |
| 4     | Hardening, DEBUG_LOG bootstrap, Play / policy deliverables          | Pending         |
