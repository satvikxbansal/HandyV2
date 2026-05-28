package com.handy.app.overlay

import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.OverlayMode
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Keeps the overlay panel's idle context aligned with the app behind it.
 *
 * The active turn snapshot remains frozen: foreground changes are queued while
 * Handy is listening, streaming, pointing, confirming, or acting, then applied
 * once the presenter returns to a quiet panel state.
 */
class PanelContextRefresher @Inject constructor(
    private val presenter: OverlayPresenter,
    private val foregroundAppMonitor: HandyForegroundAppMonitor,
    private val marksProvider: AccessibilityMarksProvider,
) {
    private val mutex = Mutex()
    private var job: Job? = null
    private var pendingTarget: PanelContextTarget? = null
    private var refreshingTarget: PanelContextTarget? = null

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            coroutineScope {
                launch {
                    presenter.state
                        .map { it.mode == OverlayMode.ChatPanel }
                        .distinctUntilChanged()
                        .flatMapLatest { visible ->
                            if (visible) {
                                foregroundAppMonitor.panelContextFlow.debounce(FOREGROUND_SETTLE_MS)
                            } else {
                                emptyFlow()
                            }
                        }
                        .collectLatest { foreground ->
                            enqueue(PanelContextTarget.from(foreground))
                        }
                }
                launch {
                    presenter.state.collect {
                        applyPendingIfPossible()
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        pendingTarget = null
        refreshingTarget = null
        presenter.clearPanelContextRefresh()
    }

    private suspend fun enqueue(target: PanelContextTarget) {
        mutex.withLock {
            pendingTarget = target
        }
        applyPendingIfPossible()
    }

    private suspend fun applyPendingIfPossible() {
        val target = mutex.withLock {
            val candidate = pendingTarget
            if (candidate == null || refreshingTarget != null) {
                null
            } else {
                refreshingTarget = candidate
                candidate
            }
        } ?: return

        when (beginRefresh(target)) {
            PanelContextRefreshStartResult.Ready -> Unit
            PanelContextRefreshStartResult.Deferred -> {
                mutex.withLock {
                    if (refreshingTarget.matchesContext(target)) {
                        refreshingTarget = null
                    }
                }
                return
            }
            PanelContextRefreshStartResult.Ignored -> {
                mutex.withLock {
                    if (pendingTarget.matchesContext(target)) {
                        pendingTarget = null
                    }
                    if (refreshingTarget.matchesContext(target)) {
                        refreshingTarget = null
                    }
                }
                return
            }
        }

        val marks = when (target) {
            is PanelContextTarget.App -> readMarksFor(target.foreground)
            PanelContextTarget.NoForegroundApp -> emptyList()
        }
        val latest = mutex.withLock { pendingTarget }
        if (!latest.matchesContext(target)) {
            presenter.clearPanelContextRefresh()
            mutex.withLock {
                if (refreshingTarget.matchesContext(target)) {
                    refreshingTarget = null
                }
            }
            applyPendingIfPossible()
            return
        }

        val applied = when (target) {
            is PanelContextTarget.App -> presenter.applyPanelContextRefresh(
                foreground = target.foreground,
                marks = marks,
            )
            PanelContextTarget.NoForegroundApp -> presenter.applyPanelContextClear()
        }
        mutex.withLock {
            if (refreshingTarget.matchesContext(target)) {
                refreshingTarget = null
            }
            if (applied && pendingTarget.matchesContext(target)) {
                pendingTarget = null
            }
        }
    }

    private fun beginRefresh(target: PanelContextTarget): PanelContextRefreshStartResult =
        when (target) {
            is PanelContextTarget.App -> presenter.beginPanelContextRefresh(target.foreground)
            PanelContextTarget.NoForegroundApp -> presenter.beginPanelContextClear()
        }

    private suspend fun readMarksFor(
        foreground: ForegroundAppSnapshot,
    ): List<AccessibilityMark> =
        withContext(Dispatchers.Main.immediate) {
            runCatching { marksProvider.collectForPackage(foreground.packageName) }
                .onFailure {
                    Timber.w(
                        it,
                        "PanelContextRefresher: targeted marks failed for %s",
                        foreground.packageName,
                    )
                }
                .getOrElse { emptyList() }
        }

    private fun PanelContextTarget.sameContextAs(other: PanelContextTarget): Boolean =
        when {
            this is PanelContextTarget.NoForegroundApp &&
                other is PanelContextTarget.NoForegroundApp -> true
            this is PanelContextTarget.App && other is PanelContextTarget.App ->
                foreground.packageName == other.foreground.packageName &&
                    foreground.umbrellaSiteLabel == other.foreground.umbrellaSiteLabel
            else -> false
        }

    private fun PanelContextTarget?.matchesContext(other: PanelContextTarget): Boolean =
        this != null && sameContextAs(other)

    private sealed class PanelContextTarget {
        data class App(val foreground: ForegroundAppSnapshot) : PanelContextTarget()
        object NoForegroundApp : PanelContextTarget()

        companion object {
            fun from(foreground: ForegroundAppSnapshot?): PanelContextTarget =
                foreground?.let(::App) ?: NoForegroundApp
        }
    }

    private companion object {
        const val FOREGROUND_SETTLE_MS: Long = 280L
    }
}
