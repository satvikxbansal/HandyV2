# Hey, as you can see in the attached screenshots, I opened the photos app and then I clicked on Handy's floating widget. So it ope…

## Metadata

- Thread ID: `019e6a34-ac86-73e0-b14a-2afd95a96aaa`
- Created: 2026-05-27 21:41:37 IST
- Updated: 2026-05-28 16:59:44 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/27/rollout-2026-05-27T21-41-37-019e6a34-ac86-73e0-b14a-2afd95a96aaa.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `80d7f6fdffe5c8d9c5821dd9165162b21df9b79c`
- Messages exported: 75

## Brief Summary

This conversation focused on: Hey, as you can see in the attached screenshots, I opened the photos app and then I clicked on Handy's floating widget. So it ope… Likely related git changes: 40510d7 Automatic Foreground Tool Refresh in Chat Overlay; 585224d Fixed Foreground tool update bugs + Recipe stoppage when App not detected.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `80d7f6fdffe5c8d9c5821dd9165162b21df9b79c`
- Likely related commits:
  - `40510d7` 2026-05-28 09:58:23 IST [medium] Automatic Foreground Tool Refresh in Chat Overlay. Files: android-runtime/src/main/kotlin/com/handy/runtime/accessibility/AccessibilityMarksProvider.kt, app/src/androidTest/kotlin/com/handy/app/overlay/OverlayQuickChatPanelV2Test.kt, app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt, app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/main/kotlin/com/handy/app/overlay/PanelContextRefresher.kt, app/src/main/kotlin/com/handy/app/overlay/design/OverlayQuickChatPanelV2.kt, app/src/main/res/xml/accessibility_service_config.xml, app/src/test/kotlin/com/handy/app/overlay/OverlayChatPipelineTest.kt, app/src/test/kotlin/com/handy/app/overlay/OverlayPresenterPanelContextRefreshTest.kt, app/src/test/kotlin/com/handy/app/overlay/PanelContextRefresherTest.kt
  - `585224d` 2026-05-28 16:59:53 IST [medium] Fixed Foreground tool update bugs + Recipe stoppage when App not detected. Files: DEBUG_LOG.md, app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt, app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/main/kotlin/com/handy/app/overlay/PanelContextRefresher.kt, app/src/main/kotlin/com/handy/app/settings/SideBubblePreviewActivity.kt, app/src/main/kotlin/com/handy/app/widget/design/SideBubbleV2.kt, app/src/main/res/drawable/ic_lucide_flag.xml, app/src/test/kotlin/com/handy/app/agent/AgentSessionControllerTest.kt, app/src/test/kotlin/com/handy/app/foreground/HandyForegroundAppMonitorTest.kt, app/src/test/kotlin/com/handy/app/overlay/OverlayPresenterFsmTest.kt, app/src/test/kotlin/com/handy/app/overlay/OverlayPresenterPanelContextRefreshTest.kt

## Conversation

### USER 2026-05-27T16:11:45.002Z

Hey, as you can see in the attached screenshots, I opened the photos app and then I clicked on Handy's floating widget. So it opened up the bottom sheet, the overlay chat overlay in which it said in photos, what can I help you with? So basically the context of the tool was photos since I was in photos. And then I changed my window from the bottom slider. So I just moved to a different window, a different app that was open in the background by sliding the bottom bar of the Android phone and I ended up in YouTube. But then the chat overlay did not flinch and it still showed us photos. I moved to a different app and it still showed us photos. Unless I closed the chat overlay window and opened it again by clicking on the floating widget, the tool name did not change. Is there a way we can trigger the tool name change whenever the app in the foreground changes? It's an auto-trigger sort of a thing. Can you deeply analyze the current code of Handy, the current code of the chat overlay window, search the web if required for best practices here, all the possible resources on Android development by different developer forums, blogs, etc. to understand how such a thing can be implemented in Android and tell me how it can be done. We will then, and if it can be done well, I want you to evaluate deeply how we will implement it in Handy without damaging any of the existing functional and non-functional code, having without introducing any new bugs, app crashes or errors, ensuring the flying, the tool recognition, the LLM processing, the pointing, the navigation, the coordinates to which it has to fly to, the recipes, the tap for me, the agentic flows, everything functional and non-functional stays intact or is improved but does not degrade with the change that we are doing. I want you to be very thorough, deep thinking, evaluate the code deeply, connect the dots, understand the patterns and then go about creating the detailed prompt to implement it.

### ASSISTANT 2026-05-27T16:12:01.354Z

I’ll ground this in the actual Handy code first, then I’ll cross-check Android foreground-app detection patterns against current Android guidance. I’ll keep this strictly read-only and turn it into an implementation-ready plan/prompt rather than touching code.

### ASSISTANT 2026-05-27T16:12:18.238Z

I found Handy already has a `HandyForegroundAppMonitor` and the overlay presenter snapshots foreground context at widget tap, which is exactly where the stale label likely comes from. Now I’m reading the presenter/panel/monitor path to see whether we can switch from “cache once” to “observe while panel is open” without disturbing turns, recipes, flight, or action gating.

### ASSISTANT 2026-05-27T16:13:29.897Z

The important shape is emerging: full-screen `ChatActivity` already auto-switches tool history by collecting `ForegroundAppMonitor.flow`, but the overlay panel intentionally freezes `PanelSnapshot` for reliability while it has focus. The fix should add a live-display/current-tool layer for the panel while preserving the frozen turn snapshot used for LLM grounding and pointer coordinates.

### ASSISTANT 2026-05-27T16:14:44.306Z

I’m going to do one targeted test pass over the overlay presenter/pipeline tests so the plan’s acceptance criteria line up with the repo’s current test surface. This may write only Gradle/build cache artifacts; I’m not changing tracked source.

### ASSISTANT 2026-05-27T16:15:00.810Z

The targeted Gradle run is blocked because this machine doesn’t currently expose a Java runtime to the shell. I’ll still build the plan around the existing test files and call out the exact tests to add/run once JDK is available.

### ASSISTANT 2026-05-27T16:16:14.406Z

There’s one product behavior choice that changes the architecture, so I want to lock it before writing the final implementation prompt. The safe technical answer is “live-update the idle panel context, but never rewrite a turn that is already in flight”; the other options are possible but risk confusing grounding or cancelling work.

### USER 2026-05-27T16:17:06.211Z

Also, how I want the tool name on the overlay to change is not by the overlay closing and then opening again. You can give a small sort of a shimmer on the subtext and show it again if you need time to do the API call to get the foreground. If it can be instant, perfect. If not, for any delays, you can introduce a minimal loading state either in the tool name placeholder or the complete subtext. I would prefer putting the loading state in the tool name placeholder part of the subtext. So some sort of a shimmer aesthetic classy shimmer would help, would do.

### ASSISTANT 2026-05-27T16:17:19.536Z

Got it: no close/reopen choreography. I’ll plan this as an in-place context refresh with a small loading/shimmer state scoped to the app-name portion of the subtext, and with active turns kept stable until they finish.

### ASSISTANT 2026-05-27T16:21:48.884Z

<proposed_plan>
# Live Foreground Context For Overlay Chat

## Summary
Implement in-place tool/context switching for Handy’s overlay panel when the Android foreground app changes, without closing/reopening the panel. Use the existing Accessibility-based foreground monitor, not `UsageStatsManager`, because Handy already has the right Accessibility window privileges and Usage Stats would add a sensitive extra permission.

Android docs support this path: `getWindows()` returns interactive windows in top-layer order, `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` is required for that, and `TYPE_APPLICATION_OVERLAY` windows sit above apps but below system UI. Sources: [AccessibilityService.getWindows](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo), [AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent.html), [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams), [Play Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491).

## Key Changes
- Strengthen [HandyForegroundAppMonitor.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt) so app switches are resolved from `AccessibilityService.getWindows()` instead of trusting `rootInActiveWindow`, which can point at Handy’s overlay while the panel is open.
- Add `TYPE_WINDOWS_CHANGED` handling alongside `TYPE_WINDOW_STATE_CHANGED`, keep distinct-by package/site, and continue filtering Handy, IME, launcher, recents, and System UI packages.
- Extend `AccessibilityMarksProvider` with a package-targeted collection path, e.g. `collectForPackage(packageName)`, so after Photos -> YouTube the next turn gets YouTube marks, not Handy overlay marks.
- Add panel context refresh state to `PanelContent`: `contextRefreshInProgress`, optional `refreshingToolLabel`, and a deferred `pendingSnapshot` or equivalent presenter-private pending snapshot.
- In [OverlayChatPanelService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelService.kt), collect foreground changes only while `OverlayMode.ChatPanel` is visible. On change, start refresh, collect target-app marks, then call a new presenter method to apply the new `PanelSnapshot`.
- Do not dismiss, detach, or restart the overlay panel for context changes.

