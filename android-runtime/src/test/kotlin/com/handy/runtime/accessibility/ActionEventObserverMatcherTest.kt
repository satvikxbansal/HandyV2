package com.handy.runtime.accessibility

import android.view.accessibility.AccessibilityEvent
import com.google.common.truth.Truth.assertThat
import com.handy.core.screen.IntRect
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ActionEventObserverMatcherTest {

    @Test fun `text change matcher accepts same view id`() {
        val target = amazonSearchTarget()

        assertThat(
            target.matches(
                event(
                    viewId = "com.amazon:id/other",
                    text = listOf("milk"),
                ),
            ),
        ).isFalse()
        assertThat(
            target.matches(
                event(
                    viewId = "rs_search_src_text",
                    text = listOf("milk"),
                ),
            ),
        ).isTrue()
    }

    @Test fun `text change observer waits for matching event`() = runTest {
        val observer = ActionEventObserver()
        val target = amazonSearchTarget()

        observer.record(
            event(
                viewId = "com.amazon:id/other",
                text = listOf("milk"),
            ),
        )
        val awaited = async(start = CoroutineStart.UNDISPATCHED) {
            observer.awaitTextChanged(target, timeoutMs = 1_500L)
        }
        observer.record(
            event(
                viewId = "rs_search_src_text",
                text = listOf("milk"),
            ),
        )

        assertThat(awaited.await()).isEqualTo(ActionEventObserver.VERIFIED_TEXT_CHANGED)
    }

    @Test fun `text change matcher falls back to bounds when no view id exists`() {
        val target = ActionEventObserver.MatchTarget(
            eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            packageName = "com.example",
            windowId = 3,
            viewIdResourceName = null,
            className = "android.widget.EditText",
            bounds = IntRect(10, 10, 210, 80),
            expectedText = "milk",
        )

        val event = ActionEventObserver.EventSnapshot(
            eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            packageName = "com.example",
            windowId = 3,
            viewIdResourceName = null,
            className = "android.widget.EditText",
            bounds = IntRect(12, 12, 208, 78),
            text = listOf("milk"),
        )

        assertThat(target.matches(event)).isTrue()
    }

    @Test fun `matcher rejects a different package`() {
        val target = ActionEventObserver.MatchTarget(
            eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            packageName = "com.example",
            windowId = 3,
            viewIdResourceName = "query",
            className = "android.widget.EditText",
            bounds = null,
            expectedText = "milk",
        )

        assertThat(
            target.matches(
                event(
                    packageName = "com.other",
                    windowId = 3,
                    viewId = "query",
                    text = listOf("milk"),
                ),
            ),
        ).isFalse()
    }

    private fun event(
        packageName: String = "com.amazon.mShop.android.shopping",
        windowId: Int = 7,
        viewId: String?,
        text: List<String>,
    ): ActionEventObserver.EventSnapshot =
        ActionEventObserver.EventSnapshot(
            eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            packageName = packageName,
            windowId = windowId,
            viewIdResourceName = viewId,
            className = "android.widget.EditText",
            bounds = IntRect(0, 0, 300, 80),
            text = text,
        )

    private fun amazonSearchTarget(): ActionEventObserver.MatchTarget =
        ActionEventObserver.MatchTarget(
            eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            packageName = "com.amazon.mShop.android.shopping",
            windowId = 7,
            viewIdResourceName = "com.amazon:id/rs_search_src_text",
            className = "android.widget.EditText",
            bounds = IntRect(0, 0, 300, 80),
            expectedText = "milk",
        )
}
