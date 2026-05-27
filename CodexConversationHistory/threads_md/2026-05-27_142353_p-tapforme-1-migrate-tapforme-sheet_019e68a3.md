# P-TAPFORME-1 migrate TapForMe sheet

## Metadata

- Thread ID: `019e68a3-e8e0-7830-b12f-cd22447693da`
- Created: 2026-05-27 14:23:53 IST
- Updated: 2026-05-27 14:41:20 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/27/rollout-2026-05-27T14-23-53-019e68a3-e8e0-7830-b12f-cd22447693da.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `74762f57e9e23a53b41df3c9e66e2aabb90bf12e`
- Messages exported: 14

## Brief Summary

This conversation focused on: P-TAPFORME-1 migrate TapForMe sheet Likely related git changes: 74762f5 Implemented the preview lab for text bubbles; 3ce396d P-TAPFORME-1 migrate TapForMe sheet.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `74762f57e9e23a53b41df3c9e66e2aabb90bf12e`
- Likely related commits:
  - `74762f5` 2026-05-27 14:23:48 IST [medium] Implemented the preview lab for text bubbles. Files: DEBUG_LOG.md, app/src/main/AndroidManifest.xml, app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt, app/src/main/kotlin/com/handy/app/settings/SideBubblePreviewActivity.kt, app/src/main/kotlin/com/handy/app/settings/sections/SettingsChrome.kt
  - `3ce396d` 2026-05-27 14:41:46 IST [medium] P-TAPFORME-1 migrate TapForMe sheet. Files: DEBUG_LOG.md, app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt, app/src/main/kotlin/com/handy/app/overlay/AgentProgressBubble.kt, app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt, app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt, app/src/main/kotlin/com/handy/app/overlay/OverlayPresenter.kt, app/src/main/kotlin/com/handy/app/overlay/TapForMeConfirmationSheet.kt, app/src/main/kotlin/com/handy/app/overlay/design/TapForMeConfirmationSheetV2.kt, app/src/test/kotlin/com/handy/app/agent/AgentSessionControllerTest.kt, app/src/test/kotlin/com/handy/app/overlay/design/TapForMeConfirmationSheetV2RenderTest.kt, app/src/test/kotlin/com/handy/app/overlay/design/TapForMeConfirmationSheetV2Test.kt, core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt

## Conversation

### USER 2026-05-27T08:53:59.051Z

PROMPT P-TAPFORME-1 — Migrate TapForMeConfirmationSheet to the new design.
Add a "plan of action" preview when the request originates from a recipe.
Migrate AgentProgressBubble to SideBubbleV2. Replace CandidateChipsBar
with a HandyDesign-styled chip stack.

DEPENDS ON
- P-BUBBLE-1 (SideBubbleV2 + BuddyBubble model). Do not attempt this prompt
  before P-BUBBLE-1 has landed.

────────────────────────────────────────────────────────────────────────
(A) GROUND TRUTH FILES
────────────────────────────────────────────────────────────────────────

1. app/src/main/kotlin/com/handy/app/overlay/TapForMeConfirmationSheet.kt
   — current implementation. 100% on legacy HandyColors / HandyGlassBottomSheet
   / HandyType. Replace wholesale.

2. app/src/main/kotlin/com/handy/app/overlay/AgentProgressBubble.kt
   — multi-step recipe progress bubble. Also 100% legacy. Replace.

3. app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt
   — chip stack for ambiguous targets. Also legacy. Replace.

4. handy-new-design-handoff/project/src/scenes-sheets.jsx (Privacy
   disclosure sheet implementation we already ported). The same sheet
   chrome rules apply here: drag handle, dark glass fill, hairline border,
   PrimaryButton + SecondaryTextButton stack. Re-read sections "Privacy
   disclosure sheet" lines 390-588 if needed.

5. handy-new-design-handoff/project/src/scenes-bubbles.jsx (ambient + recipe)
   — the "Step 2 of 5 · Open Alarms tab" bubble is the SideBubbleV2 recipe
   variant. Use it as the progress bubble.

