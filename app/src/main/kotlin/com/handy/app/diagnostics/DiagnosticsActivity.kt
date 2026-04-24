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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.app.clipboard.ClipboardAssist
import com.handy.app.notifications.HandyNotificationListenerService
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyTheme
import com.handy.core.accessibility.AccessibilityConnectionState
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditStore
import com.handy.core.llm.LocalAvailability
import com.handy.core.llm.LocalGenAiClient
import com.handy.core.model.HandySettings
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
            clipboardAssist.state.collectLatest { clip ->
                _state.value = _state.value.copy(clipState = clip.toLabel())
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
    val clipState: String = "idle",
    val localAvailability: String = "loading…",
)

/* ----------------------------- UI ----------------------------- */

@Composable
private fun DiagnosticsScreen(state: DiagnosticsUi) {
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
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HandyColors.TextPrimary,
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { DiagRow("Accessibility", state.accessibility.name) }
                item { DiagRow("Local GenAI", state.localAvailability) }
                item { DiagRow("Clipboard", state.clipState) }
                state.settings?.let { s ->
                    item { DiagRow("Cloud provider", s.cloudProvider.displayName) }
                    item { DiagRow("Tap-for-me", s.tapForMeEnabled.onOff()) }
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
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HandyColors.TextPrimary,
                        )
                    }
                    items(state.auditTail.reversed()) { event ->
                        AuditRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HandyColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = HandyColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = HandyColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AuditRow(event: AuditEvent) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HandyColors.SurfaceElevated, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Column {
            Text(
                text = "${event.action::class.simpleName} → ${event.result::class.simpleName}",
                color = HandyColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${event.targetApp} — ${event.semanticTarget}",
                color = HandyColors.TextSecondary,
                fontSize = 11.sp,
            )
            event.failureReason?.let {
                Text(text = it, color = Color(0xFFEF4444), fontSize = 10.sp)
            }
        }
    }
}

private fun Boolean.onOff(): String = if (this) "on" else "off"
