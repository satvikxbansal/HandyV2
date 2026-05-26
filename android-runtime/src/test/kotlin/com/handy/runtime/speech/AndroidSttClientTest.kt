package com.handy.runtime.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.handy.core.model.SttMode
import com.handy.core.speech.SttEvent
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSttClientTest {

    @Test
    fun `on-device only without on-device availability emits error and constructs no cloud recognizer`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = false)
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.ON_DEVICE_ONLY,
                languageTag = "en-US",
                enableLanguageSwitch = false,
            ),
            factory = factory,
        )

        client.listen().test {
            assertThat(awaitItem()).isEqualTo(
                SttEvent.Error(
                    "On-device speech isn't available on this phone for this language.",
                    isRecoverable = false,
                ),
            )
            awaitComplete()
        }

        assertThat(factory.onDeviceRecognizers).isEmpty()
        assertThat(factory.cloudRecognizers).isEmpty()
    }

    @Test
    fun `on-device only language unavailable emits pack error with no retry`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = true)
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.ON_DEVICE_ONLY,
                languageTag = "en-US",
                enableLanguageSwitch = false,
            ),
            factory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            factory.onDeviceRecognizers.single().emitError(ERROR_LANGUAGE_UNAVAILABLE)

            assertThat(awaitItem()).isEqualTo(
                SttEvent.Error(
                    "On-device speech is missing the language pack.",
                    isRecoverable = false,
                ),
            )
            awaitComplete()
        }

        assertThat(factory.cloudRecognizers).isEmpty()
    }

    @Test
    fun `auto mode language unavailable keeps DL-015 fallback path`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = true)
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.AUTO,
                languageTag = "en-US",
                enableLanguageSwitch = false,
            ),
            factory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            val onDevice = factory.onDeviceRecognizers.single()
            assertThat(onDevice.intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)).isTrue()

            onDevice.emitError(ERROR_LANGUAGE_UNAVAILABLE)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val cloud = factory.cloudRecognizers.single()
            assertThat(cloud.intent.hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE)).isFalse()
            assertThat(cloud.intent.hasExtra(EXTRA_ENABLE_BIASING_DEVICE_CONTEXT)).isFalse()
            cloud.emitResults("set a timer")

            val final = awaitItem() as SttEvent.Final
            assertThat(final.transcript).isEqualTo("set a timer")
            assertThat(final.isOnDevice).isFalse()
            awaitComplete()
        }
    }

    @Test
    fun `auto mode system recognizer language unavailable keeps DL-024 forced-online retry`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = false)
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.AUTO,
                languageTag = "en-US",
                enableLanguageSwitch = false,
            ),
            factory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertThat(factory.onDeviceRecognizers).isEmpty()
            val firstCloud = factory.cloudRecognizers.single()
            assertThat(firstCloud.intent.hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE)).isFalse()

            firstCloud.emitError(ERROR_LANGUAGE_UNAVAILABLE)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val retryCloud = factory.cloudRecognizers.last()
            assertThat(factory.cloudRecognizers).hasSize(2)
            assertThat(retryCloud.intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
                .isFalse()
            assertThat(retryCloud.intent.hasExtra(EXTRA_ENABLE_BIASING_DEVICE_CONTEXT)).isFalse()
            retryCloud.emitResults("set a timer")

            val final = awaitItem() as SttEvent.Final
            assertThat(final.transcript).isEqualTo("set a timer")
            assertThat(final.isOnDevice).isFalse()
            awaitComplete()
        }
    }

    @Test
    fun `language tag is threaded into recognizer intent`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = true)
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.NETWORK_ALLOWED,
                languageTag = "hi-IN",
                enableLanguageSwitch = false,
            ),
            factory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertThat(factory.onDeviceRecognizers).isEmpty()
            assertThat(factory.cloudRecognizers.single().intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
                .isEqualTo("hi-IN")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `config is resolved once at each listen session start`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = true)
        var config = AndroidSttConfig(
            mode = SttMode.NETWORK_ALLOWED,
            languageTag = "en-US",
            enableLanguageSwitch = false,
        )
        val client = AndroidSttClient(
            context = mockk<Context>(relaxed = true),
            settingsProvider = { config },
            recognizerFactory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertThat(factory.cloudRecognizers.single().intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
                .isEqualTo("en-US")
            config = config.copy(languageTag = "hi-IN")
            assertThat(factory.cloudRecognizers.single().intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
                .isEqualTo("en-US")
            cancelAndIgnoreRemainingEvents()
        }

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertThat(factory.cloudRecognizers.last().intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
                .isEqualTo("hi-IN")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Config(sdk = [33])
    fun `api 33 requests formatting static command biasing and on-device context biasing`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = true)
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.AUTO,
                languageTag = "en-US",
                enableLanguageSwitch = false,
            ),
            factory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val intent = factory.onDeviceRecognizers.single().intent
            assertThat(intent.getStringExtra(EXTRA_ENABLE_FORMATTING)).isEqualTo("quality")
            assertThat(intent.getBooleanExtra(EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, false))
                .isTrue()
            assertThat(intent.getStringArrayListExtra(EXTRA_BIASING_STRINGS))
                .containsAtLeast("set a timer", "set an alarm", "tap search")
            assertThat(intent.getBooleanExtra(EXTRA_ENABLE_BIASING_DEVICE_CONTEXT, false))
                .isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Config(sdk = [33])
    fun `sdk below 34 omits language switch extras`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = true)
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.NETWORK_ALLOWED,
                languageTag = "hi-IN",
                enableLanguageSwitch = true,
            ),
            factory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val intent = factory.cloudRecognizers.single().intent
            assertThat(intent.hasExtra(EXTRA_ENABLE_LANGUAGE_SWITCH)).isFalse()
            assertThat(intent.hasExtra(EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES)).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Config(sdk = [34])
    fun `sdk 34 Hinglish enables language switch extras`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = true)
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.NETWORK_ALLOWED,
                languageTag = "hi-IN",
                enableLanguageSwitch = true,
            ),
            factory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val intent = factory.cloudRecognizers.single().intent
            assertThat(intent.getStringExtra(EXTRA_ENABLE_LANGUAGE_SWITCH)).isEqualTo("balanced")
            assertThat(intent.getStringArrayListExtra(EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES))
                .containsExactly("en-IN", "hi-IN")
                .inOrder()
            assertThat(intent.getBooleanExtra(EXTRA_ENABLE_LANGUAGE_DETECTION, false)).isTrue()
            assertThat(intent.getStringArrayListExtra(EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES))
                .containsExactly("en-IN", "hi-IN")
                .inOrder()
            assertThat(intent.getBooleanExtra(EXTRA_REQUEST_WORD_CONFIDENCE, false)).isTrue()
            assertThat(intent.getStringArrayListExtra(EXTRA_BIASING_STRINGS))
                .containsAtLeast("panch minute ka timer lagao", "alarm set kar do")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Config(sdk = [34])
    fun `language switch security rejection retries start without language switch extras`() = runTest {
        val factory = FakeRecognizerFactory(
            onDeviceAvailable = true,
            rejectLanguageSwitchOnce = true,
        )
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.NETWORK_ALLOWED,
                languageTag = "hi-IN",
                enableLanguageSwitch = true,
            ),
            factory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val recognizer = factory.cloudRecognizers.single()
            assertThat(recognizer.startedIntents).hasSize(2)
            assertThat(recognizer.startedIntents.first().hasExtra(EXTRA_ENABLE_LANGUAGE_SWITCH)).isTrue()
            assertThat(recognizer.startedIntents.last().hasExtra(EXTRA_ENABLE_LANGUAGE_SWITCH)).isFalse()
            assertThat(recognizer.startedIntents.last().hasExtra(EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES))
                .isFalse()
            assertThat(recognizer.startedIntents.last().hasExtra(EXTRA_ENABLE_LANGUAGE_DETECTION)).isFalse()
            assertThat(recognizer.startedIntents.last().hasExtra(EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES))
                .isFalse()
            assertThat(recognizer.startedIntents.last().hasExtra(EXTRA_REQUEST_WORD_CONFIDENCE)).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `final result parses top three alternatives and first confidence`() = runTest {
        val factory = FakeRecognizerFactory(onDeviceAvailable = true)
        val client = client(
            config = AndroidSttConfig(
                mode = SttMode.NETWORK_ALLOWED,
                languageTag = "en-US",
                enableLanguageSwitch = false,
            ),
            factory = factory,
        )

        client.listen().test {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            factory.cloudRecognizers.single().emitResults(
                listOf("best result", "alt one", "alt two", "alt three"),
                floatArrayOf(0.42f, 0.31f, 0.2f, 0.1f),
            )

            val final = awaitItem() as SttEvent.Final
            assertThat(final.transcript).isEqualTo("best result")
            assertThat(final.alternatives).containsExactly("alt one", "alt two").inOrder()
            assertThat(final.confidence).isWithin(0.0001f).of(0.42f)
            assertThat(final.isOnDevice).isFalse()
            awaitComplete()
        }
    }

    private fun client(
        config: AndroidSttConfig,
        factory: FakeRecognizerFactory,
    ): AndroidSttClient = AndroidSttClient(
        context = mockk<Context>(relaxed = true),
        settingsProvider = { config },
        recognizerFactory = factory,
    )

    private class FakeRecognizerFactory(
        private val onDeviceAvailable: Boolean,
        private val rejectLanguageSwitchOnce: Boolean = false,
    ) : AndroidSttRecognizerFactory {
        val onDeviceRecognizers = mutableListOf<FakeRecognizer>()
        val cloudRecognizers = mutableListOf<FakeRecognizer>()

        override fun isOnDeviceRecognitionAvailable(context: Context): Boolean = onDeviceAvailable

        override fun createOnDeviceSpeechRecognizer(context: Context): AndroidSttRecognizerHandle =
            FakeRecognizer(rejectLanguageSwitchOnce = rejectLanguageSwitchOnce)
                .also { onDeviceRecognizers += it }

        override fun createSpeechRecognizer(context: Context): AndroidSttRecognizerHandle =
            FakeRecognizer(rejectLanguageSwitchOnce = rejectLanguageSwitchOnce)
                .also { cloudRecognizers += it }
    }

    private class FakeRecognizer(
        private var rejectLanguageSwitchOnce: Boolean = false,
    ) : AndroidSttRecognizerHandle {
        lateinit var listener: RecognitionListener
        lateinit var intent: Intent
        val startedIntents = mutableListOf<Intent>()
        var stopCount = 0
        var cancelCount = 0
        var destroyCount = 0

        override fun setRecognitionListener(listener: RecognitionListener) {
            this.listener = listener
        }

        override fun startListening(intent: Intent) {
            val snapshot = Intent(intent)
            startedIntents += snapshot
            if (rejectLanguageSwitchOnce && intent.hasExtra(EXTRA_ENABLE_LANGUAGE_SWITCH)) {
                rejectLanguageSwitchOnce = false
                throw SecurityException("language switch unsupported")
            }
            this.intent = snapshot
        }

        override fun stopListening() {
            stopCount += 1
        }

        override fun cancel() {
            cancelCount += 1
        }

        override fun destroy() {
            destroyCount += 1
        }

        fun emitError(code: Int) {
            listener.onError(code)
        }

        fun emitResults(vararg results: String) {
            emitResults(results.toList(), scores = null)
        }

        fun emitResults(results: List<String>, scores: FloatArray?) {
            val bundle = Bundle().apply {
                putStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION,
                    ArrayList(results),
                )
                scores?.let { putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, it) }
            }
            listener.onResults(bundle)
        }
    }

    private companion object {
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
        const val EXTRA_BIASING_STRINGS = "android.speech.extra.BIASING_STRINGS"
        const val EXTRA_ENABLE_BIASING_DEVICE_CONTEXT =
            "android.speech.extra.ENABLE_BIASING_DEVICE_CONTEXT"
        const val EXTRA_ENABLE_FORMATTING = "android.speech.extra.ENABLE_FORMATTING"
        const val EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION =
            "android.speech.extra.HIDE_PARTIAL_TRAILING_PUNCTUATION"
        const val EXTRA_ENABLE_LANGUAGE_DETECTION = "android.speech.extra.ENABLE_LANGUAGE_DETECTION"
        const val EXTRA_ENABLE_LANGUAGE_SWITCH = "android.speech.extra.ENABLE_LANGUAGE_SWITCH"
        const val EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES =
            "android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES"
        const val EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES =
            "android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES"
        const val EXTRA_REQUEST_WORD_CONFIDENCE = "android.speech.extra.REQUEST_WORD_CONFIDENCE"
    }
}
