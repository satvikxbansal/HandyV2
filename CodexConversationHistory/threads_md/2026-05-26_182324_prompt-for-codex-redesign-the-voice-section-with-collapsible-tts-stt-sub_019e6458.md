# Prompt for Codex — Redesign the Voice section with collapsible TTS + STT subsections

## Metadata

- Thread ID: `019e6458-d8a8-7b92-9e3e-0eba5d2352d9`
- Created: 2026-05-26 18:23:24 IST
- Updated: 2026-05-26 19:42:25 IST
- CWD: `/Users/satvik.bansal/Desktop/Handy.android/HandyV2`
- Source rollout: `/Users/satvik.bansal/.codex/sessions/2026/05/26/rollout-2026-05-26T18-23-24-019e6458-d8a8-7b92-9e3e-0eba5d2352d9.jsonl`
- Recorded git branch at start: `main`
- Recorded git SHA at start: `33d0563d11974b146039837cfc876b00b0b32687`
- Messages exported: 13

## Brief Summary

This conversation focused on: Prompt for Codex — Redesign the Voice section with collapsible TTS + STT subsections No likely related git commits were found for this thread. It may have been planning/review-only, debugging without a commit, or committed as part of a neighboring bundled thread.

## Git Commit Linkage

Commit links are heuristic: Codex records the starting git SHA, while likely related commits are inferred from timing plus title/commit-subject matching. Review the commit list before treating it as authoritative.

- Base SHA recorded by Codex: `33d0563d11974b146039837cfc876b00b0b32687`
- Likely related commits: none found

## Conversation

### USER 2026-05-26T12:57:57.587Z

Prompt for Codex — Redesign the Voice section with collapsible TTS + STT subsections
You are redesigning Handy's Voice settings section. The current implementation has Speech-to-Text controls inside the AI Brain card and a flat Voice section that mixes TTS and STT concerns. The redesign moves everything into one collapsible Voice section with two nested collapsible subsections — Text-to-speech and Speech-to-text — that default to collapsed.

Files you will touch
HandyV2/app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt (or wherever the Settings UI is composed)
HandyV2/app/src/main/kotlin/com/handy/app/ui/theme/HandyDesign.kt (token additions)
New file: HandyV2/app/src/main/kotlin/com/handy/app/settings/VoiceSection.kt
Remove the Speech-to-text rows currently nested in BrainCard.kt (or wherever they live)
Step 1 — Honey tokens
If you have not already, add to HandyDesign.Colors:

val Honey       = Color(0xFFF0C674)
val HoneySoft   = Color(0x2EF0C674)   // 18 % alpha — pill backgrounds, tile fill
val HoneyHair   = Color(0x5CF0C674)   // 36 % alpha — pill border (selected)
val HoneyInk    = Color(0xFF1F1709)   // text on solid honey (not used in this section)
Step 2 — Make HandySwitch and PillSelectRow tone-aware
These are reused from earlier prompts. If your existing components don't accept a tone, add optional tone, toneSoft, toneHair color parameters that default to Accent/AccentSoft/AccentHair. The Voice section passes honey variants.

Step 3 — State model
sealed class TtsProvider {
    object System : TtsProvider()
    data class Sarvam(
        val apiKey: String?,                    // null = empty, non-null = saved (masked when shown)
        val voice: SarvamVoice = SarvamVoice.Ritu,
        val language: SpokenLanguage = SpokenLanguage.Auto,
    ) : TtsProvider()
}

sealed class SttProvider {
    data class Android(
        val mode: SttMode = SttMode.Auto,
        val language: RecognitionLanguage = RecognitionLanguage.System,
    ) : SttProvider()
    data class SarvamSaarika(
        val apiKey: String?,
        val language: RecognitionLanguage = RecognitionLanguage.Auto,
    ) : SttProvider()
}

enum class SarvamVoice { Ritu, Rahul, Simran }
enum class SpokenLanguage { Auto, English, Hindi, Hinglish }
enum class SttMode { Auto, OnDevice, NetworkAllowed }
enum class RecognitionLanguage { System, Auto, English, Hindi, Hinglish }

data class VoiceSectionState(
    val expanded: Boolean = false,             // outer card open/closed
    val speakRepliesAloud: Boolean = true,     // master TTS toggle
    val tts: TtsProvider = TtsProvider.System,
    val stt: SttProvider = SttProvider.Android(),
    val ttsOpen: Boolean = false,              // TTS subsection — DEFAULT COLLAPSED
    val sttOpen: Boolean = false,              // STT subsection — DEFAULT COLLAPSED
)
Step 4 — Card structure overview
┌─────────────────────────────────────────────────┐
│ [♪]  Voice                                  ⌄  │  ← Outer accordion header (44dp tile, honey)
│      Speaks Sarvam · hears Saarika              │
├─────────────────────────────────────────────────┤
│ Speak voice replies aloud         [●━]          │  ← Master TTS toggle (honey)
├─────────────────────────────────────────────────┤
│ 🔊 Text-to-speech   Sarvam · Ritu          ⌄  │  ← Subsection header — TAPPABLE
│                                                 │     icon: yellow volume2 16dp, no tile
├─────────────────────────────────────────────────┤     subtitle: muted grey, inline
│   [TTS body — see Step 5]                       │  ← Only rendered if ttsOpen = true
├─────────────────────────────────────────────────┤
│ 🎙 Speech-to-text   Android · on-device   ⌄  │  ← Subsection header — TAPPABLE
├─────────────────────────────────────────────────┤
│   [STT body — see Step 6]                       │  ← Only rendered if sttOpen = true
└─────────────────────────────────────────────────┘
Step 5 — Outer Voice card
Header tile
Tile: 44 dp × 44 dp, 12 dp radius
Background: HoneySoft
Border: 0.5 dp Honey.copy(alpha = 0.20f)
Icon: ic_audio_lines.xml (Lucide audio-lines), 22 dp, tinted Honey
Title row
Title "Voice": display font, 17 sp, weight 600, letter-spacing -0.012em, TextPrimary
Subtitle composed from current state:
fun voiceCardSubtitle(state: VoiceSectionState): String {
    val tts = when {
        !state.speakRepliesAloud -> "Off"
        state.tts is TtsProvider.System -> "System"
        state.tts is TtsProvider.Sarvam && state.tts.apiKey != null -> "Sarvam"
        else -> "Sarvam (needs key)"
    }
    val stt = when (val s = state.stt) {
        is SttProvider.Android -> "Android"
        is SttProvider.SarvamSaarika -> if (s.apiKey != null) "Saarika" else "Saarika (needs key)"
    }
    return "Speaks $tts · hears $stt"
}
Subtitle style: 12 sp, weight 400, TextSecondary, marginTop 2 dp.

Chevron
Right-aligned. Rotates 180° on expanded toggle. Same animateFloatAsState(targetValue = if (expanded) 180f else 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) curve as your existing accordion headers.

Step 6 — Master TTS toggle row
When the outer card is expanded, render this row first:

SwitchRow(
    title = "Speak voice replies aloud",
    checked = state.speakRepliesAloud,
    onCheckedChange = onToggleSpeakReplies,
    tone = HandyDesign.Colors.Honey,
    toneSoft = HandyDesign.Colors.HoneySoft,
)
When toggled off, the TTS subsection's body content (when open) shows only "Replies are text-only" instead of the pickers. STT subsection is unaffected — speech recognition still works.

Step 7 — Subsection header composable
This is the key visual primitive. It's a tappable row that contains:

A bare yellow glyph at 16 dp (no tile, no border, no background — just the icon in Honey)
Title in display font, 14 sp, weight 600, TextPrimary
Inline subtitle in body font, 12 sp, weight 400, TextMuted (separated from title by 8 dp gap, baseline-aligned)
Right-aligned chevron in TextMuted, 12 dp, rotates 180° when open
@Composable
fun SubsectionHeader(
    icon: Painter,
    title: String,
    subtitle: String,
    open: Boolean,
    tone: Color = HandyDesign.Colors.Honey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "subsection-chevron",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(16.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = HandyDesignType.LabelDisplay.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.008).em,
                    color = HandyDesign.Colors.TextPrimary,
                ),
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.4.sp,
                    fontWeight = FontWeight.Normal,
                    color = HandyDesign.Colors.TextMuted,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = HandyDesign.Colors.TextMuted,
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { rotationZ = rotation + 90f },   // baseline points right; +90 = down when "open"
        )
    }
}
A 1 dp HorizontalDivider of BorderSubtle color sits above this row (same divider style as your other rows in the card).

