package com.handy.runtime.speech

import com.handy.core.model.HandySettings
import com.handy.core.model.TtsProvider
import com.handy.core.speech.TtsClient
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.EncryptedKeyStore
import com.handy.runtime.storage.KeyStore
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

class SwitchingTtsClient internal constructor(
    private val system: TtsClient,
    private val sarvam: TtsClient,
    private val settings: DataStoreSettings,
    private val keyStore: KeyStore,
    @ApplicationScope private val scope: CoroutineScope,
) : TtsClient {

    @Inject
    constructor(
        system: AndroidTtsClient,
        sarvam: SarvamTtsClient,
        settings: DataStoreSettings,
        keyStore: KeyStore,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        system = system as TtsClient,
        sarvam = sarvam as TtsClient,
        settings = settings,
        keyStore = keyStore,
        scope = scope,
    )

    private val currentSettings = AtomicReference(HandySettings())
    @Volatile private var active: TtsClient = system

    init {
        scope.launch {
            settings.flow.collectLatest { next ->
                currentSettings.set(next)
                active = selectClient(next)
            }
        }
    }

    override val isSpeaking: Boolean
        get() = system.isSpeaking || sarvam.isSpeaking

    override fun speak(text: String, utteranceId: String) {
        val selected = selectClient(currentSettings.get()).also { active = it }
        val inactive = if (selected === sarvam) system else sarvam
        runCatching { inactive.stop() }
            .onFailure { Timber.w(it, "SwitchingTtsClient: inactive TTS stop failed") }
        try {
            selected.speak(text, utteranceId)
        } catch (t: Throwable) {
            Timber.w(t, "SwitchingTtsClient: active TTS failed; falling back to system")
            active = system
            system.speak(text, utteranceId)
        }
    }

    override fun stop() {
        runCatching { sarvam.stop() }.onFailure { Timber.w(it, "SwitchingTtsClient: Sarvam stop failed") }
        runCatching { system.stop() }.onFailure { Timber.w(it, "SwitchingTtsClient: system stop failed") }
    }

    override fun release() {
        runCatching { sarvam.release() }.onFailure { Timber.w(it, "SwitchingTtsClient: Sarvam release failed") }
        runCatching { system.release() }.onFailure { Timber.w(it, "SwitchingTtsClient: system release failed") }
    }

    private fun selectClient(settings: HandySettings): TtsClient {
        val keyPresent = settings.ttsProvider == TtsProvider.SARVAM &&
            !keyStore.get(EncryptedKeyStore.KEY_SARVAM).isNullOrBlank()
        return if (keyPresent) sarvam else system
    }
}
