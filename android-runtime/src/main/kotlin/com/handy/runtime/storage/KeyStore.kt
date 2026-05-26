package com.handy.runtime.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

/**
 * Secrets store backed by `EncryptedSharedPreferences` + the Android
 * Keystore. All reads / writes go through this abstraction so rotating
 * the crypto backend later (e.g. to jetpack-crypto-tink) is a single
 * swap.
 *
 * No value is ever logged.
 */
interface KeyStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>

    companion object {
        const val KEY_ANTHROPIC: String = "anthropic_api_key"
        const val KEY_BRAVE: String = "brave_api_key"
        const val KEY_JINA: String = "jina_api_key"
        const val KEY_GITHUB: String = "github_api_key"
        const val KEY_SARVAM: String = "sarvam_api_key"
        /** V2 — Gemini cloud (Generative Language API). */
        const val KEY_GEMINI: String = "gemini_api_key"
    }
}

class EncryptedKeyStore(context: Context) : KeyStore {

    private val appContext = context.applicationContext
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = encryptedPrefs(CURRENT_PREFS_NAME)

    init {
        migrateLegacyPrefsIfNeeded()
        rotateCurrentPrefsIfNeeded()
    }

    override fun get(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).commitOrWarn("put")
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).commitOrWarn("remove")
    }

    override fun keys(): Set<String> = prefs.all.keys - VERSION_KEY

    private fun encryptedPrefs(name: String): SharedPreferences =
        EncryptedSharedPreferences.create(
            appContext,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun migrateLegacyPrefsIfNeeded() {
        if (!appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).hasAnyValue()) return
        runCatching {
            val legacy = encryptedPrefs(LEGACY_PREFS_NAME)
            val migrated = legacy.all
                .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
                .filter { (key, value) -> key != VERSION_KEY && value.isNotBlank() }
            if (migrated.isEmpty()) return@runCatching
            val edit = prefs.edit()
            migrated.forEach { (key, value) ->
                if (!prefs.contains(key)) edit.putString(key, value)
            }
            edit.putInt(VERSION_KEY, CURRENT_CRYPTO_VERSION).commitOrWarn("legacy-migrate")
            legacy.edit().clear().commitOrWarn("legacy-clear")
            Timber.i("EncryptedKeyStore: migrated %d secret key slots to current encrypted prefs", migrated.size)
        }.onFailure {
            Timber.w(it, "EncryptedKeyStore: legacy migration failed")
        }
    }

    private fun rotateCurrentPrefsIfNeeded() {
        val version = prefs.getInt(VERSION_KEY, 0)
        if (version >= CURRENT_CRYPTO_VERSION) return
        runCatching {
            val values = prefs.all
                .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
                .filter { (key, value) -> key != VERSION_KEY && value.isNotBlank() }
            val edit = prefs.edit().clear()
            values.forEach { (key, value) -> edit.putString(key, value) }
            edit.putInt(VERSION_KEY, CURRENT_CRYPTO_VERSION).commitOrWarn("rotate")
            Timber.i("EncryptedKeyStore: verified encrypted prefs crypto version %d", CURRENT_CRYPTO_VERSION)
        }.onFailure {
            Timber.w(it, "EncryptedKeyStore: rotation verification failed")
        }
    }

    private fun SharedPreferences.hasAnyValue(): Boolean =
        runCatching { all.isNotEmpty() }.getOrDefault(false)

    private fun SharedPreferences.Editor.commitOrWarn(operation: String) {
        if (!commit()) Timber.w("EncryptedKeyStore: %s commit failed", operation)
    }

    companion object {
        const val KEY_SARVAM: String = KeyStore.KEY_SARVAM
        private const val CURRENT_PREFS_NAME = "handy_secrets_v2"
        private const val LEGACY_PREFS_NAME = "handy_secrets"
        private const val VERSION_KEY = "__crypto_version"
        private const val CURRENT_CRYPTO_VERSION = 2
    }
}
