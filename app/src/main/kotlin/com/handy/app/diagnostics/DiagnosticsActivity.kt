package com.handy.app.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.app.clipboard.ClipboardAssist
import com.handy.app.overlay.OverlayPresenter
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType
import com.handy.core.accessibility.AccessibilityConnectionState
import com.handy.core.action.ActionExecutionGate
import com.handy.core.action.PolicyDecision
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditStore
import com.handy.core.audit.TimelineEvent
import com.handy.core.audit.TimelineExport
import com.handy.core.llm.LocalAvailability
import com.handy.core.llm.LocalGenAiClient
import com.handy.core.model.HandySettings
import com.handy.core.privacy.ScreenRedactor
import com.handy.runtime.action.DefaultActionPolicyEngine
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * DiagnosticsActivity — scope §10.
 *
 * Read-only surface. Lists runtime state so the user (and Play review)
 * can see exactly what Handy sees. No toggles live here; those belong
 * in Settings.
 */
@AndroidEntryPoint
class DiagnosticsActivity : ComponentActivity() {

    private val viewModel: DiagnosticsViewModel by viewModels()
    private val exportTimelineLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                runCatching {
                    val body = viewModel.timelineExportJson()
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(body.toByteArray(Charsets.UTF_8))
                        }
                    }
                }.onFailure { Timber.w(it, "DiagnosticsActivity: timeline export failed") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HandyTheme(darkTheme = true) {
                val state by viewModel.state.collectAsState()
                DiagnosticsScreen(
                    state = state,
                    onReviewActions = { AuditReviewActivity.open(this) },
                    onExportTimeline = {
                        exportTimelineLauncher.launch("handy-timeline-${System.currentTimeMillis()}.json")
                    },
                    onClearAll = viewModel::clearAll,
                )
            }
        }
    }

    companion object {
        fun open(context: Context) {
            val intent = Intent(context, DiagnosticsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val settings: DataStoreSettings,
    private val a11yMonitor: AccessibilityStateMonitor,
    private val auditStore: AuditStore,
    private val localGenAi: LocalGenAiClient,
    private val clipboardAssist: ClipboardAssist,
    private val overlayPresenter: OverlayPresenter,
    private val policyEngine: DefaultActionPolicyEngine,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUi())
    val state: StateFlow<DiagnosticsUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            a11yMonitor.connection.collectLatest { conn ->
                _state.value = _state.value.copy(accessibility = conn)
            }
        }
        viewModelScope.launch {
            settings.flow.collectLatest { s ->
                _state.value = _state.value.copy(settings = s)
            }
        }
        viewModelScope.launch {
            auditStore.observe(limit = 20).collectLatest { tail ->
                _state.value = _state.value.copy(auditTail = tail)
            }
        }
        viewModelScope.launch {
            auditStore.observeTimeline(limit = 200).collectLatest { tail ->
                _state.value = _state.value.copy(timelineTail = tail)
            }
        }
        viewModelScope.launch {
            policyEngine.observeDecisions(limit = 20).collectLatest { tail ->
                _state.value = _state.value.copy(policyTail = tail)
            }
        }
        viewModelScope.launch {
            clipboardAssist.state.collectLatest { clip ->
                _state.value = _state.value.copy(clipState = clip.toLabel())
            }
        }
        viewModelScope.launch {
            overlayPresenter.state.collectLatest { overlay ->
                _state.value = _state.value.copy(
                    flightFsm = overlay.flightFsm.name,
                    lastFlightCancellationReason = overlay.lastFlightCancellationReason,
                )
            }
        }
        viewModelScope.launch {
            val avail = withContext(Dispatchers.IO) {
                runCatching { localGenAi.isAvailable() }.getOrElse { LocalAvailability.Unsupported }
            }
            _state.value = _state.value.copy(localAvailability = avail.toLabel())
        }
    }

    private fun ClipboardAssist.ClipState.toLabel(): String = when (this) {
        ClipboardAssist.ClipState.Idle -> "idle"
        is ClipboardAssist.ClipState.Text -> "text (${content.length} chars)"
        is ClipboardAssist.ClipState.TooLarge -> "too large: $chars chars"
        ClipboardAssist.ClipState.SensitiveSkipped -> "sensitive skipped"
    }

    private fun LocalAvailability.toLabel(): String = when (this) {
        LocalAvailability.Available -> "available"
        LocalAvailability.Downloading -> "downloading"
        LocalAvailability.Unsupported -> "unsupported"
        is LocalAvailability.TemporarilyUnavailable -> "unavailable: $reason"
    }

    suspend fun timelineExportJson(): String =
        TimelineExport.encode(json, auditStore.timelineRecent(limit = 1_000))

    fun clearAll() {
        viewModelScope.launch { auditStore.clearAll() }
    }
}

