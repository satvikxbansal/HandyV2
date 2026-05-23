package com.handy.runtime.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.handy.runtime.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber

private val Context.learnedAllowlistDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "handy_learned_action_allowlist",
)

@Singleton
class LearnedAllowlistStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope appScope: CoroutineScope,
    private val json: Json,
) {
    private val prefs = context.learnedAllowlistDataStore
    private val successThreshold: Int = DEFAULT_SUCCESS_THRESHOLD

    @Volatile
    private var counters: Map<String, Int> = emptyMap()

    val flow = prefs.data.map { p -> p.decodeCounters() }

    init {
        appScope.launch {
            flow.collect { counters = it }
        }
    }

    fun isLearnedSync(packageName: String?): Boolean {
        val key = packageName.normalizedPackage() ?: return false
        if (key in GESTURE_FALLBACK_EXCLUDED_PACKAGES) return false
        return (counters[key] ?: 0) >= successThreshold
    }

    suspend fun recordSuccess(packageName: String?): Int {
        val key = packageName.normalizedPackage() ?: return 0
        if (key in GESTURE_FALLBACK_EXCLUDED_PACKAGES) return 0
        return withContext(Dispatchers.IO) {
            var nextCount = 0
            prefs.edit { p ->
                val current = p.decodeCounters()
                nextCount = ((current[key] ?: 0) + 1).coerceAtMost(Int.MAX_VALUE)
                val next = current + (key to nextCount)
                p[COUNTERS_JSON] = encodeCounters(next)
                counters = next
            }
            nextCount
        }
    }

    private fun Preferences.decodeCounters(): Map<String, Int> {
        val raw = this[COUNTERS_JSON] ?: return emptyMap()
        return runCatching { json.decodeFromString(COUNTERS_SERIALIZER, raw) }
            .getOrElse {
                Timber.w(it, "LearnedAllowlistStore: failed to decode counters")
                emptyMap()
            }
    }

    private fun encodeCounters(value: Map<String, Int>): String =
        json.encodeToString(COUNTERS_SERIALIZER, value)

    private fun String?.normalizedPackage(): String? =
        this?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

    private companion object {
        const val DEFAULT_SUCCESS_THRESHOLD: Int = 3
        val GESTURE_FALLBACK_EXCLUDED_PACKAGES = setOf(
            "com.ubercab",
            "com.olacabs.customer",
            "com.rapido.passenger",
        )
        val COUNTERS_JSON = stringPreferencesKey("package_success_counters_json")
        val COUNTERS_SERIALIZER = MapSerializer(String.serializer(), Int.serializer())
    }
}
