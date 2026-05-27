package com.handy.app.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.handy.app.R
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.app.clipboard.ClipboardAssist
import com.handy.app.design.ActionChip
import com.handy.app.design.ChipTone
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.design.HandyDesignType
import com.handy.app.overlay.OverlayPresenter
import com.handy.core.accessibility.AccessibilityConnectionState
import com.handy.core.action.ActionExecutionGate
import com.handy.core.action.PolicyDecision
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
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
            HandyDesignTheme {
                val state by viewModel.state.collectAsState()
                DiagnosticsScreen(
                    state = state,
                    onReviewActions = { AuditReviewActivity.open(this) },
                    onExportTimeline = {
                        exportTimelineLauncher.launch("handy-timeline-${System.currentTimeMillis()}.json")
                    },
                    onClearAll = viewModel::clearAll,
                    onBack = { finish() },
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
    onBack: () -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(DiagnosticsTab.Overview) }
    var expandedTimelineEvent by remember { mutableStateOf<TimelineEvent?>(null) }
    Surface(
        color = HandyDesign.Colors.PageBg,
        contentColor = HandyDesign.Colors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            DiagnosticsHeader(onBack)
            DiagTabs(selected = selectedTab) {
                selectedTab = it
                expandedTimelineEvent = null
            }
            Spacer(Modifier.height(14.dp))
            when (selectedTab) {
                DiagnosticsTab.Overview -> DiagOverview(
                    state = state,
                    onReviewActions = onReviewActions,
                )
                DiagnosticsTab.Timeline -> DiagTimeline(
                    state = state,
                    expandedTimelineEvent = expandedTimelineEvent,
                    onToggleExpand = {
                        expandedTimelineEvent = if (expandedTimelineEvent == it) null else it
                    },
                    onExport = onExportTimeline,
                    onClearAll = onClearAll,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsHeader(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HandyDesign.Colors.Surface)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_back),
                    contentDescription = "Back",
                    tint = HandyDesign.Colors.TextPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Diagnostics",
                    style = HandyDesignType.Display.copy(
                        fontSize = 22.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.em,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = HandyDesign.Colors.TextPrimary,
                )
                Text(
                    text = "What Handy sees right now. Read-only.",
                    style = HandyDesignType.Caption,
                    color = HandyDesign.Colors.TextSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = HandyDesign.Colors.BorderSubtle,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun DiagTabs(
    selected: DiagnosticsTab,
    onSelect: (DiagnosticsTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiagnosticsTab.values().forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) HandyDesign.Colors.Accent else HandyDesign.Colors.Surface,
                    )
                    .border(
                        1.dp,
                        if (active) Color.Transparent else HandyDesign.Colors.BorderSubtle,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    style = HandyDesignType.Body.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (active) {
                        HandyDesign.Colors.AccentInk
                    } else {
                        HandyDesign.Colors.TextSecondary
                    },
                )
            }
        }
    }
}

private enum class DiagnosticsTab(val label: String) {
    Overview("Overview"),
    Timeline("Timeline"),
}

