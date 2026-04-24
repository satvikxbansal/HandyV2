package com.handy.core.audit

import kotlinx.coroutines.flow.Flow

/**
 * Persistent audit log (scope §4.3). Bounded size — oldest entries
 * prune on write. Consumed by [DiagnosticsActivity] for the audit tail.
 *
 * Implementations:
 *  - `:android-runtime/audit/FileAuditStore.kt` — JSON on disk.
 */
interface AuditStore {
    suspend fun append(event: AuditEvent)
    suspend fun recent(limit: Int = 20): List<AuditEvent>
    fun observe(limit: Int = 20): Flow<List<AuditEvent>>
}
