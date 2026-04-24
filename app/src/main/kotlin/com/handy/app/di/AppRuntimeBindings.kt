package com.handy.app.di

import com.handy.app.accessibility.AccessibilityGestureActionPerformer
import com.handy.app.accessibility.HandyAccessibilityService
import com.handy.app.accessibility.SwitchingActionPerformer
import com.handy.app.chat.ChatConfirmationBroker
import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.action.ActionPerformer
import com.handy.core.audit.AuditStore
import com.handy.core.foreground.ForegroundAppMonitor
import com.handy.core.llm.ConfirmationPrompter
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import com.handy.runtime.accessibility.AccessibilityTreeReader
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.capture.ScreenCapturePipeline
import com.handy.runtime.di.AccessibilityServiceProvider
import com.handy.runtime.storage.DataStoreSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds Handy's `AccessibilityService`-backed adapters.
 *
 * These live in `:app` — not `:android-runtime` — because the concrete
 * service class is declared in `:app`'s manifest and the module
 * guardrail forbids `:android-runtime` from owning manifest components.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppRuntimeBindings {

    @Provides
    @Singleton
    fun provideAccessibilityServiceProvider(): AccessibilityServiceProvider =
        AccessibilityServiceProvider { HandyAccessibilityService.instance() }

    @Provides
    @Singleton
    fun provideScreenCapturePipeline(
        provider: AccessibilityServiceProvider,
    ): ScreenCapturePipeline = ScreenCapturePipeline(
        accessibilityService = { provider() },
    )

    @Provides
    @Singleton
    fun provideAccessibilityTreeReader(
        provider: AccessibilityServiceProvider,
    ): AccessibilityTreeReader = AccessibilityTreeReader(service = { provider() })

    @Provides
    @Singleton
    fun provideSemanticPointerResolver(
        provider: AccessibilityServiceProvider,
    ): SemanticPointerResolver = SemanticPointerResolver(service = { provider() })

    @Provides
    @Singleton
    fun provideAccessibilityMarksProvider(
        provider: AccessibilityServiceProvider,
    ): AccessibilityMarksProvider = AccessibilityMarksProvider(service = { provider() })

    /**
     * V2 real tap-for-me performer (cursorbuddy recipe #3, scope §4).
     * Resolved through [SwitchingActionPerformer] below based on the
     * user's `tapForMeEnabled` setting.
     */
    @Provides
    @Singleton
    fun provideAccessibilityGestureActionPerformer(
        accessibilityProvider: AccessibilityServiceProvider,
        resolver: SemanticPointerResolver,
        auditStore: AuditStore,
        foregroundMonitor: HandyForegroundAppMonitor,
    ): AccessibilityGestureActionPerformer = AccessibilityGestureActionPerformer(
        service = accessibilityProvider,
        resolver = resolver,
        auditStore = auditStore,
        foregroundPackageProvider = {
            foregroundMonitor.refreshNow()?.packageName
        },
    )
}

/**
 * Separate interface-binding module so downstream callers depend on the
 * `:core` [ForegroundAppMonitor] abstraction, not the `:app` concrete
 * class.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppRuntimeBindsModule {

    @Binds
    @Singleton
    abstract fun bindForegroundAppMonitor(
        impl: HandyForegroundAppMonitor,
    ): ForegroundAppMonitor

    /**
     * The confirmation rendezvous for destructive `dispatch_action`
     * tool calls. Bound from `:app` because the broker must be reachable
     * from the chat ViewModel (UI side) and from the tool runner
     * (network side) — `:android-runtime` can only see the `:core`
     * [ConfirmationPrompter] interface.
     */
    @Binds
    @Singleton
    abstract fun bindConfirmationPrompter(
        impl: ChatConfirmationBroker,
    ): ConfirmationPrompter

    /**
     * V2 [ActionPerformer] binding — settings-gated switcher between
     * the real `AccessibilityGestureActionPerformer` and the legacy
     * `NoopActionPerformer`. Scope §4 confirmation policy applies
     * upstream (in the tool runner / panel pipeline), not here.
     */
    @Binds
    @Singleton
    abstract fun bindActionPerformer(impl: SwitchingActionPerformer): ActionPerformer
}
