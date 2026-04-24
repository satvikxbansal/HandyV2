package com.handy.app.accessibility

import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionPerformer
import com.handy.core.action.PerformResult
import com.handy.core.action.ScrollDirection
import com.handy.core.action.TapTarget
import com.handy.runtime.action.NoopActionPerformer
import com.handy.runtime.storage.DataStoreSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Settings-gated [ActionPerformer] binding. Resolves to the real
 * [AccessibilityGestureActionPerformer] when
 * [com.handy.core.model.HandySettings.tapForMeEnabled] is `true`;
 * otherwise falls back to [NoopActionPerformer] — V1 behaviour.
 *
 * `runBlocking` is acceptable here because:
 *  - DataStore's `first()` is cheap when the value is already cached,
 *  - ActionPerformer calls are already off the main thread (gesture
 *    dispatch is a short async hop).
 *
 * The gate check runs per-call — flipping the setting in-process takes
 * effect on the next call.
 */
@Singleton
class SwitchingActionPerformer @Inject constructor(
    private val real: AccessibilityGestureActionPerformer,
    private val noop: NoopActionPerformer,
    private val settings: DataStoreSettings,
) : ActionPerformer {

    override val capabilities: Set<ActionCapability>
        get() = if (enabled()) real.capabilities else noop.capabilities

    override suspend fun tap(target: TapTarget): PerformResult =
        if (enabled()) real.tap(target) else noop.tap(target)

    override suspend fun longPress(target: TapTarget): PerformResult =
        if (enabled()) real.longPress(target) else noop.longPress(target)

    override suspend fun scroll(direction: ScrollDirection, target: TapTarget?): PerformResult =
        if (enabled()) real.scroll(direction, target) else noop.scroll(direction, target)

    private fun enabled(): Boolean =
        runCatching { runBlocking { settings.current().tapForMeEnabled } }
            .getOrElse { false }
}
