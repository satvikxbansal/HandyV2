package com.handy.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class HandySettingsJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test fun `new automation flags default true when decoding old settings payload`() {
        val decoded = json.decodeFromString(
            HandySettings.serializer(),
            """
                {
                  "tapForMeEnabled": true,
                  "actionDisclosureVersionAccepted": 1
                }
            """.trimIndent(),
        )

        assertThat(decoded.typeForMeEnabled).isTrue()
        assertThat(decoded.recipesEnabled).isTrue()
        assertThat(decoded.speakVoiceRepliesAloud).isTrue()
        assertThat(decoded.ttsProvider).isEqualTo(TtsProvider.SYSTEM)
        assertThat(decoded.sarvamSpokenLanguage).isEqualTo(SarvamLanguage.AUTO)
        assertThat(decoded.sttMode).isEqualTo(SttMode.AUTO)
        assertThat(decoded.sttLanguage).isEqualTo(SttLanguage.SYSTEM)
    }

    @Test fun `automation flags round trip through settings json`() {
        val original = HandySettings(
            typeForMeEnabled = false,
            recipesEnabled = false,
            speakVoiceRepliesAloud = false,
            sttMode = SttMode.ON_DEVICE_ONLY,
            sttLanguage = SttLanguage.HINGLISH,
            ttsProvider = TtsProvider.SARVAM,
            sarvamSpokenLanguage = SarvamLanguage.HINGLISH,
        )

        val decoded = json.decodeFromString(
            HandySettings.serializer(),
            json.encodeToString(HandySettings.serializer(), original),
        )

        assertThat(decoded.typeForMeEnabled).isFalse()
        assertThat(decoded.recipesEnabled).isFalse()
        assertThat(decoded.speakVoiceRepliesAloud).isFalse()
        assertThat(decoded.sttMode).isEqualTo(SttMode.ON_DEVICE_ONLY)
        assertThat(decoded.sttLanguage).isEqualTo(SttLanguage.HINGLISH)
        assertThat(decoded.ttsProvider).isEqualTo(TtsProvider.SARVAM)
        assertThat(decoded.sarvamSpokenLanguage).isEqualTo(SarvamLanguage.HINGLISH)
    }
}
