# Update README with latest Handy

## Metadata

- Thread ID: `019e5598-fe88-7bd1-856f-5ab993d64356`
- Created: 2026-05-23 21:39:10 IST
- Updated: 2026-05-23 21:45:13 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T21-39-10-019e5598-fe88-7bd1-856f-5ab993d64356.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `60fd40ee32a42d8ca91be1171eac3ea0aa1a63f1`
- Messages exported: 9

## Brief Summary

This conversation focused on: Update README with latest Handy Likely related git changes: 60fd40e [audit] bug fixes.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `60fd40ee32a42d8ca91be1171eac3ea0aa1a63f1`
- Likely related commits:
  - `60fd40e` 2026-05-23 21:35:24 IST [low] [audit] bug fixes. Files: DEBUG_LOG.md, app/src/main/kotlin/com/handy/app/chat/ChatViewModel.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt, core/src/main/kotlin/com/handy/core/agent/UserGoal.kt, core/src/main/kotlin/com/handy/core/orchestrator/ConversationOrchestrator.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt, core/src/test/kotlin/com/handy/core/agent/UserGoalTest.kt, core/src/test/kotlin/com/handy/core/prompts/PromptCatalogTest.kt

## Conversation

### USER 2026-05-23T16:10:00.139Z

can you update the readme with the latest on Handy? be very comprehensive in your evaluation of the currenr read me, the code changed, upgrades and then add the new stuff. follow similar language and style

### ASSISTANT 2026-05-23T16:10:11.296Z

I’ll take a full pass through the current README and the app surface, then compare that against the actual code and recent changes so the README update reflects what Handy is now, not just what it used to say.

### ASSISTANT 2026-05-23T16:10:18.753Z

I see the working tree is clean except for an untracked `handy_macos_ref/` reference folder. I’ll leave that alone unless it contains source material that helps explain the Android app.

### ASSISTANT 2026-05-23T16:10:35.799Z

The existing README is already written in a policy-first, plain-spoken style. Now I’m checking the actual feature inventory and tests so I can expand it without drifting into brochure language or claiming capabilities the build does not really have.

### ASSISTANT 2026-05-23T16:11:57.817Z

A couple of important deltas are showing up: the README knows about the main policy posture, but the code has grown more specific around Sonnet/Haiku selection, Chrome omnibox handling, app install handoff, calendar events, ride prep, incognito blocking, per-app Tap-for-me revoke/restore, and a much broader eval/test harness. I’m going to fold those into the README rather than treating them as side notes.

### ASSISTANT 2026-05-23T16:12:25.245Z

I’ve got enough context to edit now. The README’s shape is right, so I’m going to keep the same safety-first voice and expand the parts that are now under-described: the newest deterministic recipes, reduced-mode enforcement, brain/model picker, action controls, test coverage, and release blockers.

### ASSISTANT 2026-05-23T16:14:42.202Z

README is updated now. I’m doing a tight verification pass for obvious markdown issues, stale claims, and accidental formatting churn before I call it done.

### ASSISTANT 2026-05-23T16:15:05.028Z

The markdown diff is clean (`git diff --check` passed), and the referenced docs/artifact paths exist. Since this is documentation-only, I’m not running the Android build; the README now points at the build/test lanes that should be used for code or release validation.

### ASSISTANT 2026-05-23T16:15:13.509Z

Updated [README.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/README.md) with a much fuller current-state pass.

I kept the existing safety-first voice and expanded the README around the latest Handy changes: canonical recipe routing, S-1..S-10 inventory, install-app Play Store handoff, timer/calendar/ride-hailing recipes, Chrome omnibox support, reduced-mode enforcement, Sonnet/Haiku model picker, manual target recovery, privacy/action controls, release gaps, and the current test/validation surface.

Verification: `git diff --check` passed, and the referenced docs/artifact paths exist. I did not run the Android build since this was docs-only. The untracked `handy_macos_ref/` folder was left untouched.
