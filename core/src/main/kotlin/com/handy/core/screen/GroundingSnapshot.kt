package com.handy.core.screen

import com.handy.core.accessibility.AccessibilityConnectionState
import com.handy.core.capture.CaptureMode
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.tool.ToolContext

/**
 * Per-turn grounding payload shared by chat, overlay, voice, Quick
 * Settings, Assist, and future action-guard code.
 *
 * This deliberately stays pure Kotlin: Android runtime code translates
 * platform values into these small value objects before crossing into
 * `:core`.
 */
data class GroundingSnapshot(
    val requestId: String,
    val source: TurnSource,
    val toolContext: ToolContext,
    val panelSnapshot: PanelSnapshot? = null,
    val screenText: ScreenTextSnapshot? = null,
    val capture: CaptureResult? = null,
    val captureMode: CaptureMode = CaptureMode.NONE,
    val accessibilityState: AccessibilityConnectionState = AccessibilityConnectionState.NeverConnected,
    val failureReason: ContextFailureReason? = null,
    val windowId: Int? = null,
    val displayId: Int? = null,
    val orientation: String = ORIENTATION_UNKNOWN,
    val windowBounds: IntRect = IntRect.ZERO,
    val safeInsets: InsetsSnapshot = InsetsSnapshot.ZERO,
    val imeVisible: Boolean = false,
    val imeBounds: IntRect = IntRect.ZERO,
    val densityDpi: Int? = null,
    val locale: String? = null,
    val uiMode: String = UI_MODE_UNKNOWN,
    val rootBoundsHash: String? = null,
    val treeHash: String? = null,
    val capturedAtMs: Long = 0L,
    val privacyFlags: PrivacyFlags = PrivacyFlags(),
) {
    val failurePrompt: String?
        get() = failureReason?.promptText

    companion object {
        const val ORIENTATION_UNKNOWN: String = "unknown"
        const val UI_MODE_UNKNOWN: String = "unknown"

        fun rootBoundsHash(
            windowBounds: IntRect,
            imeVisible: Boolean,
            imeBounds: IntRect,
            topmostWindowId: Int?,
        ): String = stableHash(
            listOf(
                windowBounds.left,
                windowBounds.top,
                windowBounds.right,
                windowBounds.bottom,
                imeVisible,
                imeBounds.left,
                imeBounds.top,
                imeBounds.right,
                imeBounds.bottom,
                topmostWindowId ?: "none",
            ).joinToString(separator = "|"),
        )

        fun treeHash(
            marks: List<AccessibilityMark>,
            screenText: ScreenTextSnapshot?,
        ): String? {
            val labels = if (marks.isNotEmpty()) {
                marks.map { mark ->
                    mark.text
                        ?: mark.contentDescription
                        ?: mark.viewIdSuffix
                        ?: mark.role
                }
            } else {
                screenText?.root?.topLabels().orEmpty()
            }
            return labelTreeHash(labels)
        }

        fun labelTreeHash(labels: List<String>): String? {
            val normalized = labels.mapNotNull { label ->
                label.trim().takeIf { it.isNotEmpty() }
            }
            if (normalized.isEmpty()) return null
            return stableHash(
                (listOf(normalized.size.toString()) + normalized.take(TREE_HASH_LABEL_LIMIT))
                    .joinToString(separator = "|"),
            )
        }

        private fun UiNode.topLabels(): List<String> {
            val out = ArrayList<String>(TREE_HASH_LABEL_LIMIT)
            fun walk(node: UiNode) {
                if (out.size >= TREE_HASH_LABEL_LIMIT) return
                val label = node.text
                    ?: node.contentDescription
                    ?: node.viewIdResourceName?.substringAfterLast('/')
                    ?: node.role
                if (label.isNotBlank()) out += label
                node.children.forEach(::walk)
            }
            walk(this)
            return out
        }

        private fun stableHash(value: String): String =
            value.hashCode().toUInt().toString(radix = 16).padStart(8, '0')

        private const val TREE_HASH_LABEL_LIMIT = 10
    }
}

data class InsetsSnapshot(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val unreliable: Boolean = false,
) {
    companion object {
        val ZERO = InsetsSnapshot()
    }
}

data class PrivacyFlags(
    val safeInsetsUnreliable: Boolean = false,
    val secureWindow: Boolean = false,
    val captureNotPermitted: Boolean = false,
    val captureUnsupported: Boolean = false,
    val captureFailed: Boolean = false,
    val containsPasswordFields: Boolean = false,
)
