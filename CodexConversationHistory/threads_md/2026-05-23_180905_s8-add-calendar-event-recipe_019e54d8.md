# S8: Add calendar event recipe

## Metadata

- Thread ID: `019e54d8-a6fb-79d0-9961-e91747b17d03`
- Created: 2026-05-23 18:09:05 IST
- Updated: 2026-05-23 18:20:12 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/23/rollout-2026-05-23T18-09-05-019e54d8-a6fb-79d0-9961-e91747b17d03.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `f31c22ac96689c5f396c07b3e8aa928ad88de054`
- Messages exported: 16

## Brief Summary

This conversation focused on: Add CalendarEventRecipe + a bounded DateTimeParser. The recipe never auto-creates events; it always opens the OS Calendar compose UI with prefilled fields. User taps Save. FILES TO KNOW - core/src/main/kotlin/com/handy/core/action/Assistan… Likely related git changes: 6c2a51c S8: Calendar event recipe with bounded date/time parser.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `f31c22ac96689c5f396c07b3e8aa928ad88de054`
- Likely related commits:
  - `6c2a51c` 2026-05-23 18:33:13 IST [high] S8: Calendar event recipe with bounded date/time parser. Files: DEBUG_LOG.md, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/CalendarEventRecipe.kt, android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt, android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/CalendarEventRecipeTest.kt, android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/RuntimeRecipePackTest.kt, core/src/main/kotlin/com/handy/core/agent/parsing/DateTimeParser.kt, core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt, core/src/test/kotlin/com/handy/core/agent/parsing/DateTimeParserTest.kt, core/src/test/kotlin/com/handy/core/prompts/PromptCatalogTest.kt

## Conversation

### USER 2026-05-23T12:39:26.918Z

Read the standing rules. Single-pass.

GOAL
Add CalendarEventRecipe + a bounded DateTimeParser. The recipe never
auto-creates events; it always opens the OS Calendar compose UI with
prefilled fields. User taps Save.

FILES TO KNOW
- core/src/main/kotlin/com/handy/core/action/AssistantAction.kt
  (CreateCalendarEvent shape: title, startEpochMs?, endEpochMs?,
  location?, notes?)
- android-runtime/src/main/kotlin/com/handy/runtime/intent/AndroidIntentDispatcher.kt
  (how Calendar insert intent is fired today)
- android-runtime/src/main/kotlin/com/handy/runtime/action/DefaultActionPolicyEngine.kt
  (confirm CreateCalendarEvent is allowed)

If S-2 created core/.../parsing/DurationParser.kt, sit a new
DateTimeParser.kt alongside it.

IMPLEMENT
- core/src/main/kotlin/com/handy/core/agent/parsing/DateTimeParser.kt
  Return type Result<Long?> so the caller distinguishes "no time"
  from "parse error".
  Required cases (table-driven test):
    * "tomorrow 3 pm"
    * "next Monday 9 am"
    * "this Friday 6:30 pm"
    * "in 2 hours" / "in 30 minutes"
    * "at 6" (next-occurrence within 12h)
    * "2026-06-01 14:00" (ISO)
    * "Saturday" (next-occurrence at default 9 am)
    * unparseable → Result.failure
  Bounds:
    * Refuse > 1 year future.
    * Refuse in past.
    * Local timezone (system default ZoneId).

- android-runtime/.../recipes/CalendarEventRecipe.kt
  * arg "title" or text after "schedule" / "calendar event" /
    "remind me of" / "add to calendar".
  * Refuse missing title with "missing-title".
  * Optional: parsed start time. If parser fails → recipe still
    proposes (Calendar opens with no time prefilled); if parser
    refuses (negative case like "1990") → Refused("invalid-time").
  * Optional: location ("at <place>"), notes (the rest).
  * Single-step NativeAction CreateCalendarEvent.
  * displayName "Create calendar event".
  * KDoc: the recipe NEVER fires the event silently.

- Register in AndroidRuntimeRecipes.
- Router: CREATE_CALENDAR_EVENT → CalendarEventRecipe.id.
- PromptCatalog: add intent example with parser-friendly phrasing.

Tests:
- DateTimeParserTest table over the cases above.
- CalendarEventRecipeTest:
  * happy path with parseable time.
  * happy path with no time (parser returns null).
  * Refused on missing title.
  * Refused on invalid time.

SELF-VERIFY
./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug

STOP CONDITIONS (revert + report only)
- CreateCalendarEvent's intent path in AndroidIntentDispatcher does
  not honor startEpochMs the way we assume.