data class DiagnosticsUi(
    val settings: HandySettings? = null,
    val accessibility: AccessibilityConnectionState = AccessibilityConnectionState.NeverConnected,
    val auditTail: List<AuditEvent> = emptyList(),
    val timelineTail: List<TimelineEvent> = emptyList(),
    val policyTail: List<PolicyDecision> = emptyList(),
    val clipState: String = "idle",
    val localAvailability: String = "loading…",
    val flightFsm: String = "Docked",
    val lastFlightCancellationReason: String? = null,
)

/* ----------------------------- UI ----------------------------- */

@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUi,
    onReviewActions: () -> Unit = {},
    onExportTimeline: () -> Unit = {},
    onClearAll: () -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(DiagnosticsTab.Overview) }
    var expandedTimelineEvent by remember { mutableStateOf<TimelineEvent?>(null) }
    Surface(
        color = HandyColors.Background,
        contentColor = HandyColors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Text(
                text = "Diagnostics",
                style = HandyType.TitleLarge,
                color = HandyColors.TextPrimary,
            )
            Spacer(Modifier.height(16.dp))
            DiagnosticsTabs(
                selected = selectedTab,
                onSelect = {
                    selectedTab = it
                    expandedTimelineEvent = null
                },
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (selectedTab) {
                    DiagnosticsTab.Overview -> {
                        item { DiagRow("Accessibility", state.accessibility.name) }
                        item { DiagRow("Local GenAI", state.localAvailability) }
                        item { DiagRow("Clipboard", state.clipState) }
                        item { DiagRow("Buddy flight", state.flightFsm) }
                        item { DiagRow("Last flight cancel", state.lastFlightCancellationReason ?: "none") }
                        state.settings?.let { s ->
                            item { DiagRow("Cloud provider", s.cloudProvider.displayName) }
                            item { DiagRow("STT mode", s.sttMode.displayName) }
                            item { DiagRow("STT language", s.sttLanguage.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            item { DiagRow("Tap-for-me", s.tapForMeEnabled.onOff()) }
                            item { DiagRow("Gesture action gate", ActionExecutionGate.gesturesAllowed(s).onOff()) }
                            item { DiagRow("Overlay panel", s.useOverlayChatPanel.onOff()) }
                            item { DiagRow("Web search", s.webSearchEnabled.onOff()) }
                            item { DiagRow("Notifications", s.notificationListenerEnabled.onOff()) }
                            item { DiagRow("Clipboard assist", s.clipboardAssistEnabled.onOff()) }
                            item { DiagRow("Tutor mode", s.tutorModeEnabled.onOff()) }
                            item { DiagRow("Quick tile action", s.quickTileAction.displayName) }
                        }
                        if (state.auditTail.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
                                ) {
                                    Text(
                                        text = "Recent actions",
                                        style = HandyType.SectionHeader,
                                        color = HandyColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    DiagActionButton(text = "Review", onClick = onReviewActions)
                                }
                            }
                            items(state.auditTail.reversed()) { event ->
                                AuditRow(event)
                            }
                        }
                        if (state.policyTail.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Recent policy decisions",
                                    style = HandyType.SectionHeader,
                                    color = HandyColors.TextPrimary,
                                )
                            }
                            items(state.policyTail.reversed()) { decision ->
                                PolicyDecisionRow(decision)
                            }
                        }
                    }
                    DiagnosticsTab.Timeline -> {
                        item {
                            TimelineToolbar(
                                count = state.timelineTail.size,
                                onExport = onExportTimeline,
                                onClearAll = onClearAll,
                            )
                        }
                        val grouped = state.timelineTail.groupBy { it.turnId }
                        val orderedTurnIds = state.timelineTail
                            .asReversed()
                            .map { it.turnId }
                            .distinct()
                        if (orderedTurnIds.isEmpty()) {
                            item { DiagRow("Timeline", "no events yet") }
                        }
                        orderedTurnIds.forEach { turnId ->
                            val events = grouped[turnId].orEmpty()
                            item { TimelineTurnHeader(turnId = turnId, count = events.size) }
                            items(events) { event ->
                                TimelineRow(
                                    event = event,
                                    expanded = expandedTimelineEvent == event,
                                    onClick = {
                                        expandedTimelineEvent =
                                            if (expandedTimelineEvent == event) null else event
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class DiagnosticsTab { Overview, Timeline }

@Composable
private fun DiagnosticsTabs(
    selected: DiagnosticsTab,
    onSelect: (DiagnosticsTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        DiagnosticsTab.values().forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(HandyDimens.RadiusSm))
                    .background(if (active) HandyColors.Accent.copy(alpha = 0.18f) else HandyColors.ChipBg)
                    .border(
                        0.5.dp,
                        if (active) HandyColors.Accent.copy(alpha = 0.50f) else HandyColors.ChipBorder,
                        RoundedCornerShape(HandyDimens.RadiusSm),
                    )
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.name,
                    style = HandyType.Overline,
                    color = if (active) HandyColors.Accent else HandyColors.TextSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun DiagActionButton(
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) HandyColors.Danger else HandyColors.Accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(0.5.dp, tint.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = HandyType.Overline,
            color = tint,
        )
    }
}

@Composable
private fun TimelineToolbar(
    count: Int,
    onExport: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Timeline · $count",
            style = HandyType.SectionHeader,
            color = HandyColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            DiagActionButton(text = "Export JSON", onClick = onExport)
            DiagActionButton(text = "Clear all", danger = true, onClick = onClearAll)
        }
    }
}

@Composable
private fun TimelineTurnHeader(turnId: String, count: Int) {
    Text(
        text = "${turnId.take(8)} · $count events",
        style = HandyType.Overline,
        color = HandyColors.TextSecondary,
        modifier = Modifier.padding(top = HandyDimens.StackM),
    )
}

@Composable
private fun TimelineRow(
    event: TimelineEvent,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(HandyDimens.RadiusSm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.GlassTint)
            .border(0.5.dp, HandyColors.GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(HandyDimens.StackM),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.stage.name,
                style = HandyType.CaptionSmall,
                color = HandyColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            event.durationMs?.let {
                Text(
                    text = "${it}ms",
                    style = HandyType.Overline,
                    color = HandyColors.TextSecondary,
                )
            }
        }
        Text(
            text = listOfNotNull(event.provider, event.recipeId, event.toolName).joinToString(" · ")
                .ifBlank { "metadata only" },
            style = HandyType.Overline,
            color = if (event.error == null) HandyColors.TextSecondary else HandyColors.Danger,
        )
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            TimelineDetail("Stage", event.stage.name)
            TimelineDetail("Duration", event.durationMs?.let { "${it}ms" } ?: "n/a")
            TimelineDetail("Provider", event.provider ?: "n/a")
            TimelineDetail("Recipe", event.recipeId ?: "n/a")
            TimelineDetail("Tool", event.toolName ?: "n/a")
            TimelineDetail("Policy", event.policyDecision ?: "n/a")
            TimelineDetail(
                "Confidence",
                event.resolverConfidence?.let { String.format(Locale.US, "%.2f", it) } ?: "n/a",
            )
            TimelineDetail("Error", event.error ?: "none")
        }
    }
}

@Composable
private fun TimelineDetail(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = HandyType.Overline,
            color = HandyColors.TextSecondary,
            modifier = Modifier.weight(0.8f),
        )
        Text(
            text = value,
            style = HandyType.Overline,
            color = HandyColors.TextPrimary,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    val shape = RoundedCornerShape(HandyDimens.RadiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, shape)
            .padding(horizontal = HandyDimens.RowPad, vertical = HandyDimens.StackM),
    ) {
        Text(
            text = label,
            style = HandyType.Caption,
            color = HandyColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = HandyType.Caption,
            color = HandyColors.TextPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AuditRow(event: AuditEvent) {
    val shape = RoundedCornerShape(HandyDimens.RadiusSm)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.GlassTint)
            .border(0.5.dp, HandyColors.GlassBorder, shape)
            .padding(HandyDimens.StackM),
    ) {
        Column {
            Text(
                text = "${event.action::class.simpleName} → ${event.result::class.simpleName}",
                style = HandyType.CaptionSmall,
                color = HandyColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = event.redactedTargetLine(),
                style = HandyType.Overline,
                color = HandyColors.TextSecondary,
            )
            event.failureReason?.let {
                Text(text = it, color = HandyColors.Danger, style = HandyType.Overline)
            }
        }
    }
}

@Composable
private fun PolicyDecisionRow(decision: PolicyDecision) {
    val shape = RoundedCornerShape(HandyDimens.RadiusSm)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.GlassTint)
            .border(0.5.dp, HandyColors.GlassBorder, shape)
            .padding(HandyDimens.StackM),
    ) {
        Column {
            Text(
                text = "${if (decision.allowed) "allowed" else "blocked"} → ${decision.risk} / ${decision.confirmation}",
                style = HandyType.CaptionSmall,
                color = HandyColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "reason=${decision.reason ?: "none"} fresh=${decision.requireFreshSnapshot.onOff()} nodeOnly=${decision.requireNodeActionOnly.onOff()} gestureFallback=${decision.allowGestureFallback.onOff()}",
                style = HandyType.Overline,
                color = if (decision.allowed) HandyColors.TextSecondary else HandyColors.Danger,
            )
        }
    }
}