6. core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt
   — `TapForMeConfirmation` data class (lines 82-91). We extend it with
   plan-step preview fields.

7. core/src/main/kotlin/com/handy/core/agent/RecipePlan.kt + RecipeStep.kt
   — the recipe plan model. The sheet will render a 1-3 step preview.

8. core/src/main/kotlin/com/handy/core/action/ConfirmationLevel.kt
   — NONE / NORMAL / STRONG_HOLD / TYPED_CONFIRMATION. The sheet already
   handles STRONG_HOLD (hold-to-confirm 1s). We keep that behavior.

────────────────────────────────────────────────────────────────────────
(B) DATA MODEL EXTENSIONS (small)
────────────────────────────────────────────────────────────────────────

File: core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt

Extend TapForMeConfirmation with optional plan preview fields:

    data class TapForMeConfirmation(
        val id: Long,
        val targetLabel: String,
        val appLabel: String?,
        val packageName: String?,
        val confirmationLevel: ConfirmationLevel,
        val risk: ActionRisk,
        val reason: String?,
        val typingText: String? = null,
        // NEW — only set when the confirmation is for a multi-step recipe.
        // When null, render the existing single-action layout.
        val planPreview: PlanPreview? = null,
    )

    data class PlanPreview(
        val recipeId: String,
        val recipeDisplayName: String,
        val totalStepCount: Int,
        val steps: List<PlanStep>,   // First 3 visible; the rest summarized
                                     // as "+N more steps".
    )

    data class PlanStep(
        val index: Int,        // 1-based for display
        val title: String,
        val isSensitive: Boolean,
    )

File: app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt

In requestPlanApproval() (~line 139), when calling
`presenter.requestTapForMeConfirmation(...)`, also pass `planPreview`:

    val preview = PlanPreview(
        recipeId = plan.recipeId,
        recipeDisplayName = plan.displayName,
        totalStepCount = plan.stepCount,
        steps = plan.steps.take(3).mapIndexed { i, s ->
            PlanStep(index = i + 1, title = s.title, isSensitive = s.sensitive)
        },
    )
    presenter.requestTapForMeConfirmation(
        … existing args …,
        planPreview = preview,
    )

Add the `planPreview` parameter to OverlayPresenter.requestTapForMeConfirmation
with `null` default so all other call sites (single-action tap-for-me from
BuddyFlightDriver) continue working unchanged.

────────────────────────────────────────────────────────────────────────
(C) THE SHEET — TapForMeConfirmationSheetV2
────────────────────────────────────────────────────────────────────────

File: app/src/main/kotlin/com/handy/app/overlay/design/TapForMeConfirmationSheetV2.kt

Replace the existing sheet wholesale. Structure:

    @Composable
    fun TapForMeConfirmationSheetV2(
        request: TapForMeConfirmation,
        onDecision: (Boolean, String?) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        // Existing timeout + completion state machine (lines 60-80 of
        // the current sheet). Keep this as-is — only the visuals change.
        var completed by remember(request.id) { mutableStateOf(false) }
        var timeoutProgress by remember(request.id) { mutableStateOf(1f) }
        var editableTypingText by remember(request.id) {
            mutableStateOf(request.typingText.orEmpty())
        }
        val isTyping = request.typingText != null
        val isRecipePlan = request.planPreview != null

        fun decide(approved: Boolean) {
            if (completed) return
            completed = true
            onDecision(approved, editableTypingText.takeIf { isTyping })
        }

        LaunchedEffect(request.id) { … existing 8s timeout … }
        // ADDITIONALLY — stop any active TTS when the sheet appears so
        // the user's attention is on the confirmation.
        LaunchedEffect(request.id) {
            speechOutputController.stop("tap_for_me_sheet_opened")
        }
        // (Inject SpeechOutputController via Hilt's LocalContext-style
        // entry point used elsewhere in the file, or pass it as a param.
        // Look at how the sheet is currently mounted in
        // FloatingWidgetOverlayService.attachTapConfirmationOverlay; add
        // the injection at that call site.)

        Box(modifier = modifier.fillMaxSize() … scrim …) {
            HandyDesignBottomSheetV2(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                DragHandleV2()                                  // (D.1)
                Spacer(Modifier.height(8.dp))
                Header(request)                                 // (D.2)
                if (isRecipePlan) {
                    Spacer(Modifier.height(16.dp))
                    PlanPreview(request.planPreview!!)          // (D.3)
                }
                if (isTyping) {
                    Spacer(Modifier.height(16.dp))
                    TypingFieldV2(editableTypingText, onChange = { editableTypingText = it })
                                                                // (D.4)
                }
                Spacer(Modifier.height(16.dp))
                TimeoutBar(timeoutProgress, tone = request.risk)// (D.5)
                Spacer(Modifier.height(16.dp))
                ButtonRow(
                    isTyping = isTyping,
                    confirmationLevel = request.confirmationLevel,
                    onConfirm = { decide(true) },
                    onCancel = { decide(false) },
                )                                                // (D.6)
            }
        }
    }

