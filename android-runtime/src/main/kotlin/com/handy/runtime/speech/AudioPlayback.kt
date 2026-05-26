package com.handy.runtime.speech

interface AudioPlayback {
    suspend fun play(wavBytes: ByteArray, utteranceId: String)
    fun stop()
    fun release()
}
