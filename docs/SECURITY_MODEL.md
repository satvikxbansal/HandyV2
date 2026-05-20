# Handy Security Model

Lane: **A — general screen-aware AI copilot**.

This model records the security threats and mitigations that later prompts must preserve. It is sourced from [`HANDY_NEXT_LEVEL_PLAN.md`](../HANDY_NEXT_LEVEL_PLAN.md), especially the trust contract, Phase 4 policy engine, Phase 10 hardening, and the v2 risk register.

Related docs: [`PLAY_POLICY_MATRIX.md`](PLAY_POLICY_MATRIX.md), [`PRIVACY_MODEL.md`](PRIVACY_MODEL.md), [`ACTION_POLICY.md`](ACTION_POLICY.md), and [`Handy_Android_Build_Plan_V2_Scope.md`](../Handy_Android_Build_Plan_V2_Scope.md).

## Security Invariants

1. Handy shows what it sees.
2. Handy shows what it will do.
3. Handy never acts on low confidence.
4. Handy never hides automation.
5. Handy never sends, pays, deletes, purchases, or submits personal data without the policy-required confirmation.
6. Handy always allows stop or cancel and keeps a readable audit trail.
7. The LLM may suggest. A typed policy engine decides what executes.

## Threats And Mitigations

| ID | Threat from the plan | Likelihood | Impact | Required mitigations | Evidence later prompts must preserve |
|---|---|---|---|---|---|
| T1 | Play removes app for accessibility misuse or Lane drift | Med-High | App removal, loss of trust, blocked release | Commit to Lane A in docs and scope header; keep AccessibilityService use tied to visible user benefit; no LLM-executed multi-step plans; prominent disclosure; action audit; banking and sensitive denylist | [`PLAY_POLICY_MATRIX.md`](PLAY_POLICY_MATRIX.md) rows for AccessibilityService and actions; Lane A header in [`Handy_Android_Build_Plan_V2_Scope.md`](../Handy_Android_Build_Plan_V2_Scope.md) |
| T2 | `markId` loss causes the wrong tap | High today | User-requested action can land on a sibling or duplicate control | `TapTarget.AtNode` carries `markId`, expected package, expected window id, and snapshot hash; performer re-resolves on `markId`; fail closed on package/window mismatch | Phase M1 invariant: a `[POINT:markId=m7]` turn keeps `m7` through resolved target, tap target, perform result, and audit row |
| T3 | Tap or capture on a secure screen | Medium | Sensitive app data exposed or unsafe action attempted | Secure windows produce `allowed=false, reason="secure"`; never send secure captures to `LlmClient`; block reading and typing for OTP, password, card, and CVV fields | SecureWindow eval; policy decision audit row `secure`; zero secure frames in prompt logs |
| T4 | Sensitive redaction leaks through `debugCandidates`, logs, diagnostics, or audit | Medium today | Password, OTP, card, or private text can be exposed locally or to cloud prompts | Redact at `RuntimeCandidate.fromNode`, `RuntimeCandidate.fromMark`, and `TargetCandidate`; use diagnostics redaction path for audit and UI; negative tests for password, OTP, and card | Phase M2 tests and Diagnostics manual check show `[redacted]` in sensitive candidate rows |
| T5 | Capture window and grounding window mismatch | Medium today | LLM sees one window while actions target another | `GroundingSnapshot` includes window id, display id, bounds, hashes, and timestamp; thread `activeWindowIdHint` into capture; `LiveScreenGuard` rejects changed screens before execution | Manual split-screen test shows capture window id equals grounding window id; audit logs `screen-changed` on mismatch |
| T6 | OEM accessibility variance breaks `dispatchGesture` or node actions | Medium | Actions fail, land unreliably, or behave differently by device | Prefer node `performAction` before gestures; gate `canPerformGestures`; maintain device matrix; use learned allowlist for gesture fallback; no retry loops | [`DEVICE_MATRIX.md`](DEVICE_MATRIX.md) cells; action audit records unsupported or failed paths without repeated retries |
| T7 | LLM hallucinates target or fabricates screen context | Medium | Handy points at or acts on a nonexistent or wrong UI element | Mark-id resolver; confidence-tiered behavior; no Strategy A pixel-coordinate fallback; manual "Let me show you" recovery; no-context honesty eval | Resolver replay corpus passes target thresholds; low-confidence turns show candidates or ask for panel instead of acting |
| T8 | Tool-result or web-page prompt injection triggers a device action | Medium | Fetched content can manipulate the assistant into acting | `SourceTrust` envelopes; `UNTRUSTED_TOOL` veto in `ActionPolicyEngine`; fetched content is evidence only; per-session tool quotas | ToolInjectionEval; policy decisions with `reason="tool-suggestion-only"` |
| T9 | Multi-step plan goes off-rails | Medium | User loses control of a recipe or unintended actions chain together | Deterministic recipes only; LLM chooses recipe and arguments but cannot emit free-form steps; max 5 steps; re-capture, re-validate, and audit each step; abort on app switch or failed verification | RecipeRunner tests reject over-5-step plans and runtime-injected steps; audit includes recipe, step index, policy decision, confirmation, and verification |
| T10 | Hindi STT or locale routing mishears a sensitive intent | Medium | Wrong language interpretation can send, navigate, or type the wrong thing | Locale-aware STT/TTS; Hindi/Hinglish prompt evals; exact confirmation text shown before sensitive actions; strong confirmation for send/navigation; payment and purchase blocked | HindiHinglishEval and manual Hindi locale pass; confirmation UI displays the interpreted action before execution |
| T11 | Overlay sustained-memory growth, lifecycle leaks, or stale UI state | Low | Crashes, jank, stuck overlays, or stale action state | Node recycling; cancellable jobs; no leaked `ComposeView`, `ViewModelStore`, `WindowManager`, or accessibility nodes; strict flight FSM; crash-safe audit writes; macrobench and lifecycle tests | Phase F1 flight/lifecycle tests; Phase OPS1 crash-safe file audit; macrobenchmark jank target and crash-free sessions target |

## Trust Boundaries

| Boundary | Trusted side | Untrusted side | Enforcement |
|---|---|---|---|
| User request to LLM interpretation | Explicit user intent and local policy state | Model text, web pages, tool results | `SourceTrust`, `ActionPolicyEngine`, confirmation UI |
| Grounding snapshot to action performer | Fresh live `GroundingSnapshot` and resolver result | Stale marks, stale window ids, hallucinated target labels | `LiveScreenGuard`, `markId`, expected package/window/snapshot hash |
| Local app to cloud provider | Redacted, budgeted prompt payload | Provider retention and network transport | Privacy model retention rules, no secure frames, no raw secrets |
| Overlay UI to foreground app | Visible confirmation and audit state | Background automation or hidden actions | No background action runner; panel owns confirmations; audit required for every action |
