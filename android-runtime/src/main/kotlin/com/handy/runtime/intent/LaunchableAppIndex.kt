package com.handy.runtime.intent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Launcher-resolved app index. Built at app start; refreshed on
 * `ACTION_PACKAGE_ADDED / REMOVED / REPLACED` broadcasts.
 *
 * Avoids per-request fuzzy `PackageManager` walks (guardrails →
 * "Package visibility / launchable-app resolution") and never asks
 * for `QUERY_ALL_PACKAGES`.
 */
class LaunchableAppIndex(
    private val context: Context,
    /**
     * Long-lived scope used to rebuild the index on package-change
     * broadcasts. Wired to an `@ApplicationScope` in Phase 3 Hilt modules.
     * **Never pass `GlobalScope`** — see guardrails → "Concurrency".
     */
    private val appScope: CoroutineScope,
) {

    data class Entry(
        val packageName: String,
        val label: String,
        val activityComponentFlat: String,
    ) {
        val appLabel: String get() = label
    }

    private val state = MutableStateFlow<List<Entry>>(emptyList())
    val apps: StateFlow<List<Entry>> get() = state.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    suspend fun initialise() {
        refresh()
        registerPackageReceiver()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        }.getOrElse {
            Timber.w(it, "LaunchableAppIndex queryIntentActivities failed")
            emptyList()
        }

        val entries = resolved.mapNotNull { info ->
            val ai = info.activityInfo ?: return@mapNotNull null
            val pkg = ai.packageName ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(pm)?.toString() }.getOrNull() ?: pkg
            Entry(
                packageName = pkg,
                label = label,
                activityComponentFlat = "$pkg/${ai.name}",
            )
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.US) }

        state.value = entries
    }

    /**
     * Find launchable apps for a free-form `hint` like `"gmail"` →
     * `com.google.android.gm`. Returns every match at the first non-empty
     * priority tier:
     *  1. exact package match.
     *  2. exact label match (case-insensitive).
     *  3. label starts-with match.
     *  4. label contains match.
     *  5. package contains match.
     */
    fun find(hint: String): List<Entry> {
        val snapshot = state.value
        if (snapshot.isEmpty() || hint.isBlank()) return emptyList()
        val needle = hint.trim().lowercase(Locale.US)

        snapshot.filter { it.packageName.equals(needle, ignoreCase = true) }
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
        snapshot.filter { it.label.equals(needle, ignoreCase = true) }
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
        snapshot.filter { it.label.lowercase(Locale.US).startsWith(needle) }
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
        snapshot.filter { it.label.lowercase(Locale.US).contains(needle) }
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
        snapshot.filter { it.packageName.lowercase(Locale.US).contains(needle) }
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        // Last resort: minimum edit distance within a small window.
        val cutoff = min(4, needle.length)
        return snapshot
            .map { it to levenshtein(needle, it.label.lowercase(Locale.US)) }
            .filter { it.second <= cutoff }
            .sortedWith(
                compareBy<Pair<Entry, Int>> { it.second }
                    .thenBy { it.first.label.lowercase(Locale.US) }
                    .thenBy { it.first.packageName.lowercase(Locale.US) },
            )
            .map { it.first }
    }

    /**
     * Fuzzy resolve a free-form `hint` like `"gmail"` →
     * `com.google.android.gm`. Returns the first deterministic match for
     * legacy single-target callers; use [find] when ambiguity matters.
     */
    fun resolve(hint: String): Entry? = find(hint).firstOrNull()

    private fun registerPackageReceiver() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                Timber.d("LaunchableAppIndex: package change %s", i?.action)
                appScope.launch(Dispatchers.IO) { refresh() }
            }
        }
        // ContextCompat would be cleaner but keeps us :android-runtime-neutral.
        @Suppress("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(r, filter)
        }
        receiver = r
    }

    fun release() {
        receiver?.let {
            runCatching { context.unregisterReceiver(it) }
            receiver = null
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost,
                )
            }
            val t = prev; prev = curr; curr = t
        }
        return prev[b.length]
    }
}
