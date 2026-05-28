package com.handy.core.overlay

import com.handy.core.action.ActionRisk
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.IntRect
import com.handy.core.speech.SpeechAudioState
import com.handy.core.tool.ToolContext

/**
 * Single-source-of-truth state for the Handy overlay (Unified Buddy
 * widget + overlay chat panel). Observed by the Compose overlay UI.
 *
 * Mutual-exclusion rules for side bubbles (scope §3) are enforced in the
 * presenter, not here. The presenter picks exactly one non-null bubble; the
 * renderer trusts it.
 */
data class OverlayPanelState(
    val mode: OverlayMode = OverlayMode.IdleWidget,
    val flightFsm: FlightFsm = FlightFsm.Docked,
    val buddyState: BuddyState = BuddyState.DOCKED,
    val audioState: SpeechAudioState = SpeechAudioState.IDLE,
    val bubble: BuddyBubble? = null,
    /**
     * True while a `[POINT]` is actively being resolved / flown / dwelt.
     * Blocks the user from tapping the widget to open the panel while
     * the buddy is mid-flight.
     */
    val isFlying: Boolean = false,
    /**
     * Most recent reason the flight target was invalidated. Diagnostics
     * surfaces this so manual rotation / IME / fold cancellation checks
     * have a visible breadcrumb.
     */
    val lastFlightCancellationReason: String? = null,
    /**
     * Chat panel slice. Ignored when [mode] != [OverlayMode.ChatPanel].
     */
    val panel: PanelContent = PanelContent(),
    /**
     * Tap-for-me confirmation rendered by the overlay service after the
     * buddy lands on a target and before any accessibility gesture is
     * dispatched.
     */
    val tapForMeConfirmation: TapForMeConfirmation? = null,
    /**
     * Candidate targets carried from the pointer resolver. The overlay
     * may render these as chips for ambiguous hits, and voice correction
     * can reuse them without asking the model to resolve again.
     */
    val candidateOptions: CandidateOptions? = null,
) {
    val isPanelVisible: Boolean get() = mode == OverlayMode.ChatPanel
    val isManualTargetSelection: Boolean get() = mode == OverlayMode.ManualTargetSelection
}

data class CandidateOptions(
    val options: List<CandidateOption>,
    val activeCandidateId: String? = options.firstOrNull()?.id,
    val visible: Boolean = false,
) {
    val hasAlternatives: Boolean get() = options.size > 1
    val activeIndex: Int
        get() = options.indexOfFirst { it.id == activeCandidateId }
            .takeIf { it >= 0 }
            ?: 0
}

data class CandidateOption(
    val id: String,
    val label: String,
    val role: String?,
    val markId: String?,
    val viewId: String?,
    val bounds: IntRect,
    val confidence: Float,
    val actionable: Boolean = true,
)

data class TapForMeConfirmation(
    val id: Long,
    val targetLabel: String,
    val appLabel: String?,
    val packageName: String?,
    val confirmationLevel: ConfirmationLevel,
    val risk: ActionRisk,
    val reason: String?,
    val typingText: String? = null,
    val planPreview: PlanPreview? = null,
)

data class PlanPreview(
    val recipeId: String,
    val recipeDisplayName: String,
    val totalStepCount: Int,
    val steps: List<PlanStep>,
)

data class PlanStep(
    val index: Int,
    val title: String,
    val isSensitive: Boolean,
)

data class TapForMeConfirmationDecision(
    val approved: Boolean,
    val typingText: String? = null,
)

/**
 * Strict high-level lifecycle for Buddy. [OverlayPresenter] is the only
 * mutator and rejects illegal edges before publishing state.
 */
enum class FlightFsm {
    Docked,
    Listening,
    Thinking,
    Answering,
    PreparingPoint,
    Flying,
    Pointing,
    ActionConfirm,
    Acting,
    ActionResult,
    Returning,
    Error,
}

/**
 * High-level state of the Unified Buddy (widget = pointer). Drives
 * both the lens chrome and the motion controller. See scope §3.
 */
enum class BuddyState {
    DOCKED,
    LISTENING,
    THINKING,
    STREAMING,
    PREPARING_POINT,
    FLYING,
    POINTING,
    CANCELLING,
    ACTING,
    SPEAKING,
    DRAGGING,
    AUDIO_SPEAKING,
}

