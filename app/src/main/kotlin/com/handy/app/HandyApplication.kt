package com.handy.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Handy's [Application] subclass.
 *
 * Phase 0 wires Hilt only. Phase 3 will own:
 *  - StrictMode install under `BuildConfig.DEBUG` (Guardrails: Concurrency).
 *  - `NotificationChannel` creation for the foreground-service notifications
 *    (OS-1: channels are created at startup, never lazily).
 *  - Timber `DebugTree` plant + a production tree in release builds
 *    (Guardrails: Forbidden — no `println`).
 */
@HiltAndroidApp
class HandyApplication : Application()