DEBUG_LOG ENTRY: standard, plus prevention rule "any recipe that
parses natural-language time must include a table-driven parser
test covering the 7 cases above and must refuse on parse error with
explicit reason." 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS.

### ASSISTANT 2026-05-23T12:39:37.615Z

I’ll do this as a single implementation pass: first I’m going to read the local rules and the existing action/recipe/test patterns, then patch the parser, recipe registration, routing, prompts, tests, and debug log before running the full verification command.

### ASSISTANT 2026-05-23T12:39:44.395Z

I found the project debug protocol and a dirty untracked `handy_macos_ref/` directory that I won’t touch unless it is directly relevant. Next I’m reading the prevention rules and the existing recipe/action shape so the new calendar path fits what’s already there.

### ASSISTANT 2026-05-23T12:40:08.657Z

The standing rules that matter here are: keep `:core` Android-free, route every new recipe through `RecipeIntentRouter` plus runtime registration, add focused tests in the same change, and append a new DEBUG_LOG entry before calling it complete. I’m now checking the existing calendar dispatcher first because your stop condition depends on that path honoring `startEpochMs`.

### ASSISTANT 2026-05-23T12:40:19.007Z

The stop condition is clear: `AndroidIntentDispatcher.fireCreateEvent` already uses `CalendarContract.EXTRA_EVENT_BEGIN_TIME` and `EXTRA_EVENT_END_TIME`, so `startEpochMs` is honored by the OS calendar compose intent. That means it’s safe to proceed with the recipe layer instead of reverting/reporting.

### ASSISTANT 2026-05-23T12:42:43.769Z

**Plan**

Phase 4 hardening / deterministic action recipes. Plan sections advanced: agent-mode recipes, direct intent handoffs, and DEBUG_LOG prevention. Acceptance check: calendar requests become a safe OS-compose handoff, never a silent event creation, with parser and recipe tests.

Files I’ll touch:
- `core/src/main/kotlin/com/handy/core/agent/parsing/DateTimeParser.kt`: new bounded natural-language time parser, pure JVM.
- `core/src/test/kotlin/com/handy/core/agent/parsing/DateTimeParserTest.kt`: table-driven coverage for the required phrases and bounds.
- `android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/CalendarEventRecipe.kt`: new single-step calendar compose recipe.
- `android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/ClockRecipe.kt`: register the recipe in the runtime pack.
- `android-runtime/src/test/kotlin/com/handy/runtime/agent/recipes/CalendarEventRecipeTest.kt` and `RuntimeRecipePackTest.kt`: recipe behavior and pack registration.
- `android-runtime/src/test/kotlin/com/handy/runtime/action/DefaultActionPolicyEngineTest.kt`: confirm `CreateCalendarEvent` remains allowed.
- `core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt` and its test: add a parser-friendly calendar intent example.
- `DEBUG_LOG.md`: append DL-081 with the requested prevention rule after validation.

Interfaces I will not change:
- `AssistantAction.CreateCalendarEvent` shape stays exactly `title, startEpochMs?, endEpochMs?, location?, notes?`.
- `AndroidIntentDispatcher.fireCreateEvent` stays as-is because it already pre-fills `EXTRA_EVENT_BEGIN_TIME`.
- `DefaultActionPolicyEngine` production policy stays as-is unless a test exposes a real denial.

Risks and checks:
- Natural-language time can over-match locations like “at cafe”; I’ll keep parsing bounded and treat no time as `Result.success(null)`.
- Past/far-future times must be explicit refusals so the recipe returns `invalid-time`.
- Full verification will be the requested `./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug`, using the repo-local JDK if plain Java is still missing from PATH.

### ASSISTANT 2026-05-23T12:42:49.273Z

I’m going to make the parser intentionally small and boring: recognize the seven supported forms, validate against the local system zone, and expose typed failures so the calendar recipe can tell “bad/past time” apart from “I didn’t find a usable time.”

### ASSISTANT 2026-05-23T12:45:42.343Z

The recipe patch is next. I’m keeping it to a single native action so policy and execution remain the same as the existing timer/web-search handoffs: it proposes a plan, opens Calendar’s draft UI, and leaves the final Save under the user’s finger.

### ASSISTANT 2026-05-23T12:46:25.755Z

The core pieces are in place now. I’m doing a focused scan and test pass first, because natural-language parser bugs usually show up as tiny compile errors or over-eager regex behavior before they show up in the full Gradle run.

### ASSISTANT 2026-05-23T12:46:53.899Z

Focused tests passed for the new parser, recipe, pack registration, prompt copy, and policy check. I’m moving to the full requested Gradle lane now; if anything shakes loose there, I’ll fix it before writing the DEBUG_LOG entry.

