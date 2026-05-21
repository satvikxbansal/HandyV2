package com.handy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.google.common.truth.Truth.assertThat
import com.handy.core.action.PerformResult
import com.handy.core.action.TapTarget
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditStore
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.IntRect
import com.handy.runtime.accessibility.ActionEventObserver
import com.handy.runtime.accessibility.LiveScreenGuard
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolutionSource
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolvedPointTarget
import com.handy.runtime.di.AccessibilityServiceProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityGestureActionPerformerTypeTextTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typeText uses ACTION_SET_TEXT and audits text changed verification`() = runTest {
        val observer = ActionEventObserver()
        val auditStore = InMemoryAuditStore()
        val service = mockk<AccessibilityService>(relaxed = true)
        every { service.rootInActiveWindow } returns null

        val setTextAction = mockk<AccessibilityNodeInfo.AccessibilityAction>()
        every { setTextAction.id } returns AccessibilityNodeInfo.ACTION_SET_TEXT

        val node = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { node.isEditable } returns true
        every { node.isPassword } returns false
        every { node.actionList } returns listOf(setTextAction)
        every { node.packageName } returns "com.amazon.mShop.android.shopping"
        every { node.windowId } returns 7
        every { node.viewIdResourceName } returns "com.amazon:id/rs_search_src_text"
        every { node.className } returns "android.widget.EditText"
        every { node.contentDescription } returns "Search Amazon"
        every { node.text } returns ""
        every { node.getBoundsInScreen(any()) } answers {
            firstArg<Rect>().set(0, 0, 300, 80)
        }
        every { node.recycle() } just runs
        every { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, any()) } answers {
            observer.record(
                ActionEventObserver.EventSnapshot(
                    eventType = android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                    packageName = "com.amazon.mShop.android.shopping",
                    windowId = 7,
                    viewIdResourceName = "com.amazon:id/rs_search_src_text",
                    className = "android.widget.EditText",
                    bounds = IntRect(0, 0, 300, 80),
                    text = listOf("milk"),
                ),
            )
            true
        }

        val resolver = mockk<SemanticPointerResolver>()
        every {
            resolver.resolve(
                any<AssistantMarkupParser.SemanticPoint>(),
                any<List<AccessibilityMark>>(),
                null,
                null,
            )
        } returns ResolvedPointTarget(
            bounds = IntRect(0, 0, 300, 80),
            node = node,
            source = ResolutionSource.MARK_ID,
            confidence = 1f,
            candidateCount = 1,
            markId = "m3",
            role = "EditText",
            text = "Search Amazon",
            viewId = "rs_search_src_text",
        )

        val provider = AccessibilityServiceProvider { service }
        val performer = AccessibilityGestureActionPerformer(
            service = provider,
            resolver = resolver,
            liveScreenGuard = LiveScreenGuard(provider),
            actionEventObserver = observer,
            auditStore = auditStore,
            foregroundPackageProvider = { "com.amazon.mShop.android.shopping" },
            clock = { 1_000L },
            requestIdProvider = { "request-type" },
            providerId = "test",
        )
        val target = TapTarget.AtNode(
            markId = "m3",
            role = "textfield",
            text = "Search Amazon",
            viewId = "rs_search_src_text",
            desc = null,
            expectedPackage = null,
            expectedWindowId = null,
            snapshotHash = null,
            resolverConfidence = 1f,
        )

        val result = performer.typeText(target, "milk")

        assertThat(result).isEqualTo(PerformResult.Ok)
        assertThat(auditStore.events.single().verifiedBy).isEqualTo("text-changed")
    }

    private class InMemoryAuditStore : AuditStore {
        val events = mutableListOf<AuditEvent>()
        private val state = MutableStateFlow<List<AuditEvent>>(emptyList())

        override suspend fun append(event: AuditEvent) {
            events += event
            state.value = events.toList()
        }

        override suspend fun recent(limit: Int): List<AuditEvent> =
            events.takeLast(limit)

        override fun observe(limit: Int): Flow<List<AuditEvent>> =
            state.asStateFlow()
    }
}