data class BuddyBubble(
    val tone: BubbleTone,
    val label: String,
    val prefix: String? = null,
    val leading: BubbleIcon? = null,
    val progress: Float? = null,
    val italic: Boolean = false,
    val small: Boolean = false,
    /** Mirroring side for right-docked widgets. */
    val anchor: BubbleAnchor = BubbleAnchor.LEFT,
) {
    companion object {
        /** Live voice transcript (italic, accent tone). */
        fun transcript(text: String): BuddyBubble =
            BuddyBubble(tone = BubbleTone.ACCENT, label = text, italic = true)

        /** Short spoken assistant response (accent, normal weight). */
        fun spokenAnswer(text: String): BuddyBubble =
            BuddyBubble(tone = BubbleTone.ACCENT, label = text)

        /** Muted "Thinking..." pill. */
        fun thinking(label: String = "Thinking…"): BuddyBubble =
            BuddyBubble(tone = BubbleTone.MUTED, label = label, small = true)

        /** Web search status — violet for Brave / GitHub, honey for Jina. */
        fun webTool(provider: WebToolProvider, label: String): BuddyBubble = when (provider) {
            WebToolProvider.BRAVE -> BuddyBubble(tone = BubbleTone.VIOLET, label = label)
            WebToolProvider.GITHUB -> BuddyBubble(tone = BubbleTone.VIOLET, label = label)
            WebToolProvider.JINA -> BuddyBubble(
                tone = BubbleTone.HONEY,
                label = label,
                prefix = "Page · Jina",
            )
        }

        /** Pointer flying or pointing — blue tone, no leading icon. */
        fun navigation(label: String): BuddyBubble =
            BuddyBubble(tone = BubbleTone.POINT, label = label)

        /** Acting (tap or type) — emerald tone + leading icon + progress. */
        fun actingTap(label: String, progress: Float? = null): BuddyBubble =
            BuddyBubble(
                tone = BubbleTone.ACT,
                label = label,
                leading = BubbleIcon.HAND_TAP,
                progress = progress,
            )

        fun actingType(label: String, progress: Float? = null): BuddyBubble =
            BuddyBubble(
                tone = BubbleTone.ACT,
                label = label,
                leading = BubbleIcon.KEYBOARD,
                progress = progress,
            )

        /** Recipe step — accent + prefix "Step X of Y" + leading recipe icon. */
        fun recipeStep(stepIndex: Int, stepCount: Int, label: String): BuddyBubble =
            BuddyBubble(
                tone = BubbleTone.ACCENT,
                label = label,
                prefix = "Step $stepIndex of $stepCount",
                leading = BubbleIcon.RECIPE,
                progress = if (stepCount > 0) stepIndex.toFloat() / stepCount else null,
            )

        /** Blocked — danger + warning icon. */
        fun blocked(label: String): BuddyBubble =
            BuddyBubble(
                tone = BubbleTone.DANGER,
                label = blockedLabel(label),
                leading = BubbleIcon.WARNING,
            )

        /** Failed — danger + warning + prefix. */
        fun failed(prefix: String, label: String): BuddyBubble =
            BuddyBubble(
                tone = BubbleTone.DANGER,
                label = label,
                prefix = prefix,
                leading = BubbleIcon.WARNING,
            )

        /** Wrong target / undo — accent + back icon + small. */
        fun wrongTarget(label: String = "Wrong one? Tap to undo."): BuddyBubble =
            BuddyBubble(
                tone = BubbleTone.ACCENT,
                label = label,
                leading = BubbleIcon.BACK,
                small = true,
            )

        /** Ambiguous — point tone + prefix. Candidate chips render separately. */
        fun ambiguous(prefix: String, label: String): BuddyBubble =
            BuddyBubble(
                tone = BubbleTone.POINT,
                label = label,
                prefix = prefix,
                small = true,
            )

        private fun blockedLabel(label: String): String = when (label) {
            "incognito" -> "Blocked · Incognito mode"
            "secure-window" -> "Blocked · Secure window"
            "tool-suggestion-only" -> "Blocked · Tool-suggested action"
            else -> if (label.startsWith("Blocked ·")) label else "Blocked · $label"
        }
    }
}

