package com.handy.app.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.R
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuditReviewActivity : ComponentActivity() {

    private val viewModel: AuditReviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HandyTheme(darkTheme = true) {
                val state by viewModel.state.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.messages.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                AuditReviewScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onDisablePackage = viewModel::disablePackage,
                    onReportWrongTap = { event ->
                        val send = createWrongTapFeedbackIntent(event)
                        startActivity(Intent.createChooser(send, "Report wrong action"))
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        fun open(context: Context) {
            val intent = Intent(context, AuditReviewActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

@HiltViewModel
class AuditReviewViewModel @Inject constructor(
    private val settings: DataStoreSettings,
    private val auditStore: AuditStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AuditReviewUiState())
    val state: StateFlow<AuditReviewUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            settings.flow.collectLatest { s ->
                _state.value = _state.value.copy(
                    disabledPackages = s.tapForMeUserDenylistedPackages,
                )
            }
        }
        viewModelScope.launch {
            refreshAuditEvents()
            auditStore.observe(limit = 20).collectLatest {
                refreshAuditEvents()
            }
        }
    }

    fun disablePackage(packageName: String) {
        viewModelScope.launch {
            settings.addTapForMeUserDenylistedPackage(packageName)
            _messages.tryEmit("Tap-for-me disabled in $packageName")
        }
    }

    private suspend fun refreshAuditEvents() {
        _state.value = _state.value.copy(
            events = auditStore.recent(MAX_REVIEW_EVENTS),
        )
    }

    private companion object {
        const val MAX_REVIEW_EVENTS = 200
    }
}

data class AuditReviewUiState(
    val events: List<AuditEvent> = emptyList(),
    val disabledPackages: Set<String> = emptySet(),
)

@Composable
fun AuditReviewScreen(
    state: AuditReviewUiState,
    snackbarHostState: SnackbarHostState? = null,
    onDisablePackage: (String) -> Unit = {},
    onReportWrongTap: (AuditEvent) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val effectiveSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
    val disabledPackages = remember(state.disabledPackages) {
        state.disabledPackages.mapNotNull { it.normalizedPackageName() }.toSet()
    }
    Surface(
        color = HandyColors.Background,
        contentColor = HandyColors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HandyColors.Background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                AuditReviewTopBar(onBack = onBack)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HandyDimens.Gutter),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            text = "Action audit",
                            style = HandyType.TitleLarge,
                            color = HandyColors.TextPrimary,
                            modifier = Modifier.padding(top = HandyDimens.StackL),
                        )
                    }
                    if (state.events.isEmpty()) {
                        item {
                            EmptyAuditState()
                        }
                    } else {
                        items(
                            items = state.events.sortedByDescending { it.timestampEpochMs },
                            key = { "${it.requestId}-${it.timestampEpochMs}" },
                        ) { event ->
                            AuditReviewRow(
                                event = event,
                                disabled = event.targetApp.normalizedPackageName()
                                    ?.let { it in disabledPackages } == true,
                                onDisablePackage = onDisablePackage,
                                onReportWrongTap = onReportWrongTap,
                            )
                        }
                        item { Spacer(Modifier.height(HandyDimens.StackL)) }
                    }
                }
            }
            SnackbarHost(
                effectiveSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(HandyDimens.StackM),
            ) { data ->
                Snackbar(
                    containerColor = HandyColors.GlassTint,
                    contentColor = HandyColors.TextPrimary,
                    snackbarData = data,
                )
            }
        }
    }
}

@Composable
private fun AuditReviewTopBar(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HandyColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(HandyColors.ChipBg)
                    .border(0.5.dp, HandyColors.ChipBorder, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_back),
                    contentDescription = "Back",
                    tint = HandyColors.TextPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = "Review actions",
                style = HandyType.SettingsTitle,
                color = HandyColors.TextPrimary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(HandyColors.Divider),
        )
    }
}

@Composable
private fun EmptyAuditState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HandyDimens.RadiusLg))
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(HandyDimens.RadiusLg))
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        Text(
            text = "No actions yet",
            style = HandyType.Caption,
            color = HandyColors.TextMuted,
        )
    }
}

