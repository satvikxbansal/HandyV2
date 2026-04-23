# Handy Android — Design Notes

A running log of design decisions, deviations from the plan / rules,
and deferred work. Small decisions live here; architectural or
behavioural changes go in the build plan with a DL-XXX cross-reference
when one exists.

## Deviations from the canonical plan / rules

- **macOS `loadingVerbs` count.** The build plan mentions "34 strings";
  the macOS Swift source actually ships 30. Android ports the 30 that
  exist. Flag: if macOS grows the list, `LoadingVerbs.ALL` must track
  it verbatim.
- **macOS `ChatMessage.timestamp` wire format.** Swift's default
  `JSONEncoder` stores `Date` as a seconds-since-reference-date
  `Double` (Apple 2001 epoch). Android serialises `timestampEpochMs`
  as a Unix epoch `Long`. Full cross-platform round-trip requires a
  translator at the JsonHistoryStore boundary; v1 does not yet import
  macOS history files — defer.
- **`:core` `PromptCatalog` examples.** Example prose mentions macOS
  apps ("Final Cut", "Figma export panel"). Per the guardrail, every
  non-adaptation word is preserved verbatim — only the POINT tag
  syntax differs. Claude understands the examples as illustrations of
  output format.

## Dependencies

Every new library lands here with a one-line justification.

- **`androidx.dynamicanimation`** — SpringAnimation for the widget
  snap-to-edge. Matches macOS motion tokens.
- **`androidx.security:security-crypto`** — `EncryptedSharedPreferences`
  for API keys. Deprecation is noted; tink-based replacement (`1.1.x`)
  is a drop-in when the feature stabilises.
- **`com.squareup.okhttp3:okhttp-sse`** — Server-Sent Events for the
  Claude stream. No Retrofit, no "AI" helper libraries.
- **`com.jakewharton.timber:timber`** — Logging. Replaces `println` /
  `Log.*`, which are on the Forbidden list.
- **`androidx.startup:startup-runtime`** — Available for future
  bootstrap needs; currently unused.

## Deferred (v2 candidates)

- **Tap-for-me / do-it-for-me** via
  `AccessibilityService.dispatchGesture`. v1 binds
  `ActionPerformer → NoopActionPerformer`; v2 adds a real performer
  in the module that owns the AccessibilityService (currently `:app`).
- **Gemini brain**. The `LlmClient` seam is complete; v2 ships
  `GeminiLlmClient` alongside `ClaudeLlmClient` and a provider toggle
  in Settings.
- **Continuous guided workflow / multi-step runner**. Explicitly
  out-of-scope for v1 per the guardrails.
- **Accessibility-based tool-memory auto-switching** (per-app /
  per-site chat threads that follow the user across apps). The
  `ToolContext` and `UmbrellaSiteLabels` in `:core` are ready; the
  event hook in `HandyAccessibilityService.onAccessibilityEvent` is a
  v2 target.
- **Rich animations** — idle breathing, staggered listening pulses,
  rotating thinking ring, typewriter reveal. Phase 3 ships the static
  widget states and colour transitions; motion tokens are already
  defined.
- **Tutor mode** — prompt is present; enabling flow / idle-observation
  trigger land in v2.
- **On-device Gemini Nano fallback** — v2.

## Review artifacts checklist (Play Console submission)

Track completion here; every item is required before shipping v1.

- [ ] In-app Accessibility disclosure recording (OnboardingActivity).
- [ ] Canonical chat + pointing session (ChatActivity + widget tap).
- [ ] Accessibility-off reduced-mode experience.
- [ ] Play Console **Accessibility use declaration** — describes read
  + pointing + intent dispatch; explicitly says no tap-for-me in v1.
- [ ] Play Console **Foreground service use declaration** —
  `specialUse | microphone` for `AssistantForegroundService` and
  `mediaProjection` for `MediaProjectionCaptureService`.
- [ ] Privacy policy link (points to `PRIVACY_POLICY.md` or a hosted
  equivalent).
- [ ] Screenshots (widget idle / listening / thinking; chat with
  streaming response; settings).

## Performance budget (enforced in Phase 4 pass)

From build plan §15:

- Cold start → widget visible ≤ 800 ms.
- Widget tap → chat first frame ≤ 250 ms.
- Long-press → first partial transcript ≤ 400 ms.
- Chat token → visible delta ≤ 50 ms.
- JPEG encode 1024-edge screenshot ≤ 40 ms.
- Idle memory footprint ≤ 110 MB.
- APK size ≤ 25 MB.

Baseline profile targets: `ChatActivity`, `FloatingWidgetOverlayService`.
