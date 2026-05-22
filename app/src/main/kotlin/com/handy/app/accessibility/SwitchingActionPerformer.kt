package com.handy.app.accessibility

import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionExecutionGate
import com.handy.core.action.ActionPerformer
import com.handy.core.action.PerformResult
import com.handy.core.action.ScrollDirection
import com.handy.core.action.TapTarget
import com.handy.core.model.HandySettings
import com.handy.runtime.action.NoopActionPerformer
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Settings-gated [ActionPerformer] binding. Resolves to the real performer
 * only when both the tap-for-me toggle and the versioned action-disclosure
 * version are present; otherwise it stays at [NoopActionPerformer].
 *
 * The gate check is refreshed on settings changes and on a periodic clock
 * tick so time-based mutes expire without requiring another settings write.
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

    @Volatile
    private var latestSettings: HandySettings? = null

    init {
        appScope.launch {
            settings.flow.collectLatest { snapshot ->
                latestSettings = snapshot
                gesturesEnabled = ActionExecutionGate.gesturesAllowed(
                    snapshot,
                    nowEpochMs = System.currentTimeMillis(),
                )
            }
        }
        appScope.launch {
            tickerFlow().collect {
                latestSettings?.let { snapshot ->
                    gesturesEnabled = ActionExecutionGate.gesturesAllowed(
                        snapshot,
                        nowEpochMs = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    override val capabilities: Set<ActionCapability>
        get() = if (gesturesEnabled) real.capabilities else noop.capabilities

    override suspend fun tap(target: TapTarget): PerformResult =
        if (enabled()) real.tap(target) else noop.tap(target)

    override suspend fun longPress(target: TapTarget): PerformResult =
        if (enabled()) real.longPress(target) else noop.longPress(target)

    override suspend fun scroll(direction: ScrollDirection, target: TapTarget?): PerformResult =
        if (enabled()) real.scroll(direction, target) else noop.scroll(direction, target)

    override suspend fun typeText(target: TapTarget, text: String): PerformResult =
        if (enabled()) real.typeText(target, text) else noop.typeText(target, text)

    private fun enabled(): Boolean = gesturesEnabled

    private fun tickerFlow() = flow {
        while (true) {
            delay(GESTURE_GATE_TICK_MS)
            emit(Unit)
        }
    }

    private companion object {
        const val GESTURE_GATE_TICK_MS = 60_000L
    }
}
