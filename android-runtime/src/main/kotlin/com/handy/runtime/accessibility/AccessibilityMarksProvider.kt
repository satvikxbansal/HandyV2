package com.handy.runtime.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.handy.core.overlay.AccessibilityMark

/**
 * Compact, bounded list of visible actionable UI elements.
 *
 * Cursorbuddy recipe #2 (scope §15): ≤ 50 nodes, `isInteresting`
 * filter, JSON-compatible shape. Feeds:
 *  - the overlay presenter's cache-at-tap snapshot,
 *  - the system-prompt screen-text addendum (Phase 2 grounding),
 *  - the semantic pointer resolver as a "known viewId" hint source.
 *
 * Secure content handling:
 *  - Password fields (`isPassword = true`) have `text = null` — never
 *    expose raw field values.
 *  - Ambient sensitive patterns (OTPs, card numbers) are not special-
 *    cased here; the provider is a structural extractor only. The
 *    orchestrator's OS-5 gate rejects secure windows upstream.
 *
 * Pure extractor — no coroutines, no I/O, single-pass BFS. Must be
 * safe to call on the main thread because the widget service calls
 * it inline on the widget tap event.
 */
class AccessibilityMarksProvider(
    private val service: () -> AccessibilityService?,
    private val maxNodes: Int = DEFAULT_MAX_NODES,
) {

    /** Walk `rootInActiveWindow` and emit up to [maxNodes] compact marks. */
    fun collect(): List<AccessibilityMark> {
        val root = runCatching { service()?.rootInActiveWindow }.getOrNull()
            ?: return emptyList()
        return try {
            val out = ArrayList<AccessibilityMark>(maxNodes)
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.addLast(root)
            var visited = 0
            // BFS with a budget — avoids deep recursion on heavy UIs.
            // cursorbuddy's `isInteresting` filter decides membership;
            // the cap bounds the work.
            while (queue.isNotEmpty() && out.size < maxNodes && visited < HARD_VISIT_CAP) {
                val node = queue.removeFirst()
                visited++
                if (isInteresting(node)) {
                    buildMark(node)?.let(out::add)
                }
                if (out.size >= maxNodes) break
                for (i in 0 until node.childCount) {
                    val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                    queue.addLast(child)
                }
            }
            out
        } finally {
            runCatching { root.recycle() }
        }
    }

    /**
     * Filter from cursorbuddy `UiTreeSerializer.isInteresting`:
     *  - visible (bounds have non-zero area),
     *  - non-null non-blank text OR contentDescription OR
     *  - clickable / scrollable / editable.
     */
    private fun isInteresting(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        if (!node.isVisibleToUser) return false
        if (!node.text.isNullOrBlank()) return true
        if (!node.contentDescription.isNullOrBlank()) return true
        return node.isClickable || node.isScrollable || node.isEditable
    }

    private fun buildMark(node: AccessibilityNodeInfo): AccessibilityMark? {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        val role = node.className?.toString()?.substringAfterLast('.').orEmpty()
            .ifBlank { if (node.isClickable) "Button" else "Node" }
        val isPassword = node.isPassword
        val rawText = if (isPassword) null else node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return AccessibilityMark(
            text = rawText,
            contentDescription = node.contentDescription?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            viewIdSuffix = node.viewIdResourceName?.substringAfterLast('/')?.takeIf { it.isNotBlank() },
            role = role,
            bounds = intArrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom),
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            editable = node.isEditable,
            isPassword = isPassword,
            isChecked = if (node.isCheckable) node.isChecked else null,
        )
    }

    private companion object {
        /** Cursorbuddy's default. Enough for most screens; bounded for prompts. */
        const val DEFAULT_MAX_NODES = 50

        /** Upper bound on BFS visits regardless of filter hits — prevents pathological trees. */
        const val HARD_VISIT_CAP = 1200
    }
}
