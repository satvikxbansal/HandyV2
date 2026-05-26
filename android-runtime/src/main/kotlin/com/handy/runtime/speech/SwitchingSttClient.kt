package com.handy.runtime.speech

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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SwitchingSttClient internal constructor(
    private val android: SttClient,
    private val sarvam: SttClient,
    private val settings: DataStoreSettings,
    @ApplicationScope private val scope: CoroutineScope,
) : SttClient {

    @Inject
    constructor(
        android: AndroidSttClient,
        sarvam: SarvamSttClient,
        settings: DataStoreSettings,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        android = android as SttClient,
        sarvam = sarvam as SttClient,
        settings = settings,
        scope = scope,
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

    override fun listen(): Flow<SttEvent> = flow {
        val snapshot = withContext(Dispatchers.IO) { settings.current() }
        currentSettings.set(snapshot)
        val selected = selectClient(snapshot)
        val inactive = if (selected === sarvam) android else sarvam
        active = selected
        runCatching { inactive.release() }
            .onFailure { Timber.w(it, "SwitchingSttClient: inactive STT release failed") }
        emitAll(selected.listen())
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
}
