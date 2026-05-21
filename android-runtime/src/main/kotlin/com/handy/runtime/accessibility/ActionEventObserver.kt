package com.handy.runtime.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.handy.core.screen.IntRect
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class ActionEventObserver @Inject constructor() {

    data class MatchTarget(
        val eventType: Int,
        val packageName: String?,
        val windowId: Int?,
        val viewIdResourceName: String?,
        val className: String?,
        val bounds: IntRect?,
        val expectedText: String? = null,
    ) {
        fun matches(event: EventSnapshot): Boolean {
            if (event.eventType != eventType) return false
            val textMatches = expectedText
                ?.takeIf { it.isNotBlank() }
                ?.let { expected ->
                    event.text.any { it.contains(expected, ignoreCase = true) }
                }
                ?: true
            packageName?.takeIf { it.isNotBlank() }?.let { expected ->
                if (!event.packageName.equals(expected, ignoreCase = true)) return false
            }
            windowId?.let { expected ->
                if (event.windowId != null && event.windowId != expected) return false
            }
            viewIdResourceName?.takeIf { it.isNotBlank() }?.let { expected ->
                event.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { actual ->
                    return actual.equalsViewId(expected) && textMatches
                }
            }
            bounds?.let { expected ->
                val actual = event.bounds
                if (actual != null) {
                    return actual.overlapRatio(expected) >= MIN_BOUNDS_OVERLAP && textMatches
                }
            }
            className?.takeIf { it.isNotBlank() }?.let { expected ->
                if (event.className.equals(expected, ignoreCase = true)) return textMatches
            }
            return viewIdResourceName == null && bounds == null && className == null && textMatches
        }
    }

    data class EventSnapshot(
        val eventType: Int,
        val packageName: String?,
        val windowId: Int?,
        val viewIdResourceName: String?,
        val className: String?,
        val bounds: IntRect?,
        val text: List<String> = emptyList(),
    )

    private val events = MutableSharedFlow<EventSnapshot>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
    )

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        record(event.toSnapshot())
    }

    fun record(event: EventSnapshot) {
        events.tryEmit(event)
    }

    suspend fun awaitTextChanged(
        target: MatchTarget,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String? =
        awaitMatchingEvent(target.copy(eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED), timeoutMs)
            ?.let { VERIFIED_TEXT_CHANGED }

    suspend fun awaitMatchingEvent(
        target: MatchTarget,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): EventSnapshot? =
        withTimeoutOrNull(timeoutMs) {
            events.first { target.matches(it) }
        }

    fun textChangedTargetFor(
        node: AccessibilityNodeInfo,
        expectedText: String,
    ): MatchTarget =
        MatchTarget(
            eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            packageName = node.packageName?.toString()?.takeIf { it.isNotBlank() },
            windowId = runCatching { node.windowId }.getOrNull(),
            viewIdResourceName = node.viewIdResourceName?.takeIf { it.isNotBlank() },
            className = node.className?.toString()?.takeIf { it.isNotBlank() },
            bounds = node.boundsInScreenOrNull(),
            expectedText = expectedText,
        )

    private fun AccessibilityEvent.toSnapshot(): EventSnapshot {
        val source = runCatching { source }.getOrNull()
        return try {
            EventSnapshot(
                eventType = eventType,
                packageName = packageName?.toString()?.takeIf { it.isNotBlank() },
                windowId = runCatching { source?.windowId ?: windowId }.getOrNull(),
                viewIdResourceName = source?.viewIdResourceName?.takeIf { it.isNotBlank() },
                className = source?.className?.toString()?.takeIf { it.isNotBlank() }
                    ?: className?.toString()?.takeIf { it.isNotBlank() },
                bounds = source?.boundsInScreenOrNull(),
                text = text.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) },
            )
        } finally {
            runCatching { source?.recycle() }
        }
    }

    private fun AccessibilityNodeInfo.boundsInScreenOrNull(): IntRect? {
        val rect = Rect().also { getBoundsInScreen(it) }
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return IntRect(rect.left, rect.top, rect.right, rect.bottom)
    }

    companion object {
        const val VERIFIED_TEXT_CHANGED: String = "text-changed"
        const val VERIFIED_VIEW_CLICKED: String = "view-clicked"
        const val VERIFIED_SCROLLED: String = "scrolled"
        const val DEFAULT_TIMEOUT_MS: Long = 1_500L
        private const val EVENT_BUFFER_CAPACITY: Int = 64
    }
}

private const val MIN_BOUNDS_OVERLAP: Float = 0.70f

private fun String?.equalsViewId(other: String): Boolean {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return false
    val target = other.trim()
    return value.equals(target, ignoreCase = true) ||
        value.shortViewId().equals(target.shortViewId(), ignoreCase = true)
}

private fun String.shortViewId(): String =
    substringAfterLast('/').substringAfterLast(':')

private fun IntRect.overlapRatio(other: IntRect): Float {
    val left = maxOf(left, other.left)
    val top = maxOf(top, other.top)
    val right = minOf(right, other.right)
    val bottom = minOf(bottom, other.bottom)
    val overlapW = (right - left).coerceAtLeast(0)
    val overlapH = (bottom - top).coerceAtLeast(0)
    val overlapArea = overlapW * overlapH
    val smallerArea = minOf(width * height, other.width * other.height).coerceAtLeast(1)
    return overlapArea.toFloat() / smallerArea.toFloat()
}
