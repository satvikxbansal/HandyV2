package com.handy.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.handy.app.overlay.BuddyFlightDriver
import com.handy.app.overlay.OverlayPresenter
import com.handy.core.agent.CorrectionIntent
import com.handy.core.overlay.BuddyState
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Application-scoped glue between the floating-widget long-press
 * gesture, the [SttClient] adapter, and the chat pipeline.
 *
 * DL-008: the flow collector does NOT reset [_state] to IDLE on its
 * own. Only [stopAndAwaitFinal] and [cancel] do that. This prevents
 * the race where the recognizer errors out before the user releases
 * their finger, which would make [stopAndAwaitFinal] short-circuit
 * to null and swallow the transcript (or the partial).
 */
@Singleton
class VoiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sttClient: SttClient,
    private val presenter: OverlayPresenter,
    private val flightDriver: BuddyFlightDriver,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    enum class State { IDLE, LISTENING }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _latestPartial = MutableStateFlow("")
    val latestPartial: StateFlow<String> = _latestPartial.asStateFlow()

    private var collectJob: Job? = null

    @Volatile private var finalTranscript: String = ""
    @Volatile private var lastError: String? = null
    @Volatile private var startedWhilePointing: Boolean = false
    @Volatile private var lastPointingCorrectionHandled: Boolean = false

    val isOnDeviceAvailable: Boolean
        get() = sttClient.isOnDeviceAvailable

    fun start(): Boolean {
        if (_state.value != State.IDLE) {
            Timber.d("VoiceController.start: already %s — refusing", _state.value)
            return false
        }
        if (!hasMicPermission()) {
            Timber.w("VoiceController.start: RECORD_AUDIO not granted")
            return false
        }
        finalTranscript = ""
        lastError = null
        lastPointingCorrectionHandled = false
        startedWhilePointing = presenter.state.value.let { overlay ->
            overlay.buddyState == BuddyState.POINTING &&
                overlay.candidateOptions?.hasAlternatives == true
        }
        _latestPartial.value = ""
        _state.value = State.LISTENING

        Timber.d("VoiceController: starting STT session")

        collectJob = appScope.launch(Dispatchers.Main.immediate) {
            runCatching {
                sttClient.listen().collect { event ->
                    Timber.d("VoiceController: STT event=%s", event::class.simpleName)
                    when (event) {
                        is SttEvent.Partial -> {
                            _latestPartial.value = event.transcript
                        }
                        is SttEvent.Final -> {
                            finalTranscript = event.transcript
                            Timber.d("VoiceController: final transcript chars=%d", event.transcript.length)
                            if (_latestPartial.value.isBlank()) {
                                _latestPartial.value = event.transcript
                            }
                        }
                        is SttEvent.Error -> {
                            lastError = event.reason
                            Timber.w("VoiceController: STT error (recoverable=%s): %s",
                                event.isRecoverable, event.reason)
                        }
                        is SttEvent.BeginningOfSpeech -> {
                            Timber.d("VoiceController: beginning of speech")
                        }
                        is SttEvent.EndOfSpeech -> {
                            Timber.d("VoiceController: end of speech")
                        }
                    }
                }
            }.onFailure { t ->
                Timber.w(t, "VoiceController: STT flow threw")
                lastError = t.message ?: "STT flow failed"
            }
            // Flow is done — but we do NOT reset state to IDLE here.
            // The user may still have their finger down (long-press).
            // stopAndAwaitFinal() or cancel() will clean up.
            Timber.d(
                "VoiceController: STT flow completed finalChars=%d partialChars=%d err=%s",
                finalTranscript.length,
                _latestPartial.value.length,
                lastError,
            )
        }
        return true
    }

    /**
     * Gracefully stop the recognizer and return the best available
     * transcript. Returns null when nothing usable was captured.
     */
    suspend fun stopAndAwaitFinal(gracePeriodMs: Long = 2000L): String? {
        Timber.d(
            "VoiceController.stopAndAwaitFinal: state=%s finalChars=%d partialChars=%d",
            _state.value,
            finalTranscript.length,
            _latestPartial.value.length,
        )

        // Even if the flow already completed (e.g. on error), we still
        // want to drain whatever was buffered — so we do NOT bail on
        // state != LISTENING. We only bail if we were never started.
        if (_state.value == State.IDLE && collectJob == null) {
            Timber.d("VoiceController.stopAndAwaitFinal: never started — returning null")
            return null
        }

        // Ask the recognizer to stop (no-op if the flow already closed).
        sttClient.stopListening()

        // Wait for the collector to finish (the Final event, or the
        // error/close that follows stopListening).
        withTimeoutOrNull(gracePeriodMs) { collectJob?.join() }

        // Belt-and-braces: if the recognizer never closed the flow,
        // cancel it so we don't leak the mic.
        collectJob?.cancel()
        collectJob = null

        // Prefer the Final transcript; fall back to the last partial;
        // fall back to null.
        val transcript = finalTranscript.ifBlank { _latestPartial.value }.trim()
        val result = transcript.takeIf { it.isNotBlank() }
        val correctionHandled = result?.let { routePointingCorrection(it) } == true

        Timber.d("VoiceController.stopAndAwaitFinal: returningChars=%d err=%s", result?.length ?: 0, lastError)

        resetBuffers()
        return if (correctionHandled) null else result
    }

    fun cancel() {
        Timber.d("VoiceController.cancel")
        collectJob?.cancel()
        collectJob = null
        sttClient.stopListening()
        lastPointingCorrectionHandled = false
        resetBuffers()
    }

    fun consumeLastPointingCorrectionHandled(): Boolean {
        val handled = lastPointingCorrectionHandled
        lastPointingCorrectionHandled = false
        return handled
    }

    private fun resetBuffers() {
        finalTranscript = ""
        lastError = null
        _latestPartial.value = ""
        _state.value = State.IDLE
        startedWhilePointing = false
    }

    private suspend fun routePointingCorrection(transcript: String): Boolean {
        val mayCorrect = startedWhilePointing ||
            presenter.state.value.buddyState == BuddyState.POINTING
        if (!mayCorrect) return false
        val intent = CorrectionIntent.classify(transcript) ?: return false
        val handled = withContext(Dispatchers.Main.immediate) {
            flightDriver.applyCorrectionIntent(intent)
        }
        if (handled) {
            lastPointingCorrectionHandled = true
            Timber.d("VoiceController: consumed pointing correction chars=%d as %s", transcript.length, intent)
        }
        return handled
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
