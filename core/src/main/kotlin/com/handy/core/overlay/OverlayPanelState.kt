package com.handy.core.overlay

import com.handy.core.action.AssistantAction
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.tool.ToolContext

/**
 * Single-source-of-truth state for the Handy overlay (Unified Buddy
 * widget + overlay chat panel). Observed by the Compose overlay UI.
 *
 * Mutual-exclusion rules for the four bubble colors (scope §3) are
 * enforced in the presenter, not here — this data class is intentionally
 * dumb. The presenter picks exactly one non-null bubble; the renderer
 * trusts it.
 */
data class OverlayPanelState(
    val mode: OverlayMode = OverlayMode.IdleWidget,
    val buddyState: BuddyState = BuddyState.DOCKED,
    val bubble: BuddyBubble? = null,
    /**
     * True while a `[POINT]` is actively being resolved / flown / dwelt.
     * Blocks the user from tapping the widget to open the panel while
     * the buddy is mid-flight.
     */
    val isFlying: Boolean = false,
    /**
     * Chat panel slice. Ignored when [mode] != [OverlayMode.ChatPanel].
     */
    val panel: PanelContent = PanelContent(),
) {
    val isPanelVisible: Boolean get() = mode == OverlayMode.ChatPanel
    val isManualTargetSelection: Boolean get() = mode == OverlayMode.ManualTargetSelection
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
}

/** Current bubble (exactly one at a time). Scope §3 taxonomy. */
sealed class BuddyBubble {
    /** Yellow — live user transcript (voice). */
    data class Transcript(val text: String) : BuddyBubble()
    /** Teal — bounded action-in-progress. */
    data class Action(val text: String) : BuddyBubble()
    /** Green — clamped assistant response. */
    data class Response(val text: String) : BuddyBubble()
    /** Blue — pointer navigation label. */
    data class Navigation(val text: String) : BuddyBubble()
}

/** Panel content when [OverlayPanelState.mode] is [OverlayMode.ChatPanel]. */
data class PanelContent(
    val snapshot: PanelSnapshot? = null,
    val quickPrompts: List<String> = emptyList(),
    val greeting: String = "What would you like help with?",
    val draftInput: String = "",
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val streamingDelta: String = "",
    val isStreaming: Boolean = false,
    val isVoiceArmed: Boolean = false,
    val loadingVerb: String = "",
    val pendingConfirmation: PendingConfirmation? = null,
    val recentResponsePreview: String = "",
    val errorBanner: String? = null,
) {
    data class PendingConfirmation(
        val id: Long,
        val reason: String,
        val action: AssistantAction? = null,
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