@Composable
private fun DiagOverview(
    state: DiagnosticsUi,
    onReviewActions: () -> Unit,
) {
    val settings = state.settings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DiagGroup("Connections") {
                DiagStatusRow(
                    label = "Accessibility",
                    value = state.accessibility.name,
                    tone = state.accessibility.toTone(),
                    last = false,
                )
                DiagStatusRow(
                    label = "Local GenAI",
                    value = state.localAvailability,
                    tone = if (state.localAvailability == "available") DotTone.Ok else DotTone.Muted,
                    last = false,
                )
                DiagStatusRow(
                    label = "Cloud provider",
                    value = settings?.cloudProvider?.displayName?.let {
                        "$it · ${cloudModelOverrideOrDefault(settings)}"
                    } ?: "—",
                    tone = DotTone.Ok,
                    last = true,
                )
            }
        }
        item {
            DiagGroup("Voice") {
                DiagStatusRow(
                    label = "STT mode",
                    value = settings?.sttMode?.displayName ?: "—",
                    tone = DotTone.Ok,
                    last = false,
                )
                DiagStatusRow(
                    label = "STT language",
                    value = settings?.sttLanguage?.localizedName() ?: "—",
                    tone = DotTone.Muted,
                    last = true,
                )
            }
        }
        item {
            DiagGroup("Action gate") {
                DiagStatusRow(
                    label = "Tap-for-me",
                    value = (settings?.tapForMeEnabled == true).onOff(),
                    tone = if (settings?.tapForMeEnabled == true) DotTone.Ok else DotTone.Muted,
                    last = false,
                )
                DiagStatusRow(
                    label = "Gestures",
                    value = (
                        settings != null &&
                            ActionExecutionGate.gesturesAllowed(settings)
                        ).onOff(),
                    tone = if (
                        settings != null &&
                        ActionExecutionGate.gesturesAllowed(settings)
                    ) {
                        DotTone.Ok
                    } else {
                        DotTone.Muted
                    },
                    last = false,
                )
                DiagStatusRow(
                    label = "Last flight cancel",
                    value = state.lastFlightCancellationReason ?: "none",
                    tone = if (state.lastFlightCancellationReason == null) DotTone.Muted else DotTone.Warn,
                    last = true,
                )
            }
        }
        if (state.auditTail.isNotEmpty()) {
            item {
                DiagGroup(
                    title = "Recent actions",
                    trailing = {
                        ActionChip(
                            label = "Review",
                            tone = ChipTone.Accent,
                            onClick = onReviewActions,
                        )
                    },
                ) {
                    val tail = state.auditTail.takeLast(2).reversed()
                    tail.forEachIndexed { i, event ->
                        DiagStatusRow(
                            label = "${event.targetApp.substringAfterLast('.')} · ${event.action::class.simpleName}",
                            value = event.result::class.simpleName ?: "—",
                            tone = event.dotToneForResult(),
                            last = i == tail.lastIndex,
                        )
                    }
                }
            }
        }
        if (state.policyTail.isNotEmpty()) {
            item {
                DiagGroup("Recent policy decisions") {
                    val tail = state.policyTail.takeLast(3).reversed()
                    tail.forEachIndexed { i, dec ->
                        DiagStatusRow(
                            label = "${dec.risk}/${dec.confirmation}",
                            value = dec.reason ?: if (dec.allowed) "allowed" else "blocked",
                            tone = if (dec.allowed) DotTone.Ok else DotTone.Bad,
                            last = i == tail.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

internal enum class DotTone { Ok, Warn, Bad, Muted }

internal fun AccessibilityConnectionState.toTone(): DotTone = when (this) {
    AccessibilityConnectionState.Connected -> DotTone.Ok
    AccessibilityConnectionState.Disconnected -> DotTone.Bad
    else -> DotTone.Muted
}

private fun AuditEvent.dotToneForResult(): DotTone = when (result) {
    is AuditResult.Dispatched -> DotTone.Ok
    AuditResult.Cancelled -> DotTone.Warn
    else -> DotTone.Bad
}

private fun cloudModelOverrideOrDefault(s: HandySettings): String =
    s.claudeModelOverride ?: s.geminiModelOverride ?: "default"

private fun com.handy.core.model.SttLanguage.localizedName(): String =
    name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }

@Composable
private fun DiagGroup(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text(
                text = title.uppercase(Locale.ROOT),
                style = HandyDesignType.Overline.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.10.em,
                ),
                color = HandyDesign.Colors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(HandyDesign.Colors.Surface)
                .border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(18.dp)),
            content = content,
        )
    }
}

@Composable
private fun DiagStatusRow(
    label: String,
    value: String,
    tone: DotTone,
    last: Boolean,
) {
    val dotColor = when (tone) {
        DotTone.Ok -> HandyDesign.Colors.Success
        DotTone.Warn -> HandyDesign.Colors.Accent
        DotTone.Bad -> HandyDesign.Colors.Danger
        DotTone.Muted -> HandyDesign.Colors.TextMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .blur(radius = 4.dp)
                        .background(dotColor.copy(alpha = 0.45f), CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Text(
            text = label,
            style = HandyDesignType.Body.copy(fontSize = 13.sp),
            color = HandyDesign.Colors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = HandyDesignType.Mono.copy(fontSize = 12.sp),
            color = HandyDesign.Colors.TextSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (!last) {
        HorizontalDivider(thickness = 1.dp, color = HandyDesign.Colors.BorderSubtle)
    }
}

@Composable
private fun DiagTimeline(
    state: DiagnosticsUi,
    expandedTimelineEvent: TimelineEvent?,
    onToggleExpand: (TimelineEvent) -> Unit,
    onExport: () -> Unit,
    onClearAll: () -> Unit,
) {
    val grouped = state.timelineTail.groupBy { it.turnId }
    val orderedTurnIds = state.timelineTail.asReversed()
        .map { it.turnId }
        .distinct()
    val totalEvents = state.timelineTail.size

    if (state.timelineTail.isEmpty()) {
        DiagnosticsTimelineEmptyState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${orderedTurnIds.size} turns · $totalEvents events",
                    style = HandyDesignType.Overline.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.10.em,
                    ),
                    color = HandyDesign.Colors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip(
                        label = "Export JSON",
                        tone = ChipTone.Muted,
                        onClick = onExport,
                    )
                    ActionChip(
                        label = "Clear all",
                        tone = ChipTone.Danger,
                        onClick = onClearAll,
                    )
                }
            }
        }
        orderedTurnIds.forEach { turnId ->
            val events = grouped[turnId].orEmpty()
            item(key = "turn-$turnId") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    ) {
                        Text(
                            text = "turn · ${turnId.take(8)}",
                            style = HandyDesignType.Mono.copy(fontSize = 11.sp),
                            color = HandyDesign.Colors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${events.size} events",
                            style = HandyDesignType.Mono.copy(fontSize = 11.sp),
                            color = HandyDesign.Colors.TextMuted,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(HandyDesign.Colors.Surface)
                            .border(
                                1.dp,
                                HandyDesign.Colors.BorderSubtle,
                                RoundedCornerShape(18.dp),
                            ),
                    ) {
                        events.forEachIndexed { i, ev ->
                            TimelineEventRow(
                                event = ev,
                                expanded = expandedTimelineEvent == ev,
                                onClick = { onToggleExpand(ev) },
                                last = i == events.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticsTimelineEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_recipe),
            contentDescription = null,
            tint = HandyDesign.Colors.Accent.copy(alpha = 0.60f),
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = "No events yet",
            style = HandyDesignType.Display.copy(
                fontSize = 22.sp,
                lineHeight = 25.sp,
                letterSpacing = 0.em,
                fontWeight = FontWeight.SemiBold,
            ),
            color = HandyDesign.Colors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Handy hasn't done anything that left a trace.",
            style = HandyDesignType.Body.copy(fontSize = 13.sp, lineHeight = 20.sp),
            color = HandyDesign.Colors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Composable
private fun TimelineEventRow(
    event: TimelineEvent,
    expanded: Boolean,
    onClick: () -> Unit,
    last: Boolean,
) {
    val errored = event.error != null
    val dotColor = if (errored) HandyDesign.Colors.Danger else HandyDesign.Colors.Accent
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .blur(radius = 4.dp)
                            .background(dotColor.copy(alpha = 0.45f), CircleShape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.stage.name,
                    style = HandyDesignType.BodyStrong.copy(fontSize = 13.sp),
                    color = HandyDesign.Colors.TextPrimary,
                )
                val meta = listOfNotNull(
                    event.provider,
                    event.recipeId,
                    event.toolName,
                ).joinToString(" · ").ifBlank { "metadata only" }
                Text(
                    text = meta + (event.error?.let { " · $it" }.orEmpty()),
                    style = HandyDesignType.Mono.copy(fontSize = 11.sp),
                    color = if (errored) HandyDesign.Colors.Danger else HandyDesign.Colors.TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${event.durationMs ?: 0}ms",
                style = HandyDesignType.Mono.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = HandyDesign.Colors.TextSecondary,
            )
        }
        if (expanded) {
            HorizontalDivider(thickness = 1.dp, color = HandyDesign.Colors.BorderSubtle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimelineDetail("Stage", event.stage.name)
                TimelineDetail("Duration", event.durationMs?.let { "${it}ms" } ?: "n/a")
                TimelineDetail("Provider", event.provider ?: "n/a")
                TimelineDetail("Recipe", event.recipeId ?: "n/a")
                TimelineDetail("Tool", event.toolName ?: "n/a")
                TimelineDetail("Policy", event.policyDecision ?: "n/a")
                TimelineDetail(
                    "Confidence",
                    event.resolverConfidence?.let {
                        String.format(Locale.US, "%.2f", it)
                    } ?: "n/a",
                )
                TimelineDetail("Error", event.error ?: "none")
            }
        }
        if (!last && !expanded) {
            HorizontalDivider(thickness = 1.dp, color = HandyDesign.Colors.BorderSubtle)
        }
    }
}

@Composable
private fun TimelineDetail(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(Locale.ROOT),
            style = HandyDesignType.Overline.copy(
                fontSize = 9.sp,
                letterSpacing = 0.10.em,
            ),
            color = HandyDesign.Colors.TextMuted,
            modifier = Modifier.weight(0.8f),
        )
        Text(
            text = value,
            style = HandyDesignType.Mono.copy(fontSize = 11.sp),
            color = HandyDesign.Colors.TextPrimary,
            modifier = Modifier.weight(1.4f),
        )
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