Subtitle copy logic (TTS)
fun ttsSubtitle(speakOn: Boolean, tts: TtsProvider): String = when {
    !speakOn -> "Off · replies are text-only"
    tts is TtsProvider.System -> "System voice"
    tts is TtsProvider.Sarvam && tts.apiKey == null -> "Sarvam · needs key"
    tts is TtsProvider.Sarvam -> "Sarvam · ${tts.voice}"
    else -> ""
}
Subtitle copy logic (STT)
fun sttSubtitle(stt: SttProvider): String = when (stt) {
    is SttProvider.Android -> when (stt.mode) {
        SttMode.OnDevice         -> "Android · on-device only"
        SttMode.NetworkAllowed   -> "Android · network allowed"
        SttMode.Auto             -> "Android speech"
    }
    is SttProvider.SarvamSaarika ->
        if (stt.apiKey != null) "Sarvam Saarika" else "Sarvam Saarika · needs key"
}
Step 8 — TTS subsection body (rendered only when ttsOpen == true)
If speakRepliesAloud == false, render only one row:

SubsectionStatusRow(
    label = "Replies are text-only",
    dot = null,                        // no colored dot
    labelColor = HandyDesign.Colors.TextMuted,
)
If speakRepliesAloud == true, render in order:

Voice provider pill row

Options: System, Sarvam
Tone: honey
If Sarvam is selected and apiKey == null, the Sarvam pill shows tag "Add key" in TextMuted (uppercase, 9 sp, letter-spacing 0.08em, prefix " · ")
If TTS provider is Sarvam, render these in order:

Sarvam voice pill row — Ritu, Rahul, Simran
Spoken language pill row — Auto, English, Hindi, Hinglish
API key block (see Step 9)
Test voice row (see Step 10)
Status footer — always present at the end of the TTS body:

Green dot + "Using System voice" if provider == System
Green dot + "Sarvam ready" if Sarvam + key saved
Honey dot + "Add Sarvam key to speak with Bulbul" if Sarvam + no key
Footer style: 13 sp / weight 500 (or 600 when ready) / colored label, 8 dp colored dot with 0 0 12px <color>77 glow
Step 9 — STT subsection body (rendered only when sttOpen == true)
Render in order:

Speech provider pill row — Android, Sarvam Saarika. Saarika gets "Add key" tag when its key is missing.

If provider is Android:

STT mode pill row — Auto, On-device only, Network allowed
Recognition language pill row — System, English, Hindi, Hinglish
If language is Hinglish, render an inline helper line (12 sp, secondary text):
Hinglish enables Android's code-mix recognition where supported (Android 14+).

If provider is Sarvam Saarika:

Recognition language pill row — Auto, English, Hindi, Hinglish
API key block (see Step 9 below)
Status footer — always present at the end of the STT body:

Green dot + "Using Android speech recognition" if provider == Android
Green dot + "Saarika ready" if Saarika + key saved
Honey dot + "Add Sarvam key to transcribe with Saarika" if Saarika + no key
Step 10 — API key block (shared by TTS Sarvam and STT Saarika)
@Composable
fun ApiKeyBlock(
    overline: String,                  // e.g. "SARVAM API KEY"
    apiKey: String?,                   // null = empty, non-null = saved
    placeholder: String,               // e.g. "Paste your Sarvam key"
    helperWhenEmpty: String,           // e.g. "Add Sarvam key to use Bulbul. Handy falls back to System until a key is saved."
    onChange: (String) -> Unit,
    onPaste: () -> Unit,
)
Container: 14 dp top padding, 16 dp horizontal padding, 14 dp bottom padding (when no helper) / 24 dp bottom padding (when helper present)
Top divider: 1 dp BorderSubtle
Overline: 10 sp mono font, letter-spacing 0.12em, uppercase, TextMuted, 8 dp bottom margin
No key icon prefix on the overline. This was rejected in design review.
Text field: standard HandyTextField with eye + copy trailing icons
When apiKey == null: empty value, show placeholder
When saved: show masked value of format sk-····fddd (first 3 chars + middle dots + last 4 chars). Use a VisualTransformation to mask without exposing the raw key
Helper caption (only when apiKey == null): 12 sp, line-height 145 %, TextSecondary, 10 dp top margin from the text field
Step 11 — Test voice row (TTS only, when Sarvam selected)
Same ActionRow pattern as the Automations "Stop for 1 hour" rows:

Title: "Test voice" — body font, 14 sp, weight 500, TextPrimary
Subtitle: "Match the device language" — 11 sp, TextMuted, marginTop 2 dp
Trailing button: "Speak" — SurfaceElevated background, 12 dp radius, 8 / 16 dp padding, 13 sp weight 600, TextPrimary
Disabled state (when no key saved): opacity 0.55, TextMuted color, cursor = default, tap is a no-op. Show, don't hide — it tells the user that this affordance exists once they save a key.
When playing audio, the button label changes to "Stop" and the icon (if any) flips to a 12 dp square stop glyph. Loading state (waking up the TTS engine) briefly shows a 14 dp spinner.

Step 12 — Reset on provider switch — keep state
Critical UX rule: when the user switches providers, do not destroy saved values. Example flows:

User has Sarvam TTS with a saved key, voice=Rahul, language=Hindi. They switch to System. The Sarvam config is preserved. When they switch back to Sarvam, voice=Rahul and language=Hindi are restored.
Same applies to STT switching between Android and Saarika.
DataStore should persist:
tts.system.lastSelected (timestamp, for analytics only)
tts.sarvam.apiKey, tts.sarvam.voice, tts.sarvam.language
stt.android.mode, stt.android.language
stt.saarika.apiKey, stt.saarika.language
tts.currentProvider, stt.currentProvider
voice.speakRepliesAloud, voice.expanded, voice.ttsOpen, voice.sttOpen
Step 13 — Edge / error states
Scenario	Behavior
Speak replies OFF	Master toggle off. TTS subsection still tappable; opens to show only "Replies are text-only" muted line. Outer card subtitle: "Speaks Off · hears <stt>".
TTS = Sarvam, key empty	Helper caption visible. Test button disabled. Status footer: honey dot + "Add Sarvam key to speak with Bulbul". Outer card subtitle: "Speaks Sarvam (needs key) · hears <stt>".
TTS = Sarvam, invalid key after validation	Helper caption changes to red: "That key didn't work. Double-check it and try again." Status footer: red dot + "Invalid key — falling back to System".
TTS = Sarvam, offline during validation	Helper: "Couldn't reach Sarvam. Check your internet and retry." Test button enabled (retries validation on tap). Status footer: muted "Offline — falling back to System".
TTS = System, system TTS engine missing	Status footer: red dot + "System TTS unavailable. Install a TTS engine in System Settings." with a tappable "System Settings" link firing Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).
STT = Android, mic permission missing	Show a permission-prompt strip ABOVE the speech-provider pill row: amber background, mic icon, "Microphone permission needed", "Grant" button. Tapping fires ActivityResultLauncher.
STT = Android, network unavailable + mode = Network allowed	Pill row shows "Network allowed" still selected. Helper line below: "Offline — falling back to on-device".
STT = Saarika, key empty	Same pattern as TTS Sarvam empty. Helper: "Add Sarvam key to transcribe with Saarika. Falls back to Android until saved."
Hinglish language selected (Android STT)	Inline helper line below the language pill row: "Hinglish enables Android's code-mix recognition where supported (Android 14+)." If Build.VERSION.SDK_INT < 34, swap helper to: "Hinglish needs Android 14 or newer. Currently using English fallback."
Voice section completely off (master toggle off + both Sarvam keys missing)	Outer card subtitle: "Speaks Off · hears Saarika (needs key)". Both subsection rows still tappable; user can resolve from either.
Step 14 — Animations
Event	Animation
Outer card expand/collapse	Existing accordion animation — AnimatedVisibility(expandVertically(tween(240, FastOutSlowInEasing)) + fadeIn(tween(180))). Chevron rotates 180°.
Subsection expand/collapse	Same as outer — expandVertically(tween(220)). Subsection chevron rotates 180°.
Pill selection	160 ms color crossfade on background, border, and text.
Provider switch (System → Sarvam or vice versa)	The sub-rows below the provider pill row animate via AnimatedVisibility with vertical expand + 180 ms fadeIn. Don't crossfade — slide them in.
Status footer color transitions	animateColorAsState(targetValue = <new color>, animationSpec = tween(240, FastOutSlowInEasing)). Apply to both dot fill and label color.
Test voice button enable/disable	animateFloatAsState on opacity (1.0 ↔ 0.55) over 200 ms.
Step 15 — Accessibility
Outer card header: Modifier.toggleable(value = expanded, role = Role.Button, onValueChange = { onToggle() }) + contentDescription = "Voice settings. ${voiceCardSubtitle(state)}. Tap to ${if (expanded) "collapse" else "expand"}."
Each subsection header: Modifier.toggleable(...) with similar contentDescription
Master toggle: contentDescription = "Speak voice replies aloud" + stateDescription = if (on) "on" else "off"
Each pill: Modifier.selectable(selected, role = Role.RadioButton)
API key field: password-masking inputType, KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password)
Test button when disabled: contentDescription = "Test voice. Disabled. Add a Sarvam API key first."
Status footer: liveRegion = LiveRegionMode.Polite so TalkBack announces state changes
Step 16 — Wire into Settings screen
In the LazyColumn of section cards:

