package com.handy.runtime.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.IntRect
import kotlin.math.max
import kotlin.math.min

/**
 * Resolves a [AssistantMarkupParser.SemanticPoint] to an
 * on-screen rectangle using the fallback chain from the build plan §8 /
 * guardrails → "Pointing (Strategy B only)":
 *
 *   1. exact text + role
 *   2. contentDescription
 *   3. viewId suffix
 *   4. fuzzy text (Levenshtein ≤ 2)
 *
 * Strategy A (pixel pointing) is **never** used at runtime.
 */
class SemanticPointerResolver(private val service: () -> AccessibilityService?) {

    data class Resolved(val bounds: IntRect, val node: AccessibilityNodeInfo)

    fun resolve(spec: AssistantMarkupParser.SemanticPoint): Resolved? {
        val root = service()?.rootInActiveWindow ?: return null
        try {
            exactTextAndRole(root, spec)?.let { return it.toResolved() }
            byContentDescription(root, spec)?.let { return it.toResolved() }
            byViewIdSuffix(root, spec)?.let { return it.toResolved() }
            fuzzyText(root, spec, maxDistance = 2)?.let { return it.toResolved() }
            return null
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun exactTextAndRole(
        root: AccessibilityNodeInfo,
        spec: AssistantMarkupParser.SemanticPoint,
    ): AccessibilityNodeInfo? {
        val text = spec.text ?: return null
        val matches = root.findAccessibilityNodeInfosByText(text)
        val roleHint = spec.role?.lowercase()
        return matches?.firstOrNull { node ->
            val nodeRole = node.className?.toString()?.substringAfterLast('.')?.lowercase().orEmpty()
            roleHint == null || nodeRole.contains(roleHint)
        } ?: matches?.firstOrNull()
    }

    private fun byContentDescription(
        root: AccessibilityNodeInfo,
        spec: AssistantMarkupParser.SemanticPoint,
    ): AccessibilityNodeInfo? {
        val desc = spec.contentDescription ?: return null
        return walk(root) { node ->
            node.contentDescription?.toString()?.equals(desc, ignoreCase = true) == true
        }
    }

    private fun byViewIdSuffix(
        root: AccessibilityNodeInfo,
        spec: AssistantMarkupParser.SemanticPoint,
    ): AccessibilityNodeInfo? {
        val hint = spec.viewId ?: return null
        return walk(root) { node ->
            node.viewIdResourceName?.endsWith("/$hint", ignoreCase = true) == true ||
                node.viewIdResourceName?.endsWith(":$hint", ignoreCase = true) == true
        }
    }

    private fun fuzzyText(
        root: AccessibilityNodeInfo,
        spec: AssistantMarkupParser.SemanticPoint,
        maxDistance: Int,
    ): AccessibilityNodeInfo? {
        val needle = spec.text?.lowercase() ?: return null
        var best: AccessibilityNodeInfo? = null
        var bestDistance = Int.MAX_VALUE
        walkAll(root) { node ->
            val candidate = node.text?.toString()?.lowercase()
            if (candidate != null) {
                val d = levenshtein(needle, candidate)
                if (d < bestDistance && d <= maxDistance) {
                    best = node
                    bestDistance = d
                }
            }
        }
        return best
    }

    // ---- helpers ----

    private fun AccessibilityNodeInfo.toResolved(): Resolved {
        val r = Rect().also { getBoundsInScreen(it) }
        return Resolved(
            bounds = IntRect(r.left, r.top, r.right, r.bottom),
            node = this,
        )
    }

    private fun walk(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val hit = walk(child, predicate)
            if (hit != null) return hit
        }
        return null
    }

    private fun walkAll(
        root: AccessibilityNodeInfo,
        visitor: (AccessibilityNodeInfo) -> Unit,
    ) {
        visitor(root)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            walkAll(child, visitor)
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost,
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    // unused `max` kept intentionally — left as a hint that the distance
    // threshold might graduate to a ratio-based measure in v2.
    private fun maxDistanceRoom(): Int = max(0, 0)
}