enum class BubbleTone { ACCENT, MUTED, VIOLET, HONEY, POINT, ACT, DANGER }
enum class BubbleIcon { HAND_TAP, KEYBOARD, RECIPE, BACK, WARNING, CURSOR, GLOBE }
enum class BubbleAnchor { LEFT, RIGHT }
enum class WebToolProvider {
    BRAVE,
    GITHUB,
    JINA,
    ;

    companion object {
        fun fromVerb(text: String): WebToolProvider {
            val normalized = text.lowercase()
            return when {
                "github" in normalized -> GITHUB
                "jina" in normalized || "page" in normalized || "reading" in normalized -> JINA
                else -> BRAVE
            }
        }
    }
}

/** Panel content when [OverlayPanelState.mode] is [OverlayMode.ChatPanel]. */
data class PanelContent(
    val snapshot: PanelSnapshot? = null,
    val greeting: String = "What can I help you with?",
    val draftInput: String = "",
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val voiceNotice: String = "",
    val streamingDelta: String = "",
    val isStreaming: Boolean = false,
    val isVoiceArmed: Boolean = false,
    val loadingVerb: String = "",
    val pendingConfirmation: PendingConfirmation? = null,
    val lowConfidenceTranscript: LowConfidenceTranscript? = null,
    val recentResponsePreview: String = "",
    val errorBanner: String? = null,
    val contextRefreshInProgress: Boolean = false,
    val contextRefreshPreviewGreeting: String? = null,
    val contextRefreshPreviewLabel: String? = null,
) {
    data class PendingConfirmation(
        val id: Long,
        val reason: String,
        val action: AssistantAction? = null,
    )

    data class LowConfidenceTranscript(
        val best: String,
        val alternatives: List<String>,
    )
}

/**
 * Cache-at-tap snapshot (cursorbuddy recipe #4). Captured at the exact
 * moment the widget is tapped or long-pressed — before the panel
 * requests focus and changes which window is "active" in Accessibility
 * terms.
 *
 * Pure data: `:core` never touches Android. `AccessibilityMark` carries
 * the compact per-node shape emitted by `AccessibilityMarksProvider`.
 */
data class PanelSnapshot(
    val toolContext: ToolContext,
    val capturedAtEpochMs: Long,
    val marks: List<AccessibilityMark> = emptyList(),
    /**
     * Optional pointing result that the last assistant turn emitted —
     * carried so the panel can reattach a [POINT] decision if the user
     * reopens the panel before dismissing it.
     */
    val lastPointing: AssistantMarkupParser.PointingResult? = null,
)

/**
 * Compact representation of one actionable UI element. Ported from
 * cursorbuddy `UiTreeSerializer.toCompactJson` (recipe #2) — 50-node
 * cap is enforced by the provider, not the data class.
 *
 * Passwords: `isPassword=true` with redacted text/description — never expose
 * field contents.
 */
data class AccessibilityMark(
    val markId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdSuffix: String? = null,
    val role: String,
    val bounds: IntArray,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = true,
    val isPassword: Boolean = false,
    val isChecked: Boolean? = null,
) {
    init {
        require(bounds.size == 4) { "bounds must be [left, top, right, bottom]" }
    }

    /** `[left, top, right, bottom]` accessor. */
    val left: Int get() = bounds[0]
    val top: Int get() = bounds[1]
    val right: Int get() = bounds[2]
    val bottom: Int get() = bounds[3]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccessibilityMark) return false
        return markId == other.markId &&
            text == other.text &&
            contentDescription == other.contentDescription &&
            viewIdSuffix == other.viewIdSuffix &&
            role == other.role &&
            bounds.contentEquals(other.bounds) &&
            clickable == other.clickable &&
            scrollable == other.scrollable &&
            editable == other.editable &&
            enabled == other.enabled &&
            isPassword == other.isPassword &&
            isChecked == other.isChecked
    }

    override fun hashCode(): Int {
        var result = markId?.hashCode() ?: 0
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (contentDescription?.hashCode() ?: 0)
        result = 31 * result + (viewIdSuffix?.hashCode() ?: 0)
        result = 31 * result + role.hashCode()
        result = 31 * result + bounds.contentHashCode()
        result = 31 * result + clickable.hashCode()
        result = 31 * result + scrollable.hashCode()
        result = 31 * result + editable.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + isPassword.hashCode()
        result = 31 * result + (isChecked?.hashCode() ?: 0)
        return result
    }
}