────────────────────────────────────────────────────────────────────────
(D) SUB-COMPONENT SPECS
────────────────────────────────────────────────────────────────────────

D.0  HandyDesignBottomSheetV2 (rename the existing helper if you've already
     ported one for the overlay quick-chat; otherwise create it locally
     here):

         @Composable
         fun HandyDesignBottomSheetV2(
             modifier: Modifier = Modifier,
             shape: Shape,
             content: @Composable ColumnScope.() -> Unit,
         ) {
             Box(
                 modifier = modifier
                     .clip(shape)
                     // Same dark glass fill the overlay quick-chat uses.
                     .background(Color(0xD1121418))
                     .border(
                         width = 0.5.dp,
                         color = Color.White.copy(alpha = 0.12f),
                         shape = shape,
                     )
                     .padding(horizontal = 20.dp, vertical = 16.dp),
             ) {
                 Column(
                     verticalArrangement = Arrangement.spacedBy(0.dp),
                     content = content,
                 )
             }
         }

D.1  DragHandleV2 — 38 dp x 4 dp pill, white@25 alpha, centered.

     Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
         Box(
             Modifier
                 .size(width = 38.dp, height = 4.dp)
                 .clip(RoundedCornerShape(2.dp))
                 .background(Color.White.copy(alpha = 0.25f))
         )
     }

D.2  Header — same icon + title + body + reason structure as today,
     re-tinted on HandyDesign tokens. Specifically:

         icon disc: 40 dp, HandyDesign.Colors.AccentSoft fill,
                    1 dp HandyDesign.Colors.AccentHairline border, CircleShape.
                    Inside: ic_phosphor_hand_pointing_fill (or ic_keyboard for
                    isTyping=true), tinted HandyDesign.Colors.Accent, 20 dp.

         title:    HandyDesignType.TitleSmall (18 sp / SemiBold / -0.01 em)
                   color = HandyDesign.Colors.TextPrimary.
                   "Tap-for-me confirmation" (existing string).
                   When isRecipePlan: "Review recipe plan".

         body:     HandyDesignType.Body (15 sp / 22 sp lineHeight)
                   color = HandyDesign.Colors.TextSecondary.
                   Existing template: "Tap \"$targetLabel\" in $appLabel?"

         reason:   HandyDesignType.Caption (13 sp), color = TextMuted.
                   Existing template: "Policy: $reason".