### ASSISTANT 2026-05-23T12:47:25.586Z

The full requested Gradle lane passed cleanly. I’m doing the extra hygiene pass now: diff whitespace check, then I’ll try a launch smoke test if an emulator/device is available so we can catch obvious startup crashes too.

### ASSISTANT 2026-05-23T12:47:40.674Z

Install succeeded, but my smoke command scoped `PATH` too narrowly and the follow-up `adb` calls weren’t found. That’s a command wrapper mistake, not an app failure; I’m rerunning the launch/logcat part with `adb` exported for the whole shell.

### ASSISTANT 2026-05-23T12:47:54.850Z

The app launch smoke is clean too: installDebug succeeded, `OnboardingActivity` cold-launched with `Status: ok`, the app process stayed alive, and the logcat crash scan returned no matches. I’m writing the DEBUG_LOG entry now with the parser-specific prevention rule you asked for.

### ASSISTANT 2026-05-23T12:48:25.325Z

The DEBUG_LOG entry is appended as DL-081. I’m doing one final diff/status pass now so the close-out can be precise and not hand-wavey.

### ASSISTANT 2026-05-23T12:48:50.026Z

I added one extra recipe regression for the subtle branch: a parser failure like “next someday” still opens a Calendar draft with no time, while hard refusals like past dates return `invalid-time`. I’m rerunning the focused and full lanes so the final validation reflects the actual final diff.

### ASSISTANT 2026-05-23T12:50:12.399Z

Implemented the Calendar event flow end to end.

**What Changed**
Added [DateTimeParser.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/agent/parsing/DateTimeParser.kt:21) with bounded local-time parsing for the required phrases, plus [CalendarEventRecipe.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/agent/recipes/CalendarEventRecipe.kt:19). The recipe opens Android Calendar’s compose UI only. It never silently creates an event.

Before: “schedule dentist tomorrow 3 pm” had no deterministic runtime recipe path, so it could fall back to chat guidance or depend on a model-supplied epoch.
After: Handy proposes “Create calendar event”, opens Calendar with title/start/location/notes prefilled, and the user taps Save.

Practical examples:
- “schedule dentist tomorrow 3 pm at Smile Dental notes bring card” opens a draft with title, time, location, and notes.
- “schedule team sync” opens a title-only draft with no time prefilled.
- “schedule checkup 1990” is refused as `invalid-time`.
- “schedule tomorrow 3 pm” is refused as `missing-title`.

Registered it in `AndroidRuntimeRecipes`, kept `CREATE_CALENDAR_EVENT -> create_calendar_event`, and added parser-friendly prompt guidance in [PromptCatalog.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/prompts/PromptCatalog.kt:316). Added DL-081 to [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md).

**Verification**
Passed:
```bash
JAVA_HOME=/Users/satvik.bansal/.cache/codex-jdk17 PATH=/Users/satvik.bansal/.cache/codex-jdk17/bin:$PATH ./gradlew :core:test :android-runtime:test :app:test :app:lint :app:assembleDebug
```

Also passed focused parser/recipe/policy tests, `git diff --check`, `:app:installDebug`, and a launch smoke on `emulator-5554`. `OnboardingActivity` cold-launched with `Status: ok`, process stayed alive, and logcat had no `AndroidRuntime`, `FATAL EXCEPTION`, `am_crash`, or Handy crash matches.

**Manual Testing Plan**
1. From full chat and overlay, ask: “schedule dentist tomorrow 3 pm”. Approve the plan. Verify Calendar opens, fields are prefilled, and no event exists until Save is tapped.
2. Cancel/back out of Calendar. Verify no event was created.
3. Save once. Verify the event appears at the correct local time.
4. Test all parser phrases: “next Monday 9 am”, “this Friday 6:30 pm”, “in 2 hours”, “in 30 minutes”, “at 6”, “2026-06-01 14:00”, and “Saturday”.
5. Test no-time path: “schedule team sync”. Calendar should open with no start time prefilled.
6. Test refusal paths: “schedule checkup 1990”, a date more than one year out, and “schedule tomorrow 3 pm”.
7. Test location/notes: “schedule dentist tomorrow 3 pm at Smile Dental notes bring card”.
8. Disable/remove Calendar handler if possible and verify Handy does not crash.

Note: pre-existing untracked `handy_macos_ref/` is still untouched.

Advanced Phase 4 / agent-mode recipes plus direct intent handoffs. Acceptance check: calendar compose handoff with bounded parser and tests. Status: PASS.