LazyColumn(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
) {
    item { BrainCard(state.brain, onBrainAction) }
    item {
        VoiceSection(
            state = state.voice,
            onAction = onVoiceAction,
        )
    }
    item { CapabilitiesSection(state.capabilities, onCapabilitiesAction) }
    item { AutomationsSection(state.automations, onAutomationsAction) }
    item { PrivacySection(state.privacy, onPrivacyAction) }
}
Remove all Voice / TTS / STT controls from inside BrainCard — the Brain card now contains only AI Brain (model + Anthropic key) and nothing else. If you find code paths that read voice settings from the Brain card's state, migrate them.

Step 17 — ViewModel migration
Add to SettingsViewModel:

sealed class VoiceAction {
    object ToggleExpanded : VoiceAction()
    object ToggleSpeakReplies : VoiceAction()
    object ToggleTtsOpen : VoiceAction()
    object ToggleSttOpen : VoiceAction()
    data class SelectTtsProvider(val provider: TtsProvider) : VoiceAction()
    data class SelectTtsVoice(val voice: SarvamVoice) : VoiceAction()
    data class SelectSpokenLanguage(val lang: SpokenLanguage) : VoiceAction()
    data class SetTtsKey(val key: String) : VoiceAction()
    object ClearTtsKey : VoiceAction()
    object TestTtsVoice : VoiceAction()
    data class SelectSttProvider(val provider: SttProvider) : VoiceAction()
    data class SelectSttMode(val mode: SttMode) : VoiceAction()
    data class SelectRecognitionLanguage(val lang: RecognitionLanguage) : VoiceAction()
    data class SetSttKey(val key: String) : VoiceAction()
    object ClearSttKey : VoiceAction()
    object RequestMicPermission : VoiceAction()
}
Persist all four "open" flags (expanded, speakRepliesAloud, ttsOpen, sttOpen) but default ttsOpen = false and sttOpen = false always on app launch, regardless of last persisted value. This is the design rule: when the user re-enters the Settings screen, they always see the compact collapsed Voice card first — they shouldn't be greeted with a 1200 dp open card just because they left it open last time.

Implementation:

