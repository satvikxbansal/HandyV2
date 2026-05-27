package com.handy.app.screen

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.core.accessibility.AccessibilityConnectionState
import com.handy.core.capture.RequestBudgeter
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.overlay.toScreenTextSnapshot
import com.handy.core.overlay.withStableMarkIds
import com.handy.core.privacy.ScreenRedactor
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.ContextFailureReason
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.InsetsSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.PrivacyFlags
import com.handy.core.screen.ScreenInputRouter
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import com.handy.runtime.accessibility.AccessibilityTreeReader
import com.handy.runtime.capture.ScreenCapturePipeline
import com.handy.runtime.di.AccessibilityServiceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Builds the small per-turn screen wrapper shared by overlay, full chat,
 * voice, Quick Settings, and future Assist entry points.
 */
@Singleton
class ScreenContextBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessibilityServiceProvider: AccessibilityServiceProvider,
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
        requestIdOverride: String? = null,
    ): GroundingSnapshot {
        val requestId = requestIdOverride?.safeRequestId()
            ?: java.util.UUID.randomUUID().toString()
        runCatching { accessibilityStateMonitor.refresh() }
            .onFailure { Timber.w(it, "ScreenContextBuilder: accessibility refresh failed") }
        val accessibilityState = accessibilityStateMonitor.connection.value
        val capturedAtMs = System.currentTimeMillis()
        val activeWindow = if (accessibilityState == AccessibilityConnectionState.Connected) {
            withContext(Dispatchers.Main.immediate) { readActiveWindowSnapshot() }
        } else {
            ActiveWindowSnapshot.EMPTY
        }
        val deviceEnvironment = readDeviceEnvironment()
        val windowBounds = activeWindow.windowBounds.takeUnless { it == IntRect.ZERO }
            ?: deviceEnvironment.windowBounds

        val normalizedPanel = panelSnapshot?.copy(
            marks = panelSnapshot.marks.withStableMarkIds().map {
                ScreenRedactor.redactMark(it)
            },
        )

        val liveMarks = if (normalizedPanel == null &&
            accessibilityState == AccessibilityConnectionState.Connected
        ) {
            collectMarksWithinBudget()
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
                captureWithinBudget(activeWindowIdHint = activeWindow.windowId)
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
        val privacyFlags = PrivacyFlags(
            safeInsetsUnreliable = deviceEnvironment.safeInsets.unreliable,
            secureWindow = rawCapture is CaptureResult.SecureWindow,
            captureNotPermitted = rawCapture is CaptureResult.NotPermitted,
            captureUnsupported = rawCapture is CaptureResult.Unsupported,
            captureFailed = rawCapture is CaptureResult.Failed,
            containsPasswordFields = marksForBudget.any { it.isPassword },
        )
        val rootBoundsHash = GroundingSnapshot.rootBoundsHash(
            windowBounds = windowBounds,
            imeVisible = deviceEnvironment.imeVisible,
            imeBounds = deviceEnvironment.imeBounds,
            topmostWindowId = activeWindow.topmostWindowId,
        )
        val treeHash = GroundingSnapshot.treeHash(
            marks = marksForBudget,
            screenText = budget.screenText,
        )

        Timber.d(
            "ScreenContextBuilder: request=%s source=%s a11y=%s windowId=%s displayId=%s captureWindowId=%s mode=%s capture=%s failure=%s text=%s marks=%d rootHash=%s treeHash=%s",
            requestId,
            source,
            accessibilityState,
            activeWindow.windowId,
            activeWindow.displayId,
            activeWindow.windowId,
            budget.captureMode,
            rawCapture?.javaClass?.simpleName ?: "none",
            failure,
            budget.screenText != null,
            marksForBudget.size,
            rootBoundsHash,
            treeHash,
        )

        return GroundingSnapshot(
            requestId = requestId,
            source = source,
            toolContext = toolContext,
            panelSnapshot = effectivePanel,
            screenText = budget.screenText,
            capture = rawCapture,
            captureMode = budget.captureMode,
            accessibilityState = accessibilityState,
            failureReason = failure,
            windowId = activeWindow.windowId,
            displayId = activeWindow.displayId,
            orientation = deviceEnvironment.orientation,
            windowBounds = windowBounds,
            safeInsets = deviceEnvironment.safeInsets,
            imeVisible = deviceEnvironment.imeVisible,
            imeBounds = deviceEnvironment.imeBounds,
            densityDpi = deviceEnvironment.densityDpi,
            locale = deviceEnvironment.locale,
            uiMode = deviceEnvironment.uiMode,
            rootBoundsHash = rootBoundsHash,
            treeHash = treeHash,
            capturedAtMs = capturedAtMs,
            privacyFlags = privacyFlags,
        )
    }

    private suspend fun collectMarksWithinBudget(): List<AccessibilityMark> =
        budgetedStep(
            step = "marks",
            onTimeout = { emptyList() },
            onFailure = { t ->
                Timber.w(t, "ScreenContextBuilder: marks collect failed")
                emptyList()
            },
        ) {
            withContext(Dispatchers.Main.immediate) { marksProvider.collect() }
        }

    private suspend fun readLiveScreenText(): ScreenTextSnapshot? =
        budgetedStep(
            step = "tree",
            onTimeout = { null },
            onFailure = { t ->
                Timber.w(t, "ScreenContextBuilder: tree read failed")
                null
            },
        ) {
            withContext(Dispatchers.Main.immediate) { treeReader.read() }
        }

    private suspend fun captureWithinBudget(activeWindowIdHint: Int?): CaptureResult =
        budgetedStep(
            step = "capture",
            onTimeout = { CaptureResult.Failed("budget-exceeded") },
            onFailure = { t ->
                Timber.w(t, "ScreenContextBuilder: capture failed")
                CaptureResult.Failed(t.message ?: t::class.simpleName.orEmpty())
            },
        ) {
            capturePipeline.capture(activeWindowIdHint = activeWindowIdHint)
        }

    private suspend fun <T> budgetedStep(
        step: String,
        onTimeout: () -> T,
        onFailure: (Throwable) -> T,
        block: suspend () -> T,
    ): T {
        val result = withTimeoutOrNull(STEP_BUDGET_MS) {
            try {
                Result.success(block())
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Result.failure(t)
            }
        } ?: run {
            Timber.w("ScreenContextBuilder: %s exceeded %dms budget", step, STEP_BUDGET_MS)
            return onTimeout()
        }
        return result.getOrElse(onFailure)
    }

    private fun readActiveWindowSnapshot(): ActiveWindowSnapshot {
        val svc = accessibilityServiceProvider() ?: return ActiveWindowSnapshot.EMPTY
        val root = runCatching { svc.rootInActiveWindow }.getOrNull()
            ?: return ActiveWindowSnapshot.EMPTY
        return try {
            val rootBounds = Rect().also { root.getBoundsInScreen(it) }
            val window = runCatching { root.window }.getOrNull()
            val windowBounds = Rect()
            val hasWindowBounds = runCatching {
                window?.getBoundsInScreen(windowBounds)
                windowBounds.width() > 0 && windowBounds.height() > 0
            }.getOrDefault(false)
            ActiveWindowSnapshot(
                windowId = root.windowId,
                displayId = displayIdFor(window),
                windowBounds = (if (hasWindowBounds) windowBounds else rootBounds).toIntRect(),
                topmostWindowId = topmostWindowId(svc),
            )
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun displayIdFor(window: android.view.accessibility.AccessibilityWindowInfo?): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { window?.displayId }.getOrNull() ?: Display.DEFAULT_DISPLAY
        } else {
            Display.DEFAULT_DISPLAY
        }
    }

    private fun topmostWindowId(service: AccessibilityService): Int? {
        return runCatching {
            service.windows
                ?.maxByOrNull { window -> window.layer }
                ?.id
        }.getOrNull()
    }

    private fun readDeviceEnvironment(): DeviceEnvironment {
        val resources = runCatching { context.resources }.getOrNull()
        val configuration = resources?.configuration
        val displayMetrics = resources?.displayMetrics
        val windowGeometry = readWindowGeometry()
        return DeviceEnvironment(
            orientation = orientationName(configuration?.orientation),
            windowBounds = windowGeometry.windowBounds,
            safeInsets = windowGeometry.safeInsets,
            imeVisible = windowGeometry.imeVisible,
            imeBounds = windowGeometry.imeBounds,
            densityDpi = displayMetrics?.densityDpi,
            locale = localeTag(configuration),
            uiMode = uiModeName(configuration?.uiMode),
        )
    }

    private fun readWindowGeometry(): WindowGeometry {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val modern = runCatching {
                val manager = context.getSystemService(WindowManager::class.java)
                    ?: return@runCatching null
                val metrics = manager.currentWindowMetrics
                val bounds = metrics.bounds.toIntRect()
                val types = WindowInsets.Type.systemBars() or
                    WindowInsets.Type.displayCutout() or
                    WindowInsets.Type.ime()
                val safe = metrics.windowInsets.getInsets(types)
                val imeInsets = metrics.windowInsets.getInsets(WindowInsets.Type.ime())
                val imeVisible = metrics.windowInsets.isVisible(WindowInsets.Type.ime())
                WindowGeometry(
                    windowBounds = bounds,
                    safeInsets = InsetsSnapshot(
                        left = safe.left,
                        top = safe.top,
                        right = safe.right,
                        bottom = safe.bottom,
                        unreliable = false,
                    ),
                    imeVisible = imeVisible,
                    imeBounds = if (imeVisible && imeInsets.bottom > 0) {
                        IntRect(
                            left = bounds.left,
                            top = (bounds.bottom - imeInsets.bottom).coerceAtLeast(bounds.top),
                            right = bounds.right,
                            bottom = bounds.bottom,
                        )
                    } else {
                        IntRect.ZERO
                    },
                )
            }.getOrNull()
            if (modern != null) return modern
        }

        val resources = runCatching { context.resources }.getOrNull()
        val metrics = resources?.displayMetrics
        val width = metrics?.widthPixels?.takeIf { it > 0 } ?: 0
        val height = metrics?.heightPixels?.takeIf { it > 0 } ?: 0
        return WindowGeometry(
            windowBounds = IntRect(0, 0, width, height),
            safeInsets = InsetsSnapshot(
                top = systemDimen("status_bar_height"),
                bottom = systemDimen("navigation_bar_height"),
                unreliable = true,
            ),
            imeVisible = false,
            imeBounds = IntRect.ZERO,
        )
    }

    private fun systemDimen(name: String): Int {
        val resources = runCatching { context.resources }.getOrNull() ?: return 0
        val id = resources.getIdentifier(name, "dimen", "android")
        if (id <= 0) return 0
        return runCatching { resources.getDimensionPixelSize(id) }.getOrDefault(0)
    }

    private fun orientationName(orientation: Int?): String = when (orientation) {
        Configuration.ORIENTATION_PORTRAIT -> "portrait"
        Configuration.ORIENTATION_LANDSCAPE -> "landscape"
        Configuration.ORIENTATION_SQUARE -> "square"
        else -> GroundingSnapshot.ORIENTATION_UNKNOWN
    }

    private fun uiModeName(uiMode: Int?): String {
        if (uiMode == null) return GroundingSnapshot.UI_MODE_UNKNOWN
        val type = when (uiMode and Configuration.UI_MODE_TYPE_MASK) {
            Configuration.UI_MODE_TYPE_NORMAL -> "normal"
            Configuration.UI_MODE_TYPE_DESK -> "desk"
            Configuration.UI_MODE_TYPE_CAR -> "car"
            Configuration.UI_MODE_TYPE_TELEVISION -> "television"
            Configuration.UI_MODE_TYPE_APPLIANCE -> "appliance"
            Configuration.UI_MODE_TYPE_WATCH -> "watch"
            Configuration.UI_MODE_TYPE_VR_HEADSET -> "vr"
            else -> "unknown"
        }
        val night = when (uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> "night"
            Configuration.UI_MODE_NIGHT_NO -> "notnight"
            else -> "unknown"
        }
        return "$type/$night"
    }

    private fun localeTag(configuration: Configuration?): String? {
        if (configuration == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            @Suppress("DEPRECATION")
            configuration.locale?.toLanguageTag()
        }
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

    private fun Rect.toIntRect(): IntRect = IntRect(left, top, right, bottom)

    private data class ActiveWindowSnapshot(
        val windowId: Int?,
        val displayId: Int?,
        val windowBounds: IntRect,
        val topmostWindowId: Int?,
    ) {
        companion object {
            val EMPTY = ActiveWindowSnapshot(
                windowId = null,
                displayId = null,
                windowBounds = IntRect.ZERO,
                topmostWindowId = null,
            )
        }
    }

    private data class DeviceEnvironment(
        val orientation: String,
        val windowBounds: IntRect,
        val safeInsets: InsetsSnapshot,
        val imeVisible: Boolean,
        val imeBounds: IntRect,
        val densityDpi: Int?,
        val locale: String?,
        val uiMode: String,
    )

    private data class WindowGeometry(
        val windowBounds: IntRect,
        val safeInsets: InsetsSnapshot,
        val imeVisible: Boolean,
        val imeBounds: IntRect,
    )

    private companion object {
        const val STEP_BUDGET_MS = 200L
    }
}

private fun String.safeRequestId(): String? =
    trim()
        .takeIf { it.isNotBlank() }
        ?.replace(Regex("""[^A-Za-z0-9_.:-]"""), "_")
        ?.take(MAX_REQUEST_ID_CHARS)

private const val MAX_REQUEST_ID_CHARS = 96
