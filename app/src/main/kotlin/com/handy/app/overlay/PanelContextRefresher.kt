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
    private var pendingForeground: ForegroundAppSnapshot? = null
    private var refreshingForeground: ForegroundAppSnapshot? = null

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
                            println("PCR visible=$visible")
                            if (visible) {
                                foregroundAppMonitor.flow.debounce(FOREGROUND_SETTLE_MS)
                            } else {
                                emptyFlow()
                            }
                        }
                        .collectLatest { foreground ->
                            println("PCR collect ${foreground.appLabel}")
                            enqueue(foreground)
                        }
                }
                launch {
                    presenter.state.collect {
                        println("PCR state buddy=${it.buddyState} streaming=${it.panel.isStreaming}")
                        applyPendingIfPossible()
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        pendingForeground = null
        refreshingForeground = null
        presenter.clearPanelContextRefresh()
    }

    private suspend fun enqueue(foreground: ForegroundAppSnapshot) {
        println("PCR enqueue ${foreground.appLabel}")
        mutex.withLock {
            pendingForeground = foreground
        }
        applyPendingIfPossible()
    }

    private suspend fun applyPendingIfPossible() {
        val foreground = mutex.withLock {
            val candidate = pendingForeground
            if (candidate == null || refreshingForeground != null) {
                null
            } else {
                refreshingForeground = candidate
                candidate
            }
        } ?: return

        when (presenter.beginPanelContextRefresh(foreground)) {
            PanelContextRefreshStartResult.Ready -> {
                println("PCR ready ${foreground.appLabel}")
                Unit
            }
            PanelContextRefreshStartResult.Deferred -> {
                println("PCR deferred ${foreground.appLabel}")
                mutex.withLock {
                    if (refreshingForeground.matchesContext(foreground)) {
                        refreshingForeground = null
                    }
                }
                return
            }
            PanelContextRefreshStartResult.Ignored -> {
                mutex.withLock {
                    if (pendingForeground.matchesContext(foreground)) {
                        pendingForeground = null
                    }
                    if (refreshingForeground.matchesContext(foreground)) {
                        refreshingForeground = null
                    }
                }
                return
            }
        }

        val marks = readMarksFor(foreground)
        val latest = mutex.withLock { pendingForeground }
        if (!latest.matchesContext(foreground)) {
            presenter.clearPanelContextRefresh()
            mutex.withLock {
                if (refreshingForeground.matchesContext(foreground)) {
                    refreshingForeground = null
                }
            }
            applyPendingIfPossible()
            return
        }

        val applied = presenter.applyPanelContextRefresh(
            foreground = foreground,
            marks = marks,
        )
        mutex.withLock {
            if (refreshingForeground.matchesContext(foreground)) {
                refreshingForeground = null
            }
            if (applied && pendingForeground.matchesContext(foreground)) {
                pendingForeground = null
            }
        }
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

    private fun ForegroundAppSnapshot.sameContextAs(other: ForegroundAppSnapshot): Boolean =
        packageName == other.packageName &&
            umbrellaSiteLabel == other.umbrellaSiteLabel

    private fun ForegroundAppSnapshot?.matchesContext(other: ForegroundAppSnapshot): Boolean =
        this != null && sameContextAs(other)

    private companion object {
        const val FOREGROUND_SETTLE_MS: Long = 280L
    }
}
