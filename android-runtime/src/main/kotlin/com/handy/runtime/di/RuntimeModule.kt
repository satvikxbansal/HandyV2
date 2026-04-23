package com.handy.runtime.di

import android.content.Context
import com.handy.core.action.ActionPerformer
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmClient
import com.handy.core.speech.SttClient
import com.handy.core.speech.TtsClient
import com.handy.runtime.action.NoopActionPerformer
import com.handy.runtime.intent.AndroidIntentDispatcher
import com.handy.runtime.intent.LaunchableAppIndex
import com.handy.runtime.llm.ClaudeLlmClient
import com.handy.runtime.speech.AndroidSttClient
import com.handy.runtime.speech.AndroidTtsClient
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.EncryptedKeyStore
import com.handy.runtime.storage.JsonHistoryStore
import com.handy.runtime.storage.KeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Wires every `:core` interface to its `:android-runtime` implementation.
 *
 * `:app`'s Hilt graph inherits these bindings via `@HiltAndroidApp`. The
 * `AccessibilityService`-backed seams (tree reader + pointer resolver)
 * and the `MediaProjectionCaptureSource` are NOT bound here — their
 * lifecycles belong to `:app` so the module that owns them can inject
 * the live service instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object RuntimeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // SSE: unlimited idle
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideKeyStore(@ApplicationContext context: Context): KeyStore =
        EncryptedKeyStore(context)

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context): DataStoreSettings =
        DataStoreSettings(context)

    @Provides
    @Singleton
    fun provideHistoryStore(
        @ApplicationContext context: Context,
        json: Json,
    ): ChatHistoryStore = JsonHistoryStore(context = context, json = json)

    @Provides
    @Singleton
    fun provideLlmClient(
        keyStore: KeyStore,
        httpClient: OkHttpClient,
        json: Json,
    ): LlmClient = ClaudeLlmClient(
        keyStore = keyStore,
        httpClient = httpClient,
        json = json,
    )

    @Provides
    @Singleton
    fun provideSttClient(@ApplicationContext context: Context): SttClient =
        AndroidSttClient(context)

    @Provides
    @Singleton
    fun provideTtsClient(@ApplicationContext context: Context): TtsClient =
        AndroidTtsClient(context)

    @Provides
    @Singleton
    fun provideActionPerformer(): ActionPerformer = NoopActionPerformer()

    @Provides
    @Singleton
    fun provideLaunchableAppIndex(
        @ApplicationContext context: Context,
        @ApplicationScope appScope: CoroutineScope,
    ): LaunchableAppIndex = LaunchableAppIndex(context, appScope)

    @Provides
    @Singleton
    fun provideIntentDispatcher(
        @ApplicationContext context: Context,
        launchableApps: LaunchableAppIndex,
    ): AndroidIntentDispatcher = AndroidIntentDispatcher(context, launchableApps)

    // NB: `ScreenCapturePipeline`, `AccessibilityTreeReader`, and
    // `SemanticPointerResolver` need the live `AccessibilityService`
    // instance and therefore bind in `:app` (`AppRuntimeBindings`) rather
    // than here. `:android-runtime` stays agnostic of the `:app`-owned
    // service class.
}

/**
 * Lambda-like indirection that lets `:android-runtime` adapters reach the
 * live `AccessibilityService` without depending on `:app`. `:app`
 * provides a concrete implementation that returns
 * `HandyAccessibilityService.instance()`.
 */
fun interface AccessibilityServiceProvider {
    operator fun invoke(): android.accessibilityservice.AccessibilityService?
}