init {
    viewModelScope.launch {
        val persisted = dataStore.loadVoiceState()
        _state.update { it.copy(
            voice = persisted.copy(ttsOpen = false, sttOpen = false)
        )}
    }
}
Step 18 — Final QA checklist
 Voice section sits between AI Brain and Capabilities
 AI Brain card no longer contains any voice or speech controls
 Outer Voice card header uses 44dp audio-lines tile in honey with Speaks X · hears Y subtitle
 Master "Speak voice replies aloud" toggle uses honey on-state
 Both subsection headers default to collapsed on every fresh entry
 Bare yellow volume-2 glyph on TTS subheading at 16 dp (no tile, no border)
 Bare yellow mic-vocal glyph on STT subheading at 16 dp
 Each subheading shows inline grey state subtitle baseline-aligned with title
 Chevrons rotate 180° in 220 ms with FastOutSlowInEasing
 All pill rows in both subsections use honey for selected state
 API key fields use the existing eye + copy trailing icons
 No key-icon prefix on the "SARVAM API KEY" overline (this was rejected)
 Test voice button stays in DOM when disabled (don't hide)
 Hinglish helper caption appears when applicable, falls back to "needs Android 14+" on older devices
 All 10 error / edge states from Step 13 render correctly
 Provider switches preserve last-used config (voice, language, key) per provider
 All accessibility semantics in place; TalkBack reads Voice settings · Speaks Sarvam · hears Saarika · Tap to collapse correctly
 Migration: existing users with TTS or STT settings stored in old locations have their values migrated to the new voice.* keys
End of prompt. After implementation, run your settings screenshot tests and verify all five demonstration artboards (07 · Settings (collapsed Voice), 07b · TTS expanded (Sarvam, no key), 07c · STT expanded (Android), 07d · both expanded, all ready, 07e · Settings (full page)) match the design canvas.

### ASSISTANT 2026-05-26T12:58:05.637Z

I’ll trace the existing settings UI and state wiring first, then I’ll move the voice pieces into a dedicated composable without disturbing unrelated settings. After that I’ll run whatever local build/tests or screenshot checks the project already has.

### ASSISTANT 2026-05-26T12:58:19.102Z

There’s already a `VoiceSection.kt` under `settings/sections`, so this is a redesign of an existing split rather than a brand-new island. I’m reading the section primitives and view model now so the new card uses the app’s current component language.

### ASSISTANT 2026-05-26T12:58:41.098Z

The honey palette and tone-aware switch/pills are already present, which is good. The main work is cleaning STT out of `BrainSection`, expanding `VoiceSection` to own both TTS and STT, and teaching the view model to expose the fuller voice state.

### ASSISTANT 2026-05-26T13:15:23.170Z

I’m going to make the data model changes first: keep existing runtime-compatible settings, add the new Voice open flags and Saarika language slot, then wire the UI state from those. That gives the new section somewhere stable to land.

### USER 2026-05-26T13:31:07.811Z

can u restart please? 

Prompt for Codex — Redesign the Voice section with collapsible TTS + STT subsections
You are redesigning Handy's Voice settings section. The current implementation has Speech-to-Text controls inside the AI Brain card and a flat Voice section that mixes TTS and STT concerns. The redesign moves everything into one collapsible Voice section with two nested collapsible subsections — Text-to-speech and Speech-to-text — that default to collapsed.

Files you will touch
HandyV2/app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt (or wherever the Settings UI is composed)
HandyV2/app/src/main/kotlin/com/handy/app/ui/theme/HandyDesign.kt (token additions)
New file: HandyV2/app/src/main/kotlin/com/handy/app/settings/VoiceSection.kt
Remove the Speech-to-text rows currently nested in BrainCard.kt (or wherever they live)
Step 1 — Honey tokens
If you have not already, add to HandyDesign.Colors:

val Honey       = Color(0xFFF0C674)
val HoneySoft   = Color(0x2EF0C674)   // 18 % alpha — pill backgrounds, tile fill
val HoneyHair   = Color(0x5CF0C674)   // 36 % alpha — pill border (selected)
val HoneyInk    = Color(0xFF1F1709)   // text on solid honey (not used in this section)
Step 2 — Make HandySwitch and PillSelectRow tone-aware
These are reused from earlier prompts. If your existing components don't accept a tone, add optional tone, toneSoft, toneHair color parameters that default to Accent/AccentSoft/AccentHair. The Voice section passes honey variants.

Step 3 — State model
sealed class TtsProvider {
    object System : TtsProvider()
    data class Sarvam(
        val apiKey: String?,                    // null = empty, non-null = saved (masked when shown)
        val voice: SarvamVoice = SarvamVoice.Ritu,
        val language: SpokenLanguage = SpokenLanguage.Auto,
    ) : TtsProvider()
}

sealed class SttProvider {
    data class Android(
        val mode: SttMode = SttMode.Auto,
        val language: RecognitionLanguage = RecognitionLanguage.System,
    ) : SttProvider()
    data class SarvamSaarika(
        val apiKey: String?,
        val language: RecognitionLanguage = RecognitionLanguage.Auto,
    ) : SttProvider()
}

enum class SarvamVoice { Ritu, Rahul, Simran }
enum class SpokenLanguage { Auto, English, Hindi, Hinglish }
enum class SttMode { Auto, OnDevice, NetworkAllowed }
enum class RecognitionLanguage { System, Auto, English, Hindi, Hinglish }

data class VoiceSectionState(
    val expanded: Boolean = false,             // outer card open/closed
    val speakRepliesAloud: Boolean = true,     // master TTS toggle
    val tts: TtsProvider = TtsProvider.System,
    val stt: SttProvider = SttProvider.Android(),
    val ttsOpen: Boolean = false,              // TTS subsection — DEFAULT COLLAPSED
    val sttOpen: Boolean = false,              // STT subsection — DEFAULT COLLAPSED
)
Step 4 — Card structure overview
┌─────────────────────────────────────────────────┐
│ [♪]  Voice                                  ⌄  │  ← Outer accordion header (44dp tile, honey)
│      Speaks Sarvam · hears Saarika              │
├─────────────────────────────────────────────────┤
│ Speak voice replies aloud         [●━]          │  ← Master TTS toggle (honey)
├─────────────────────────────────────────────────┤
│ 🔊 Text-to-speech   Sarvam · Ritu          ⌄  │  ← Subsection header — TAPPABLE
│                                                 │     icon: yellow volume2 16dp, no tile
├─────────────────────────────────────────────────┤     subtitle: muted grey, inline
│   [TTS body — see Step 5]                       │  ← Only rendered if ttsOpen = true
├─────────────────────────────────────────────────┤
│ 🎙 Speech-to-text   Android · on-device   ⌄  │  ← Subsection header — TAPPABLE
├─────────────────────────────────────────────────┤
│   [STT body — see Step 6]                       │  ← Only rendered if sttOpen = true
└─────────────────────────────────────────────────┘
Step 5 — Outer Voice card
Header tile
Tile: 44 dp × 44 dp, 12 dp radius
Background: HoneySoft
Border: 0.5 dp Honey.copy(alpha = 0.20f)
Icon: ic_audio_lines.xml (Lucide audio-lines), 22 dp, tinted Honey
Title row
Title "Voice": display font, 17 sp, weight 600, letter-spacing -0.012em, TextPrimary
Subtitle composed from current state:
fun voiceCardSubtitle(state: VoiceSectionState): String {
    val tts = when {
        !state.speakRepliesAloud -> "Off"
        state.tts is TtsProvider.System -> "System"
        state.tts is TtsProvider.Sarvam && state.tts.apiKey != null -> "Sarvam"
        else -> "Sarvam (needs key)"
    }
    val stt = when (val s = state.stt) {
        is SttProvider.Android -> "Android"
        is SttProvider.SarvamSaarika -> if (s.apiKey != null) "Saarika" else "Saarika (needs key)"
    }
    return "Speaks $tts · hears $stt"
}
Subtitle style: 12 sp, weight 400, TextSecondary, marginTop 2 dp.

Chevron
Right-aligned. Rotates 180° on expanded toggle. Same animateFloatAsState(targetValue = if (expanded) 180f else 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) curve as your existing accordion headers.

Step 6 — Master TTS toggle row
When the outer card is expanded, render this row first:

SwitchRow(
    title = "Speak voice replies aloud",
    checked = state.speakRepliesAloud,
    onCheckedChange = onToggleSpeakReplies,
    tone = HandyDesign.Colors.Honey,
    toneSoft = HandyDesign.Colors.HoneySoft,
)
When toggled off, the TTS subsection's body content (when open) shows only "Replies are text-only" instead of the pickers. STT subsection is unaffected — speech recognition still works.

Step 7 — Subsection header composable
This is the key visual primitive. It's a tappable row that contains:

A bare yellow glyph at 16 dp (no tile, no border, no background — just the icon in Honey)
Title in display font, 14 sp, weight 600, TextPrimary
Inline subtitle in body font, 12 sp, weight 400, TextMuted (separated from title by 8 dp gap, baseline-aligned)
Right-aligned chevron in TextMuted, 12 dp, rotates 180° when open
@Composable
fun SubsectionHeader(
    icon: Painter,
    title: String,
    subtitle: String,
    open: Boolean,
    tone: Color = HandyDesign.Colors.Honey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "subsection-chevron",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(16.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = HandyDesignType.LabelDisplay.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.008).em,
                    color = HandyDesign.Colors.TextPrimary,
                ),
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.4.sp,
                    fontWeight = FontWeight.Normal,
                    color = HandyDesign.Colors.TextMuted,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = HandyDesign.Colors.TextMuted,
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { rotationZ = rotation + 90f },   // baseline points right; +90 = down when "open"
        )
    }
}
A 1 dp HorizontalDivider of BorderSubtle color sits above this row (same divider style as your other rows in the card).

Subtitle copy logic (TTS)
fun ttsSubtitle(speakOn: Boolean, tts: TtsProvider): String = when {
    !speakOn -> "Off · replies are text-only"
    tts is TtsProvider.System -> "System voice"
    tts is TtsProvider.Sarvam && tts.apiKey == null -> "Sarvam · needs key"
    tts is TtsProvider.Sarvam -> "Sarvam · ${tts.voice}"
    else -> ""
}
Subtitle copy logic (STT)
fun sttSubtitle(stt: SttProvider): String = when (stt) {
    is SttProvider.Android -> when (stt.mode) {
        SttMode.OnDevice         -> "Android · on-device only"
        SttMode.NetworkAllowed   -> "Android · network allowed"
        SttMode.Auto             -> "Android speech"
    }
    is SttProvider.SarvamSaarika ->
        if (stt.apiKey != null) "Sarvam Saarika" else "Sarvam Saarika · needs key"
}
Step 8 — TTS subsection body (rendered only when ttsOpen == true)
If speakRepliesAloud == false, render only one row:

SubsectionStatusRow(
    label = "Replies are text-only",
    dot = null,                        // no colored dot
    labelColor = HandyDesign.Colors.TextMuted,
)
If speakRepliesAloud == true, render in order:

Voice provider pill row

Options: System, Sarvam
Tone: honey
If Sarvam is selected and apiKey == null, the Sarvam pill shows tag "Add key" in TextMuted (uppercase, 9 sp, letter-spacing 0.08em, prefix " · ")
If TTS provider is Sarvam, render these in order:

Sarvam voice pill row — Ritu, Rahul, Simran
Spoken language pill row — Auto, English, Hindi, Hinglish
API key block (see Step 9)
Test voice row (see Step 10)
Status footer — always present at the end of the TTS body:

Green dot + "Using System voice" if provider == System
Green dot + "Sarvam ready" if Sarvam + key saved
Honey dot + "Add Sarvam key to speak with Bulbul" if Sarvam + no key
Footer style: 13 sp / weight 500 (or 600 when ready) / colored label, 8 dp colored dot with 0 0 12px <color>77 glow
Step 9 — STT subsection body (rendered only when sttOpen == true)
Render in order:

Speech provider pill row — Android, Sarvam Saarika. Saarika gets "Add key" tag when its key is missing.

If provider is Android:

STT mode pill row — Auto, On-device only, Network allowed
Recognition language pill row — System, English, Hindi, Hinglish
If language is Hinglish, render an inline helper line (12 sp, secondary text):
Hinglish enables Android's code-mix recognition where supported (Android 14+).

If provider is Sarvam Saarika:

Recognition language pill row — Auto, English, Hindi, Hinglish
API key block (see Step 9 below)
Status footer — always present at the end of the STT body:

