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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.R
import com.handy.app.design.ActionChip
import com.handy.app.design.ChipTone
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.design.HandyDesignType
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
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
            HandyDesignTheme {
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
        color = HandyDesign.Colors.PageBg,
        contentColor = HandyDesign.Colors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                ActivityHeader(
                    eventCount = state.events.size,
                    isEmpty = state.events.isEmpty(),
                    onBack = onBack,
                )
                if (state.events.isEmpty()) {
                    ActivityEmpty()
                } else {
                    ActivityList(
                        events = state.events.sortedByDescending { it.timestampEpochMs },
                        disabledPackages = disabledPackages,
                        onDisablePackage = onDisablePackage,
                        onReportWrongTap = onReportWrongTap,
                    )
                }
            }
            SnackbarHost(
                effectiveSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) { data ->
                Snackbar(
                    containerColor = HandyDesign.Colors.Surface,
                    contentColor = HandyDesign.Colors.TextPrimary,
                    shape = RoundedCornerShape(14.dp),
                    snackbarData = data,
                )
            }
        }
    }
}

@Composable
private fun ActivityHeader(
    eventCount: Int,
    isEmpty: Boolean,
    onBack: () -> Unit,
) {
    val eventCountLabel = if (eventCount == 1) "1 event" else "$eventCount events"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 14.dp),
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
                    text = "Activity",
                    style = HandyDesignType.Display.copy(
                        fontSize = 22.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.em,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = HandyDesign.Colors.TextPrimary,
                )
                Text(
                    text = "Every action Handy took. Targets redacted.",
                    style = HandyDesignType.Caption,
                    color = HandyDesign.Colors.TextSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (!isEmpty) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HandyDesign.Colors.AccentSoft)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = eventCountLabel,
                        style = HandyDesignType.Overline.copy(
                            fontSize = 10.sp,
                            letterSpacing = 0.10.em,
                        ),
                        color = HandyDesign.Colors.Accent,
                    )
                }
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
private fun ActivityEmpty() {
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
            text = "Nothing here yet",
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
            text = "When Handy taps, types, or fetches a page for you, the action shows up here with the target redacted.",
            style = HandyDesignType.Body.copy(fontSize = 13.sp, lineHeight = 20.sp),
            color = HandyDesign.Colors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Composable
private fun ActivityList(
    events: List<AuditEvent>,
    disabledPackages: Set<String>,
    onDisablePackage: (String) -> Unit,
    onReportWrongTap: (AuditEvent) -> Unit,
) {
    val grouped = events.groupBy { it.dayBucket() }
    val orderedBuckets = listOf(DayBucket.TODAY, DayBucket.YESTERDAY, DayBucket.OLDER)
        .filter { grouped[it]?.isNotEmpty() == true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 18.dp),
    ) {
        orderedBuckets.forEach { bucket ->
            item(key = "day-${bucket.label}") {
                Text(
                    text = bucket.label,
                    style = HandyDesignType.Overline,
                    color = HandyDesign.Colors.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            items(
                items = grouped[bucket].orEmpty(),
                key = { "${it.requestId}-${it.timestampEpochMs}" },
            ) { event ->
                ActivityRow(
                    event = event,
                    disabled = event.targetApp.normalizedPackageName()
                        ?.let { it in disabledPackages } == true,
                    onDisable = { onDisablePackage(event.targetApp) },
                    onReport = { onReportWrongTap(event) },
                )
            }
        }
    }
}

private data class ResultTone(
    val softBg: Color,
    val fg: Color,
    val label: String,
)

private val MutedSoft = Color(0x1AA8A39B)

private fun AuditEvent.resultTone(): ResultTone = when (result) {
    is AuditResult.Dispatched -> ResultTone(
        HandyDesign.Colors.SuccessSoft,
        HandyDesign.Colors.Success,
        "Done",
    )
    AuditResult.Cancelled -> ResultTone(
        MutedSoft,
        HandyDesign.Colors.TextMuted,
        "Cancelled",
    )
    is AuditResult.Failed,
    AuditResult.NotPermitted,
    AuditResult.NotFound -> ResultTone(
        HandyDesign.Colors.DangerSoft,
        HandyDesign.Colors.Danger,
        "Failed",
    )
    AuditResult.ChooserShown -> ResultTone(
        HandyDesign.Colors.AccentSoft,
        HandyDesign.Colors.Accent,
        "Chooser",
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActivityRow(
    event: AuditEvent,
    disabled: Boolean,
    onDisable: () -> Unit,
    onReport: () -> Unit,
) {
    val tone = event.resultTone()
    val illu = when (event.action) {
        AuditAction.TypeText -> R.drawable.ic_keyboard
        is AuditAction.Intent -> R.drawable.ic_globe
        else -> R.drawable.ic_hand_tap
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HandyDesign.Colors.Surface)
            .border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tone.softBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(illu),
                    contentDescription = null,
                    tint = tone.fg,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = HandyDesign.Colors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append("${event.action.displayName()} · ")
                        }
                        withStyle(
                            SpanStyle(
                                color = HandyDesign.Colors.TextSecondary,
                                fontWeight = FontWeight.Normal,
                            ),
                        ) {
                            append(event.redactedTargetLine())
                        }
                    },
                    style = HandyDesignType.Body.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${event.targetAppDisplayName()} · ${event.timestampLabel()}" +
                        (event.failureReason?.let { " · $it" }.orEmpty()),
                    style = HandyDesignType.Caption.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = HandyDesign.Colors.TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(tone.softBg)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = tone.label.uppercase(),
                    style = HandyDesignType.Overline.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.10.em,
                    ),
                    color = tone.fg,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 48.dp),
        ) {
            if (disabled) {
                ActionChip(
                    label = "Disabled here",
                    tone = ChipTone.Muted,
                    enabled = false,
                    onClick = {},
                )
            } else {
                ActionChip(
                    label = "Disable in this app",
                    tone = ChipTone.Danger,
                    enabled = true,
                    onClick = onDisable,
                )
            }
            ActionChip(
                label = "Report wrong action",
                tone = ChipTone.Muted,
                enabled = true,
                onClick = onReport,
            )
        }
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
    AuditAction.LongPress -> "Long-press"
    AuditAction.TypeText -> "Type"
    AuditAction.ManualSelect -> "Pick"
    is AuditAction.Intent -> "Web fetch"
    AuditAction.RecipeStepFailed -> "Recipe step failed"
    AuditAction.RecipeCompleted -> "Recipe completed"
    else -> this::class.simpleName.orEmpty()
}

