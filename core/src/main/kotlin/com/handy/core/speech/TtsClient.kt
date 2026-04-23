package com.handy.core.speech

/**
 * Text-to-speech. Speaks only the clamped `[SPOKEN]…[/SPOKEN]` portion —
 * the orchestrator hands this interface text that has already been
 * processed by `AssistantMarkupParser.clampVoiceSpokenForTts`.
 *
 * The `:android-runtime` implementation wraps `TextToSpeech` and chunks
 * on sentence boundaries so utterances longer than ~4 KB don't get
 * silently dropped (see DEBUG_LOG template for the canonical example).
 */
interface TtsClient {

    /** True while [speak] is producing audio. Observed by `VoiceController`. */
    val isSpeaking: Boolean

    /** Queue [text] for playback. Returns immediately. */
    fun speak(text: String, utteranceId: String = "handy-${System.nanoTime()}")

    /** Stop playback immediately and flush the queue. */
    fun stop()

    /** Full teardown. After this, [speak] must not be called. */
    fun release()
}
