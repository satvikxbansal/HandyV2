package com.handy.app.screen

import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.core.accessibility.AccessibilityConnectionState
import com.handy.core.capture.RequestBudgeter
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.overlay.toScreenTextSnapshot
import com.handy.core.overlay.withStableMarkIds
import com.handy.core.privacy.ScreenRedactor
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.ContextFailureReason
import com.handy.core.screen.ScreenInputRouter
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnScreenContext
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import com.handy.runtime.accessibility.AccessibilityTreeReader
import com.handy.runtime.capture.ScreenCapturePipeline
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Builds the small per-turn screen wrapper shared by overlay, full chat,
 * voice, Quick Settings, and future Assist entry points.
 */
@Singleton
class ScreenContextBuilder @Inject constructor(
    private val accessibilityStateMonitor: AccessibilityStateMonitor,
    private val marksProvider: AccessibilityMarksProvider,
    private val treeReader: AccessibilityTreeReader,
    private val capturePipeline: ScreenCapturePipeline,
) {

    suspend fun build(
        userMessage: String,
        source: TurnSource,
        toolContext: ToolContext,
        panelSnapshot: PanelSnapshot?,
        preferFocusedWindow: Boolean,
    ): TurnScreenContext {
        val requestId = java.util.UUID.randomUUID().toString()
        runCatching { accessibilityStateMonitor.refresh() }
            .onFailure { Timber.w(it, "ScreenContextBuilder: accessibility refresh failed") }
        val accessibilityState = accessibilityStateMonitor.connection.value

        val normalizedPanel = panelSnapshot?.copy(
            marks = panelSnapshot.marks.withStableMarkIds().map {
                ScreenRedactor.redactMark(it)
            },
        )

        val liveMarks = if (normalizedPanel == null &&
            accessibilityState == AccessibilityConnectionState.Connected
        ) {
            withContext(Dispatchers.Main.immediate) {
                runCatching { marksProvider.collect() }
                    .onFailure { Timber.w(it, "ScreenContextBuilder: marks collect failed") }
                    .getOrDefault(emptyList())
            }
        } else {
            emptyList()
        }

        val synthesizedPanel = if (normalizedPanel == null && liveMarks.isNotEmpty()) {
            PanelSnapshot(
                toolContext = toolContext,
                capturedAtEpochMs = System.currentTimeMillis(),
                marks = liveMarks.withStableMarkIds().map { ScreenRedactor.redactMark(it) },
            )
        } else {
            null
        }
        val effectivePanel = normalizedPanel ?: synthesizedPanel

        val panelText = effectivePanel?.toScreenTextSnapshot()
        val liveText = if (panelText == null &&
            accessibilityState == AccessibilityConnectionState.Connected
        ) {
            readLiveScreenText()
        } else {
            null
        }

        val screenText = (panelText ?: liveText)
            ?.let(ScreenRedactor::redactSnapshot)
            ?.let { snapshot ->
                // Mark ids are actionable only when we also hold the
                // matching compact marks for resolver fallback. Full-tree
                // fallback text is still useful, but its ids would be
                // unresolvable if the marks provider returned nothing.
                if (effectivePanel == null) snapshot.withoutMarkIds() else snapshot
            }
        val marksForBudget = effectivePanel?.marks ?: emptyList()

        val routerMode = ScreenInputRouter.choose(
            userMessage = userMessage,
            treeQualityScore = screenText?.qualityScore() ?: 0,
            screenTextPresent = screenText != null,
        )
        val rawCapture = if (
            accessibilityState == AccessibilityConnectionState.Connected &&
            routerMode != ScreenInputRouter.Mode.TextOnly
        ) {
            withContext(Dispatchers.Main.immediate) {
                runCatching { capturePipeline.capture() }
                    .onFailure { Timber.w(it, "ScreenContextBuilder: capture failed") }
                    .getOrElse { CaptureResult.Failed(it.message ?: it::class.simpleName.orEmpty()) }
            }
        } else {
            null
        }

        val budget = RequestBudgeter.budget(
            userMessage = userMessage,
            screenText = screenText,
            marks = marksForBudget,
            capture = rawCapture,
            preferFocusedWindow = preferFocusedWindow,
        )
        val failure = classifyFailure(
            accessibilityState = accessibilityState,
            screenText = budget.screenText,
            capture = rawCapture,
        )

        Timber.d(
            "ScreenContextBuilder: request=%s source=%s a11y=%s mode=%s capture=%s failure=%s text=%s marks=%d",
            requestId,
            source,
            accessibilityState,
            budget.captureMode,
            rawCapture?.javaClass?.simpleName ?: "none",
            failure,
            budget.screenText != null,
            marksForBudget.size,
        )

        return TurnScreenContext(
            requestId = requestId,
            source = source,
            toolContext = toolContext,
            panelSnapshot = effectivePanel,
            screenText = budget.screenText,
            capture = budget.capture,
            captureMode = budget.captureMode,
            accessibilityState = accessibilityState,
            failureReason = failure,
        )
    }

    private suspend fun readLiveScreenText(): ScreenTextSnapshot? =
        withContext(Dispatchers.Main.immediate) {
            runCatching { treeReader.read() }
                .onFailure { Timber.w(it, "ScreenContextBuilder: tree read failed") }
                .getOrNull()
        }

    private fun classifyFailure(
        accessibilityState: AccessibilityConnectionState,
        screenText: ScreenTextSnapshot?,
        capture: CaptureResult?,
    ): ContextFailureReason? {
        if (capture is CaptureResult.SecureWindow) return ContextFailureReason.SECURE_WINDOW
        if (capture is CaptureResult.NotPermitted) return ContextFailureReason.CAPTURE_NOT_PERMITTED
        if (capture is CaptureResult.Unsupported) return ContextFailureReason.CAPTURE_UNSUPPORTED
        if (capture is CaptureResult.Failed) return ContextFailureReason.CAPTURE_FAILED
        if (accessibilityState != AccessibilityConnectionState.Connected && screenText == null) {
            return ContextFailureReason.ACCESSIBILITY_NOT_CONNECTED
        }
        if (screenText == null && capture !is CaptureResult.Image) {
            return ContextFailureReason.NO_VISIBLE_CONTEXT
        }
        return null
    }

    private fun ScreenTextSnapshot.withoutMarkIds(): ScreenTextSnapshot =
        copy(root = root.withoutMarkIds())

    private fun UiNode.withoutMarkIds(): UiNode =
        copy(
            markId = null,
            children = children.map { it.withoutMarkIds() },
        )
}