D.3  PlanPreview — the new piece for recipe-driven requests.

     Column(
         modifier = Modifier
             .fillMaxWidth()
             .clip(RoundedCornerShape(14.dp))
             .background(Color.White.copy(alpha = 0.03f))
             .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
             .padding(14.dp),
         verticalArrangement = Arrangement.spacedBy(8.dp),
     ) {
         Text(
             text = preview.recipeDisplayName,
             style = HandyDesignType.BodyStrong.copy(fontSize = 14.sp),
             color = HandyDesign.Colors.TextPrimary,
         )
         preview.steps.forEach { step ->
             PlanStepRow(step)
         }
         if (preview.totalStepCount > preview.steps.size) {
             val remaining = preview.totalStepCount - preview.steps.size
             Text(
                 text = "+ $remaining more step${if (remaining == 1) "" else "s"}",
                 style = HandyDesignType.Caption.copy(fontSize = 12.sp),
                 color = HandyDesign.Colors.TextMuted,
                 modifier = Modifier.padding(start = 24.dp),
             )
         }
     }

     PlanStepRow:
         Row(
             verticalAlignment = Alignment.CenterVertically,
             horizontalArrangement = Arrangement.spacedBy(10.dp),
         ) {
             // Step number disc — 18 dp circle with the step number.
             Box(
                 Modifier
                     .size(18.dp)
                     .clip(CircleShape)
                     .background(
                         if (step.isSensitive) HandyDesign.Colors.Danger.copy(alpha = 0.22f)
                         else HandyDesign.Colors.Accent.copy(alpha = 0.22f),
                     )
                     .border(
                         0.5.dp,
                         (if (step.isSensitive) HandyDesign.Colors.Danger
                          else HandyDesign.Colors.Accent).copy(alpha = 0.42f),
                         CircleShape,
                     ),
                 contentAlignment = Alignment.Center,
             ) {
                 Text(
                     text = step.index.toString(),
                     style = HandyDesignType.Overline.copy(fontSize = 10.sp,
                         fontWeight = FontWeight.SemiBold),
                     color = if (step.isSensitive) HandyDesign.Colors.Danger
                             else HandyDesign.Colors.Accent,
                 )
             }
             Text(
                 text = step.title,
                 style = HandyDesignType.Body.copy(fontSize = 13.5.sp),
                 color = HandyDesign.Colors.TextPrimary,
                 maxLines = 2,
                 overflow = TextOverflow.Ellipsis,
                 modifier = Modifier.weight(1f),
             )
             if (step.isSensitive) {
                 Icon(
                     painter = painterResource(R.drawable.ic_phosphor_warning),
                     contentDescription = "Sensitive step",
                     tint = HandyDesign.Colors.Danger,
                     modifier = Modifier.size(14.dp),
                 )
             }
         }

D.4  TypingFieldV2 — replace Material3 OutlinedTextField with a
     HandyDesign-styled BasicTextField (same recipe as the OverlayQuickChat
     composer field). Specifically:

         Box(
             Modifier
                 .fillMaxWidth()
                 .heightIn(min = 48.dp)
                 .clip(RoundedCornerShape(12.dp))
                 .background(Color.White.copy(alpha = 0.05f))
                 .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                 .padding(horizontal = 14.dp, vertical = 12.dp),
         ) {
             BasicTextField(
                 value = text,
                 onValueChange = onChange,
                 textStyle = HandyDesignType.Body.copy(color = HandyDesign.Colors.TextPrimary),
                 cursorBrush = SolidColor(HandyDesign.Colors.Accent),
                 maxLines = 4,
             )
         }

D.5  TimeoutBar — re-tint on HandyDesign.

     val toneColor = when (risk) {
         ActionRisk.LOW, ActionRisk.MEDIUM -> HandyDesign.Colors.Accent
         ActionRisk.HIGH -> HandyDesign.Colors.Honey
         ActionRisk.CRITICAL -> HandyDesign.Colors.Danger
     }
     Box(
         Modifier
             .fillMaxWidth()
             .height(2.dp)
             .clip(RoundedCornerShape(1.dp))
             .background(Color.White.copy(alpha = 0.06f)),
     ) {
         Box(
             Modifier
                 .fillMaxWidth(timeoutProgress)
                 .height(2.dp)
                 .background(toneColor.copy(alpha = 0.70f)),
         )
     }