private fun AuditResult.displayName(): String = when (this) {
    is AuditResult.Dispatched -> "Dispatched"
    AuditResult.ChooserShown -> "Chooser shown"
    is AuditResult.Failed -> "Failed"
    AuditResult.Cancelled -> "Cancelled"
    AuditResult.NotPermitted -> "Not permitted"
    AuditResult.NotFound -> "Not found"
}

private fun AuditEvent.targetAppDisplayName(): String {
    return targetApp.substringAfterLast('.').replaceFirstChar { it.titlecase() }
}

private fun AuditEvent.timestampLabel(): String {
    val cal = Calendar.getInstance().apply {
        timeInMillis = this@timestampLabel.timestampEpochMs
    }
    val now = Calendar.getInstance()
    val sameDay =
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val fmt = if (sameDay) {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    } else {
        SimpleDateFormat("MMM d · h:mm a", Locale.getDefault())
    }
    return fmt.format(Date(timestampEpochMs))
}

private enum class DayBucket(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    OLDER("Older"),
}

private fun AuditEvent.dayBucket(): DayBucket {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timestampEpochMs
    }
    val now = Calendar.getInstance()
    val yesterday = (now.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(cal, now) -> DayBucket.TODAY
        sameDay(cal, yesterday) -> DayBucket.YESTERDAY
        else -> DayBucket.OLDER
    }
}

private fun String.normalizedPackageName(): String? =
    trim()
        .lowercase()
        .takeIf { it.isNotBlank() && it != "unknown" }
