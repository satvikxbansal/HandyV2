package com.handy.runtime.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.UiNode

/**
 * Walks `rootInActiveWindow` and produces a [ScreenTextSnapshot].
 *
 * Secure-window handling follows OS-5: this reader does NOT treat
 * `windowInfo.isSecure` as the required source of truth. The
 * orchestrator gates on the capture pipeline's `CaptureResult` first;
 * this reader exists purely to produce the structured tree when the
 * capture pipeline says the screen is not secure.
 *
 * Depth and node count are capped so memory stays bounded on heavy UIs.
 */
class AccessibilityTreeReader(
    private val service: () -> AccessibilityService?,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun read(
        maxDepth: Int = 20,
        maxNodes: Int = 400,
    ): ScreenTextSnapshot? {
        val svc = service() ?: return null
        val root = svc.rootInActiveWindow ?: return null
        return try {
            val pkg = root.packageName?.toString() ?: "unknown"
            val title = windowTitle(svc, root)
            val counter = intArrayOf(0)
            val uiRoot = walk(root, depth = 0, maxDepth = maxDepth, counter = counter, maxNodes = maxNodes)
            ScreenTextSnapshot(
                packageName = pkg,
                windowTitle = title,
                timestampEpochMs = clock(),
                root = uiRoot ?: UiNode(role = "Unknown"),
            )
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int,
        counter: IntArray,
        maxNodes: Int,
    ): UiNode? {
        if (counter[0] >= maxNodes) return null
        counter[0]++

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val children = mutableListOf<UiNode>()
        if (depth < maxDepth) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    walk(child, depth + 1, maxDepth, counter, maxNodes)?.let { children += it }
                } finally {
                    runCatching { child.recycle() }
                }
                if (counter[0] >= maxNodes) break
            }
        }

        return UiNode(
            role = roleFor(node),
            text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            contentDescription = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            viewIdResourceName = node.viewIdResourceName?.takeIf { it.isNotBlank() },
            boundsInScreen = IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            children = children,
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            enabled = node.isEnabled,
        )
    }

    private fun windowTitle(service: AccessibilityService, root: AccessibilityNodeInfo): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            service.windows
                ?.firstOrNull { w -> w.id == root.windowId }
                ?.title
                ?.toString()
        }.getOrNull()
    }

    private fun roleFor(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString()?.substringAfterLast('.').orEmpty()
        return when {
            className.isNotBlank() -> className
            node.isClickable -> "Button"
            else -> "Node"
        }
    }
}
