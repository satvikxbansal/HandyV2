@file:Suppress("DEPRECATION")

package com.handy.runtime.accessibility

import android.graphics.Rect
import com.handy.core.privacy.ScreenRedactor
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.runtime.di.AccessibilityServiceProvider
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the active accessibility root immediately before performing an
 * action so stale grounded targets fail closed after app/window changes.
 */
class LiveScreenGuard @Inject constructor(
    private val service: AccessibilityServiceProvider,
) {
    data class LiveScreen(
        val packageName: String?,
        val windowId: Int?,
        val rootBoundsHash: String?,
        val treeHash: String?,
    )

    suspend fun snapshot(): LiveScreen? = withContext(Dispatchers.Main.immediate) {
        val root = runCatching { service()?.rootInActiveWindow }.getOrNull() ?: return@withContext null
        try {
            val bounds = Rect().also { root.getBoundsInScreen(it) }
            val windowId = root.windowId
            val labels = root.treeHashLabels()
            LiveScreen(
                packageName = root.packageName?.toString()?.takeIf { it.isNotBlank() },
                windowId = windowId,
                rootBoundsHash = GroundingSnapshot.rootBoundsHash(
                    windowBounds = IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                    imeVisible = false,
                    imeBounds = IntRect.ZERO,
                    topmostWindowId = windowId,
                ),
                treeHash = GroundingSnapshot.labelTreeHash(labels),
            )
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun android.view.accessibility.AccessibilityNodeInfo.treeHashLabels(): List<String> {
        val out = ArrayList<String>(TREE_HASH_NODE_CAP)
        val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        queue.addLast(this)
        var visited = 0
        while (queue.isNotEmpty() && out.size < TREE_HASH_NODE_CAP && visited < HARD_VISIT_CAP) {
            val node = queue.removeFirst()
            try {
                visited++
                if (node.isTreeHashInteresting()) {
                    val role = node.className?.toString()?.substringAfterLast('.').orEmpty()
                        .ifBlank { if (node.isClickable) "Button" else "Node" }
                    val context = listOfNotNull(
                        node.className?.toString(),
                        node.viewIdResourceName,
                        node.contentDescription?.toString(),
                    ).joinToString(" ")
                    val redactedText = ScreenRedactor.redactText(
                        value = node.text?.toString(),
                        context = context,
                        isPassword = node.isPassword,
                    )
                    val redactedDescription = ScreenRedactor.redactText(
                        value = node.contentDescription?.toString(),
                        context = "$context ${redactedText.orEmpty()}",
                        isPassword = node.isPassword,
                    )
                    val label = redactedText?.takeIf { it.isNotBlank() }
                        ?: redactedDescription?.takeIf { it.isNotBlank() }
                        ?: node.viewIdResourceName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                        ?: role.takeIf { it.isNotBlank() }
                    if (!label.isNullOrBlank()) out += label
                }
                if (out.size < TREE_HASH_NODE_CAP) {
                    for (i in 0 until node.childCount) {
                        val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                        queue.addLast(child)
                    }
                }
            } finally {
                if (node !== this) runCatching { node.recycle() }
            }
        }
        while (queue.isNotEmpty()) {
            val pending = queue.removeFirst()
            if (pending !== this) runCatching { pending.recycle() }
        }
        return out
    }

    private fun android.view.accessibility.AccessibilityNodeInfo.isTreeHashInteresting(): Boolean {
        val bounds = Rect().also { getBoundsInScreen(it) }
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        if (!isVisibleToUser) return false
        if (!text.isNullOrBlank()) return true
        if (!contentDescription.isNullOrBlank()) return true
        return isClickable || isScrollable || isEditable
    }

    private companion object {
        const val TREE_HASH_NODE_CAP: Int = 50
        const val HARD_VISIT_CAP: Int = 1200
    }
}