## Behavior Rules
- If the panel is idle/listening/editing, switch the visible greeting and active `panel.snapshot` in place as soon as the new foreground snapshot is ready.
- If Handy is processing/streaming/confirming/acting, keep the current turn locked to its original snapshot and apply the new app context after that turn finishes. This protects LLM grounding, recipes, pointing coordinates, tap/type policy, and flight behavior.
- While refresh is pending, show a classy shimmer only on the app-name/tool-name portion of the subtext. If instant, skip the shimmer entirely.
- Preserve draft input, recent response preview, voice state, pending confirmations, bubbles, and panel visibility during context refresh.
- Do not surface launcher/recents/System UI as a tool context.

## UI
- Update [OverlayQuickChatPanelV2.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/design/OverlayQuickChatPanelV2.kt) so `ContextLineV2` can render either the normal accented app label or an animated shimmer span/skeleton in that label slot.
- Avoid visible explanatory copy like “detecting app”; the loading affordance should live in the label position only.
- Keep layout stable: one-line text, same height, no panel jump, no composer reset.

## Test Plan
- Add presenter tests: idle app switch applies immediately; streaming app switch defers; deferred context applies after final response; draft input survives; same package/site is ignored.
- Add pipeline test: after a Photos -> YouTube panel refresh, `OverlayChatPipeline.runTurn` uses YouTube `ToolContext` and YouTube marks.
- Add UI/Compose test: refreshed panel renders the new greeting in place and no stale Photos greeting remains.
- Manual QA: Photos -> open overlay -> switch to YouTube via bottom gesture -> subtext updates without closing; send a query and verify YouTube history/key/context; repeat while streaming to confirm deferred switch.
- Run once JDK is available: `./gradlew :app:testDebugUnitTest --tests com.handy.app.overlay.OverlayPresenterFsmTest --tests com.handy.app.overlay.OverlayChatPipelineTest --tests com.handy.app.overlay.PanelGreetingCatalogTest` and the overlay Compose android test.
</proposed_plan>

### USER 2026-05-27T16:23:44.511Z

Below is a response from Claude on what it would do. There is also a detailed prompt that it gave. Can you go through it, evaluate it, understand it, and compare it with the plan that you had come up with, and tell me what can be done better in the Claude's plan? What were some things that you could think of that Claude couldn't, and what more do we need to keep in mind if we were to only implement Claude's plan? 

Let me deeply investigate the current code before designing the fix. I need to understand: (1) how the panel captures the tool context today, (2) what foreground-monitoring infrastructure already exists, (3) where the snapshot gets refreshed, and (4) whether updating mid-panel risks breaking the recipe/flight/pointing flows that depend on the captured snapshot.Let me also do a quick check on Android best practices — though I'm confident the accessibility-event path is already the right approach, let me verify against current developer guidance.I have a complete picture. The infrastructure already exists; the fix is purely about wiring an observer with strict guards. Here's the analysis and the prompt.

---

# Part 1 — What I found in the code

The whole foreground-monitoring stack already exists and uses the right Android primitive.

1. **`HandyForegroundAppMonitor`** (`app/foreground/HandyForegroundAppMonitor.kt`) — already implements both paths:
   - **Event-driven**: `onAccessibilityEvent(event, root)` consumes `TYPE_WINDOW_STATE_CHANGED` from `HandyAccessibilityService`. Filters self-package, IMEs, launchers, system UI. Builds a `ForegroundAppSnapshot(packageName, appLabel, umbrellaSiteLabel, umbrellaSiteUrl)` and emits to a `MutableSharedFlow(replay=1)` deduped via `distinctUntilChanged { old, new -> old.packageName == new.packageName && old.umbrellaSiteLabel == new.umbrellaSiteLabel }`.
   - **Proactive poll**: `refreshNow()` walks `service.windows`, picks the highest-layer `TYPE_APPLICATION` window that isn't self/IME/launcher, and emits.
   - This is exactly the technique the 2026 Android dev community recommends — `TYPE_WINDOW_STATE_CHANGED` via `AccessibilityService` is the best-practice signal for live foreground detection. `UsageStatsManager` is the only alternative and it requires `PACKAGE_USAGE_STATS`, has noticeable latency, and is unreliable for transient window swaps like recents-bar app switches.

2. **`OverlayPresenter.captureSnapshot(...)`** (line 950) — calls `foregroundAppMonitor.refreshNow()` then falls back to `lastKnownSnapshot()`. This is invoked from `onWidgetTap` and `onWidgetLongPressArmed`. The result is stored as `PanelContent.snapshot`.

