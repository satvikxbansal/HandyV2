package com.handy.runtime.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
        /** V2 — Gemini cloud (Generative Language API). */
        const val KEY_GEMINI: String = "gemini_api_key"
    }
}

class EncryptedKeyStore(context: Context) : KeyStore {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun get(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun keys(): Set<String> = prefs.all.keys

    private companion object {
        const val PREFS_NAME = "handy_secrets"
    }
}
