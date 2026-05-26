package com.handy.runtime.di

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import com.handy.core.action.ActionAppPolicy
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionPerformer
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmSessionBudget
import com.handy.core.llm.InMemoryLlmSessionBudget
import com.handy.core.llm.ToolRunner
import com.handy.core.model.HandySettings
import com.handy.core.speech.SttClient
import com.handy.core.speech.TtsClient
import com.handy.runtime.action.NoopActionPerformer
import com.handy.runtime.action.DefaultActionPolicyEngine
import com.handy.runtime.intent.AndroidIntentDispatcher
import com.handy.runtime.intent.LaunchableAppIndex
import com.handy.runtime.llm.ClaudeLlmClient
import com.handy.runtime.llm.GeminiCloudLlmClient
import com.handy.runtime.llm.GeminiNanoLocalGenAiClient
import com.handy.runtime.llm.HandyToolRunner
import com.handy.runtime.llm.NetworkDiagnostics
import com.handy.runtime.llm.SwitchingCloudLlmClient
import com.handy.runtime.speech.AndroidSttClient
import com.handy.runtime.speech.AndroidTtsClient
import com.handy.runtime.speech.AudioPlayback
import com.handy.runtime.speech.MediaPlayerAudioPlayback
import com.handy.runtime.speech.SwitchingTtsClient
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.EncryptedKeyStore
import com.handy.runtime.storage.JsonHistoryStore
import com.handy.runtime.storage.KeyStore
import com.handy.runtime.storage.LearnedAllowlistStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Qualifier
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // SSE: unlimited idle
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .apply {
                // Debug/emulator QA often runs behind corporate HTTPS
                // inspection whose CA is trusted by macOS but not by the
                // Android image. Keep this strictly debuggable-only; release
                // builds use the platform trust manager.
                if (context.isDebuggableApp()) {
                    trustLocalQaCertificatesForDebugOnly()
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideKeyStore(@ApplicationContext context: Context): KeyStore =
        EncryptedKeyStore(context)

    @Provides
    @Singleton
    fun provideLlmSessionBudget(): LlmSessionBudget = InMemoryLlmSessionBudget()

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
    fun provideClaudeClient(
        @ApplicationContext context: Context,
        keyStore: KeyStore,
        httpClient: OkHttpClient,
        json: Json,
        sessionBudget: LlmSessionBudget,
    ): ClaudeLlmClient = ClaudeLlmClient(
        keyStore = keyStore,
        httpClient = httpClient,
        json = json,
        networkDiagnostics = NetworkDiagnostics.from(context),
        sessionBudget = sessionBudget,
    )

    @Provides
    @Singleton
    fun provideGeminiCloudClient(
        keyStore: KeyStore,
        httpClient: OkHttpClient,
        json: Json,
        sessionBudget: LlmSessionBudget,
    ): GeminiCloudLlmClient = GeminiCloudLlmClient(
        keyStore = keyStore,
        httpClient = httpClient,
        json = json,
        sessionBudget = sessionBudget,
    )

    @Provides
    @Singleton
    fun provideLocalGenAiClient(
        @ApplicationContext context: Context,
    ): com.handy.core.llm.LocalGenAiClient = GeminiNanoLocalGenAiClient(context)

    /**
     * V2: the primary [LlmClient] seen by the orchestrator is a
     * settings-gated switcher between Claude and Gemini. Scope §5.
     */
    @Provides
    @Singleton
    fun provideLlmClient(impl: SwitchingCloudLlmClient): LlmClient = impl

    @Provides
    @Singleton
    fun provideBrainRouter(
        claude: ClaudeLlmClient,
        gemini: GeminiCloudLlmClient,
        local: com.handy.core.llm.LocalGenAiClient,
    ): com.handy.core.brain.BrainRouter = com.handy.core.brain.BrainRouter(
        claude = claude,
        geminiCloud = gemini,
        localGenAi = local,
    )

    @Provides
    @Singleton
    fun provideSttClient(@ApplicationContext context: Context): SttClient =
        AndroidSttClient(context)

    @Provides
    @Singleton
    fun provideAndroidTtsClient(@ApplicationContext context: Context): AndroidTtsClient =
        AndroidTtsClient(context)

    @Provides
    @Singleton
    fun provideAudioPlayback(impl: MediaPlayerAudioPlayback): AudioPlayback = impl

    @Provides
    @Singleton
    fun provideTtsClient(impl: SwitchingTtsClient): TtsClient = impl

    /**
     * V2: the [ActionPerformer] binding now lives in `:app` as
     * [com.handy.app.accessibility.SwitchingActionPerformer] (settings-gated).
     *
     * We still provide the plain [NoopActionPerformer] here so the
     * switcher can inject it as one of its two arms. V1-style tests
     * that inject `ActionPerformer` directly still work because Hilt
     * resolves the `:app`-level binding last.
     */
    @Provides
    @Singleton
    fun provideNoopActionPerformer(): NoopActionPerformer = NoopActionPerformer()

    @Provides
    @Singleton
    fun provideDefaultActionPolicyEngine(
        settings: DataStoreSettings,
        learnedAllowlistStore: LearnedAllowlistStore,
        @ApplicationScope appScope: CoroutineScope,
    ): DefaultActionPolicyEngine {
        val currentSettings = AtomicReference(HandySettings())
        appScope.launch {
            settings.flow.collectLatest { currentSettings.set(it) }
        }
        return DefaultActionPolicyEngine(
            appPolicy = ActionAppPolicy {
                currentSettings.get().tapForMeUserDenylistedPackages
            },
            settingsProvider = { currentSettings.get() },
            learnedAllowlistProvider = { packageName ->
                learnedAllowlistStore.isLearnedSync(packageName)
            },
        )
    }

    @Provides
    @Singleton
    fun provideActionPolicyEngine(
        impl: DefaultActionPolicyEngine,
    ): ActionPolicyEngine = impl

    @Provides
    @Singleton
    fun provideAuditStore(
        @ApplicationContext context: Context,
        json: Json,
    ): com.handy.core.audit.AuditStore =
        com.handy.runtime.audit.FileAuditStore(context, json)

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

private fun Context.isDebuggableApp(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

@SuppressLint("BadHostnameVerifier", "CustomX509TrustManager", "TrustAllX509TrustManager")
private fun OkHttpClient.Builder.trustLocalQaCertificatesForDebugOnly(): OkHttpClient.Builder {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }
    sslSocketFactory(sslContext.socketFactory, trustManager)
    hostnameVerifier { _, _ -> true }
    return this
}

/**
 * Interface-only bindings. Kept in a separate `abstract class` module so
 * `@Binds` and `@Provides` don't collide in the same module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeBindsModule {

    @Binds
    @Singleton
    abstract fun bindToolRunner(impl: HandyToolRunner): ToolRunner
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
