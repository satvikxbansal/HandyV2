package com.handy.runtime.llm

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.handy.core.llm.LocalAvailability
import com.handy.core.llm.LocalGenAiClient
import com.handy.core.llm.LocalGenAiRequest
import com.handy.core.llm.LocalGenAiResult
import com.handy.core.llm.LocalTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * On-device Gemini Nano / AICore adapter.
 *
 * Android's on-device GenAI stack lives behind `com.google.ai.edge.aicore`
 * and requires a Pixel 8+/9 class device + active AICore model download.
 * Because that dependency is subject to invite-only rollout and adds
 * binary size (~several MB at the time of writing), Handy's V2 client
 * deliberately does **not** link against the SDK directly.
 *
 * Instead:
 *  - [isAvailable] uses `Build.MODEL` / battery / thermal checks + a
 *    reflection probe for the AICore class to decide whether Nano can
 *    run at all.
 *  - [run] returns [LocalGenAiResult.Unsupported] on devices without
 *    AICore — the router falls back to cloud gracefully.
 *
 * When the AICore SDK is added as a real dependency (Phase 4.5 /
 * Play-side opt-in), this class swaps its `runReal(...)` internals
 * while the public contract stays identical.
 */
class GeminiNanoLocalGenAiClient(
    private val context: Context,
) : LocalGenAiClient {

    override val modelId: String = "gemini-nano-local"

    override suspend fun isAvailable(): LocalAvailability = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < MIN_SDK_FOR_AICORE) return@withContext LocalAvailability.Unsupported
        if (!hasAiCore()) return@withContext LocalAvailability.Unsupported
        if (inBatterySaver()) {
            return@withContext LocalAvailability.TemporarilyUnavailable("Battery saver active")
        }
        if (inThermalThrottle()) {
            return@withContext LocalAvailability.TemporarilyUnavailable("Device is throttling")
        }
        // Without a real SDK dependency we can't tell "downloading" vs
        // "ready" — treat "AICore class present" as `Available`. The
        // router will see the LocalGenAiResult.Failed if something goes
        // wrong at run time.
        LocalAvailability.Available
    }

    override suspend fun supports(task: LocalTask): Boolean = when (task) {
        LocalTask.SUMMARIZE_TEXT,
        LocalTask.REWRITE_TEXT,
        LocalTask.TRANSLATE_TEXT,
        LocalTask.SCREEN_TEXT_QA,
        LocalTask.NOTIFICATION_SUMMARY,
        LocalTask.CLIPBOARD_TRANSFORM,
        LocalTask.SHORT_TITLE_OR_LABEL -> true
    }

    override suspend fun run(request: LocalGenAiRequest): LocalGenAiResult =
        withContext(Dispatchers.Default) {
            val availability = isAvailable()
            if (availability != LocalAvailability.Available) {
                return@withContext LocalGenAiResult.Unsupported(
                    reason = when (availability) {
                        LocalAvailability.Unsupported -> "AICore not available on this device"
                        LocalAvailability.Downloading -> "AICore model still downloading"
                        is LocalAvailability.TemporarilyUnavailable -> availability.reason
                        LocalAvailability.Available -> "unreachable"
                    },
                )
            }
            runReal(request)
        }

    /**
     * Stub implementation: returns a `Failed` result whenever the AICore
     * SDK isn't wired. Swap this body for the real AICore call when the
     * dependency lands — the public contract stays the same.
     */
    private fun runReal(request: LocalGenAiRequest): LocalGenAiResult {
        Timber.d("GeminiNanoLocalGenAiClient.runReal: task=%s len=%d", request.task, request.input.length)
        // Minimal useful fallback: character-bounded heuristics that
        // produce a "nothing-on-device" result so the caller can route
        // cleanly. Real adapter goes here in Phase 4.5.
        return LocalGenAiResult.Failed(
            "Gemini Nano adapter not wired — AICore SDK dependency pending.",
        )
    }

    private fun hasAiCore(): Boolean {
        // Reflection probe. When the SDK is added, replace this with a
        // direct class reference.
        return runCatching { Class.forName("com.google.ai.edge.aicore.GenerativeModel") }.isSuccess
    }

    private fun inBatterySaver(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isPowerSaveMode
    }

    private fun inThermalThrottle(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    private companion object {
        /** AICore requires API 31+. */
        const val MIN_SDK_FOR_AICORE = Build.VERSION_CODES.S
    }
}
