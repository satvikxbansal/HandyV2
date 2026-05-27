package com.handy.core.screen

/**
 * Flattened accessibility-tree snapshot for the "zero-vision" path.
 *
 * The `:android-runtime` `AccessibilityTreeReader` produces this from
 * `AccessibilityNodeInfo` while respecting OS-5 (secure-window handling
 * via the capture pipeline's classification; `windowInfo.isSecure` is an
 * optional hint only).
 */
data class ScreenTextSnapshot(
    val packageName: String,
    val windowTitle: String? = null,
    val timestampEpochMs: Long,
    val root: UiNode,
) {
    /**
     * Cheap quality score used by the [ScreenInputRouter]:
     *  - 0 when no visible text / clickable controls.
     *  - rough node count when the tree looks usable.
     */
    fun qualityScore(): Int {
        var count = 0
        fun walk(n: UiNode) {
            if (!n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank()) count++
            n.children.forEach(::walk)
        }
        walk(root)
        return count
    }
}

data class UiNode(
    val markId: String? = null,
    val role: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val boundsInScreen: IntRect = IntRect.ZERO,
    val children: List<UiNode> = emptyList(),
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
)

/** Pure-Kotlin rectangle. Exists so `:core` doesn't drag in `android.graphics.Rect`. */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    companion object {
        val ZERO = IntRect(0, 0, 0, 0)
    }
}

fun IntRect.intersects(other: IntRect): Boolean =
    left < other.right && right > other.left &&
        top < other.bottom && bottom > other.top