Green dot + "Using Android speech recognition" if provider == Android
Green dot + "Saarika ready" if Saarika + key saved
Honey dot + "Add Sarvam key to transcribe with Saarika" if Saarika + no key
Step 10 — API key block (shared by TTS Sarvam and STT Saarika)
@Composable
fun ApiKeyBlock(
    overline: String,                  // e.g. "SARVAM API KEY"
    apiKey: String?,                   // null = empty, non-null = saved
    placeholder: String,               // e.g. "Paste your Sarvam key"
    helperWhenEmpty: String,           // e.g. "Add Sarvam key to use Bulbul. Handy falls back to System until a key is saved."
    onChange: (String) -> Unit,
    onPaste: () -> Unit,
)
Container: 14 dp top padding, 16 dp horizontal padding, 14 dp bottom padding (when no helper) / 24 dp bottom padding (when helper present)
Top divider: 1 dp BorderSubtle
Overline: 10 sp mono font, letter-spacing 0.12em, uppercase, TextMuted, 8 dp bottom margin
No key icon prefix on the overline. This was rejected in design review.
Text field: standard HandyTextField with eye + copy trailing icons
When apiKey == null: empty value, show placeholder
When saved: show masked value of format sk-····fddd (first 3 chars + middle dots + last 4 chars). Use a VisualTransformation to mask without exposing the raw key
Helper caption (only when apiKey == null): 12 sp, line-height 145 %, TextSecondary, 10 dp top margin from the text field
Step 11 — Test voice row (TTS only, when Sarvam selected)
Same ActionRow pattern as the Automations "Stop for 1 hour" rows:

Title: "Test voice" — body font, 14 sp, weight 500, TextPrimary
Subtitle: "Match the device language" — 11 sp, TextMuted, marginTop 2 dp
Trailing button: "Speak" — SurfaceElevated background, 12 dp radius, 8 / 16 dp padding, 13 sp weight 600, TextPrimary
Disabled state (when no key saved): opacity 0.55, TextMuted color, cursor = default, tap is a no-op. Show, don't hide — it tells the user that this affordance exists once they save a key.
When playing audio, the button label changes to "Stop" and the icon (if any) flips to a 12 dp square stop glyph. Loading state (waking up the TTS engine) briefly shows a 14 dp spinner.

Step 12 — Reset on provider switch — keep state
Critical UX rule: when the user switches providers, do not destroy saved values. Example flows:

User has Sarvam TTS with a saved key, voice=Rahul, language=Hindi. They switch to System. The Sarvam config is preserved. When they switch back to Sarvam, voice=Rahul and language=Hindi are restored.
Same applies to STT switching between Android and Saarika.
DataStore should persist:
tts.system.lastSelected (timestamp, for analytics only)
tts.sarvam.apiKey, tts.sarvam.voice, tts.sarvam.language
stt.android.mode, stt.android.language
stt.saarika.apiKey, stt.saarika.language
tts.currentProvider, stt.currentProvider
voice.speakRepliesAloud, voice.expanded, voice.ttsOpen, voice.sttOpen
Step 13 — Edge / error states
Scenario	Behavior
Speak replies OFF	Master toggle off. TTS subsection still tappable; opens to show only "Replies are text-only" muted line. Outer card subtitle: "Speaks Off · hears <stt>".
TTS = Sarvam, key empty	Helper caption visible. Test button disabled. Status footer: honey dot + "Add Sarvam key to speak with Bulbul". Outer card subtitle: "Speaks Sarvam (needs key) · hears <stt>".
TTS = Sarvam, invalid key after validation	Helper caption changes to red: "That key didn't work. Double-check it and try again." Status footer: red dot + "Invalid key — falling back to System".
TTS = Sarvam, offline during validation	Helper: "Couldn't reach Sarvam. Check your internet and retry." Test button enabled (retries validation on tap). Status footer: muted "Offline — falling back to System".
TTS = System, system TTS engine missing	Status footer: red dot + "System TTS unavailable. Install a TTS engine in System Settings." with a tappable "System Settings" link firing Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).
STT = Android, mic permission missing	Show a permission-prompt strip ABOVE the speech-provider pill row: amber background, mic icon, "Microphone permission needed", "Grant" button. Tapping fires ActivityResultLauncher.
STT = Android, network unavailable + mode = Network allowed	Pill row shows "Network allowed" still selected. Helper line below: "Offline — falling back to on-device".
STT = Saarika, key empty	Same pattern as TTS Sarvam empty. Helper: "Add Sarvam key to transcribe with Saarika. Falls back to Android until saved."
Hinglish language selected (Android STT)	Inline helper line below the language pill row: "Hinglish enables Android's code-mix recognition where supported (Android 14+)." If Build.VERSION.SDK_INT < 34, swap helper to: "Hinglish needs Android 14 or newer. Currently using English fallback."
Voice section completely off (master toggle off + both Sarvam keys missing)	Outer card subtitle: "Speaks Off · hears Saarika (needs key)". Both subsection rows still tappable; user can resolve from either.
Step 14 — Animations
Event	Animation
Outer card expand/collapse	Existing accordion animation — AnimatedVisibility(expandVertically(tween(240, FastOutSlowInEasing)) + fadeIn(tween(180))). Chevron rotates 180°.
Subsection expand/collapse	Same as outer — expandVertically(tween(220)). Subsection chevron rotates 180°.
Pill selection	160 ms color crossfade on background, border, and text.
Provider switch (System → Sarvam or vice versa)	The sub-rows below the provider pill row animate via AnimatedVisibility with vertical expand + 180 ms fadeIn. Don't crossfade — slide them in.
Status footer color transitions	animateColorAsState(targetValue = <new color>, animationSpec = tween(240, FastOutSlowInEasing)). Apply to both dot fill and label color.
Test voice button enable/disable	animateFloatAsState on opacity (1.0 ↔ 0.55) over 200 ms.
Step 15 — Accessibility
Outer card header: Modifier.toggleable(value = expanded, role = Role.Button, onValueChange = { onToggle() }) + contentDescription = "Voice settings. ${voiceCardSubtitle(state)}. Tap to ${if (expanded) "collapse" else "expand"}."
Each subsection header: Modifier.toggleable(...) with similar contentDescription
Master toggle: contentDescription = "Speak voice replies aloud" + stateDescription = if (on) "on" else "off"
Each pill: Modifier.selectable(selected, role = Role.RadioButton)
API key field: password-masking inputType, KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password)
Test button when disabled: contentDescription = "Test voice. Disabled. Add a Sarvam API key first."
Status footer: liveRegion = LiveRegionMode.Polite so TalkBack announces state changes
Step 16 — Wire into Settings screen
In the LazyColumn of section cards:

LazyColumn(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
) {
    item { BrainCard(state.brain, onBrainAction) }
    item {
        VoiceSection(
            state = state.voice,
            onAction = onVoiceAction,
        )
    }
    item { CapabilitiesSection(state.capabilities, onCapabilitiesAction) }
    item { AutomationsSection(state.automations, onAutomationsAction) }
    item { PrivacySection(state.privacy, onPrivacyAction) }
}
Remove all Voice / TTS / STT controls from inside BrainCard — the Brain card now contains only AI Brain (model + Anthropic key) and nothing else. If you find code paths that read voice settings from the Brain card's state, migrate them.

Step 17 — ViewModel migration
Add to SettingsViewModel:

sealed class VoiceAction {
    object ToggleExpanded : VoiceAction()
    object ToggleSpeakReplies : VoiceAction()
    object ToggleTtsOpen : VoiceAction()
    object ToggleSttOpen : VoiceAction()
    data class SelectTtsProvider(val provider: TtsProvider) : VoiceAction()
    data class SelectTtsVoice(val voice: SarvamVoice) : VoiceAction()
    data class SelectSpokenLanguage(val lang: SpokenLanguage) : VoiceAction()
    data class SetTtsKey(val key: String) : VoiceAction()
    object ClearTtsKey : VoiceAction()
    object TestTtsVoice : VoiceAction()
    data class SelectSttProvider(val provider: SttProvider) : VoiceAction()
    data class SelectSttMode(val mode: SttMode) : VoiceAction()
    data class SelectRecognitionLanguage(val lang: RecognitionLanguage) : VoiceAction()
    data class SetSttKey(val key: String) : VoiceAction()
    object ClearSttKey : VoiceAction()
    object RequestMicPermission : VoiceAction()
}
Persist all four "open" flags (expanded, speakRepliesAloud, ttsOpen, sttOpen) but default ttsOpen = false and sttOpen = false always on app launch, regardless of last persisted value. This is the design rule: when the user re-enters the Settings screen, they always see the compact collapsed Voice card first — they shouldn't be greeted with a 1200 dp open card just because they left it open last time.

