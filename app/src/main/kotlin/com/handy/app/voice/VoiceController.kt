package com.handy.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.handy.core.speech.SttClient
import com.handy.core.speech.SttEvent
import com.handy.runtime.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Application-scoped glue between the floating-widget long-press
 * gesture, the [SttClient] adapter, and the chat pipeline.
 *
 * Lifecycle (per press-and-hold):
 *   start()            — on 400 ms long-press fire. Begins recognising.
 *   latestPartial      — streams partial transcripts for UI feedback.
 *   stopAndAwaitFinal() — on release. Sends a graceful stop to the
 *                        recognizer, waits briefly for the Final
 *                        transcript, returns it (or the best partial).
 *   cancel()           — on drag-started / error. Tears down without
 *                        a transcript.
 *
 * All STT calls are confined to the main thread by [SttClient]; this
 * class only owns the collector job.
 */
@Singleton
class VoiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sttClient: SttClient,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    enum class State { IDLE, LISTENING }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _latestPartial = MutableStateFlow("")
    val latestPartial: StateFlow<String> = _latestPartial.asStateFlow()

    private var collectJob: Job? = null

    @Volatile private var finalTranscript: String = ""

    val isOnDeviceAvailable: Boolean
        get() = sttClient.isOnDeviceAvailable

    /**
     * Begin a listening session. No-op if one is already active or the
     * `RECORD_AUDIO` runtime permission is missing.
     *
     * Returns true if the session actually started.
     */
    fun start(): Boolean {
        if (_state.value != State.IDLE) return false
        if (!hasMicPermission()) {
            Timber.w("VoiceController.start: RECORD_AUDIO not granted — bailing")
            return false
        }
        finalTranscript = ""
        _latestPartial.value = ""
        _state.value = State.LISTENING

        collectJob = appScope.launch(Dispatchers.Main.immediate) {
            runCatching {
                sttClient.listen().collect { event ->
                    when (event) {
                        is SttEvent.Partial -> _latestPartial.value = event.transcript
                        is SttEvent.Final -> {
                            finalTranscript = event.transcript
                            if (_latestPartial.value.isBlank()) {
                                _latestPartial.value = event.transcript
                            }
                        }
                        is SttEvent.Error -> {
                            Timber.w("STT error (recoverable=%s): %s", event.isRecoverable, event.reason)
                        }
                        is SttEvent.BeginningOfSpeech,
                        is SttEvent.EndOfSpeech -> Unit
                    }
                }
            }.onFailure { t ->
                Timber.w(t, "VoiceController: STT flow failed")
            }
            _state.value = State.IDLE
        }
        return true
    }

    /**
     * Ask the recognizer to stop and wait up to [gracePeriodMs] for the
     * Final transcript. Returns the best usable transcript (or null if
     * nothing usable arrived).
     */
    suspend fun stopAndAwaitFinal(gracePeriodMs: Long = 1500L): String? {
        if (_state.value != State.LISTENING) {
            resetBuffers()
            return null
        }
        sttClient.stopListening()
        withTimeoutOrNull(gracePeriodMs) { collectJob?.join() }
        // Belt-and-braces: if the recognizer never closed the flow (rare),
        // cancel so we don't leak the mic into the next session.
        collectJob?.cancel()
        collectJob = null

        val transcript = finalTranscript.ifBlank { _latestPartial.value }.trim()
        resetBuffers()
        return transcript.takeIf { it.isNotBlank() }
    }

    /**
     * Abort without consuming a transcript (e.g. the user started
     * dragging the widget while listening).
     */
    fun cancel() {
        collectJob?.cancel()
        collectJob = null
        resetBuffers()
    }

    private fun resetBuffers() {
        finalTranscript = ""
        _latestPartial.value = ""
        _state.value = State.IDLE
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
