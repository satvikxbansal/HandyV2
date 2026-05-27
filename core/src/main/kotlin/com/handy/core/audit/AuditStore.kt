package com.handy.core.audit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Persistent audit log (scope §4.3). Bounded size — oldest entries
 * prune on write. Consumed by [DiagnosticsActivity] for the audit tail.
 *
 * Implementations:
 *  - `:android-runtime/audit/FileAuditStore.kt` — JSON on disk.
 */
interface AuditStore {
    suspend fun append(event: AuditEvent)
    suspend fun append(event: TimelineEvent) = Unit
    suspend fun recent(limit: Int = 20): List<AuditEvent>
    suspend fun timelineRecent(limit: Int = 100): List<TimelineEvent> = emptyList()
    fun observe(limit: Int = 20): Flow<List<AuditEvent>>
    fun observeTimeline(limit: Int = 100): Flow<List<TimelineEvent>> = flowOf(emptyList())
    suspend fun clearAll() = Unit
}