Implementation:

init {
    viewModelScope.launch {
        val persisted = dataStore.loadVoiceState()
        _state.update { it.copy(
            voice = persisted.copy(ttsOpen = false, sttOpen = false)
        )}
    }
}
Step 18 — Final QA checklist
 Voice section sits between AI Brain and Capabilities
 AI Brain card no longer contains any voice or speech controls
 Outer Voice card header uses 44dp audio-lines tile in honey with Speaks X · hears Y subtitle
 Master "Speak voice replies aloud" toggle uses honey on-state
 Both subsection headers default to collapsed on every fresh entry
 Bare yellow volume-2 glyph on TTS subheading at 16 dp (no tile, no border)
 Bare yellow mic-vocal glyph on STT subheading at 16 dp
 Each subheading shows inline grey state subtitle baseline-aligned with title
 Chevrons rotate 180° in 220 ms with FastOutSlowInEasing
 All pill rows in both subsections use honey for selected state
 API key fields use the existing eye + copy trailing icons
 No key-icon prefix on the "SARVAM API KEY" overline (this was rejected)
 Test voice button stays in DOM when disabled (don't hide)
 Hinglish helper caption appears when applicable, falls back to "needs Android 14+" on older devices
 All 10 error / edge states from Step 13 render correctly
 Provider switches preserve last-used config (voice, language, key) per provider
 All accessibility semantics in place; TalkBack reads Voice settings · Speaks Sarvam · hears Saarika · Tap to collapse correctly
 Migration: existing users with TTS or STT settings stored in old locations have their values migrated to the new voice.* keys
End of prompt. After implementation, run your settings screenshot tests and verify all five demonstration artboards (07 · Settings (collapsed Voice), 07b · TTS expanded (Sarvam, no key), 07c · STT expanded (Android), 07d · both expanded, all ready, 07e · Settings (full page)) match the design canvas.

### USER 2026-05-26T13:38:45.459Z

<turn_aborted>
The user interrupted the previous turn on purpose. Any running unified exec processes may still be running in the background. If any tools/commands were aborted, they may have partially executed.
</turn_aborted>

### USER 2026-05-26T13:38:49.640Z

can u restart please? 

Prompt for Codex — Redesign the Voice section with collapsible TTS + STT subsections
You are redesigning Handy's Voice settings section. The current implementation has Speech-to-Text controls inside the AI Brain card and a flat Voice section that mixes TTS and STT concerns. The redesign moves everything into one collapsible Voice section with two nested collapsible subsections — Text-to-speech and Speech-to-text — that default to collapsed.

Files you will touch
HandyV2/app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt (or wherever the Settings UI is composed)
HandyV2/app/src/main/kotlin/com/handy/app/ui/theme/HandyDesign.kt (token additions)
New file: HandyV2/app/src/main/kotlin/com/handy/app/settings/VoiceSection.kt
Remove the Speech-to-text rows currently nested in BrainCard.kt (or wherever they live)
Step 1 — Honey tokens
If you have not already, add to HandyDesign.Colors:

val Honey       = Color(0xFFF0C674)
val HoneySoft   = Color(0x2EF0C674)   // 18 % alpha — pill backgrounds, tile fill
val HoneyHair   = Color(0x5CF0C674)   // 36 % alpha — pill border (selected)
val HoneyInk    = Color(0xFF1F1709)   // text on solid honey (not used in this section)
Step 2 — Make HandySwitch and PillSelectRow tone-aware
These are reused from earlier prompts. If your existing components don't accept a tone, add optional tone, toneSoft, toneHair color parameters that default to Accent/AccentSoft/AccentHair. The Voice section passes honey variants.

Step 3 — State model
sealed class TtsProvider {
    object System : TtsProvider()
    data class Sarvam(
        val apiKey: String?,                    // null = empty, non-null = saved (masked when shown)
        val voice: SarvamVoice = SarvamVoice.Ritu,
        val language: SpokenLanguage = SpokenLanguage.Auto,
    ) : TtsProvider()
}

sealed class SttProvider {
    data class Android(
        val mode: SttMode = SttMode.Auto,
        val language: RecognitionLanguage = RecognitionLanguage.System,
    ) : SttProvider()
    data class SarvamSaarika(
        val apiKey: String?,
        val language: RecognitionLanguage = RecognitionLanguage.Auto,
    ) : SttProvider()
}

enum class SarvamVoice { Ritu, Rahul, Simran }
enum class SpokenLanguage { Auto, English, Hindi, Hinglish }
enum class SttMode { Auto, OnDevice, NetworkAllowed }
enum class RecognitionLanguage { System, Auto, English, Hindi, Hinglish }

data class VoiceSectionState(
    val expanded: Boolean = false,             // outer card open/closed
    val speakRepliesAloud: Boolean = true,     // master TTS toggle
    val tts: TtsProvider = TtsProvider.System,
    val stt: SttProvider = SttProvider.Android(),
    val ttsOpen: Boolean = false,              // TTS subsection — DEFAULT COLLAPSED
    val sttOpen: Boolean = false,              // STT subsection — DEFAULT COLLAPSED
)
Step 4 — Card structure overview
┌─────────────────────────────────────────────────┐
│ [♪]  Voice                                  ⌄  │  ← Outer accordion header (44dp tile, honey)
│      Speaks Sarvam · hears Saarika              │
├─────────────────────────────────────────────────┤
│ Speak voice replies aloud         [●━]          │  ← Master TTS toggle (honey)
├─────────────────────────────────────────────────┤
│ 🔊 Text-to-speech   Sarvam · Ritu          ⌄  │  ← Subsection header — TAPPABLE
│                                                 │     icon: yellow volume2 16dp, no tile
├─────────────────────────────────────────────────┤     subtitle: muted grey, inline
│   [TTS body — see Step 5]                       │  ← Only rendered if ttsOpen = true
├─────────────────────────────────────────────────┤
│ 🎙 Speech-to-text   Android · on-device   ⌄  │  ← Subsection header — TAPPABLE
├─────────────────────────────────────────────────┤
│   [STT body — see Step 6]                       │  ← Only rendered if sttOpen = true
└─────────────────────────────────────────────────┘
Step 5 — Outer Voice card
Header tile
Tile: 44 dp × 44 dp, 12 dp radius
Background: HoneySoft
Border: 0.5 dp Honey.copy(alpha = 0.20f)
Icon: ic_audio_lines.xml (Lucide audio-lines), 22 dp, tinted Honey
Title row
Title "Voice": display font, 17 sp, weight 600, letter-spacing -0.012em, TextPrimary
Subtitle composed from current state:
fun voiceCardSubtitle(state: VoiceSectionState): String {
    val tts = when {
        !state.speakRepliesAloud -> "Off"
        state.tts is TtsProvider.System -> "System"
        state.tts is TtsProvider.Sarvam && state.tts.apiKey != null -> "Sarvam"
        else -> "Sarvam (needs key)"
    }
    val stt = when (val s = state.stt) {
        is SttProvider.Android -> "Android"
        is SttProvider.SarvamSaarika -> if (s.apiKey != null) "Saarika" else "Saarika (needs key)"
    }
    return "Speaks $tts · hears $stt"
}
Subtitle style: 12 sp, weight 400, TextSecondary, marginTop 2 dp.

Chevron
Right-aligned. Rotates 180° on expanded toggle. Same animateFloatAsState(targetValue = if (expanded) 180f else 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) curve as your existing accordion headers.

Step 6 — Master TTS toggle row
When the outer card is expanded, render this row first:

SwitchRow(
    title = "Speak voice replies aloud",
    checked = state.speakRepliesAloud,
    onCheckedChange = onToggleSpeakReplies,
    tone = HandyDesign.Colors.Honey,
    toneSoft = HandyDesign.Colors.HoneySoft,
)
When toggled off, the TTS subsection's body content (when open) shows only "Replies are text-only" instead of the pickers. STT subsection is unaffected — speech recognition still works.

Step 7 — Subsection header composable
This is the key visual primitive. It's a tappable row that contains:

A bare yellow glyph at 16 dp (no tile, no border, no background — just the icon in Honey)
Title in display font, 14 sp, weight 600, TextPrimary
Inline subtitle in body font, 12 sp, weight 400, TextMuted (separated from title by 8 dp gap, baseline-aligned)
Right-aligned chevron in TextMuted, 12 dp, rotates 180° when open
@Composable
fun SubsectionHeader(
    icon: Painter,
    title: String,
    subtitle: String,
    open: Boolean,
    tone: Color = HandyDesign.Colors.Honey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "subsection-chevron",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(16.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = HandyDesignType.LabelDisplay.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.008).em,
                    color = HandyDesign.Colors.TextPrimary,
                ),
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.4.sp,
                    fontWeight = FontWeight.Normal,
                    color = HandyDesign.Colors.TextMuted,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = HandyDesign.Colors.TextMuted,
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { rotationZ = rotation + 90f },   // baseline points right; +90 = down when "open"
        )
    }
}
A 1 dp HorizontalDivider of BorderSubtle color sits above this row (same divider style as your other rows in the card).

