package com.handy.runtime.speech

import com.handy.core.audit.AuditStore
import com.handy.core.audit.Stage
import com.handy.core.audit.TimelineEvent
import com.handy.core.model.HandySettings
import com.handy.core.model.SttProvider
import com.handy.core.speech.SttClient
import com.handy.core.speech.SttEvent
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SwitchingSttClient internal constructor(
    private val android: SttClient,
    private val sarvam: SttClient,
    private val settings: DataStoreSettings,
    @ApplicationScope private val scope: CoroutineScope,
    private val auditStore: AuditStore? = null,
) : SttClient {

    @Inject
    constructor(
        android: AndroidSttClient,
        sarvam: SarvamSttClient,
        settings: DataStoreSettings,
        auditStore: AuditStore,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        android = android as SttClient,
        sarvam = sarvam as SttClient,
        settings = settings,
        scope = scope,
        auditStore = auditStore,
    )

    private val currentSettings = AtomicReference(HandySettings())
    @Volatile private var active: SttClient? = null

    init {
        scope.launch {
            settings.flow.collectLatest { currentSettings.set(it) }
        }
    }

    override val isOnDeviceAvailable: Boolean
        get() = selectClient(currentSettings.get()).isOnDeviceAvailable

    override val finalResultTimeoutMs: Long
        get() = (active ?: selectClient(currentSettings.get())).finalResultTimeoutMs

    override fun listen(): Flow<SttEvent> =
        listen("stt-${System.currentTimeMillis()}")

    override fun listen(timelineTurnId: String): Flow<SttEvent> = flow {
        val snapshot = withContext(Dispatchers.IO) { settings.current() }
        currentSettings.set(snapshot)
        val selected = selectClient(snapshot)
        val inactive = if (selected === sarvam) android else sarvam
        val provider = snapshot.sttProvider.name.lowercase()
        val startedAt = System.currentTimeMillis()
        var terminalEventSeen = false
        active = selected
        appendTimeline(
            TimelineEvent(
                turnId = timelineTurnId,
                timestamp = startedAt,
                stage = Stage.STT_START,
                provider = provider,
            ),
        )
        runCatching { inactive.release() }
            .onFailure { Timber.w(it, "SwitchingSttClient: inactive STT release failed") }
        try {
            selected.listen().collect { event ->
                when (event) {
                    is SttEvent.Final -> {
                        terminalEventSeen = true
                        appendTimeline(
                            TimelineEvent(
                                turnId = timelineTurnId,
                                timestamp = System.currentTimeMillis(),
                                stage = Stage.STT_FINAL,
                                durationMs = (System.currentTimeMillis() - startedAt).takeIf { it >= 0L },
                                provider = provider,
                            ),
                        )
                    }
                    is SttEvent.Error -> {
                        terminalEventSeen = true
                        appendTimeline(
                            TimelineEvent(
                                turnId = timelineTurnId,
                                timestamp = System.currentTimeMillis(),
                                stage = Stage.ERROR,
                                durationMs = (System.currentTimeMillis() - startedAt).takeIf { it >= 0L },
                                provider = provider,
                                error = event.reason.toReasonCode(),
                            ),
                        )
                    }
                    is SttEvent.BeginningOfSpeech,
                    is SttEvent.EndOfSpeech,
                    is SttEvent.Notice,
                    is SttEvent.Partial -> Unit
                }
                emit(event)
            }
        } finally {
            if (!terminalEventSeen) {
                appendTimeline(
                    TimelineEvent(
                        turnId = timelineTurnId,
                        timestamp = System.currentTimeMillis(),
                        stage = Stage.ERROR,
                        durationMs = (System.currentTimeMillis() - startedAt).takeIf { it >= 0L },
                        provider = provider,
                        error = "stt-ended",
                    ),
                )
            }
        }
    }

    override fun stopListening() {
        active?.stopListening()
    }

    override fun release() {
        runCatching { sarvam.release() }.onFailure { Timber.w(it, "SwitchingSttClient: Sarvam release failed") }
        runCatching { android.release() }.onFailure { Timber.w(it, "SwitchingSttClient: Android release failed") }
        active = null
    }

    private fun selectClient(settings: HandySettings): SttClient =
        if (settings.sttProvider == SttProvider.SARVAM_SAARIKA) sarvam else android

    private suspend fun appendTimeline(event: TimelineEvent) {
        auditStore?.let { store ->
            runCatching { store.append(event) }
                .onFailure { Timber.w(it, "SwitchingSttClient timeline append failed") }
        }
    }
}

private fun String.toReasonCode(): String =
    trim()
        .substringBefore('.')
        .replace(Regex("""[^A-Za-z0-9_.-]+"""), "_")
        .trim('_')
        .take(80)
        .ifBlank { "stt-error" }
