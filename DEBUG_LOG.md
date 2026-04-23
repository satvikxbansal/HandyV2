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