Subtitle copy logic (TTS)
fun ttsSubtitle(speakOn: Boolean, tts: TtsProvider): String = when {
    !speakOn -> "Off · replies are text-only"
    tts is TtsProvider.System -> "System voice"
    tts is TtsProvider.Sarvam && tts.apiKey == null -> "Sarvam · needs key"
    tts is TtsProvider.Sarvam -> "Sarvam · ${tts.voice}"
    else -> ""
}
Subtitle copy logic (STT)
fun sttSubtitle(stt: SttProvider): String = when (stt) {
    is SttProvider.Android -> when (stt.mode) {
        SttMode.OnDevice         -> "Android · on-device only"
        SttMode.NetworkAllowed   -> "Android · network allowed"
        SttMode.Auto             -> "Android speech"
    }
    is SttProvider.SarvamSaarika ->
        if (stt.apiKey != null) "Sarvam Saarika" else "Sarvam Saarika · needs key"
}
Step 8 — TTS subsection body (rendered only when ttsOpen == true)
If speakRepliesAloud == false, render only one row:

SubsectionStatusRow(
    label = "Replies are text-only",
    dot = null,                        // no colored dot
    labelColor = HandyDesign.Colors.TextMuted,
)
If speakRepliesAloud == true, render in order:

Voice provider pill row

Options: System, Sarvam
Tone: honey
If Sarvam is selected and apiKey == null, the Sarvam pill shows tag "Add key" in TextMuted (uppercase, 9 sp, letter-spacing 0.08em, prefix " · ")
If TTS provider is Sarvam, render these in order:

Sarvam voice pill row — Ritu, Rahul, Simran
Spoken language pill row — Auto, English, Hindi, Hinglish
API key block (see Step 9)
Test voice row (see Step 10)
Status footer — always present at the end of the TTS body:

Green dot + "Using System voice" if provider == System
Green dot + "Sarvam ready" if Sarvam + key saved
Honey dot + "Add Sarvam key to speak with Bulbul" if Sarvam + no key
Footer style: 13 sp / weight 500 (or 600 when ready) / colored label, 8 dp colored dot with 0 0 12px <color>77 glow
Step 9 — STT subsection body (rendered only when sttOpen == true)
Render in order:

Speech provider pill row — Android, Sarvam Saarika. Saarika gets "Add key" tag when its key is missing.

If provider is Android:

STT mode pill row — Auto, On-device only, Network allowed
Recognition language pill row — System, English, Hindi, Hinglish
If language is Hinglish, render an inline helper line (12 sp, secondary text):
Hinglish enables Android's code-mix recognition where supported (Android 14+).

If provider is Sarvam Saarika:

Recognition language pill row — Auto, English, Hindi, Hinglish
API key block (see Step 9 below)
Status footer — always present at the end of the STT body:

Green dot + "Using Android speech recognition" if provider == Android
Green dot + "Saarika ready" if Saarika + key saved
Honey dot + "Add Sarvam key to transcribe with Saarika" if Saarika + no key
Step 10 — API key block (shared by TTS Sarvam and STT Saarika)
@Composable
fun ApiKeyBlock(
    overline: String,                  // e.g. "SARVAM API KEY"
    apiKey: String?,                   // null = empty, non-null = saved
    placeholder: String,               // e.g. "Paste your Sarvam key"
    helperWhenEmpty: String,           // e.g. "Add Sarvam key to use Bulbul. Handy falls back to System until a key is saved."
    onChange: (String) -> Unit,
    onPaste: () -> Unit,
)
Container: 14 dp top padding, 16 dp horizontal padding, 14 dp bottom padding (when no helper) / 24 dp bottom padding (when helper present)
Top divider: 1 dp BorderSubtle
Overline: 10 sp mono font, letter-spacing 0.12em, uppercase, TextMuted, 8 dp bottom margin
No key icon prefix on the overline. This was rejected in design review.
Text field: standard HandyTextField with eye + copy trailing icons
When apiKey == null: empty value, show placeholder
When saved: show masked value of format sk-····fddd (first 3 chars + middle dots + last 4 chars). Use a VisualTransformation to mask without exposing the raw key
Helper caption (only when apiKey == null): 12 sp, line-height 145 %, TextSecondary, 10 dp top margin from the text field
Step 11 — Test voice row (TTS only, when Sarvam selected)
Same ActionRow pattern as the Automations "Stop for 1 hour" rows:

Title: "Test voice" — body font, 14 sp, weight 500, TextPrimary
Subtitle: "Match the device language" — 11 sp, TextMuted, marginTop 2 dp
Trailing button: "Speak" — SurfaceElevated background, 12 dp radius, 8 / 16 dp padding, 13 sp weight 600, TextPrimary
Disabled state (when no key saved): opacity 0.55, TextMuted color, cursor = default, tap is a no-op. Show, don't hide — it tells the user that this affordance exists once they save a key.
When playing audio, the button label changes to "Stop" and the icon (if any) flips to a 12 dp square stop glyph. Loading state (waking up the TTS engine) briefly shows a 14 dp spinner.

Step 12 — Reset on provider switch — keep state
Critical UX rule: when the user switches providers, do not destroy saved values. Example flows:

User has Sarvam TTS with a saved key, voice=Rahul, language=Hindi. They switch to System. The Sarvam config is preserved. When they switch back to Sarvam, voice=Rahul and language=Hindi are restored.
Same applies to STT switching between Android and Saarika.
DataStore should persist:
tts.system.lastSelected (timestamp, for analytics only)
tts.sarvam.apiKey, tts.sarvam.voice, tts.sarvam.language
stt.android.mode, stt.android.language
stt.saarika.apiKey, stt.saarika.language
tts.currentProvider, stt.currentProvider
voice.speakRepliesAloud, voice.expanded, voice.ttsOpen, voice.sttOpen
Step 13 — Edge / error states
Scenario	Behavior
Speak replies OFF	Master toggle off. TTS subsection still tappable; opens to show only "Replies are text-only" muted line. Outer card subtitle: "Speaks Off · hears <stt>".
TTS = Sarvam, key empty	Helper caption visible. Test button disabled. Status footer: honey dot + "Add Sarvam key to speak with Bulbul". Outer card subtitle: "Speaks Sarvam (needs key) · hears <stt>".
TTS = Sarvam, invalid key after validation	Helper caption changes to red: "That key didn't work. Double-check it and try again." Status footer: red dot + "Invalid key — falling back to System".
TTS = Sarvam, offline during validation	Helper: "Couldn't reach Sarvam. Check your internet and retry." Test button enabled (retries validation on tap). Status footer: muted "Offline — falling back to System".
TTS = System, system TTS engine missing	Status footer: red dot + "System TTS unavailable. Install a TTS engine in System Settings." with a tappable "System Settings" link firing Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).
STT = Android, mic permission missing	Show a permission-prompt strip ABOVE the speech-provider pill row: amber background, mic icon, "Microphone permission needed", "Grant" button. Tapping fires ActivityResultLauncher.
STT = Android, network unavailable + mode = Network allowed	Pill row shows "Network allowed" still selected. Helper line below: "Offline — falling back to on-device".
STT = Saarika, key empty	Same pattern as TTS Sarvam empty. Helper: "Add Sarvam key to transcribe with Saarika. Falls back to Android until saved."
Hinglish language selected (Android STT)	Inline helper line below the language pill row: "Hinglish enables Android's code-mix recognition where supported (Android 14+)." If Build.VERSION.SDK_INT < 34, swap helper to: "Hinglish needs Android 14 or newer. Currently using English fallback."
Voice section completely off (master toggle off + both Sarvam keys missing)	Outer card subtitle: "Speaks Off · hears Saarika (needs key)". Both subsection rows still tappable; user can resolve from either.
Step 14 — Animations
Event	Animation
Outer card expand/collapse	Existing accordion animation — AnimatedVisibility(expandVertically(tween(240, FastOutSlowInEasing)) + fadeIn(tween(180))). Chevron rotates 180°.
Subsection expand/collapse	Same as outer — expandVertically(tween(220)). Subsection chevron rotates 180°.
Pill selection	160 ms color crossfade on background, border, and text.
Provider switch (System → Sarvam or vice versa)	The sub-rows below the provider pill row animate via AnimatedVisibility with vertical expand + 180 ms fadeIn. Don't crossfade — slide them in.
Status footer color transitions	animateColorAsState(targetValue = <new color>, animationSpec = tween(240, FastOutSlowInEasing)). Apply to both dot fill and label color.
Test voice button enable/disable	animateFloatAsState on opacity (1.0 ↔ 0.55) over 200 ms.
Step 15 — Accessibility
Outer card header: Modifier.toggleable(value = expanded, role = Role.Button, onValueChange = { onToggle() }) + contentDescription = "Voice settings. ${voiceCardSubtitle(state)}. Tap to ${if (expanded) "collapse" else "expand"}."
Each subsection header: Modifier.toggleable(...) with similar contentDescription
Master toggle: contentDescription = "Speak voice replies aloud" + stateDescription = if (on) "on" else "off"
Each pill: Modifier.selectable(selected, role = Role.RadioButton)
API key field: password-masking inputType, KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password)
Test button when disabled: contentDescription = "Test voice. Disabled. Add a Sarvam API key first."
Status footer: liveRegion = LiveRegionMode.Polite so TalkBack announces state changes
Step 16 — Wire into Settings screen
In the LazyColumn of section cards:

