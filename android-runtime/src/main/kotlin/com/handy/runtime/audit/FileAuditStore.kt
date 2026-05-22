package com.handy.runtime.audit

import android.content.Context
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditStore
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
) : AuditStore {

    private val dir: File = File(context.filesDir, "audit").apply { mkdirs() }
    private val file: File = File(dir, "events.json")
    private val tempFile: File = File(dir, "events.json.tmp")
    private val mutex = Mutex()

    private val tail = MutableStateFlow<List<AuditEvent>>(loadInitial())

    val state: StateFlow<List<AuditEvent>> = tail.asStateFlow()

    override suspend fun append(event: AuditEvent): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = loadBlocking().toMutableList()
            current.add(event)
            // Prune from the head so we keep the most recent [maxEntries].
            val pruned = if (current.size > maxEntries) {
                current.subList(current.size - maxEntries, current.size).toList()
            } else {
                current.toList()
            }
            writeAtomically(pruned)
            tail.value = pruned.takeLast(20)
        }
    }

    override suspend fun recent(limit: Int): List<AuditEvent> = withContext(Dispatchers.IO) {
        mutex.withLock { loadBlocking().takeLast(limit) }
    }

    override fun observe(limit: Int): Flow<List<AuditEvent>> =
        tail.asStateFlow().map { it.takeLast(limit) }

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
    private fun loadBlocking(): List<AuditEvent> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return runCatching {
            file.inputStream().use { input -> json.decodeFromStream<List<AuditEvent>>(input) }
        }.getOrElse {
            Timber.w(it, "FileAuditStore: read failed")
            emptyList()
        }
    }

    private fun loadInitial(): List<AuditEvent> = runCatching { loadBlocking().takeLast(20) }
        .getOrElse { emptyList() }
}
