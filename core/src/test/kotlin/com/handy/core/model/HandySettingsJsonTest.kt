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
        assertThat(decoded.saarikaLanguage).isEqualTo(SttLanguage.SYSTEM)
        assertThat(decoded.sttProvider).isEqualTo(SttProvider.ANDROID)
        assertThat(decoded.sarvamSttConsentGranted).isFalse()
        assertThat(decoded.voiceExpanded).isFalse()
        assertThat(decoded.voiceTtsOpen).isFalse()
        assertThat(decoded.voiceSttOpen).isFalse()
        assertThat(decoded.reduceBuddyMotion).isFalse()
    }

    @Test fun `automation flags round trip through settings json`() {
        val original = HandySettings(
            typeForMeEnabled = false,
            recipesEnabled = false,
            reduceBuddyMotion = true,
            speakVoiceRepliesAloud = false,
            sttProvider = SttProvider.SARVAM_SAARIKA,
            sttMode = SttMode.ON_DEVICE_ONLY,
            sttLanguage = SttLanguage.HINGLISH,
            saarikaLanguage = SttLanguage.HINDI,
            sarvamSttConsentGranted = true,
            ttsProvider = TtsProvider.SARVAM,
            ttsSystemLastSelectedEpochMs = 1234L,
            sarvamSpokenLanguage = SarvamLanguage.HINGLISH,
            voiceExpanded = true,
            voiceTtsOpen = true,
            voiceSttOpen = true,
        )

        val decoded = json.decodeFromString(
            HandySettings.serializer(),
            json.encodeToString(HandySettings.serializer(), original),
        )

        assertThat(decoded.typeForMeEnabled).isFalse()
        assertThat(decoded.recipesEnabled).isFalse()
        assertThat(decoded.reduceBuddyMotion).isTrue()
        assertThat(decoded.speakVoiceRepliesAloud).isFalse()
        assertThat(decoded.sttProvider).isEqualTo(SttProvider.SARVAM_SAARIKA)
        assertThat(decoded.sttMode).isEqualTo(SttMode.ON_DEVICE_ONLY)
        assertThat(decoded.sttLanguage).isEqualTo(SttLanguage.HINGLISH)
        assertThat(decoded.saarikaLanguage).isEqualTo(SttLanguage.HINDI)
        assertThat(decoded.sarvamSttConsentGranted).isTrue()
        assertThat(decoded.ttsProvider).isEqualTo(TtsProvider.SARVAM)
        assertThat(decoded.ttsSystemLastSelectedEpochMs).isEqualTo(1234L)
        assertThat(decoded.sarvamSpokenLanguage).isEqualTo(SarvamLanguage.HINGLISH)
        assertThat(decoded.voiceExpanded).isTrue()
        assertThat(decoded.voiceTtsOpen).isTrue()
        assertThat(decoded.voiceSttOpen).isTrue()
    }
}
