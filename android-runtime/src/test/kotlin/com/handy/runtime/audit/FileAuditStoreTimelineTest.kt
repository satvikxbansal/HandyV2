package com.handy.runtime.audit

import com.google.common.truth.Truth.assertThat
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.Stage
import com.handy.core.audit.TimelineEvent
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileAuditStoreTimelineTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    @Test
    fun `clear all wipes action and timeline rows within 100ms`() = runTest {
        val store = FileAuditStore(
            context = RuntimeEnvironment.getApplication(),
            json = json,
        )
        store.clearAll()

        store.append(auditEvent())
        repeat(40) { index ->
            store.append(
                TimelineEvent(
                    turnId = "turn-$index",
                    timestamp = 1_700_000_000_000L + index,
                    stage = Stage.TOOL_RESULT,
                    durationMs = index.toLong(),
                    provider = "test",
                    toolName = "web_search",
                ),
            )
        }

        val elapsedMs = measureTimeMillis { store.clearAll() }

        assertThat(elapsedMs).isLessThan(100L)
        assertThat(store.recent(limit = 10)).isEmpty()
        assertThat(store.timelineRecent(limit = 10)).isEmpty()
    }

    private fun auditEvent(): AuditEvent =
        AuditEvent(
            timestampEpochMs = 1_700_000_000_000L,
            requestId = "turn-action",
            provider = "test",
            action = AuditAction.Tap,
            targetApp = "Example",
            semanticTarget = "markId=m1",
            confirmationRequired = false,
            userConfirmed = false,
            result = AuditResult.Dispatched("test"),
        )
}
