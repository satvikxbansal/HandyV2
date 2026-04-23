package com.handy.runtime.storage

import android.content.Context
import com.handy.core.history.ChatHistoryStore
import com.handy.core.model.ChatMessage
import com.handy.core.model.ConversationTurn
import com.handy.core.model.MessageRole
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Per-tool chat history, stored as one JSON file under
 * `<filesDir>/handy/history/<sanitised-tool-key>.json`.
 *
 * Writes are atomic: we write to `<path>.tmp` then `renameTo(<path>)`.
 * Reads / writes happen on `Dispatchers.IO`. A per-tool `Mutex` guards
 * against read-modify-write races across the orchestrator and any
 * Settings → Clear operations.
 *
 * Respects the guardrail "Persistence" rule: JSON files, not Room.
 */
class JsonHistoryStore(
    context: Context,
    private val json: Json = DEFAULT_JSON,
    private val maxTurnsPerTool: Int = ChatHistoryStore.MAX_TURNS_PER_TOOL,
) : ChatHistoryStore {

    private val rootDir: File = File(context.filesDir, "handy/history").apply { mkdirs() }
    private val states = HashMap<String, MutableStateFlow<List<ChatMessage>>>()
    private val mutexes = HashMap<String, Mutex>()
    private val registryLock = Mutex()

    override fun observe(toolName: String): Flow<List<ChatMessage>> {
        val state = stateFor(toolName)
        return state.asStateFlow()
    }

    override suspend fun load(toolName: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        stateFor(toolName).value.ifEmpty { readFromDisk(toolName) }
    }

    override suspend fun appendTurn(toolName: String, turn: ConversationTurn) {
        mutexFor(toolName).withLock {
            withContext(Dispatchers.IO) {
                val existing = readFromDisk(toolName).toMutableList()
                existing += ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = MessageRole.USER,
                    content = turn.userMessage,
                    timestampEpochMs = turn.timestampEpochMs,
                    toolName = toolName,
                )
                existing += ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    content = turn.assistantMessage,
                    timestampEpochMs = turn.timestampEpochMs,
                    toolName = toolName,
                )
                val capped = capToMax(existing)
                writeAtomically(toolName, capped)
                stateFor(toolName).value = capped
            }
        }
    }

    override suspend fun replace(toolName: String, messages: List<ChatMessage>) {
        mutexFor(toolName).withLock {
            withContext(Dispatchers.IO) {
                val capped = capToMax(messages)
                writeAtomically(toolName, capped)
                stateFor(toolName).value = capped
            }
        }
    }

    override suspend fun clear(toolName: String) {
        mutexFor(toolName).withLock {
            withContext(Dispatchers.IO) {
                fileFor(toolName).delete()
                stateFor(toolName).value = emptyList()
            }
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            rootDir.listFiles()?.forEach { it.delete() }
            states.values.forEach { it.value = emptyList() }
        }
    }

    override suspend fun listTools(): List<String> = withContext(Dispatchers.IO) {
        rootDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(SUFFIX) }
            .map { it.nameWithoutExtension }
            .map(::desanitise)
    }

    // -------------------- internals --------------------

    private fun stateFor(toolName: String): MutableStateFlow<List<ChatMessage>> {
        states[toolName]?.let { return it }
        val initial = runCatching { readFromDisk(toolName) }.getOrDefault(emptyList())
        val created = MutableStateFlow(initial)
        states[toolName] = created
        return created
    }

    private fun mutexFor(toolName: String): Mutex {
        mutexes[toolName]?.let { return it }
        val created = Mutex()
        mutexes[toolName] = created
        return created
    }

    private fun readFromDisk(toolName: String): List<ChatMessage> {
        val f = fileFor(toolName)
        if (!f.exists()) return emptyList()
        return runCatching {
            val bytes = f.readBytes()
            if (bytes.isEmpty()) return@runCatching emptyList<ChatMessage>()
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(ChatMessage.serializer()),
                String(bytes, Charsets.UTF_8),
            )
        }.getOrDefault(emptyList())
    }

    private fun writeAtomically(toolName: String, messages: List<ChatMessage>) {
        val target = fileFor(toolName)
        val temp = File(target.parentFile, "${target.name}.tmp")
        val body = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ChatMessage.serializer()),
            messages,
        )
        temp.writeText(body, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            // Fallback for rare filesystems where rename fails across
            // same-directory atomic — last resort.
            target.writeText(body, Charsets.UTF_8)
            temp.delete()
        }
    }

    private fun fileFor(toolName: String): File =
        File(rootDir, sanitise(toolName) + SUFFIX)

    private fun capToMax(messages: List<ChatMessage>): List<ChatMessage> {
        // Cap is expressed in ConversationTurns; a turn = 2 messages
        // (user + assistant). Preserve the most recent N turns.
        val maxMessages = maxTurnsPerTool * 2
        if (messages.size <= maxMessages) return messages
        return messages.takeLast(maxMessages)
    }

    companion object {
        const val SUFFIX = ".json"

        /** URL-safe sanitisation so keys like `com.google.android.gm::GitHub` become safe file names. */
        fun sanitise(toolName: String): String = toolName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(180)

        /** Best-effort reverse; collisions are tolerated because this is display-only. */
        fun desanitise(fileStem: String): String = fileStem

        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}
