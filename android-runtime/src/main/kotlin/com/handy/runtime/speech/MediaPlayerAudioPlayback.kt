package com.handy.runtime.speech

import android.content.Context
import android.media.MediaPlayer
import com.handy.runtime.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MediaPlayerAudioPlayback @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : AudioPlayback {

    private val lock = Any()
    private val trackedFiles = mutableSetOf<File>()
    private val playbackGeneration = AtomicLong(0L)
    private val cacheDir = File(context.applicationContext.cacheDir, "tts").apply {
        mkdirs()
        deleteExistingAudioFiles()
    }
    private var current: PlaybackHandle? = null
    @Volatile private var released = false

    override suspend fun play(wavBytes: ByteArray, utteranceId: String) {
        check(!released) { "AudioPlayback has been released" }
        val generation = playbackGeneration.get()
        val file = withContext(Dispatchers.IO) { writeCacheFile(wavBytes, utteranceId, generation) }
        try {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { cont ->
                    val player = MediaPlayer()
                    val completed = AtomicBoolean(false)
                    lateinit var handle: PlaybackHandle

                    fun finish(error: Throwable?) {
                        if (!completed.compareAndSet(false, true)) return
                        synchronized(lock) {
                            if (current === handle) current = null
                        }
                        runCatching { player.setOnCompletionListener(null) }
                        runCatching { player.setOnErrorListener(null) }
                        runCatching { player.release() }
                        deleteNow(file)
                        if (!cont.isActive) return
                        if (error == null) {
                            cont.resume(Unit)
                        } else {
                            cont.resumeWithException(error)
                        }
                    }

                    handle = PlaybackHandle {
                        finish(CancellationException("playback stopped"))
                    }
                    cont.invokeOnCancellation { handle.finish() }

                    player.setOnCompletionListener { finish(null) }
                    player.setOnErrorListener { _, what, extra ->
                        finish(IllegalStateException("MediaPlayer error what=$what extra=$extra"))
                        true
                    }

                    try {
                        synchronized(lock) {
                            current?.finish()
                            current = handle
                        }
                        player.setDataSource(file.absolutePath)
                        player.prepare()
                        player.start()
                    } catch (t: Throwable) {
                        finish(t)
                    }
                }
            }
        } finally {
            deleteSoon(file)
        }
    }

    override fun stop() {
        playbackGeneration.incrementAndGet()
        val handle = synchronized(lock) {
            current.also { current = null }
        }
        handle?.finish()
        deleteTrackedFiles()
        cacheDir.deleteExistingAudioFiles()
    }

    override fun release() {
        released = true
        stop()
        cacheDir.deleteExistingAudioFiles()
    }

    private fun writeCacheFile(wavBytes: ByteArray, utteranceId: String, generation: Long): File {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Timber.w("MediaPlayerAudioPlayback: failed to create cache dir")
        }
        val safeId = utteranceId
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(48)
            .ifBlank { "utterance" }
        val file = File(cacheDir, "$safeId-${System.nanoTime()}.wav")
        file.outputStream().use { it.write(wavBytes) }
        synchronized(lock) {
            if (released || playbackGeneration.get() != generation) {
                if (file.exists() && !file.delete()) deleteSoon(file)
                throw CancellationException("playback stopped")
            }
            trackedFiles += file
        }
        return file
    }

    private fun deleteNow(file: File) {
        synchronized(lock) { trackedFiles -= file }
        if (file.exists() && !file.delete()) {
            Timber.w("MediaPlayerAudioPlayback: cache file delete deferred")
            deleteSoon(file)
        }
    }

    private fun deleteSoon(file: File) {
        if (!file.exists()) return
        scope.launch(Dispatchers.IO) {
            delay(MAX_DELETE_DELAY_MS)
            if (file.exists() && !file.delete()) {
                Timber.w("MediaPlayerAudioPlayback: cache file delete failed")
            }
            synchronized(lock) { trackedFiles -= file }
        }
    }

    private fun deleteTrackedFiles() {
        val files = synchronized(lock) {
            trackedFiles.toList().also { trackedFiles.clear() }
        }
        files.forEach { file ->
            if (file.exists() && !file.delete()) {
                deleteSoon(file)
            }
        }
    }

    private fun File.deleteExistingAudioFiles() {
        listFiles { file -> file.isFile && file.extension.equals("wav", ignoreCase = true) }
            .orEmpty()
            .forEach { file ->
                if (file.exists() && !file.delete()) {
                    Timber.w("MediaPlayerAudioPlayback: stale cache file delete failed")
                    deleteSoon(file)
                }
            }
    }

    private fun interface PlaybackHandle {
        fun finish()
    }

    private companion object {
        const val MAX_DELETE_DELAY_MS: Long = 5_000L
    }
}