D.6  ButtonRow — two buttons, equal weight, 12 dp gap.

     - SecondaryTextButton "Cancel" — HandyDesign primitive that already
       ships in HandyDesignPrimitives.kt.
     - PrimaryButton "Continue" OR HoldToConfirmButton based on
       confirmationLevel. Reuse the existing HoldToConfirmButton's
       gesture logic (it works); only change its visual:
           background: HandyDesign.Colors.Accent.copy(alpha = 0.22f)
           border:     0.5 dp HandyDesign.Colors.AccentHairline
           progress fill: HandyDesign.Colors.Accent.copy(alpha = 0.70f)
           text:       HandyDesignType.BodyStrong, HandyDesign.Colors.TextPrimary

     The hold-to-confirm duration stays 1000 ms for confirmationLevel =
     STRONG_HOLD. For TYPED_CONFIRMATION the button is the same Primary
     "Continue" — typed confirmation comes from the field above.

────────────────────────────────────────────────────────────────────────
(E) AGENT PROGRESS BUBBLE — migrate to SideBubbleV2
────────────────────────────────────────────────────────────────────────

File: app/src/main/kotlin/com/handy/app/overlay/AgentProgressBubble.kt

Replace the entire `AgentProgressBubble` composable body with a
SideBubbleV2 invocation.

    @Composable
    fun AgentProgressBubble(
        state: AgentProgressBubbleState,
        modifier: Modifier = Modifier,
    ) {
        if (!state.visible) return
        val bubble = if (state.stepCount > 0) {
            BuddyBubble.recipeStep(
                stepIndex = state.stepIndex,
                stepCount = state.stepCount,
                label = state.title,
            )
        } else {
            BuddyBubble(
                tone = BubbleTone.ACCENT,
                label = state.title,
                prefix = null,
                progress = null,
                small = true,
            )
        }
        Box(modifier = modifier.padding(8.dp)) { SideBubbleV2(bubble) }
    }

Keep `AgentProgressBubbleState` unchanged (it's the state contract; only
its renderer is changing).

────────────────────────────────────────────────────────────────────────
(F) CANDIDATE CHIPS BAR — migrate
────────────────────────────────────────────────────────────────────────

File: app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt

Replace the legacy chip rendering with HandyDesign tokens. The chip
visual matches the JSX `<span>` chip stack (scenes-bubbles.jsx lines
365-375):

    Each chip:
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) HandyDesign.Colors.Point.copy(alpha = 0.14f)
                else HandyDesign.Colors.Surface,
            )
            .border(
                0.5.dp,
                if (selected) HandyDesign.Colors.PointHairline
                else HandyDesign.Colors.BorderSubtle,
                RoundedCornerShape(999.dp),
            )
            .clickable { onSelect(option.id) }
            .padding(horizontal = 12.dp, vertical = 6.dp)

    Text:
        HandyDesignType.Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
        color = if (selected) HandyDesign.Colors.Point else HandyDesign.Colors.TextSecondary

The container Row keeps the same layout (LazyRow with spacedBy 6 dp).

────────────────────────────────────────────────────────────────────────
(G) CODE-STRENGTHENING DELTAS while you are here
────────────────────────────────────────────────────────────────────────

G.1 The 8-second confirmation timeout should scale with risk. CRITICAL
    actions get 12 seconds; MEDIUM gets 8; LOW gets 6. Add a small
    `timeoutForRisk(risk: ActionRisk)` helper:

        private fun timeoutForRisk(risk: ActionRisk): Long = when (risk) {
            ActionRisk.LOW -> 6_000L
            ActionRisk.MEDIUM -> 8_000L
            ActionRisk.HIGH -> 10_000L
            ActionRisk.CRITICAL -> 12_000L
        }

    Use this in place of the existing `CONFIRMATION_TIMEOUT_MS` constant.

G.2 The hold-to-confirm duration should scale with risk: CRITICAL = 1500 ms,
    HIGH = 1000 ms, others = 750 ms. Wire this through HoldToConfirmButton
    as a parameter.

G.3 When `request.planPreview != null` and any step is `isSensitive`, the
    sheet must downgrade `confirmationLevel` to at least STRONG_HOLD even
    if the policy decision returned NORMAL. (Belt-and-braces: we already
    prompt for STRONG_HOLD on sensitive steps via
    `RecipeStep.applyConfirmationOverride`, but the multi-step plan
    review screen should never let an entire plan slip past with just a
    tap if any step is sensitive.)

