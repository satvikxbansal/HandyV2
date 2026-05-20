package com.handy.app.pointing

import com.google.common.truth.Truth.assertThat
import com.handy.app.accessibility.AccessibilityGestureActionPerformer
import com.handy.app.overlay.buildTapTargetForResolved
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditStore
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.accessibility.LiveScreenGuard
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolutionSource
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolvedPointTarget
import com.handy.runtime.di.AccessibilityServiceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MarkIdHandoffInvariantTest {

    @Test
    fun assistant_mark_id_survives_resolved_target_tap_target_and_audit() = runBlocking {
        val emitted = AssistantMarkupParser.parsePoint("Tap it [POINT:markId=m7]").semantic!!
        val resolved = ResolvedPointTarget(
            bounds = IntRect(10, 20, 110, 80),
            node = null,
            source = ResolutionSource.MARK_ID,
            confidence = 1f,
            candidateCount = 1,
            markId = "m7",
            role = "Button",
            text = "Continue",
            viewId = "continue",
        )
        val grounding = GroundingSnapshot(
            requestId = "request-7",
            source = TurnSource.OVERLAY_PANEL,
            toolContext = ToolContext(
                packageName = "com.example.checkout",
                appLabel = "Checkout",
            ),
            windowId = 42,
            rootBoundsHash = "root-hash-7",
        )

        val tapTarget = buildTapTargetForResolved(emitted, resolved, grounding)
        val auditStore = InMemoryAuditStore()
        val serviceProvider = AccessibilityServiceProvider { null }
        val performer = AccessibilityGestureActionPerformer(
            service = serviceProvider,
            resolver = SemanticPointerResolver(
                service = { null },
                applicationPackageName = "com.handy.android",
            ),
            liveScreenGuard = LiveScreenGuard(serviceProvider),
            auditStore = auditStore,
            foregroundPackageProvider = { "com.example.checkout" },
            clock = { 7L },
            requestIdProvider = { "request-7" },
            providerId = "test",
        )

        performer.tap(tapTarget)

        assertThat(emitted.markId).isEqualTo("m7")
        assertThat(resolved.markId).isEqualTo("m7")
        assertThat(tapTarget.markId).isEqualTo("m7")
        assertThat(auditStore.events).hasSize(1)
        assertThat(auditStore.events.single().semanticTarget).contains("markId=m7")
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
