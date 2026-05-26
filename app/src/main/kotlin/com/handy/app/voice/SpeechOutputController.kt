package com.handy.app.voice

import com.handy.app.overlay.OverlayPresenter
import com.handy.core.speech.SpeechAudioState
import com.handy.core.speech.TtsClient
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class SpeechOutputController @Inject constructor(
    private val tts: TtsClient,
    private val presenter: OverlayPresenter,
    private val settings: DataStoreSettings,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(SpeechAudioState.IDLE)
    val state: StateFlow<SpeechAudioState> = _state.asStateFlow()

    @Volatile private var activeRequestId: String? = null
    @Volatile private var pendingRequestId: String? = null
    private var speakJob: Job? = null
    private var monitorJob: Job? = null
    private val consumedRequestIds = ArrayDeque<String>()
    private val consumedRequestIdSet = mutableSetOf<String>()

    @Synchronized
    fun speakForVoiceTurn(requestId: String, ttsText: String?) {
        val text = ttsText?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (requestId.isBlank()) return
        if (hasConsumed(requestId)) {
            Timber.d("SpeechOutputController: request already consumed id=%s", requestId)
            return
        }

        val previousRequestId = activeRequestId ?: pendingRequestId
        if (previousRequestId != null && previousRequestId != requestId) {
            stop("new_voice_turn")
        } else if (previousRequestId == requestId) {
            Timber.d("SpeechOutputController: request already pending or active id=%s", requestId)
            return
        }

        speakJob?.cancel()
        pendingRequestId = requestId
        speakJob = scope.launch {
            try {
                val enabled = runCatching { settings.current().speakVoiceRepliesAloud }
                    .onFailure {
                        Timber.w(
                            it,
                            "SpeechOutputController: settings read failed; defaulting speech on",
                        )
                    }
                    .getOrDefault(true)
                if (!enabled) {
                    Timber.d("SpeechOutputController: speech disabled id=%s", requestId)
                    return@launch
                }
                val started = synchronized(this@SpeechOutputController) {
                    if (hasConsumed(requestId) || pendingRequestId != requestId) {
                        false
                    } else {
                        rememberConsumed(requestId)
                        pendingRequestId = null
                        activeRequestId = requestId
                        publish(SpeechAudioState.PREPARING)
                        val failure = runCatching {
                            tts.speak(text = text, utteranceId = utteranceIdFor(requestId))
                        }.exceptionOrNull()
                        if (failure != null) {
                            Timber.w(failure, "SpeechOutputController: speak failed id=%s", requestId)
                            activeRequestId = null
                            publish(SpeechAudioState.ERROR)
                            publish(SpeechAudioState.IDLE)
                            false
                        } else {
                            true
                        }
                    }
                }
                if (!started) {
                    return@launch
                }

                monitorPlayback(requestId)
            } finally {
                synchronized(this@SpeechOutputController) {
                    if (pendingRequestId == requestId) {
                        pendingRequestId = null
                    }
                }
            }
        }
    }

    @Synchronized
    fun stop(reason: String) {
        Timber.d(
            "SpeechOutputController.stop: reason=%s active=%s pending=%s speaking=%s",
            reason,
            activeRequestId,
            pendingRequestId,
            tts.isSpeaking,
        )
        speakJob?.cancel()
        speakJob = null
        monitorJob?.cancel()
        monitorJob = null
        publish(SpeechAudioState.STOPPING)
        runCatching { tts.stop() }
            .onFailure { Timber.w(it, "SpeechOutputController: stop failed reason=%s", reason) }
        pendingRequestId = null
        activeRequestId = null
        publish(SpeechAudioState.IDLE)
    }

    private fun monitorPlayback(requestId: String) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val started = waitForPlaybackStart(requestId)
            if (!started) {
                if (activeRequestId == requestId) {
                    Timber.w("SpeechOutputController: TTS never reported speaking id=%s", requestId)
                    synchronized(this@SpeechOutputController) {
                        activeRequestId = null
                        publish(SpeechAudioState.ERROR)
                        publish(SpeechAudioState.IDLE)
                    }
                }
                return@launch
            }

            synchronized(this@SpeechOutputController) {
                if (activeRequestId != requestId || !tts.isSpeaking) {
                    return@launch
                }
                publish(SpeechAudioState.SPEAKING)
            }
            while (activeRequestId == requestId && tts.isSpeaking) {
                delay(POLL_MS)
            }
            synchronized(this@SpeechOutputController) {
                if (activeRequestId == requestId) {
                    activeRequestId = null
                    publish(SpeechAudioState.IDLE)
                }
            }
        }
    }

    private suspend fun waitForPlaybackStart(requestId: String): Boolean {
        var waitedMs = 0L
        while (activeRequestId == requestId && waitedMs < START_TIMEOUT_MS) {
            if (tts.isSpeaking) return true
            delay(POLL_MS)
            waitedMs += POLL_MS
        }
        return activeRequestId == requestId && tts.isSpeaking
    }

    private fun publish(next: SpeechAudioState) {
        _state.value = next
        presenter.onSpeechAudio(next)
    }

    @Synchronized
    private fun hasConsumed(requestId: String): Boolean =
        requestId in consumedRequestIdSet

    @Synchronized
    private fun rememberConsumed(requestId: String) {
        if (!consumedRequestIdSet.add(requestId)) return
        consumedRequestIds.addLast(requestId)
        while (consumedRequestIds.size > MAX_CONSUMED_REQUEST_IDS) {
            consumedRequestIdSet.remove(consumedRequestIds.removeFirst())
        }
    }

    private fun utteranceIdFor(requestId: String): String =
        "handy-voice-$requestId"

    private companion object {
        const val POLL_MS: Long = 50L
        const val START_TIMEOUT_MS: Long = 1_500L
        const val MAX_CONSUMED_REQUEST_IDS: Int = 64
    }
}