────────────────────────────────────────────────────────────────────────
(H) TESTS
────────────────────────────────────────────────────────────────────────

H.1 Update existing `TapForMeConfirmationSheetTest` (if any) to cover:
    - Single-action request → header + timeout bar + buttons, no plan section.
    - Recipe request (planPreview != null) → plan section rendered with
      step count, first 3 steps visible, "+ N more steps" when total > 3.
    - Sensitive step → step row shows the warning icon and danger-tinted disc.
    - STRONG_HOLD → HoldToConfirmButton; NORMAL → PrimaryButton.
    - Timeout matches risk (CRITICAL = 12 s, etc.).

H.2 Manual on Pixel:
    - Trigger a single tap-for-me ("tap Settings") and confirm the new sheet
      renders.
    - Trigger a recipe ("set an alarm for 7am") and confirm the plan section
      shows the steps (set alarm, …).
    - Trigger a Gmail send recipe (sensitive step) and confirm STRONG_HOLD
      kicks in.
    - Speak right before the sheet opens — confirm TTS audio stops the moment
      the sheet appears.

────────────────────────────────────────────────────────────────────────
(I) DO NOT
────────────────────────────────────────────────────────────────────────

- Touch the policy engine.
- Touch BuddyFlightDriver's confirmation contract.
- Change the existing decision-result wiring
  (`onDecision(approved, typingText)`).
- Persist plan previews outside the in-memory confirmation state.

────────────────────────────────────────────────────────────────────────
(J) UPDATE
────────────────────────────────────────────────────────────────────────

