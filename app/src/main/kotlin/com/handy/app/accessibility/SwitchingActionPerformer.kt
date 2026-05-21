package com.handy.app.accessibility

import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionExecutionGate
import com.handy.core.action.ActionPerformer
import com.handy.core.action.PerformResult
import com.handy.core.action.ScrollDirection
import com.handy.core.action.TapTarget
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.action.NoopActionPerformer
import com.handy.runtime.storage.DataStoreSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Settings-gated [ActionPerformer] binding. Resolves to the real performer
 * only when both the tap-for-me toggle and the versioned action-disclosure
 * version are present; otherwise it stays at [NoopActionPerformer].
 *
 * The gate check runs per-call — flipping the setting in-process takes
 * effect on the next call.
 */
@Singleton
class SwitchingActionPerformer @Inject constructor(
    private val real: AccessibilityGestureActionPerformer,
    private val noop: NoopActionPerformer,
    private val settings: DataStoreSettings,
    @ApplicationScope appScope: CoroutineScope,
) : ActionPerformer {

    @Volatile
    private var gesturesEnabled: Boolean = false

    init {
        appScope.launch {
            settings.flow.collectLatest { snapshot ->
                gesturesEnabled = ActionExecutionGate.gesturesAllowed(snapshot)
            }
        }
    }

    override val capabilities: Set<ActionCapability>
        get() = if (enabled()) real.capabilities else noop.capabilities

    override suspend fun tap(target: TapTarget): PerformResult =
        if (enabled()) real.tap(target) else noop.tap(target)

    override suspend fun longPress(target: TapTarget): PerformResult =
        if (enabled()) real.longPress(target) else noop.longPress(target)

    override suspend fun scroll(direction: ScrollDirection, target: TapTarget?): PerformResult =
        if (enabled()) real.scroll(direction, target) else noop.scroll(direction, target)

    private fun enabled(): Boolean = gesturesEnabled
}
