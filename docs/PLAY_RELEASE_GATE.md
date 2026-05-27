# Play Release Gate

Use this checklist before uploading a release candidate to Play Console.
Every row needs an owner, date, and evidence link/path before the build is
promoted.

| Gate | Status | Owner | Date | Evidence / notes |
|---|---:|---|---|---|
| Device matrix: Pixel current API, Samsung current API, API 26, API 27, API 28, API 29, API 30+, and one low-RAM profile smoke-tested. | [ ] |  |  | Link to `docs/DEVICE_MATRIX.md` run notes. |
| Recipe sweeps: happy, ambiguous, and blocked prompts for every active recipe family in `docs/CAPABILITIES.yaml`. | [ ] |  |  | Include action audit export and fixture revision. |
| API 26-29 capture fallback: MediaProjection consent, start, capture, stop, denial, and rotation flows tested. | [ ] |  |  | Required because old APIs cannot use newer Accessibility screenshot paths. |
| Disclosure video: onboarding Accessibility disclosure, OS permission handoff, Accessibility toggle, Tap-for-me disclosure, and Settings capability page recorded. | [ ] |  |  | Upload to Play review artifacts. |
| Privacy form answers: Data Safety, permissions declarations, AccessibilityService declaration, foreground service types, and target audience copied from `PLAYSTORE_SUBMISSION.md`. | [ ] |  |  | Confirm generated capability block matches current manifest. |
| No debug trust or local CA in release: release network security uses system trust anchors only; debug overrides are not active in non-debuggable builds. | [ ] |  |  | Inspect merged release manifest/config. |
| Redaction smoke: passwords, OTPs, cards, secure windows, clipboard secrets, notification bodies, screenshots, audio, and API keys absent from logs/audit/export. | [ ] |  |  | Include logcat grep and Diagnostics export scan. |
| Capability manifest sync: `./gradlew verifyCapabilityDocs` passes and generated README, Play, privacy, and `capabilities.xml` are committed. | [ ] |  |  | Attach CI link. |