3. **The bug is architectural, intentional, and documented.** Comment at line 45-49:
    > Cache-at-tap (cursorbuddy recipe #4):
    > - `onWidgetTap` and `onWidgetLongPressArmed` snapshot the current tool context + accessibility marks BEFORE the panel takes focus.
    > - The panel consumes the cached `PanelSnapshot`; it never re-reads `rootInActiveWindow` mid-panel.

   This cache-at-tap is correct policy for the *running turn* (the LLM, the marks, the pointer all must agree on a frozen ground truth). It is incorrect policy for the *idle moments between turns* — which is exactly what the user is reporting. The presenter has no listener on `foregroundAppMonitor.flow` while the panel is visible.

4. **Constraints we must preserve** (verified across the codebase):
   - `OverlayChatPipeline.runTurn` (line 121) reads `presenter.state.value.panel.snapshot` to build `panelSnapshot` → fed to `ScreenContextBuilder.build` → feeds the LLM + marks for pointer resolution. Mid-stream refresh would corrupt this.
   - `BuddyFlightDriver.flyTo / flyToAndTap / flyToAndType` resolve targets against the captured marks. Mid-flight refresh would invalidate landing coordinates.
   - Recipes (`AgentSessionController.runIfRecipeRequested`) execute against the snapshot's `toolContext.packageName`. Mid-recipe refresh would dispatch the recipe against the wrong package.
   - Tap-for-me confirmation (`OverlayPresenter.requestTapForMeConfirmation`) waits on a continuation; if we refresh the snapshot during the wait, the user sees the new app context but the action they're about to confirm is for the old one. Dangerous.

So: **refresh ONLY when the panel is in a fully quiet state.**

# Part 2 — Architecture

A single tiny bridge class, plus one new presenter method with strict guards:

```
HandyAccessibilityService.onAccessibilityEvent
        │
        ▼
HandyForegroundAppMonitor.onAccessibilityEvent
        │
        ▼
HandyForegroundAppMonitor.flow  (SharedFlow, replay=1, distinct by pkg+umbrella)
        │
        ▼
PanelContextRefresher  (NEW — observes, debounces 300ms)
        │
        ▼
OverlayPresenter.refreshPanelContextIfIdle(fg, marksProvider)
        │
        ▼  (if guards pass)
panel.snapshot ← new ToolContext + fresh marks
panel.greeting ← panelGreetingFor(newSnapshot)
panel.draftInput preserved
```

`PanelContextRefresher` is a `@Singleton` started once from `HandyApplication.onCreate()`. Because the flow is `replay=1 + distinctUntilChanged`, observing it forever is essentially free; the presenter's guards reject every emission that arrives while the panel isn't open or any in-flight operation is active.

# Part 3 — The prompt for Codex

```
PROMPT P-PANEL-FOLLOW — Make the overlay quick-chat panel follow the
foreground app when the panel is open and idle. The infrastructure to
detect foreground app changes already exists (HandyForegroundAppMonitor
emits via TYPE_WINDOW_STATE_CHANGED from HandyAccessibilityService).
This prompt only adds an observer + a strictly guarded presenter method
that refreshes the panel's snapshot, greeting, and marks when it is
safe to do so.

VERIFIED PRE-CONDITIONS — read first
- app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt
  exposes `flow: Flow<ForegroundAppSnapshot>` (SharedFlow with replay=1
  and `distinctUntilChanged` by packageName+umbrellaSiteLabel), plus
  `refreshNow(): ForegroundAppSnapshot?` and `lastKnownSnapshot()`.
  Launcher / IME / self-package / system-UI filtering is already done
  inside the monitor — DO NOT replicate that filtering elsewhere.
- app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt
  - Cache-at-tap is documented at lines 45-49 and implemented at
    `onWidgetTap` (line 136) / `onWidgetLongPressArmed` (line 173).
    Do NOT change those — they remain the authoritative ground truth
    for any turn that is already in flight.
  - `captureSnapshot(...)` at line 950 is the canonical builder for a
    PanelSnapshot from the monitor + a marks provider. Reuse this
    function inside the new presenter method.
  - `panelGreetingFor(snapshot)` is the pure function in the same file
    that maps a snapshot to the muted greeting subtitle.
- app/src/main/kotlin/com/handy/app/overlay/OverlayChatPipeline.kt
  reads `presenter.state.value.panel.snapshot` inside `runTurn` (line
  127). The guards in this prompt prevent any refresh from racing the
  LLM call.
- core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt has
  `PanelContent.draftInput`, `isListening`, `isStreaming`,
  `pendingConfirmation`, `lowConfidenceTranscript`,
  `recentResponsePreview`, `errorBanner`. We preserve draftInput,
  recentResponsePreview, and errorBanner across refresh; we let
  voiceNotice / partialTranscript be wiped (they were tied to the old
  context anyway).
- HandyAccessibilityService is the source of TYPE_WINDOW_STATE_CHANGED
  events that drive the monitor — verify by `git grep
  "onAccessibilityEvent" app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt`
  and confirm it forwards to foregroundAppMonitor.onAccessibilityEvent.

────────────────────────────────────────────────────────────────────────
(A) NEW PRESENTER METHOD — strict guards
────────────────────────────────────────────────────────────────────────

File: app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt

Append this method to OverlayPresenter (alongside the existing
captureSnapshot at line 950). Do NOT modify captureSnapshot,
onWidgetTap, onWidgetLongPressArmed, or any other entry point.

    /**
     * Called by [PanelContextRefresher] when the foreground app changes
     * while the panel is open. Refreshes the panel's tool context,
     * greeting, and marks IF AND ONLY IF the panel is in a fully quiet
     * state. Preserves the user's draft input, recent response preview,
     * and any error banner.
     *
     * IMPORTANT — the cache-at-tap policy is preserved for in-flight
     * turns: this method refuses to refresh while any voice, LLM, or
     * action operation is active. The check is intentionally
     * defence-in-depth — any new in-flight state introduced later must
     * either pass through one of these flags or this method must be
     * updated.
     */
    fun refreshPanelContextIfIdle(
        foreground: ForegroundAppSnapshot,
        marksProvider: () -> List<AccessibilityMark>,
        clock: () -> Long = { System.currentTimeMillis() },
    ) {
        val current = _state.value

        // (1) Panel must be open.
        if (current.mode != OverlayMode.ChatPanel) return

        // (2) Buddy must be docked or in audio-only state. Any in-flight
        //     widget state — listening, thinking, streaming, preparing,
        //     flying, pointing, acting, dragging — locks the snapshot.
        if (current.buddyState !in QUIET_BUDDY_STATES) return

        // (3) Flight FSM must be at rest.
        if (current.isFlying) return
        if (current.flightFsm !in QUIET_FLIGHT_FSM) return

        // (4) Panel-side in-flight checks.
        val panel = current.panel
        if (panel.isListening) return
        if (panel.isStreaming) return
        if (panel.pendingConfirmation != null) return
        if (panel.lowConfidenceTranscript != null) return

        // (5) Tap-for-me / candidate-options modals are blocking.
        if (current.tapForMeConfirmation != null) return
        if (current.candidateOptions?.visible == true) return

        // (6) Manual-target-fallback is mid-pick.
        if (manualTargetFallbackCandidates.isNotEmpty()) return

        // (7) De-dupe: if package + umbrella didn't change, skip the work.
        //     The foreground monitor already dedupes by these fields,
        //     but defence-in-depth: a stray emission shouldn't trigger
        //     a marks re-walk.
        val priorContext = panel.snapshot?.toolContext
        val sameApp = priorContext?.packageName == foreground.packageName
        val sameUmbrella = priorContext?.umbrellaSiteLabel == foreground.umbrellaSiteLabel
        if (sameApp && sameUmbrella) return

        // Build the refreshed snapshot. NOTE — we call marksProvider
        // synchronously here because (a) this method runs on the
        // foreground monitor's collector dispatcher, which is already
        // off the main thread, and (b) marksProvider already protects
        // itself with runCatching internally.
        val marks = runCatching { marksProvider() }.getOrElse {
            Timber.w(it, "OverlayPresenter.refreshPanelContextIfIdle: marks read failed")
            emptyList()
        }
        val newSnapshot = PanelSnapshot(
            toolContext = ToolContext(
                packageName = foreground.packageName,
                appLabel = foreground.appLabel,
                umbrellaSiteLabel = foreground.umbrellaSiteLabel,
            ),
            capturedAtEpochMs = clock(),
            marks = marks,
        )
        val newGreeting = panelGreetingFor(newSnapshot)

        setState(event = "refreshPanelContextIfIdle") { it.copy(
            panel = it.panel.copy(
                snapshot = newSnapshot,
                greeting = newGreeting,
                // PRESERVE the user's typed text, the response preview
                // from the last turn, and any error banner. Wipe the
                // voiceNotice / partialTranscript which were tied to
                // the previous context.
                voiceNotice = "",
                partialTranscript = "",
                lowConfidenceTranscript = null,
            )
        ) }
        Timber.d(
            "OverlayPresenter.refreshPanelContextIfIdle: %s -> %s (umbrella=%s, marks=%d)",
            priorContext?.displayLabel ?: "(none)",
            newSnapshot.toolContext.displayLabel,
            newSnapshot.toolContext.umbrellaSiteLabel ?: "—",
            marks.size,
        )
    }

    private companion object {
        val QUIET_BUDDY_STATES: Set<BuddyState> = setOf(
            BuddyState.DOCKED,
            BuddyState.AUDIO_SPEAKING,
        )
        val QUIET_FLIGHT_FSM: Set<FlightFsm> = setOf(
            FlightFsm.Docked,
        )
    }

If the file already has a `private companion object`, MERGE the two
sets into it; do not duplicate the companion.

────────────────────────────────────────────────────────────────────────
(B) THE BRIDGE — PanelContextRefresher
────────────────────────────────────────────────────────────────────────

File (NEW): app/src/main/kotlin/com/handy/app/overlay/PanelContextRefresher.kt

    package com.handy.app.overlay

    import com.handy.core.overlay.AccessibilityMark
    import com.handy.runtime.accessibility.AccessibilityMarksProvider
    import com.handy.runtime.di.ApplicationScope
    import javax.inject.Inject
    import javax.inject.Singleton
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.FlowPreview
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.flow.debounce
    import kotlinx.coroutines.launch
    import com.handy.app.foreground.HandyForegroundAppMonitor
    import timber.log.Timber

    /**
     * Subscribes to [HandyForegroundAppMonitor.flow] and forwards
     * emissions to [OverlayPresenter.refreshPanelContextIfIdle]. Started
     * once from [com.handy.app.HandyApplication.onCreate] — no stop is
     * needed; the appScope lives for the process lifetime.
     *
     * The 300 ms debounce smooths over rapid app-switch sequences
     * (e.g. recents-swipe pauses on app A before settling on app B).
     * The presenter's guard set catches everything else.
     */
    @Singleton
    class PanelContextRefresher @Inject constructor(
        private val presenter: OverlayPresenter,
        private val foregroundAppMonitor: HandyForegroundAppMonitor,
        private val marksProvider: AccessibilityMarksProvider,
        @ApplicationScope private val appScope: CoroutineScope,
    ) {
        @Volatile private var job: Job? = null

        @OptIn(FlowPreview::class)
        fun start() {
            if (job?.isActive == true) return
            job = appScope.launch {
                foregroundAppMonitor.flow
                    .debounce(DEBOUNCE_MS)
                    .collect { fg ->
                        runCatching {
                            presenter.refreshPanelContextIfIdle(
                                foreground = fg,
                                marksProvider = ::readMarks,
                            )
                        }.onFailure {
                            Timber.w(it, "PanelContextRefresher: refresh failed")
                        }
                    }
            }
        }

        fun stop() {
            job?.cancel()
            job = null
        }

        private fun readMarks(): List<AccessibilityMark> =
            runCatching { marksProvider.provide() }
                .onFailure { Timber.w(it, "PanelContextRefresher: marks read failed") }
                .getOrElse { emptyList() }

        private companion object {
            const val DEBOUNCE_MS: Long = 300L
        }
    }

If `AccessibilityMarksProvider.provide()` has a different name (e.g.
`marks()`), use the existing method — do not invent.

────────────────────────────────────────────────────────────────────────
(C) START THE BRIDGE
────────────────────────────────────────────────────────────────────────

File: app/src/main/kotlin/com/handy/app/HandyApplication.kt
(or whichever class is annotated @HiltAndroidApp).

Inject `PanelContextRefresher` and call `start()` in `onCreate()`,
AFTER `super.onCreate()`:

    @Inject lateinit var panelContextRefresher: PanelContextRefresher

    override fun onCreate() {
        super.onCreate()
        panelContextRefresher.start()
    }

────────────────────────────────────────────────────────────────────────
(D) TESTS
────────────────────────────────────────────────────────────────────────

D.1 New unit test: OverlayPresenterPanelContextRefreshTest
    Cover the guard matrix:
      - mode != ChatPanel → no refresh
      - panel.isListening → no refresh
      - panel.isStreaming → no refresh
      - panel.pendingConfirmation != null → no refresh
      - panel.lowConfidenceTranscript != null → no refresh
      - tapForMeConfirmation != null → no refresh
      - candidateOptions.visible == true → no refresh
      - manualTargetFallbackCandidates non-empty → no refresh
      - isFlying == true → no refresh
      - buddyState in {LISTENING, THINKING, STREAMING, FLYING,
        POINTING, ACTING, PREPARING_POINT, CANCELLING, DRAGGING} → no refresh
      - buddyState == DOCKED + everything else quiet → refresh fires
      - buddyState == AUDIO_SPEAKING + everything else quiet → refresh fires
      - same-app emission (no package or umbrella change) → no refresh
    And on a successful refresh:
      - panel.snapshot.toolContext.packageName is the new package
      - panel.greeting matches panelGreetingFor(newSnapshot)
      - panel.draftInput is preserved
      - panel.recentResponsePreview is preserved
      - panel.errorBanner is preserved
      - panel.voiceNotice is cleared
      - panel.partialTranscript is cleared

D.2 New unit test: PanelContextRefresherTest
    With a fake foreground monitor (MutableSharedFlow) and a fake
    presenter:
      - emit two distinct snapshots 200 ms apart → only the second is
        forwarded (debounce squelches the first).
      - emit one snapshot, advance >300 ms → presenter.refreshPanelContextIfIdle
        called exactly once with the right fg + marks-provider result.
      - start() is idempotent (calling twice doesn't double-collect).

D.3 Update OverlayPresenterFsmTest if its setup constructs an
    OverlayPresenter without the foregroundAppMonitor — it already
    injects it, so no change expected.

D.4 Manual on Pixel:
    a. Open Photos. Tap the Handy widget. Confirm greeting reads
       "In Photos. Describe a photo or find one?".
    b. Swipe the gesture bar to YouTube. Within ~500 ms confirm the
       greeting becomes "In YouTube. Summarise or pick what's next?"
       (or whatever the catalog returns for VIDEO category).
    c. Type "what's in this photo?" in the composer in step (a) before
       swiping. After swipe, confirm the draft is preserved and the
       greeting changes.
    d. Mid-stream test: type a query, hit send, then while the response
       is streaming, swipe to a new app. Confirm the greeting does NOT
       change until the stream finishes and audio (if any) settles.
    e. Tap-for-me test: trigger a tap-for-me confirmation. While the
       confirmation sheet is showing, swipe to a new app. Confirm the
       sheet's app/package label does NOT change (the action is still
       targeting the old app).
    f. Voice test: hold the widget long-press, start speaking. Swipe
       mid-utterance. Confirm the panel's listening state and tool
       context don't change until the utterance finalises.
    g. Flying / pointing test: trigger a flight ("show me storage").
       While the pointer is sticky on the target, swipe to a new app.
       Confirm the snapshot stays anchored to the original app. Tap
       the pointer dismiss; THEN a refresh may fire on the next
       foreground event.
    h. Launcher test: swipe to home screen. Confirm the panel stays
       on the previous app's context (the monitor sets
       lastSnapshot=null when the foreground is the launcher, so no
       emission fires — which is correct).

────────────────────────────────────────────────────────────────────────
(E) CODE-STRENGTHENING DELTAS while you're here (small, related)
────────────────────────────────────────────────────────────────────────

E.1 `HandyForegroundAppMonitor.refreshNow()` walks `service.windows`
    every call. The new refresh path can fire on every foreground
    emission. To avoid hammering the accessibility service, the
    PanelContextRefresher uses `foregroundAppMonitor.flow` (NOT
    `refreshNow()`) so we only pay the cost the monitor already paid
    inside its event handler. Do not call `refreshNow()` from the
    refresher.

E.2 If `AccessibilityMarksProvider.provide()` is synchronous and may
    block briefly on the accessibility tree, that's acceptable here
    because the refresher runs on `@ApplicationScope` (Dispatchers.Default
    by convention). Verify the existing scope in
    `android-runtime/.../di/RuntimeModule.kt provideApplicationScope`
    — if it's Dispatchers.Main, switch the collect to `.flowOn(
    Dispatchers.Default)` before `.collect`.

────────────────────────────────────────────────────────────────────────
(F) DO NOT
────────────────────────────────────────────────────────────────────────

- Modify `onWidgetTap`, `onWidgetLongPressArmed`, or `captureSnapshot`.
- Add a foreground refresh anywhere inside `OverlayChatPipeline.runTurn`.
- Refresh during AUDIO_SPEAKING WITHOUT the quiet-state checks — the
  quiet-state checks cover it; do not loosen them.
- Add UI affordances (no "Pinned to Photos" pill, no manual unpin
  button). Silent refresh only for this prompt.
- Touch chat history, recipe definitions, or the policy engine.
- Add new tokens to HandyDesign.
- Persist or audit the refresh events. They're transient view-state
  changes.

────────────────────────────────────────────────────────────────────────
(G) UPDATE
────────────────────────────────────────────────────────────────────────

- DEBUG_LOG.md: append DL-PANEL-FOLLOW summarising the guard set, the
  300 ms debounce, the cache-at-tap preservation for in-flight turns,
  and the draft-input preservation.

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
```

# Why this is safe

The cache-at-tap invariant the rest of Handy relies on is preserved by the guard set:
- Recipes, flight resolution, and the LLM all read `panel.snapshot` only during a turn (`isStreaming==true` or `isListening==true` or `pendingConfirmation != null` etc.). All of those gate-out the refresh.
- Pointer flight and tap-for-me use `buddyState in {PREPARING_POINT, FLYING, POINTING, ACTING}` — all blocked.
- Tap-for-me confirmation and manual-target-fallback both have explicit guards.
- The de-dupe at step (7) prevents thrashing when the monitor re-emits the same app (e.g. browser URL change in the same app — but we DO refresh on umbrella site changes because the greeting catalog cares about umbrella).

The user's `draftInput` is preserved, so typing → swiping → typing more is non-disruptive. The greeting and the placeholder follow the host app; the marks are refreshed so a subsequent voice query will resolve against the right tree.

Sources:
- [AccessibilityService — Android Developers](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Create an accessibility service — Android Developers](https://developer.android.com/guide/topics/ui/accessibility/service)
- [Foreground app detection via AccessibilityService and TYPE_WINDOW_STATE_CHANGED — comparative implementation reference](https://github.com/ngdathd/ForegroundActivity)

### ASSISTANT 2026-05-27T16:24:14.827Z

Claude’s plan is directionally good, but it under-specifies the hardest part: while the overlay panel is open, `rootInActiveWindow` and plain marks collection may point at Handy’s own overlay, not the app behind it.

The biggest upgrades I’d make:

1. **Strengthen foreground detection, not just observe it.**  
   Claude mostly trusts existing `TYPE_WINDOW_STATE_CHANGED`. My plan adds `TYPE_WINDOWS_CHANGED` and leans harder on `AccessibilityService.getWindows()` because Android reports interactive windows in descending layer order. That matters during gesture-bar app switches and overlay focus weirdness.

2. **Target marks by package.**  
   Claude’s `PanelContextRefresher` calls `marksProvider.provide()` or similar, but the actual code has `collect()`, and it reads `rootInActiveWindow`. With the panel open, that can collect Handy overlay nodes instead of YouTube nodes. We need `collectForPackage(packageName)` or a window-root based collector.

3. **Add in-place loading UI.**  
   Claude explicitly says “no UI affordances,” which misses your updated requirement. We should add a shimmer/loading state only in the app-name span of the subtext, without closing/reopening the overlay.

4. **Handle deferred updates explicitly.**  
   Claude’s method simply returns if the panel is streaming/acting/listening. That can drop the foreground change forever unless another foreground event arrives later. My plan keeps a pending snapshot/update and applies it after the current turn finishes.

5. **Don’t wipe voice fields casually.**  
   Claude says to clear `voiceNotice` / `partialTranscript`. If the guard is truly idle, they should already be empty; if not, clearing them is a footgun. Better to preserve panel state unless there is a very specific reason to reset it.

6. **Application-wide singleton is broader than needed.**  
   Starting `PanelContextRefresher` from `HandyApplication` works, but observing from `OverlayChatPanelService` only while the panel is visible is cleaner, cheaper, and matches the feature’s lifecycle.

7. **Fix API inaccuracies.**  
   Claude references `AccessibilityMarksProvider.provide()`, but the code method is `collect()`. It also assumes the app scope dispatcher may be non-main. The implementation must explicitly do heavy a11y/marks work safely and on the right thread.

If you implemented only Claude’s plan, the main risks are: stale or wrong marks, missed app switches during active turns, no shimmer/loading state, and possible collection of Handy overlay content instead of the target app. The core idea is solid; it just needs the window-targeted refresh and deferred-apply layer to be truly safe for Handy’s LLM grounding, recipes, pointing, and tap-for-me flows.

Best combined plan: use Claude’s strict guard mindset, but add my package-targeted `getWindows()`/marks refresh, `TYPE_WINDOWS_CHANGED`, deferred pending context, and your shimmer-on-tool-name UI requirement.

### USER 2026-05-28T03:56:21.363Z

Great. can you now create a revised consolidated stronger and better plan to tackle the tool name update in the chat overlay basis your initial plan and claude's plan. create a final plan that has all the good parts, weaker links strengthened and is a comprehensive one after which we will go about building this solution

### ASSISTANT 2026-05-28T03:58:07.281Z

<proposed_plan>
# Consolidated Plan: Live Tool Name Updates In Overlay Chat

## Summary
Make the overlay chat panel follow the foreground app in place when the user switches apps behind it, without closing/reopening the panel. Preserve Handy’s existing cache-at-turn invariant: the active LLM turn, recipe, pointer flight, and tap/type confirmation must keep using the snapshot they started with.

Use the existing AccessibilityService foreground-monitoring path, strengthen it with window-based detection, add package-targeted mark collection, and add a small shimmer state in the app-name portion of the overlay subtext while the refreshed context is being prepared.

## Key Decisions
- **No `UsageStatsManager` / `PACKAGE_USAGE_STATS`:** stay with Accessibility because Handy already depends on it and it is the right real-time signal for overlays.
- **In-place only:** never dismiss, detach, restart, or reopen `OverlayChatPanelService` just to refresh the tool name.
- **Apply after active turn:** if the user switches apps while Handy is listening, streaming, speaking, pointing, confirming, acting, or running a recipe, queue the latest foreground app and apply it only after Handy returns to a fully quiet panel state.
- **Targeted marks:** never use plain `rootInActiveWindow` for refresh marks while the panel is open; it may point to Handy’s overlay. Collect marks from the topmost `TYPE_APPLICATION` window whose root package matches the new foreground package.
- **Shimmer behavior:** show a subtle shimmer only in the app-name/tool-name slot while an idle panel is refreshing. Do not show explanatory copy.

## Implementation Changes
- Strengthen [HandyAccessibilityService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt) and `accessibility_service_config.xml` to forward both `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOWS_CHANGED` into the foreground monitor.
- Refactor [HandyForegroundAppMonitor.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt) so foreground snapshots prefer `AccessibilityService.windows`, sorted by layer, filtered for Handy/IME/launcher/System UI. Keep browser umbrella-site extraction when the matched root is a known browser.
- Extend `AccessibilityMarksProvider` with `collectForPackage(packageName: String)`, implemented by walking the highest-layer application window for that package and reusing the current bounded/redacted mark-building logic.
- Add panel refresh fields to `PanelContent`: `contextRefreshInProgress`, `contextRefreshPreviewGreeting`, and `contextRefreshPreviewLabel`. Preserve all existing fields by default.
- Add presenter methods in [OverlayPresenter.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt):
  - begin refresh only if panel is visible and quiet
  - apply refreshed `PanelSnapshot` and `panelGreetingFor(...)`
  - clear refresh loading
  - preserve `draftInput`, `recentResponsePreview`, `errorBanner`, voice state, confirmations, and bubbles
- Add `PanelContextRefresher`, owned by [OverlayChatPanelService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelService.kt), not app-wide. It starts collecting only while the panel is visible, debounces foreground changes around 250-300 ms, keeps only the latest pending app, collects targeted marks, and retries when the presenter becomes quiet.
- Update [OverlayQuickChatPanelV2.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/design/OverlayQuickChatPanelV2.kt) so `ContextLineV2` can split greeting text into prefix/app-label/suffix and render the label segment as either accented text or a stable-width shimmer placeholder.

## Safety Rules
- Do not refresh `panel.snapshot` inside `OverlayChatPipeline.runTurn`.
- Do not mutate snapshots during `isListening`, `isStreaming`, `audioState != IDLE`, `flightFsm != Docked`, `isFlying`, `tapForMeConfirmation`, `pendingConfirmation`, `candidateOptions`, low-confidence transcript, manual target selection, recipe/action progress, or pointer states.
- If multiple app switches happen during an active turn, keep only the latest pending foreground app.
- If the foreground is launcher/recents/System UI, keep the previous applied context and do not show a refresh.
- If targeted mark collection fails, apply the new tool context with empty marks rather than staying stale; the next turn can still fall back to live screen/capture logic.

## Test Plan
- Presenter unit tests: idle refresh applies; busy refresh defers; latest pending app wins; same app/site is ignored; draft/preview/error are preserved; shimmer fields clear after apply.
- Refresher tests: debounce collapses rapid foreground emissions; start/stop is idempotent; pending refresh applies on quiet transition; no duplicate collectors.
- Marks-provider tests: package-targeted collection ignores Handy overlay roots and uses the matching app window.
- Pipeline test: after Photos -> YouTube refresh, the next overlay turn uses YouTube `ToolContext`, history key, and marks.
- Compose test: context line renders old label normally, shimmer during refresh, and new greeting after apply without duplicate stale text.
- Manual QA: Photos -> overlay -> swipe to YouTube updates in place; typing draft survives; mid-stream switch applies after response/audio settles; tap-for-me confirmation keeps old app until dismissed; home/recents do not replace the tool name.

## Assumptions
- Accessibility permission remains the required source of foreground context.
- No new user-facing setting, audit event, persistent history migration, or recipe-policy change is needed.
- The refreshed panel context affects only future overlay turns; anything already submitted remains grounded to its original snapshot.
</proposed_plan>

### USER 2026-05-28T03:58:15.195Z

PLEASE IMPLEMENT THIS PLAN:
# Consolidated Plan: Live Tool Name Updates In Overlay Chat

## Summary
Make the overlay chat panel follow the foreground app in place when the user switches apps behind it, without closing/reopening the panel. Preserve Handy’s existing cache-at-turn invariant: the active LLM turn, recipe, pointer flight, and tap/type confirmation must keep using the snapshot they started with.

Use the existing AccessibilityService foreground-monitoring path, strengthen it with window-based detection, add package-targeted mark collection, and add a small shimmer state in the app-name portion of the overlay subtext while the refreshed context is being prepared.

## Key Decisions
- **No `UsageStatsManager` / `PACKAGE_USAGE_STATS`:** stay with Accessibility because Handy already depends on it and it is the right real-time signal for overlays.
- **In-place only:** never dismiss, detach, restart, or reopen `OverlayChatPanelService` just to refresh the tool name.
- **Apply after active turn:** if the user switches apps while Handy is listening, streaming, speaking, pointing, confirming, acting, or running a recipe, queue the latest foreground app and apply it only after Handy returns to a fully quiet panel state.
- **Targeted marks:** never use plain `rootInActiveWindow` for refresh marks while the panel is open; it may point to Handy’s overlay. Collect marks from the topmost `TYPE_APPLICATION` window whose root package matches the new foreground package.
- **Shimmer behavior:** show a subtle shimmer only in the app-name/tool-name slot while an idle panel is refreshing. Do not show explanatory copy.

## Implementation Changes
- Strengthen [HandyAccessibilityService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt) and `accessibility_service_config.xml` to forward both `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOWS_CHANGED` into the foreground monitor.
- Refactor [HandyForegroundAppMonitor.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt) so foreground snapshots prefer `AccessibilityService.windows`, sorted by layer, filtered for Handy/IME/launcher/System UI. Keep browser umbrella-site extraction when the matched root is a known browser.
- Extend `AccessibilityMarksProvider` with `collectForPackage(packageName: String)`, implemented by walking the highest-layer application window for that package and reusing the current bounded/redacted mark-building logic.
- Add panel refresh fields to `PanelContent`: `contextRefreshInProgress`, `contextRefreshPreviewGreeting`, and `contextRefreshPreviewLabel`. Preserve all existing fields by default.
- Add presenter methods in [OverlayPresenter.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt):
  - begin refresh only if panel is visible and quiet
  - apply refreshed `PanelSnapshot` and `panelGreetingFor(...)`
  - clear refresh loading
  - preserve `draftInput`, `recentResponsePreview`, `errorBanner`, voice state, confirmations, and bubbles
- Add `PanelContextRefresher`, owned by [OverlayChatPanelService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelService.kt), not app-wide. It starts collecting only while the panel is visible, debounces foreground changes around 250-300 ms, keeps only the latest pending app, collects targeted marks, and retries when the presenter becomes quiet.
- Update [OverlayQuickChatPanelV2.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/design/OverlayQuickChatPanelV2.kt) so `ContextLineV2` can split greeting text into prefix/app-label/suffix and render the label segment as either accented text or a stable-width shimmer placeholder.

## Safety Rules
- Do not refresh `panel.snapshot` inside `OverlayChatPipeline.runTurn`.
- Do not mutate snapshots during `isListening`, `isStreaming`, `audioState != IDLE`, `flightFsm != Docked`, `isFlying`, `tapForMeConfirmation`, `pendingConfirmation`, `candidateOptions`, low-confidence transcript, manual target selection, recipe/action progress, or pointer states.
- If multiple app switches happen during an active turn, keep only the latest pending foreground app.
- If the foreground is launcher/recents/System UI, keep the previous applied context and do not show a refresh.
- If targeted mark collection fails, apply the new tool context with empty marks rather than staying stale; the next turn can still fall back to live screen/capture logic.

## Test Plan
- Presenter unit tests: idle refresh applies; busy refresh defers; latest pending app wins; same app/site is ignored; draft/preview/error are preserved; shimmer fields clear after apply.
- Refresher tests: debounce collapses rapid foreground emissions; start/stop is idempotent; pending refresh applies on quiet transition; no duplicate collectors.
- Marks-provider tests: package-targeted collection ignores Handy overlay roots and uses the matching app window.
- Pipeline test: after Photos -> YouTube refresh, the next overlay turn uses YouTube `ToolContext`, history key, and marks.
- Compose test: context line renders old label normally, shimmer during refresh, and new greeting after apply without duplicate stale text.
- Manual QA: Photos -> overlay -> swipe to YouTube updates in place; typing draft survives; mid-stream switch applies after response/audio settles; tap-for-me confirmation keeps old app until dismissed; home/recents do not replace the tool name.

## Assumptions
- Accessibility permission remains the required source of foreground context.
- No new user-facing setting, audit event, persistent history migration, or recipe-policy change is needed.
- The refreshed panel context affects only future overlay turns; anything already submitted remains grounded to its original snapshot.

### ASSISTANT 2026-05-28T03:58:28.326Z

I’ll implement this end to end now. I’m starting by re-reading the exact current files around foreground detection, panel state, and overlay UI so the patches land cleanly and don’t disturb the existing flight/recipe path.

### ASSISTANT 2026-05-28T03:58:56.891Z

The current code matches what we planned: the monitor only handles `TYPE_WINDOW_STATE_CHANGED`, marks are still `rootInActiveWindow`-based, and `PanelContent` has no refresh fields yet. I’m going to patch the lower-level detection and targeted marks first, then wire the presenter/refresher on top.

### ASSISTANT 2026-05-28T03:59:43.958Z

I’m applying the platform-side changes now: `TYPE_WINDOWS_CHANGED`, window-first foreground resolution, and a package-targeted marks collector. This is the layer that keeps the overlay from accidentally reading its own Compose tree as the “current app.”

### ASSISTANT 2026-05-28T04:01:20.280Z

Now I’m adding the presenter API that separates “show refresh pending” from “apply refreshed snapshot.” The methods re-check quiet state before applying so an LLM turn or action can’t accidentally inherit a half-refreshed context.

### ASSISTANT 2026-05-28T04:03:06.547Z

The refresh lifecycle is in place. I’m moving up into Compose now: the context line will keep its one-line layout, but during a refresh the app-name span becomes a small animated shimmer instead of changing the whole sheet or adding explanatory text.

### ASSISTANT 2026-05-28T04:03:43.838Z

I’m doing a sanity pass over the new coroutine/refresher code before adding tests. There are a few Kotlin edge cases around mutex lambdas and retry flow that are worth tightening now rather than discovering them in Gradle.

### ASSISTANT 2026-05-28T04:04:32.092Z

I’m adding focused tests around the new state machine rather than broad snapshot tests that would require real Android accessibility windows. The coverage will lock the important behavior: idle applies, busy defers, same-app ignores, and the refresher debounces to the latest app.

### ASSISTANT 2026-05-28T04:06:36.378Z

Most of the implementation is now in place. I’m checking the affected call sites for API mismatches and then I’ll run the available verification; if the local JDK is still missing, I’ll report that clearly and still give you the exact commands.

### ASSISTANT 2026-05-28T04:06:45.625Z

The code paths are wired and the focused tests are added. I’m running the targeted Gradle checks now; earlier this machine was missing Java, so this will either validate the patch or confirm the environment blocker.

### ASSISTANT 2026-05-28T04:08:18.433Z

Whitespace checks pass. Since Gradle failed on the default Java lookup, I’m checking whether Android Studio’s bundled JBR is available locally so I can still run the test suite without asking you to install anything.

### ASSISTANT 2026-05-28T04:09:22.834Z

Implemented the consolidated overlay tool-name refresh.

What changed:
- Strengthened foreground detection in [HandyForegroundAppMonitor.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt) and [HandyAccessibilityService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/accessibility/HandyAccessibilityService.kt) to use `TYPE_WINDOWS_CHANGED` plus window-layer inspection.
- Added targeted mark collection via `collectForPackage(...)` in [AccessibilityMarksProvider.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/android-runtime/src/main/kotlin/com/handy/runtime/accessibility/AccessibilityMarksProvider.kt).
- Added panel refresh state to [OverlayPanelState.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt).
- Added guarded presenter APIs in [OverlayPresenter.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt) so snapshots only refresh when the panel is quiet.
- Added [PanelContextRefresher.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/PanelContextRefresher.kt), owned by [OverlayChatPanelService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayChatPanelService.kt), with debounce, pending-latest behavior, and deferred apply.
- Added the app-name shimmer in [OverlayQuickChatPanelV2.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/design/OverlayQuickChatPanelV2.kt).

Tests added for presenter refresh behavior, refresher debounce/defer behavior, greeting segmentation, and overlay preview rendering.

Verification:
- `git diff --check` passes.
- Gradle tests could not run because this machine still has no Java runtime visible to the shell: `Unable to locate a Java Runtime`.

The exact test command to run once Java is available:
```sh
./gradlew :app:testDebugUnitTest --tests com.handy.app.overlay.OverlayPresenterPanelContextRefreshTest --tests com.handy.app.overlay.PanelContextRefresherTest --tests com.handy.app.overlay.PanelGreetingCatalogTest --no-daemon
```

### USER 2026-05-28T04:09:23.101Z

That was a very important fix and task! Lots of changes done above which included context compactions in the middle. 1000+ lines of code were written across 20+ files- that's a lot of code and it needs a deep deep thorough audit - functional as well as non-functional as it is a critical functionality we habe added! be thorough and deep in your audit. Can you go through the original prompt, code changes done in extreme detail, audit them, ensure all the required changes were done and done correctly with no downstream impact. the code before this works fine with it - no suspicious lines of codes for app crashes, or faulty wirings. all the services that needed to be changed have been changed and nothing is left or missed. ensure the ui is also proper with correct spacings, colours, fonts and elements. be very thorough in your analysis and evaluation. use highest level of reasoning available. Log the rca in the debug log and explain why the misses were missed. use highest level of thinking possible and think deeply. Evaluate and understand very deeply. Be very thorough and detailed. use your highest level of intelligence to analyse the requirement, code generated and plug gaps. be very thorough and have highest attention to detail. carefully analyse all the code - new and old and all dependencies too. apart from the code written in this conversation do an audit of the neighbouring and downstream services too to find any improvements, possible edge cases or errors.

### ASSISTANT 2026-05-28T04:09:36.970Z

I’ll audit this as if I’m trying to break it before a release: requirements trace, compile-risk pass, runtime-state pass, downstream LLM/flight/action impacts, UI pass, and then I’ll patch anything suspicious and log the RCA in `DEBUG_LOG.md`.

### ASSISTANT 2026-05-28T04:11:02.144Z

I’ll treat this as a real audit pass, not a rubber stamp. I’m going back through the implemented diff plus the foreground monitor, marks provider, presenter/refresher state paths, UI rendering, and downstream pipeline assumptions, then I’ll patch any concrete gaps I find and log the RCA in `DEBUG_LOG.md`.

### ASSISTANT 2026-05-28T04:11:21.380Z

I’ve confirmed the footprint: the implementation touched the expected foreground, marks, presenter, service, Compose, XML, and test files, with the macOS reference folder still unrelated and untouched. Now I’m drilling into the actual control flow and compile-risk spots rather than just the file list.

### ASSISTANT 2026-05-28T04:14:29.062Z

A few concrete audit findings are emerging: the high-level wiring is there, but I see a compile-risk import in the refresher, a browser-URL tree walk that can leak child accessibility nodes now that it runs more often, and a shimmer path that deserves a closer non-functional look because marks collection happens immediately after the preview state is published.

### ASSISTANT 2026-05-28T04:15:06.165Z

I’m going to patch the two hard issues first: a missing Flow `collect` import that could stop Kotlin compilation, and the browser URL scanner so child `AccessibilityNodeInfo`s are recycled even when URL extraction returns early. That second one matters because this new feature increases how often browser roots are walked.

### ASSISTANT 2026-05-28T04:15:49.856Z

One more foreground-monitor issue is worth tightening: when Home/Recents/System UI is the top application surface, the detector should stop and keep the old panel context, not scan past it and accidentally “discover” some lower app window. I’m patching that now because it directly matches the safety rule in your plan.

### ASSISTANT 2026-05-28T04:18:31.257Z

The audit patch is now broader than a lint sweep: I also added coverage for the “latest pending foreground wins” case and a pipeline guard proving the next overlay turn reads the refreshed panel snapshot/tool context. I’m going to compile and run the focused tests with the repo-local JDK now.

### ASSISTANT 2026-05-28T04:19:25.565Z

Compile did its job: two misses from the first implementation surfaced immediately. I’m fixing the public/internal mismatch on the presenter refresh result and the Compose `Offset` import, then I’ll rerun the same compile gate.

### ASSISTANT 2026-05-28T04:20:12.124Z

The compile gate passes now. The only warnings are existing Android deprecations around `recycle()` / accessibility announcements / clipboard, not new hard failures. I’m running the focused JVM test set next so the state/refresher/pipeline pieces get exercised together.

### ASSISTANT 2026-05-28T04:22:50.376Z

The failing queued-refresh tests exposed a real non-functional hazard: the refresher was calling presenter state mutations while holding its mutex. That can make retry behavior brittle because presenter state emissions and refresher state collection are connected. I’m refactoring that section so the mutex only protects pending/refreshing bookkeeping, while all presenter mutations happen outside the lock.

### ASSISTANT 2026-05-28T04:29:20.137Z

Gradle’s generated Hilt/Javac output tripped over a stale missing class while rerunning the tests, which this repo has seen before after repeated compile probes. I’m clearing only generated build intermediates for the app module and then rerunning; source files are untouched.

### ASSISTANT 2026-05-28T04:30:26.778Z

The queued-refresh behavior is now covered and passing. The tests had to model the real foreground monitor’s replaying flow; while there I also hardened the refresher so presenter mutations happen outside the mutex, which is the safer production shape.

### ASSISTANT 2026-05-28T04:31:15.686Z

I also checked the actual implementation commit footprint: 14 tracked files and about 970 inserted lines. The current audit diff is deliberately tiny: production cleanup of stray debug prints from the refresher plus a test-scope correction so the queued-refresh tests exercise state transitions faithfully. I’m going to run a broader assemble/test compile gate now.

### USER 2026-05-28T04:32:28.243Z

# Files mentioned by the user:

## Screenshot 2026-05-28 at 10.01.03 AM.png: /var/folders/75/nndfzddd11b8kv28kpwdjlh40000gp/T/TemporaryItems/NSIRD_screencaptureui_r161hH/Screenshot 2026-05-28 at 10.01.03 AM.png

## My request for Codex:
something is breaking when i come back to home. the prev app that i opened still shows in the chat overlay subtext. even if i kill all apps, it still shows the last opened app. can you deeply anakyse and fix this please?? ensure we dont find similar bugs. analuse the code deeply and evaluate it and fix it

<image name=[Image #1]>
</image>

### ASSISTANT 2026-05-28T04:32:48.828Z

I see the screenshot: home screen is visible, but the overlay still says `In Google Play Store`. That points to stale foreground fallback leaking into a launcher/no-app state. I’m going to trace the capture and live-refresh paths together, then patch the monitor/presenter so “home/recents/no real app” is an explicit state instead of “null, maybe use the last app.”

### ASSISTANT 2026-05-28T04:34:20.867Z

I’m going to trace this from the foreground monitor through the presenter and refresher, because the screenshot smells like an ambiguous “no foreground app” state being treated as “reuse last known app.” I’ll keep the fix tight, but I’m going to inspect the nearby downstream pieces too so we don’t just move the stale label somewhere else.

### ASSISTANT 2026-05-28T04:34:40.133Z

The first pass confirms the likely culprit: the monitor can say “I didn’t find a usable app” but the overlay only understands concrete app snapshots. That means Home/Recents/null foreground gets swallowed, so the panel keeps the last app label.

### ASSISTANT 2026-05-28T04:37:18.426Z

I found two separate weak links that line up with the behavior: the monitor’s public stream only emits “some app,” never “no app,” and the refresher only knows how to apply app snapshots. I’m going to add an explicit no-foreground-app signal and teach the idle overlay to clear to the neutral greeting when Home/Recents/System UI is topmost.

### ASSISTANT 2026-05-28T04:40:00.647Z

The fix is now in code: Home/Recents/System UI becomes an explicit “clear context” event, and the refresher queues/applies that just like an app switch, with the same quiet-state guards. I’m going to run focused tests now; if anything shakes loose, I’ll tighten it before broad validation.

### ASSISTANT 2026-05-28T04:41:34.896Z

Focused overlay tests pass now. I’m doing one more audit pass through the diff and downstream call sites before broad validation, especially the places that still use the non-null foreground stream so we can be sure this no-app signal doesn’t silently regress action gating or full-chat behavior.

### ASSISTANT 2026-05-28T04:42:02.975Z

One more important edge popped out in the audit: for `TYPE_WINDOWS_CHANGED`, Android can report a launcher/System UI/self event while `windows` still contains an old application surface. The monitor was trusting the window scan first. I’m tightening that ordering so explicit Home/Recents/System UI events clear the context before any stale app window can win.

### USER 2026-05-28T04:42:29.555Z

you did some changes and now the tool conext is completely gone! it is a generic subtext everywhere!? please revert and re-evaluate the code deeply and ensure we are implementing this correctly??

### ASSISTANT 2026-05-28T04:42:47.207Z

You’re right to stop this. I’m going to revert the changes I just made for the Home clear behavior first, preserving the earlier cleanups/tests that were already in the worktree, then I’ll re-evaluate with a less blunt approach. The bug is very likely in how I treated “Handy overlay/self is topmost” as “no app,” which can wipe valid context too aggressively.

### ASSISTANT 2026-05-28T04:45:55.723Z

The revert is done for the overly broad “no app” implementation. I’ve re-read the path and the safer rule is: only clear the panel context when Accessibility gives us a confident Home/Recents/System UI signal, never when `windows()` is empty, root is missing, or Handy’s own overlay is topmost. Those ambiguous states are exactly what made the label disappear everywhere.

### ASSISTANT 2026-05-28T04:47:47.629Z

I’ve reintroduced the clear behavior in the narrower form: `null` is only emitted for confident launcher/System UI signals or a window scan that explicitly sees a launcher package. Empty windows, missing roots, and Handy/self windows remain “unknown,” so they no longer erase the context. Running the focused regression suite again now.

### ASSISTANT 2026-05-28T04:49:19.927Z

I spotted another subtle trap before broad validation: `System UI` events are noisy during normal app use, so clearing purely because an event came from `com.android.systemui` can also make the overlay generic everywhere. I’m splitting “actual home launcher package” from “system UI package”: launcher events can clear immediately, System UI only clears when the window scan confirms it is actually the top application surface.

### ASSISTANT 2026-05-28T04:51:01.781Z

The static diff check is clean. I’m running the broader Android validation now (`assemble`, unit tests, android-test compile) so we catch wiring or Hilt/Compose/resource issues, not just the focused presenter/refresher cases.

### ASSISTANT 2026-05-28T04:52:21.098Z

Fixed the Home/launcher stale context bug, and reverted the too-broad approach that made the overlay generic everywhere.

The corrected fix is narrower: Handy now only clears the overlay tool context when Accessibility confidently reports Home/launcher or a top launcher/System UI application window. It does **not** clear on ambiguous states like empty `windows()`, missing roots, or Handy’s own overlay being focused.

Key changes:
- [HandyForegroundAppMonitor.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt:55) now exposes an overlay-only nullable `panelContextFlow`, while keeping the existing non-null `flow` intact.
- [PanelContextRefresher.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/PanelContextRefresher.kt:56) now handles both app targets and “no app/Home” targets, still gated by the same quiet-state rules.
- [OverlayPresenter.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt:954) can now clear the panel snapshot to the neutral greeting without touching active turns.
- [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:2244) has the RCA, including why the first fix over-cleared.

Validation passed:
- `git diff --check`
- Focused overlay tests for refresher/presenter/FSM
- `./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --stacktrace`

I did not change the overlay UI layout/colors for this fix; the existing neutral subtext/shimmer rendering is reused. Manual phone QA still matters here: Photos/Play Store -> Home should become neutral, while Photos -> YouTube should still update to YouTube rather than going generic.

### USER 2026-05-28T04:55:27.771Z

hey can you deeply evaluate the app switch behaviour again please? you also have accessibility access so test is again in depth. somethings broke in the earlier chat and it is still not accurate. sometimes when i switch from youutube to another app , it still shows youtube. please review thw code deeply. below is the prompt for audit of this feature. udnerstand the code deeply please. 

That was a very important fix and task! Lots of changes done above which included context compactions in the middle. 1000+ lines of code were written across 20+ files- that's a lot of code and it needs a deep deep thorough audit - functional as well as non-functional as it is a critical functionality we habe added! be thorough and deep in your audit. Can you go through the original prompt, code changes done in extreme detail, audit them, ensure all the required changes were done and done correctly with no downstream impact. the code before this works fine with it - no suspicious lines of codes for app crashes, or faulty wirings. all the services that needed to be changed have been changed and nothing is left or missed. ensure the ui is also proper with correct spacings, colours, fonts and elements. be very thorough in your analysis and evaluation. use highest level of reasoning available. Log the rca in the debug log and explain why the misses were missed. use highest level of thinking possible and think deeply. Evaluate and understand very deeply. Be very thorough and detailed. use your highest level of intelligence to analyse the requirement, code generated and plug gaps. be very thorough and have highest attention to detail. carefully analyse all the code - new and old and all dependencies too. apart from the code written in this conversation do an audit of the neighbouring and downstream services too to find any improvements, possible edge cases or errors.

### ASSISTANT 2026-05-28T04:55:43.537Z

I’m going back through the foreground path end to end: accessibility events, window ordering, the panel-only stream, refresher queuing, presenter guards, and the UI render. The symptom “YouTube sticks after switching” usually means either the monitor is still selecting a stale lower YouTube window, or the correct new app is emitted but the refresher drops/defers it.

### ASSISTANT 2026-05-28T04:56:10.542Z

The code audit points to one likely source already: the monitor still trusts the topmost application window scan before considering the event package. During gesture switching, Android can leave a stale YouTube app window in `service.windows` while the event package/root already belongs to the newly focused app. I’m going to test the live event/window behavior with `adb` next so we fix the ordering based on evidence, not guesswork.

### ASSISTANT 2026-05-28T04:56:56.235Z

`adb` is available via the local Android SDK, and there’s an emulator connected. I’m checking whether Handy is installed/enabled and whether we can reproduce with package launches plus UI dumps/logcat. If the emulator lacks overlay/accessibility permissions, I’ll still validate the monitor logic with deterministic unit tests.

### ASSISTANT 2026-05-28T04:58:20.098Z

I reproduced the sticky bug on the emulator. The sequence is exactly the dangerous one: after launching Photos behind an open panel, Handy correctly emitted/applied Photos, then about 300 ms later received a stale YouTube accessibility event and applied YouTube again, even though Android’s focused app was Photos. That gives us a concrete root cause to fix.

### ASSISTANT 2026-05-28T04:58:57.264Z

The live reproduction makes the fix much clearer. `AccessibilityService.windows` does contain the real visible app below Handy’s overlay, but our monitor stops as soon as it sees Handy’s own overlay window. That forces it to fall back to the event package, and stale YouTube events can win after Photos has already become focused. I’m changing that stop into a skip: ignore Handy overlay windows and keep scanning down to the actual visible app; still stop/clear for launcher/System UI.

### ASSISTANT 2026-05-28T05:02:29.343Z

The regression tests now cover the exact failure: a Handy overlay window above Photos, plus a stale YouTube event. Before the monitor change, that scenario re-applied YouTube. The test now proves the monitor walks past Handy’s overlay and keeps Photos. I’m installing the rebuilt debug APK onto the emulator and re-running the same YouTube -> Photos overlay test.

### ASSISTANT 2026-05-28T05:05:09.640Z

I’m picking this up from the exact failure mode we reproduced: the monitor was seeing Handy’s own overlay as the top accessibility window, then falling back to a stale app event. I’ll finish the live retest, add the RCA note, and run the broader checks so we’re not just patching the symptom.

### ASSISTANT 2026-05-28T05:05:25.537Z

The current diff matches the RCA from the live repro: the foreground scanner now skips Handy’s own overlay window and keeps looking for the real app underneath. I’m going to verify both code shape and the actual emulator behavior now, because this class of bug only shows itself when Accessibility window focus and Android activity focus disagree.

### ASSISTANT 2026-05-28T05:05:57.657Z

The overlay panel is currently open, which is useful. It is already in a suspicious state: Android says the focused app underneath is Gmail, while the panel still says YouTube. I’m clearing logs and forcing a few app switches so we can see whether the new window scan repairs this on actual foreground events or whether there is still a wiring gap.

### ASSISTANT 2026-05-28T05:06:44.474Z

The live test found a second, sharper bug: YouTube still wins after switching to Play Store because the monitor emits the correct Play Store event, then accepts a late stale YouTube event about 150 ms later. So the window-scan fix helps when the scan can see the real app, but the fallback path is still too trusting of old event packages.

### ASSISTANT 2026-05-28T05:08:00.302Z

The next fix is clearer now: `TYPE_WINDOWS_CHANGED` should be a scan trigger only. Its `packageName` can describe a window that changed or disappeared, not the app that is now foreground. The monitor was treating those event packages as authoritative fallback app contexts, which lets a retiring YouTube window overwrite the newly focused app.

### ASSISTANT 2026-05-28T05:08:24.656Z

I’ve patched the unsafe fallback and added regressions for the exact pattern: a real `TYPE_WINDOW_STATE_CHANGED` to Play Store followed by a stale `TYPE_WINDOWS_CHANGED` from YouTube. Now I’m running the focused test set before reinstalling and trying the same switch on the emulator.

### ASSISTANT 2026-05-28T05:09:07.651Z

Focused tests passed and the patched APK is installed. I’m going back to the emulator now to run the exact YouTube → Play Store / Photos transitions against the real overlay, because the previous live failure was not visible from unit tests alone.

### ASSISTANT 2026-05-28T05:10:11.172Z

That narrowed it further: the stale YouTube event is not just `TYPE_WINDOWS_CHANGED`; it is coming through the `TYPE_WINDOW_STATE_CHANGED` fallback after Play Store has already applied. I’m going to instrument the event metadata briefly so the permanent fix is based on what Accessibility is actually handing us, not a guess.

### ASSISTANT 2026-05-28T11:23:16.555Z

The instrumentation confirmed activeRoot is Handy’s overlay for both the real Play Store event and the stale YouTube reversion, so root matching cannot solve it. I replaced that temporary log with a narrow stale-reversion guard: after accepting A → B from the fallback path, a near-immediate fallback back to A is ignored unless it survives the short stale window.

### ASSISTANT 2026-05-28T11:24:56.056Z

The exact live failure now passes: after YouTube → Play Store, the monitor logs “ignored stale fallback reversion to com.google.android.youtube,” and the panel stays on Google Play Store. I’m doing a couple more live passes now: another app target and Home, because those were the two brittle paths from the earlier fixes.

### ASSISTANT 2026-05-28T11:26:01.306Z

The live matrix now passes for YouTube → Play Store, YouTube → Photos, and app → Home. I’m moving into the broader build/test pass and then I’ll write the RCA into `DEBUG_LOG.md` with the exact reason this escaped the previous fix.

### ASSISTANT 2026-05-28T11:28:16.924Z

I also re-read the downstream consumers: full chat still consumes the non-null `flow`, the overlay alone consumes nullable `panelContextFlow`, targeted marks still ignore Handy roots, and the panel service owns the refresher lifecycle. I’m adding the core/runtime test pass too, since this feature sits on the boundary between app overlay and accessibility runtime.

### ASSISTANT 2026-05-28T11:29:44.506Z

Fixed and audited the app-switch behavior again.

Root cause: while the overlay is open, `rootInActiveWindow` is often Handy itself. During YouTube -> Play Store, Accessibility first emitted the correct Play Store event, then a late `TYPE_WINDOW_STATE_CHANGED` from the retiring YouTube window. The monitor trusted that late raw package and reverted the panel to YouTube.

I hardened [HandyForegroundAppMonitor.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/foreground/HandyForegroundAppMonitor.kt:92) so it now:

- skips Handy/self windows during top-window scans instead of stopping there
- treats `TYPE_WINDOWS_CHANGED` as a scan trigger only, never as raw app truth
- suppresses short-window fallback reversions back to the previous app
- still clears correctly on Home/launcher
- keeps existing `flow` behavior for full chat and uses nullable `panelContextFlow` only for overlay panel context

Added regression coverage in [HandyForegroundAppMonitorTest.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/test/kotlin/com/handy/app/foreground/HandyForegroundAppMonitorTest.kt:24), including the exact stale YouTube reversion case. Logged the RCA and why it was missed in [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:2252).

Live emulator validation passed:

- YouTube -> Play Store stays on `In Google Play Store...`
- YouTube -> Photos stays on Photos
- App -> Home clears to neutral `What can I help you with?`
- Handy process stayed alive; no fatal Handy crash patterns found

Build/test validation passed:

```bash
git diff --check
./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.handy.app.foreground.HandyForegroundAppMonitorTest' --tests 'com.handy.app.overlay.PanelContextRefresherTest' --tests 'com.handy.app.overlay.OverlayPresenterPanelContextRefreshTest' --stacktrace
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --stacktrace
./gradlew --no-daemon :core:test :android-runtime:testDebugUnitTest --stacktrace
```
