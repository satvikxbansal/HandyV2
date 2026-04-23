package com.handy.app.di

import com.handy.app.accessibility.HandyAccessibilityService
import com.handy.runtime.accessibility.AccessibilityTreeReader
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.capture.ScreenCapturePipeline
import com.handy.runtime.di.AccessibilityServiceProvider
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
}