LazyColumn(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
) {
    item { BrainCard(state.brain, onBrainAction) }
    item {
        VoiceSection(
            state = state.voice,
            onAction = onVoiceAction,
        )
    }
    item { CapabilitiesSection(state.capabilities, onCapabilitiesAction) }
    item { AutomationsSection(state.automations, onAutomationsAction) }
    item { PrivacySection(state.privacy, onPrivacyAction) }
}
Remove all Voice / TTS / STT controls from inside BrainCard — the Brain card now contains only AI Brain (model + Anthropic key) and nothing else. If you find code paths that read voice settings from the Brain card's state, migrate them.

Step 17 — ViewModel migration
Add to SettingsViewModel:

sealed class VoiceAction {
    object ToggleExpanded : VoiceAction()
    object ToggleSpeakReplies : VoiceAction()
    object ToggleTtsOpen : VoiceAction()
    object ToggleSttOpen : VoiceAction()
    data class SelectTtsProvider(val provider: TtsProvider) : VoiceAction()
    data class SelectTtsVoice(val voice: SarvamVoice) : VoiceAction()
    data class SelectSpokenLanguage(val lang: SpokenLanguage) : VoiceAction()
    data class SetTtsKey(val key: String) : VoiceAction()
    object ClearTtsKey : VoiceAction()
    object TestTtsVoice : VoiceAction()
    data class SelectSttProvider(val provider: SttProvider) : VoiceAction()
    data class SelectSttMode(val mode: SttMode) : VoiceAction()
    data class SelectRecognitionLanguage(val lang: RecognitionLanguage) : VoiceAction()
    data class SetSttKey(val key: String) : VoiceAction()
    object ClearSttKey : VoiceAction()
    object RequestMicPermission : VoiceAction()
}
Persist all four "open" flags (expanded, speakRepliesAloud, ttsOpen, sttOpen) but default ttsOpen = false and sttOpen = false always on app launch, regardless of last persisted value. This is the design rule: when the user re-enters the Settings screen, they always see the compact collapsed Voice card first — they shouldn't be greeted with a 1200 dp open card just because they left it open last time.

Implementation:

init {
    viewModelScope.launch {
        val persisted = dataStore.loadVoiceState()
        _state.update { it.copy(
            voice = persisted.copy(ttsOpen = false, sttOpen = false)
        )}
    }
}
Step 18 — Final QA checklist
 Voice section sits between AI Brain and Capabilities
 AI Brain card no longer contains any voice or speech controls
 Outer Voice card header uses 44dp audio-lines tile in honey with Speaks X · hears Y subtitle
 Master "Speak voice replies aloud" toggle uses honey on-state
 Both subsection headers default to collapsed on every fresh entry
 Bare yellow volume-2 glyph on TTS subheading at 16 dp (no tile, no border)
 Bare yellow mic-vocal glyph on STT subheading at 16 dp
 Each subheading shows inline grey state subtitle baseline-aligned with title
 Chevrons rotate 180° in 220 ms with FastOutSlowInEasing
 All pill rows in both subsections use honey for selected state
 API key fields use the existing eye + copy trailing icons
 No key-icon prefix on the "SARVAM API KEY" overline (this was rejected)
 Test voice button stays in DOM when disabled (don't hide)
 Hinglish helper caption appears when applicable, falls back to "needs Android 14+" on older devices
 All 10 error / edge states from Step 13 render correctly
 Provider switches preserve last-used config (voice, language, key) per provider
 All accessibility semantics in place; TalkBack reads Voice settings · Speaks Sarvam · hears Saarika · Tap to collapse correctly
 Migration: existing users with TTS or STT settings stored in old locations have their values migrated to the new voice.* keys
End of prompt. After implementation, run your settings screenshot tests and verify all five demonstration artboards (07 · Settings (collapsed Voice), 07b · TTS expanded (Sarvam, no key), 07c · STT expanded (Android), 07d · both expanded, all ready, 07e · Settings (full page)) match the design canvas.

### ASSISTANT 2026-05-26T13:39:11.771Z

Yep, restarting from the top of the task. A couple of tiny persistence edits landed before the interruption, so I’ll treat the current worktree as the starting point, re-read the affected files, and then implement the redesign cleanly from there.

### ASSISTANT 2026-05-26T13:49:46.998Z

I’ve got the shape now: the current repo already has honey tokens and tone-aware pills/switches, and the old STT rows are inside `BrainSection`. I’m replacing the old flat `VoiceSection` with the nested TTS/STT accordion and then I’ll slim `BrainSection` back down to just the model/key card.

### ASSISTANT 2026-05-26T13:54:58.696Z

The visible UI migration is now in place: `BrainSection` no longer renders STT controls, and `VoiceSection` owns TTS/STT with the two nested accordions. I’m switching into build mode now, because this was a broad Kotlin/Compose edit and the compiler will be the honest friend here.

### ASSISTANT 2026-05-26T13:55:07.812Z

Build hit a local environment snag: Gradle can’t find a Java runtime. I’m going to look for an installed Android Studio/JBR on the machine and, if it’s there, rerun with `JAVA_HOME` pointed at it.

### ASSISTANT 2026-05-26T14:12:25.959Z

Restarted and implemented the Voice redesign.

What changed:
- Rebuilt [VoiceSection.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/settings/sections/VoiceSection.kt) with one honey Voice accordion and nested collapsed TTS/STT subsections.
- Removed STT UI from [BrainSection.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/settings/sections/BrainSection.kt), so AI Brain is back to model + Anthropic key only.
- Wired Voice between Brain and Capabilities in [SettingsActivity.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/settings/SettingsActivity.kt), now using the requested `LazyColumn`.
- Added Voice state/actions and persistence mapping in [SettingsViewModel.kt](/Users/satvik.bansal/Desktop/Handy.android/HandyV2/app/src/main/kotlin/com/handy/app/settings/SettingsViewModel.kt).
- Added DataStore fields for Voice open state, Saarika language, and TTS analytics timestamp.
- Added `ic_volume_2.xml` and `ic_mic_vocal.xml`.
- Honey tokens and tone-aware switch/pills were already present; I adjusted the honey tile border alpha and switch accessibility state.

Verification:
- `git diff --check` passed.
- Gradle/screenshot tests could not run because this machine has no usable Java runtime: `java -version` and `./gradlew ...` both fail with “Unable to locate a Java Runtime.”