- DEBUG_LOG.md: append DL-TAPFORME-1.
- README current-state: no change.

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :core:test
:android-runtime:test` after the edits. 

Additional stuff
A) Mention the change the code brings to the application - the before and after with real life practical examples if possible. 
B) Share an indepth end to end manual testing plan of the changes done - be practical and ensure all aspects are tested well before we proceed.   
C) ENSURE THERE ARE NO BUILDS ERRORS OR APP CRASHES. AUDIOT THE CODE COMPLETELY AND FIX ANY ERRORS

### ASSISTANT 2026-05-27T08:54:09.115Z

I’ll trace the existing overlay/design pieces first, especially whether `SideBubbleV2` and `BuddyBubble` have already landed. Then I’ll make the model and renderer changes, wire the plan preview through the presenter, and run the requested Gradle checks end to end.

### ASSISTANT 2026-05-27T08:54:17.591Z

`SideBubbleV2` and `BuddyBubble` are present, so the dependency looks landed. I also see only an untracked `handy_macos_ref/` directory before touching anything; I’ll leave that alone.

### ASSISTANT 2026-05-27T08:55:07.455Z

The sheet has no existing test file, so I’m going to add focused tests around the non-visual behavior I can expose cleanly: risk-based timeouts, hold durations, and sensitive-plan confirmation downgrading. For render details, I’ll keep the composables small and semantically labeled where tests can attach later.

### ASSISTANT 2026-05-27T09:00:27.060Z

I’ve got the shape now: the presenter owns the in-memory confirmation, the service mounts the sheet, and recipes already have a central `requestPlanApproval` point. I’m going to add the preview model first so the UI and controller can compile against the same core state.

### ASSISTANT 2026-05-27T09:01:51.836Z

Next I’m replacing the sheet itself with the new glass bottom-sheet implementation. I’m keeping the old timeout/decision state machine intact, but making the timeout and hold duration risk-aware and adding the recipe plan card.

### ASSISTANT 2026-05-27T09:03:38.289Z

The sheet now takes a tiny `onSheetOpened` hook, and the overlay service uses its existing `SpeechOutputController` injection to stop TTS as soon as the confirmation appears. I’m moving on to the small renderer swaps for progress and candidate chips.

### ASSISTANT 2026-05-27T09:05:05.219Z

I’m adding tests at two layers: pure behavior tests for timeout/hold/effective confirmation, and a recipe-preview mapping test so the controller always passes the first three steps plus total count into the sheet state.

### ASSISTANT 2026-05-27T09:06:54.359Z

The old `TapForMeConfirmationSheet.kt` is gone from source and the service now mounts only the V2 design sheet. I’m going to do a compile/test pass now, then I’ll use the errors, if any, as the audit checklist.

### ASSISTANT 2026-05-27T09:07:31.781Z

The compile pass is clean; only the existing deprecated accessibility announcement warnings surfaced. I’ll run the new focused tests next, then the full requested Gradle line once the debug log is appended.

### ASSISTANT 2026-05-27T09:08:15.136Z

The new focused tests pass, including a Robolectric render smoke for single-action and recipe sheets. I’m at the full requested validation line now; this may take a bit, so I’ll keep an eye on failures rather than assuming the green path.

### ASSISTANT 2026-05-27T09:08:54.021Z

Full requested Gradle validation passed. I’m adding the `DL-TAPFORME-1` log entry now with the practical before/after impact and the manual device test plan the prompt asked for.

### ASSISTANT 2026-05-27T09:09:37.757Z

The debug-log entry is in. Since that was a post-validation file edit, I’m doing one final whitespace check and rerunning the requested Gradle line so the final state, including the log entry, is what I report against.

### ASSISTANT 2026-05-27T09:11:20.472Z

Implemented `P-TAPFORME-1`.

Changed:
- Added `PlanPreview` / `PlanStep` to [OverlayPanelState.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/core/src/main/kotlin/com/handy/core/overlay/OverlayPanelState.kt:81).
- Wired recipe plan previews through [AgentSessionController.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/agent/AgentSessionController.kt:182) and `OverlayPresenter`.
- Replaced the legacy sheet with [TapForMeConfirmationSheetV2.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/design/TapForMeConfirmationSheetV2.kt:63).
- Stopped TTS when the sheet opens in [FloatingWidgetOverlayService.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/FloatingWidgetOverlayService.kt:681).
- Migrated progress rendering to `SideBubbleV2` in [AgentProgressBubble.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/AgentProgressBubble.kt:27).
- Reworked ambiguous target chips with HandyDesign tokens in [CandidateChipsBar.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/overlay/CandidateChipsBar.kt:27).
- Appended `DL-TAPFORME-1` to [DEBUG_LOG.md](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/DEBUG_LOG.md:2156).

Before/after examples:
- Before: “tap Settings” showed the old legacy glass sheet with a fixed 8s timeout. After: it shows the V2 dark-glass sheet with HandyDesign buttons and a risk-scaled timeout.
- Before: “set an alarm for 7am” only asked for generic recipe approval. After: it previews the recipe name, first 3 steps, and “+ N more steps”.
- Before: sensitive recipe plan approval depended mostly on upstream policy. After: the sheet/controller enforce strong-hold for sensitive plans too.

Validation passed:
- `git diff --check`
- `./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :core:test :android-runtime:test --stacktrace`

Added focused tests for timeout/hold scaling, sensitive-plan strong-hold upgrade, plan-preview mapping, and Robolectric render smoke. I did not run the physical Pixel manual pass in this session.

Manual test plan:
1. Enable overlay/accessibility permissions on Pixel, fresh debug APK.
2. Trigger “tap Settings”; verify V2 sheet, Cancel/scrim cancellation, Continue approval, timeout bar.
3. Trigger Type-for-me into a harmless field; edit text, approve, cancel, and test multiline text.
4. Trigger “set an alarm for 7am”; verify recipe title, first 3 steps, “+ N more steps”, and SideBubbleV2 progress.
5. Trigger a sensitive Gmail-send style recipe; verify warning icon and hold-to-confirm, including early release does not approve.
6. Start TTS right before confirmation appears; verify audio stops immediately.
7. Repeat with rotation, navigation bar, and IME open/closed.
8. Trigger ambiguous targets; verify new chip stack selection and no crashes.

Note: pre-existing untracked `handy_macos_ref/` remains untouched.
