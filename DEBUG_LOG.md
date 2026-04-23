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
