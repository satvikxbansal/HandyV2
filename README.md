# Handy for Android

Handy is an on-screen AI assistant for Android. It floats above other apps,
listens when you speak, chats in a full panel, reads screen context (where
available), points to UI elements, and can trigger safe direct actions through
Android intents.

This project is a clean-room Android build inspired by the shipped macOS Handy
product, with strict guardrails for reliability, privacy, and Play policy.

---

## Why this repo exists

Most mobile assistants are either too shallow ("just search") or too risky
("full automation with no boundaries"). Handy aims for a middle path:

- Helpful enough to guide and act on simple, explicit tasks.
- Strict enough to avoid unsafe or hidden behavior.
- Fast enough to feel like a real copilot, not a chatbot in a box.

---

## Current project snapshot

The codebase is already substantial and production-minded. The three-module
architecture is in place, the core chat/tool loop works, the floating-widget
experience is implemented, and policy documentation is actively maintained.

### What is working now

- Multi-module Android app with strict boundaries: `:core`, `:android-runtime`,
  `:app`.
- LLM chat orchestration with streaming responses.
- Tool loop wired for `web_search`, `github_search`, `fetch_page`,
  `dispatch_action`.
- Voice input/output path integrated (widget and chat composer flows).
- Overlay widget + overlay chat panel ("Unified Buddy" direction).
- Accessibility service integration, semantic pointer resolver, and diagnostics.
- Intent dispatch with confirmation gating for destructive actions.
- Settings, onboarding, Play policy disclosure docs, and debug-log discipline.

### Known active gaps (important)

- Main chat send paths still pass `capture = null` and `screenText = null` in
  orchestration requests; full screen-context wiring is not complete yet.
- `BrainRouter` and local-gen routing seams exist but are not the primary
  production path today.
- `MediaProjection` fallback is not fully wired into the capture pipeline DI on
  all paths yet.
- Some UI polish assets remain placeholder-level (for example default system
  icons in manifest entries).

---

## Architecture (simple view)

```text
User
  -> app (Compose UI, services, activities, manifest components)
    -> android-runtime (Android adapters: llm, capture, speech, intent, storage)
      -> core (pure Kotlin domain + orchestrator + prompts + contracts)
```

### Module responsibilities

- `core/`
  - Pure Kotlin/JVM domain logic only.
  - No Android imports allowed.
  - Holds orchestrator, prompts, parsing, tool contracts, budgeting/routing
    seams, and domain models.
- `android-runtime/`
  - Android adapters and infrastructure.
  - LLM clients, tool runner, web search, capture adapters, STT/TTS, secure
    storage, settings persistence, intent dispatch, audit storage.
  - No UI screens or manifest-owned app components.
- `app/`
  - All user-facing UI and all manifest components.
  - Activities, foreground/overlay/accessibility services, onboarding, settings,
    diagnostics, widget/panel presentation, and DI bindings to runtime adapters.

---

## Key capabilities by area

### Assistant and tools

- Streaming assistant responses through the core orchestrator.
- Tool-aware turns with runtime execution and result folding.
- Web + GitHub + page-fetch tools available behind settings gates.
- Direct action dispatch for well-defined intents, with confirmation policy for
  risky actions (call/text/share flows).

### Voice and interaction

- Long-press widget to speak.
- Voice handoff into overlay panel or full chat depending on settings.
- Chat composer voice controls also supported.
- Structured state handling around listening/thinking/responding.

### Overlay and UI behavior

- Floating widget service with drag/snap behavior and visual state transitions.
- Overlay chat panel service (V2-facing quick surface).
- Full chat activity remains available as a complete chat UI.

### Accessibility and pointing

- Accessibility service configured for window content and view IDs.
- Semantic pointer resolver to map model pointer hints to actual nodes.
- Foreground-app awareness and diagnostics visibility.

### Privacy, safety, policy posture

- Explicit permission flow and reduced-mode concept documented.
- Secure storage for keys and DataStore-backed app settings.
- Play submission dossier and disclosure workstream tracked in-repo.
- Audit-trail and diagnostics foundations in place.

