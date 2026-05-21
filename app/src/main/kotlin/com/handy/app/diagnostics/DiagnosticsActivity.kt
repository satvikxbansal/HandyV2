package com.handy.app.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.app.clipboard.ClipboardAssist
import com.handy.app.notifications.HandyNotificationListenerService
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
import com.handy.core.llm.LocalAvailability
import com.handy.core.llm.LocalGenAiClient
import com.handy.core.model.HandySettings
import com.handy.core.privacy.ScreenRedactor
import com.handy.runtime.action.DefaultActionPolicyEngine
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HandyTheme(darkTheme = true) {
                val state by viewModel.state.collectAsState()
                DiagnosticsScreen(state)
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
}

data class DiagnosticsUi(
    val settings: HandySettings? = null,
    val accessibility: AccessibilityConnectionState = AccessibilityConnectionState.NeverConnected,
    val auditTail: List<AuditEvent> = emptyList(),
    val policyTail: List<PolicyDecision> = emptyList(),
    val clipState: String = "idle",
    val localAvailability: String = "loading…",
    val flightFsm: String = "Docked",
    val lastFlightCancellationReason: String? = null,
)

/* ----------------------------- UI ----------------------------- */

@Composable
fun DiagnosticsScreen(state: DiagnosticsUi) {
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { DiagRow("Accessibility", state.accessibility.name) }
                item { DiagRow("Local GenAI", state.localAvailability) }
                item { DiagRow("Clipboard", state.clipState) }
                item { DiagRow("Buddy flight", state.flightFsm) }
                item { DiagRow("Last flight cancel", state.lastFlightCancellationReason ?: "none") }
                state.settings?.let { s ->
                    item { DiagRow("Cloud provider", s.cloudProvider.displayName) }
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
                        Text(
                            text = "Recent actions",
                            style = HandyType.SectionHeader,
                            color = HandyColors.TextPrimary,
                        )
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
        }
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

private fun AuditEvent.redactedTargetLine(): String {
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
