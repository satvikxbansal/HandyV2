package com.handy.runtime.speech

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.concurrent.thread

interface SpeechAudioRecorder {
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start()
    fun stop(): RecordedAudio
    fun cancel()
    fun release()
}

data class RecordedAudio(
    val wavBytes: ByteArray,
    val audioMs: Long,
    val truncated: Boolean,
) {
    val isEmpty: Boolean get() = wavBytes.isEmpty() || audioMs <= 0L

    companion object {
        val Empty = RecordedAudio(ByteArray(0), audioMs = 0L, truncated = false)
    }
}

class MicAudioRecorder @Inject constructor() : SpeechAudioRecorder {

    private val lock = Any()
    private var activeSession: Session? = null
    private var lastRecording: RecordedAudio = RecordedAudio.Empty

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() {
        synchronized(lock) {
            check(activeSession == null) { "MicAudioRecorder is already recording" }
            lastRecording = RecordedAudio.Empty
        }

        val bufferSize = recorderBufferSize()
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("Microphone could not start.")
        }

        val session = Session(recorder = recorder)
        synchronized(lock) {
            activeSession = session
        }
        try {
            recorder.startRecording()
        } catch (t: Throwable) {
            synchronized(lock) {
                if (activeSession === session) activeSession = null
            }
            recorder.release()
            throw t
        }

        session.thread = thread(
            name = "handy-sarvam-mic-recorder",
            isDaemon = true,
        ) {
            readLoop(session = session, bufferSize = bufferSize)
        }
    }

    override fun stop(): RecordedAudio {
        val session = synchronized(lock) {
            activeSession.also { activeSession = null }
        } ?: return lastRecording

        session.running.set(false)
        runCatching { session.recorder.stop() }
        session.thread?.join(STOP_JOIN_MS)
        runCatching { session.recorder.release() }

        val pcm = synchronized(session.buffer) {
            session.buffer.toByteArray().also { session.buffer.wipe() }
        }
        val audioMs = pcmAudioMs(pcm.size)
        val recording = try {
            if (pcm.isEmpty()) {
                RecordedAudio.Empty.copy(truncated = session.truncated.get())
            } else {
                RecordedAudio(
                    wavBytes = encodeWav(pcm),
                    audioMs = audioMs,
                    truncated = session.truncated.get(),
                )
            }
        } finally {
            pcm.fill(0)
        }
        lastRecording = recording
        return recording
    }

    fun consumeWavBytes(): ByteArray = stop().wavBytes

    override fun cancel() {
        val session = synchronized(lock) {
            activeSession.also { activeSession = null }
        } ?: run {
            lastRecording = RecordedAudio.Empty
            return
        }
        session.running.set(false)
        runCatching { session.recorder.stop() }
        session.thread?.join(STOP_JOIN_MS)
        runCatching { session.recorder.release() }
        synchronized(session.buffer) {
            session.buffer.wipe()
        }
        lastRecording = RecordedAudio.Empty
    }

    override fun release() {
        cancel()
    }

    private fun readLoop(session: Session, bufferSize: Int) {
        val scratch = ByteArray(bufferSize)
        try {
            while (session.running.get()) {
                val remaining = MAX_PCM_BYTES - session.bytesWritten.get()
                if (remaining <= 0) {
                    session.truncated.set(true)
                    break
                }
                val read = session.recorder.read(scratch, 0, minOf(scratch.size, remaining))
                if (read <= 0) continue
                synchronized(session.buffer) {
                    session.buffer.write(scratch, 0, read)
                }
                val total = session.bytesWritten.addAndGet(read)
                if (total >= MAX_PCM_BYTES) {
                    session.truncated.set(true)
                    break
                }
            }
        } finally {
            session.running.set(false)
            runCatching { session.recorder.stop() }
            runCatching { session.recorder.release() }
            scratch.fill(0)
        }
    }

    private fun recorderBufferSize(): Int {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).takeIf { it > 0 } ?: DEFAULT_BUFFER_BYTES
        return maxOf(DEFAULT_BUFFER_BYTES, minBuffer)
    }

    private fun pcmAudioMs(bytes: Int): Long =
        TimeUnit.SECONDS.toMillis(1) * bytes / BYTES_PER_SECOND

    private fun encodeWav(pcm: ByteArray): ByteArray {
        val totalDataLen = pcm.size + WAV_HEADER_BYTES - 8
        val byteRate = BYTES_PER_SECOND
        val header = ByteBuffer.allocate(WAV_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putAscii("RIFF")
            .putInt(totalDataLen)
            .putAscii("WAVE")
            .putAscii("fmt ")
            .putInt(16)
            .putShort(1.toShort())
            .putShort(CHANNELS.toShort())
            .putInt(SAMPLE_RATE_HZ)
            .putInt(byteRate)
            .putShort((CHANNELS * BYTES_PER_SAMPLE).toShort())
            .putShort(BITS_PER_SAMPLE.toShort())
            .putAscii("data")
            .putInt(pcm.size)
            .array()
        return header + pcm
    }

    private fun ByteBuffer.putAscii(value: String): ByteBuffer {
        put(value.toByteArray(Charsets.US_ASCII))
        return this
    }

    private class Session(
        val recorder: AudioRecord,
        val running: AtomicBoolean = AtomicBoolean(true),
        val truncated: AtomicBoolean = AtomicBoolean(false),
        val bytesWritten: AtomicInteger = AtomicInteger(0),
        val buffer: WipeableByteArrayOutputStream = WipeableByteArrayOutputStream(MAX_PCM_BYTES),
    ) {
        @Volatile var thread: Thread? = null
    }

    private class WipeableByteArrayOutputStream(capacity: Int) : ByteArrayOutputStream(capacity) {
        fun wipe() {
            buf.fill(0)
            reset()
        }
    }

    companion object {
        const val SAMPLE_RATE_HZ: Int = 16_000
        const val MAX_SESSION_MS: Long = 30_000L
        private const val CHANNELS: Int = 1
        private const val BITS_PER_SAMPLE: Int = 16
        private const val BYTES_PER_SAMPLE: Int = BITS_PER_SAMPLE / 8
        private const val BYTES_PER_SECOND: Int = SAMPLE_RATE_HZ * CHANNELS * BYTES_PER_SAMPLE
        private const val MAX_PCM_BYTES: Int = BYTES_PER_SECOND * 30
        private const val WAV_HEADER_BYTES: Int = 44
        private const val DEFAULT_BUFFER_BYTES: Int = 4096
        private const val STOP_JOIN_MS: Long = 750L
    }
}
