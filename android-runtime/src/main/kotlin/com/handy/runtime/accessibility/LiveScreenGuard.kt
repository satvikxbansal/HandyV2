@file:Suppress("DEPRECATION")

package com.handy.runtime.accessibility

import android.graphics.Rect
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
    )

    suspend fun snapshot(): LiveScreen? = withContext(Dispatchers.Main.immediate) {
        val root = runCatching { service()?.rootInActiveWindow }.getOrNull() ?: return@withContext null
        try {
            val bounds = Rect().also { root.getBoundsInScreen(it) }
            val windowId = root.windowId
            LiveScreen(
                packageName = root.packageName?.toString()?.takeIf { it.isNotBlank() },
                windowId = windowId,
                rootBoundsHash = GroundingSnapshot.rootBoundsHash(
                    windowBounds = IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                    imeVisible = false,
                    imeBounds = IntRect.ZERO,
                    topmostWindowId = windowId,
                ),
            )
        } finally {
            runCatching { root.recycle() }
        }
    }
}