private fun Boolean.onOff(): String = if (this) "on" else "off"

internal fun AuditEvent.redactedTargetLine(): String {
    val context = "${action::class.simpleName.orEmpty()} $targetApp $semanticTarget"
    val redactedApp = ScreenRedactor.redactText(
        value = targetApp,
        context = context,
        diagnostics = true,
    ) ?: targetApp
    return "$redactedApp — ${semanticTarget.redactAuditTarget(context)}"
}

private fun String.redactAuditTarget(context: String): String {
    if (!contains("=")) {
        return ScreenRedactor.redactText(
            value = this,
            context = context,
            isPassword = context.containsPasswordContext(),
            diagnostics = true,
        ) ?: this
    }
    return split(';')
        .filter { it.isNotBlank() }
        .joinToString(";") { part ->
            val name = part.substringBefore('=').trim()
            val value = part.substringAfter('=', missingDelimiterValue = "").trim()
            val isPassword = name in passwordRedactedAuditFields && context.containsPasswordContext()
            val redacted = ScreenRedactor.redactText(
                value = value,
                context = context,
                isPassword = isPassword,
                diagnostics = true,
            ) ?: value
            "$name=$redacted"
        }
}

private fun String.containsPasswordContext(): Boolean =
    contains("password", ignoreCase = true) ||
        contains("passcode", ignoreCase = true) ||
        Regex("""\bpwd\b""", RegexOption.IGNORE_CASE).containsMatchIn(this)

private val passwordRedactedAuditFields = setOf("text", "desc")
