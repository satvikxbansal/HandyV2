package com.handy.runtime.audit

import android.content.Context
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditStore
import com.handy.core.audit.TimelineEvent
import com.handy.core.audit.redacted
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import timber.log.Timber

/**
 * JSON-file audit store. Rolling buffer — oldest entries prune on
 * write once the file exceeds [maxEntries].
 *
 * Atomic writes via temp-file + rename, matching the `JsonHistoryStore`
 * pattern. The in-memory tail is a `StateFlow<List<AuditEvent>>` so
 * [DiagnosticsActivity] can observe without re-reading the file.
 */
class FileAuditStore(
    context: Context,
    private val json: Json,
    private val maxEntries: Int = 200,
    private val maxTimelineEntries: Int = 1_000,
    private val retentionDays: Long = 30,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AuditStore {

    private val dir: File = File(context.filesDir, "audit").apply { mkdirs() }
    private val file: File = File(dir, "events.json")
    private val tempFile: File = File(dir, "events.json.tmp")
    private val timelineFile: File = File(dir, "timeline.json")
    private val timelineTempFile: File = File(dir, "timeline.json.tmp")
    private val mutex = Mutex()
    private val retentionMs: Long = retentionDays.coerceAtLeast(1L) * 24L * 60L * 60L * 1_000L

    private val tail = MutableStateFlow<List<AuditEvent>>(loadInitial())
    private val timelineTail = MutableStateFlow<List<TimelineEvent>>(loadInitialTimeline())

    val state: StateFlow<List<AuditEvent>> = tail.asStateFlow()
    val timelineState: StateFlow<List<TimelineEvent>> = timelineTail.asStateFlow()

    override suspend fun append(event: AuditEvent): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = loadBlocking().toMutableList()
            current.add(event)
            val pruned = current.pruneAuditEvents()
            writeAtomically(pruned)
            tail.value = pruned
        }
    }

    override suspend fun append(event: TimelineEvent): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = loadTimelineBlocking().toMutableList()
            current.add(event.redacted())
            val pruned = current.pruneTimelineEvents()
            writeTimelineAtomically(pruned)
            timelineTail.value = pruned
        }
    }

    override suspend fun recent(limit: Int): List<AuditEvent> = withContext(Dispatchers.IO) {
        mutex.withLock { loadBlocking().pruneAuditEvents().takeLast(limit) }
    }

    override suspend fun timelineRecent(limit: Int): List<TimelineEvent> = withContext(Dispatchers.IO) {
        mutex.withLock { loadTimelineBlocking().pruneTimelineEvents().takeLast(limit) }
    }

    override fun observe(limit: Int): Flow<List<AuditEvent>> =
        tail.asStateFlow().map { it.takeLast(limit) }

    override fun observeTimeline(limit: Int): Flow<List<TimelineEvent>> =
        timelineTail.asStateFlow().map { it.takeLast(limit) }

    override suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                file.delete()
                tempFile.delete()
                timelineFile.delete()
                timelineTempFile.delete()
                tail.value = emptyList()
                timelineTail.value = emptyList()
            }.onFailure { Timber.w(it, "FileAuditStore: clear failed") }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeAtomically(events: List<AuditEvent>) {
        runCatching {
            FileOutputStream(tempFile).use { os ->
                json.encodeToStream(events, os)
                os.flush()
                os.fd.sync()
            }
            if (!tempFile.renameTo(file)) {
                // Cross-device fallback: copy + delete.
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            FileInputStream(file).use { it.fd.sync() }
        }.onFailure { Timber.w(it, "FileAuditStore: write failed") }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeTimelineAtomically(events: List<TimelineEvent>) {
        runCatching {
            FileOutputStream(timelineTempFile).use { os ->
                json.encodeToStream(events, os)
                os.flush()
                os.fd.sync()
            }
            if (!timelineTempFile.renameTo(timelineFile)) {
                timelineTempFile.copyTo(timelineFile, overwrite = true)
                timelineTempFile.delete()
            }
            FileInputStream(timelineFile).use { it.fd.sync() }
        }.onFailure { Timber.w(it, "FileAuditStore: timeline write failed") }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadBlocking(): List<AuditEvent> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return runCatching {
            file.inputStream().use { input -> json.decodeFromStream<List<AuditEvent>>(input) }
        }.getOrElse {
            Timber.w(it, "FileAuditStore: read failed")
            emptyList()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadTimelineBlocking(): List<TimelineEvent> {
        if (!timelineFile.exists() || timelineFile.length() == 0L) return emptyList()
        return runCatching {
            timelineFile.inputStream().use { input ->
                json.decodeFromStream<List<TimelineEvent>>(input).map { it.redacted() }
            }
        }.getOrElse {
            Timber.w(it, "FileAuditStore: timeline read failed")
            emptyList()
        }
    }

    private fun List<AuditEvent>.pruneAuditEvents(): List<AuditEvent> {
        val cutoff = clock() - retentionMs
        return filter { it.timestampEpochMs >= cutoff }
            .takeLast(maxEntries)
    }

    private fun List<TimelineEvent>.pruneTimelineEvents(): List<TimelineEvent> {
        val cutoff = clock() - retentionMs
        return filter { it.timestamp >= cutoff }
            .map { it.redacted() }
            .takeLast(maxTimelineEntries)
    }

    private fun loadInitial(): List<AuditEvent> = runCatching { loadBlocking().pruneAuditEvents() }
        .getOrElse { emptyList() }

    private fun loadInitialTimeline(): List<TimelineEvent> =
        runCatching { loadTimelineBlocking().pruneTimelineEvents() }
            .getOrElse { emptyList() }
}