@Composable
private fun AuditReviewRow(
    event: AuditEvent,
    disabled: Boolean,
    onDisablePackage: (String) -> Unit,
    onReportWrongTap: (AuditEvent) -> Unit,
) {
    val shape = RoundedCornerShape(HandyDimens.RadiusLg)
    val canDisable = event.targetApp.normalizedPackageName() != null && !disabled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.GlassTint)
            .border(0.5.dp, HandyColors.GlassBorder, shape)
            .padding(HandyDimens.StackM),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackS),
        ) {
            Text(
                text = event.action.displayName(),
                style = HandyType.BodyStrong,
                color = HandyColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = event.result.displayName(),
                style = HandyType.Overline.copy(letterSpacing = 0.sp),
                color = if (event.result is AuditResult.Dispatched) {
                    HandyColors.Success
                } else {
                    HandyColors.TextSecondary
                },
            )
        }
        Text(
            text = "${event.timestampLabel()} · ${event.targetApp}",
            style = HandyType.CaptionSmall,
            color = HandyColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        event.verifiedBy?.let { verifiedBy ->
            Text(
                text = "Verified by $verifiedBy",
                style = HandyType.CaptionSmall,
                color = HandyColors.Success,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = event.redactedTargetLine(),
            style = HandyType.CaptionSmall,
            color = HandyColors.TextSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReviewPillButton(
                text = if (disabled) "Disabled here" else "Disable here",
                enabled = canDisable,
                danger = true,
                onClick = { onDisablePackage(event.targetApp) },
            )
            ReviewPillButton(
                text = "Report wrong action",
                enabled = true,
                danger = false,
                onClick = { onReportWrongTap(event) },
            )
        }
    }
}

@Composable
private fun ReviewPillButton(
    text: String,
    enabled: Boolean,
    danger: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (danger) HandyColors.Danger else HandyColors.Accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = if (enabled) 0.16f else 0.07f))
            .border(
                0.5.dp,
                accent.copy(alpha = if (enabled) 0.40f else 0.18f),
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = HandyType.Overline.copy(letterSpacing = 0.sp),
            color = if (enabled) accent else HandyColors.TextMuted,
        )
    }
}

internal fun createWrongTapFeedbackIntent(event: AuditEvent): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Handy wrong action report")
        putExtra(Intent.EXTRA_TEXT, event.feedbackBody())
    }

private fun AuditEvent.feedbackBody(): String = buildString {
    appendLine("Wrong action report")
    appendLine("requestId=$requestId")
    appendLine("provider=$provider")
    appendLine("action=${action.displayName()}")
    appendLine("result=${result.displayName()}")
    appendLine("target=${redactedTargetLine()}")
    failureReason?.let { appendLine("failureReason=$it") }
    verifiedBy?.let { appendLine("verifiedBy=$it") }
}

private fun AuditAction.displayName(): String = when (this) {
    AuditAction.Tap -> "Tap"
    AuditAction.LongPress -> "Long press"
    AuditAction.ManualSelect -> "Manual select"
    is AuditAction.Scroll -> "Scroll ${direction.lowercase()}"
    is AuditAction.Swipe -> "Swipe ${direction.lowercase()}"
    AuditAction.TypeText -> "Type text"
    is AuditAction.Intent -> "Intent $name"
}

private fun AuditResult.displayName(): String = when (this) {
    is AuditResult.Dispatched -> "Dispatched"
    AuditResult.ChooserShown -> "Chooser shown"
    is AuditResult.Failed -> "Failed"
    AuditResult.Cancelled -> "Cancelled"
    AuditResult.NotPermitted -> "Not permitted"
    AuditResult.NotFound -> "Not found"
}

private fun AuditEvent.timestampLabel(): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestampEpochMs))

private fun String.normalizedPackageName(): String? =
    trim()
        .lowercase()
        .takeIf { it.isNotBlank() && it != "unknown" }
