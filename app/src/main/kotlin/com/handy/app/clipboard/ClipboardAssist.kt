package com.handy.app.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.ContextCompat
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Clipboard assist — scope §9.
 *
 * Rules:
 *  - No ambient harvesting. We only read the clipboard when Handy's
 *    panel / chat activity is user-visible AND the user has enabled
 *    the feature in settings.
 *  - Dedup by SHA-256 of the clip text — the same clip won't process
 *    twice.
 *  - 32 KB cap on clip size.
 *  - OTP / password heuristics skip auto-processing (the user must
 *    explicitly re-paste or confirm to include).
 *  - When writing back, mark sensitive transforms via
 *    `EXTRA_IS_SENSITIVE` on API 33+.
 *
 * The service/panel sets [visible] to true when it attaches; the
 * ClipboardManager listener only fires updates while visible.
 */
@Singleton
class ClipboardAssist @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: DataStoreSettings,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val clipboard: ClipboardManager? =
        ContextCompat.getSystemService(context, ClipboardManager::class.java)

    private val _state = MutableStateFlow<ClipState>(ClipState.Idle)
    val state: StateFlow<ClipState> = _state.asStateFlow()

    @Volatile private var visible: Boolean = false
    private var lastHash: String? = null

    private val primaryClipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!visible) return@OnPrimaryClipChangedListener
        appScope.launch(Dispatchers.IO) { readCurrentClip() }
    }

    init {
        appScope.launch {
            settings.flow.collectLatest { snapshot ->
                if (snapshot.clipboardAssistEnabled && visible) {
                    clipboard?.addPrimaryClipChangedListener(primaryClipListener)
                } else {
                    clipboard?.removePrimaryClipChangedListener(primaryClipListener)
                    if (!snapshot.clipboardAssistEnabled) _state.value = ClipState.Idle
                }
            }
        }
    }

    /** Call when the panel / chat attaches. */
    fun onVisible() {
        visible = true
        appScope.launch(Dispatchers.IO) {
            if (runCatching { settings.current().clipboardAssistEnabled }.getOrDefault(false)) {
                readCurrentClip()
                clipboard?.addPrimaryClipChangedListener(primaryClipListener)
            }
        }
    }

    /** Call when the panel / chat detaches. */
    fun onHidden() {
        visible = false
        runCatching { clipboard?.removePrimaryClipChangedListener(primaryClipListener) }
    }

    private fun readCurrentClip() {
        val cm = clipboard ?: return
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0) return
        val item = clip.getItemAt(0) ?: return
        val mime = clip.description?.getMimeType(0).orEmpty()
        if (!mime.startsWith(ClipDescription.MIMETYPE_TEXT_PLAIN.dropLast(1)) &&
            mime != ClipDescription.MIMETYPE_TEXT_PLAIN &&
            mime != ClipDescription.MIMETYPE_TEXT_HTML
        ) {
            // Ignore URI / binary clips in V2 — scope §9 edge cases.
            return
        }
        val text = item.text?.toString().orEmpty()
        if (text.isBlank()) return
        if (text.length > MAX_CLIP_CHARS) {
            _state.value = ClipState.TooLarge(text.length)
            return
        }
        val hash = sha256(text)
        if (hash == lastHash) return
        lastHash = hash
        if (isSensitiveHint(text)) {
            _state.value = ClipState.SensitiveSkipped
            return
        }
        _state.value = ClipState.Text(text)
    }

    /**
     * Write [text] back to the primary clip, marking it sensitive on
     * API 33+ when [sensitive] is true.
     */
    fun writeBack(text: String, sensitive: Boolean = false) {
        val cm = clipboard ?: return
        val clip = android.content.ClipData.newPlainText("Handy", text)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = PersistableBundle()
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true)
            clip.description.extras = extras
        }
        runCatching { cm.setPrimaryClip(clip) }.onFailure {
            Timber.w(it, "ClipboardAssist.writeBack failed")
        }
    }

    /**
     * Reset dedupe — call when the user has explicitly consented to
     * re-process a previously-skipped clip.
     */
    fun clearDedup() {
        lastHash = null
    }

    private fun sha256(text: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Conservative heuristic — matches anything that looks like:
     *  - a 4–8 digit OTP
     *  - a 14–19 digit credit-card
     *  - a password-like string (16+ chars with mixed symbols + digits)
     *
     * False negatives are fine; the point is only to skip auto-processing.
     */
    private fun isSensitiveHint(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.matches(Regex("^\\d{4,8}$"))) return true
        if (trimmed.replace(" ", "").replace("-", "").matches(Regex("^\\d{14,19}$"))) return true
        if (trimmed.length in 16..64 &&
            trimmed.any { it.isDigit() } &&
            trimmed.any { it.isUpperCase() } &&
            trimmed.any { !it.isLetterOrDigit() }
        ) return true
        return false
    }

    sealed class ClipState {
        data object Idle : ClipState()
        data class Text(val content: String) : ClipState()
        data class TooLarge(val chars: Int) : ClipState()
        data object SensitiveSkipped : ClipState()
    }

    private companion object {
        const val MAX_CLIP_CHARS = 32_000
    }
}