---

## Tech stack and versions

Source of truth is `gradle/libs.versions.toml`.

- JDK: `17`
- Kotlin: `2.2.21`
- Android Gradle Plugin: `8.13.2`
- Gradle wrapper: `8.14.3`
- KSP: `2.2.21-2.0.4`
- Compose BOM: `2025.11.01`
- Hilt: `2.57.2`
- OkHttp: `5.3.2`
- minSdk: `26`
- targetSdk: `36`
- compileSdk: `36`

---

## Getting started

### 1) Prerequisites

- JDK 17.
- Android SDK installed and discoverable via `local.properties` (`sdk.dir=...`).
- Android platform/support packages for API 36.
- Use the checked-in Gradle wrapper (`./gradlew`), not a global Gradle install.

### 2) First run sanity checks

Run from repo root:

```bash
./gradlew :core:test
./gradlew :android-runtime:test
./gradlew :app:assembleDebug
./gradlew build
```

### 3) Useful day-to-day commands

```bash
# Unit tests
./gradlew :core:test
./gradlew :android-runtime:test
./gradlew :app:testDebugUnitTest

# Instrumentation tests (emulator/device)
./gradlew :app:connectedDebugAndroidTest

# Lint
./gradlew :app:lint

# Install debug build
./gradlew :app:installDebug
```

---

## Repo map

- `core/` - domain + contracts + orchestrator.
- `android-runtime/` - Android adapters and service implementations.
- `app/` - Android app layer, Compose UI, and manifest components.
- `.cursor/rules/` - operational guardrails and coding constraints.
- `DEBUG_LOG.md` - append-only bug-fix log and prevention rules.
- `DESIGN_NOTES.md` - architectural decisions and deviations.
- `PLAYSTORE_SUBMISSION.md` - policy, declarations, and release checklist.
- `Handy_Android_Build_Plan_v2.md` - primary build and rollout plan.

---

## Source-of-truth documents (read these first)

1. `Handy_Android_Build_Plan_v2.md`  
   Full architecture, scope, quality bar, OS constraints, and test strategy.
2. `Handy_Android_Cursor_Prompt_v2.md`  
   Execution-phase framing and implementation contract for assistant-driven work.
3. `.cursor/rules/`  
   Hard guardrails (module boundaries, OS rules, safety constraints, coding
   behavior).
4. `DEBUG_LOG.md`  
   Historical bug fixes and prevention rules that should guide new changes.
5. `DESIGN_NOTES.md`  
   Rationale for divergences, dependencies, and V2 decisions.
6. `PLAYSTORE_SUBMISSION.md`  
   Store compliance, disclosure, and release artifacts.

---

## Development principles in plain English

- Keep module boundaries strict. If `:core` knows about Android, we broke the
  contract.
- Prefer boring, clear Kotlin over clever abstractions.
- Build one concrete flow before generalizing.
- Ship with policy in mind, not as an afterthought.
- Every bug fix should teach the system something (via `DEBUG_LOG.md`).

---

## A realistic status read

Handy Android is beyond scaffold stage. The assistant stack, runtime adapters,
overlay/panel surfaces, and major policy scaffolding are present and actively
iterated.

The project is now in the "integration hardening" zone: connecting all context
signals end-to-end, tightening fallback behavior across Android API tiers, and
finishing release polish for a safe public rollout.

---

## Contributing without breaking things

- Follow `.cursor/rules/` before touching code.
- Keep changes surgical; avoid opportunistic refactors.
- Run the most relevant tests locally before opening/merging work.
- If you fix a bug, add a structured entry to `DEBUG_LOG.md` in the same
  change.

---

## License and references

This repo includes a read-only macOS reference app and a separate
`cursorbuddy-android-main` reference tree for implementation techniques and
constants. Follow project guidance in `DESIGN_NOTES.md` on "recipes, not
source", clean-room implementation, and licensing boundaries.
