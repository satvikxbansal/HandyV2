package com.handy.app.settings

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.handy.app.BuildConfig
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.diagnostics.AuditReviewActivity
import com.handy.app.notifications.HandyNotificationListenerService
import com.handy.app.onboarding.ActionDisclosureActivity
import com.handy.app.onboarding.OnboardingActivity
import com.handy.app.service.AssistantForegroundService
import com.handy.app.settings.design.DisabledAppEntry
import com.handy.app.settings.sections.AutomationsSection
import com.handy.app.settings.sections.BrainSection
import com.handy.app.settings.sections.CapabilitiesSection
import com.handy.app.settings.sections.ModelPickerSheet
import com.handy.app.settings.sections.PrivacySection
import com.handy.app.settings.sections.SettingsFooter
import com.handy.app.settings.sections.SettingsHeader
import com.handy.app.settings.sections.colorForPackage
import com.handy.app.settings.sections.friendlyAppLabelOrPackage
import com.handy.core.action.ActionExecutionGate
import com.handy.core.model.HandySettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject lateinit var accessibilityStateMonitor: AccessibilityStateMonitor

    private val viewModel: SettingsViewModel by viewModels()
    private val micLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        lifecycleScope.launch { accessibilityStateMonitor.refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HandyDesignTheme {
                val context = LocalContext.current
                val state by viewModel.state.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    accessibilityStateMonitor.refresh()
                }

                // Surface one-shot key-save toasts — DL-007.
                LaunchedEffect(Unit) {
                    viewModel.messages.collect { text ->
                        snackbarHostState.showSnackbar(text)
                    }
                }

                SettingsScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onClaudeKeyChange = viewModel::setClaudeKey,
                    onBraveKeyChange = viewModel::setBraveKey,
                    onJinaKeyChange = viewModel::setJinaKey,
                    onGithubKeyChange = viewModel::setGithubKey,
                    onWebSearchToggle = { enabled ->
                        viewModel.updateSettings { it.copy(webSearchEnabled = enabled) }
                    },
                    onClaudeModelVariant = viewModel::setClaudeModelVariant,
                    onTutorModeToggle = { enabled ->
                        viewModel.updateSettings { it.copy(tutorModeEnabled = enabled) }
                    },
                    onSpeakVoiceRepliesAloudToggle = { enabled ->
                        viewModel.updateSettings { it.copy(speakVoiceRepliesAloud = enabled) }
                    },
                    onTapForMeToggle = viewModel::setTapForMeEnabled,
                    onNoActionsInIncognitoToggle = { enabled ->
                        viewModel.updateSettings { it.copy(noActionsInIncognito = enabled) }
                    },
                    onTapForMePanicMute = viewModel::muteTapForMeForOneHour,
                    onTapForMeStopUntilTurnedBackOn = viewModel::disableTapForMeUntilTurnedBackOn,
                    onTapForMeRestorePackage = viewModel::restoreTapForMeForPackage,
                    onReviewActionDisclosure = {
                        startActivity(Intent(this, ActionDisclosureActivity::class.java))
                    },
                    onClearHistory = viewModel::clearAllHistory,
                    onBack = { finish() },
                    onTypeForMeToggle = viewModel::setTypeForMeEnabled,
                    onRecipesToggle = viewModel::setRecipesEnabled,
                    onClipboardAssistToggle = viewModel::setClipboardAssistEnabled,
                    onOpenActivityLog = { AuditReviewActivity.open(context) },
                    onRequestMic = {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onResetOnboarding = if (BuildConfig.DEBUG) {
                        {
                            lifecycleScope.launch {
                                viewModel.resetOnboardingForDebug()
                                AssistantForegroundService.stop(this@SettingsActivity)
                                startActivity(
                                    Intent(this@SettingsActivity, OnboardingActivity::class.java)
                                        .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                        ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { accessibilityStateMonitor.refresh() }
    }
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onClaudeKeyChange: (String) -> Unit,
    onBraveKeyChange: (String) -> Unit,
    onJinaKeyChange: (String) -> Unit,
    onGithubKeyChange: (String) -> Unit,
    onWebSearchToggle: (Boolean) -> Unit,
    onClaudeModelVariant: (Boolean) -> Unit,
    onTutorModeToggle: (Boolean) -> Unit,
    onSpeakVoiceRepliesAloudToggle: (Boolean) -> Unit,
    onTapForMeToggle: (Boolean) -> Unit,
    onNoActionsInIncognitoToggle: (Boolean) -> Unit,
    onTapForMePanicMute: () -> Unit,
    onTapForMeStopUntilTurnedBackOn: () -> Unit,
    onTapForMeRestorePackage: (String) -> Unit,
    onReviewActionDisclosure: () -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    onTypeForMeToggle: (Boolean) -> Unit = {},
    onRecipesToggle: (Boolean) -> Unit = {},
    onClipboardAssistToggle: (Boolean) -> Unit = {},
    onOpenActivityLog: () -> Unit = {},
    onRequestMic: () -> Unit = {},
    onResetOnboarding: (() -> Unit)? = null,
) {
    var capabilitiesOpen by rememberSaveable { mutableStateOf(true) }
    var automationsOpen by rememberSaveable { mutableStateOf(false) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var brainSheetOpen by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsActivity = context.findSettingsActivity()
    val fallbackAccessibilityEnabled = state.settings?.accessibilityDisclosureAcknowledged == true
    var accessibilityEnabled = fallbackAccessibilityEnabled
    if (settingsActivity != null) {
        val monitoredAccessibility by settingsActivity.accessibilityStateMonitor.isEnabled.collectAsState()
        accessibilityEnabled = monitoredAccessibility
    }
    var micGranted by remember(context) { mutableStateOf(isRecordAudioGranted(context)) }
    var notificationsOn by remember(context) {
        mutableStateOf(HandyNotificationListenerService.isGranted(context))
    }

    fun refreshPermissionRows() {
        micGranted = isRecordAudioGranted(context)
        notificationsOn = HandyNotificationListenerService.isGranted(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionRows()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(context) {
        refreshPermissionRows()
        settingsActivity?.accessibilityStateMonitor?.refresh()
    }

    val tapForMeMuted =
        (state.settings?.tapForMeMutedUntilEpochMs ?: 0L) > System.currentTimeMillis()
    val actionDisclosureAccepted =
        (state.settings?.actionDisclosureVersionAccepted ?: 0) >=
            ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION
    val tapForMeAvailable = actionDisclosureAccepted && !tapForMeMuted

    val useHaiku = state.settings?.claudeModelOverride == HandySettings.DEFAULT_CLAUDE_HAIKU_MODEL
    val selectedModelLabel = if (useHaiku) "Claude Haiku 4.5" else "Claude Sonnet 4.5"
    val selectedModelId = if (useHaiku) "haiku-4-5" else "sonnet-4-5"

    val disabledApps = remember(state.settings?.tapForMeUserDenylistedPackages, context) {
        val pm = context.packageManager
        state.settings?.tapForMeUserDenylistedPackages.orEmpty()
            .toList()
            .sorted()
            .map { pkg ->
                DisabledAppEntry(
                    label = friendlyAppLabelOrPackage(pkg, pm),
                    packageName = pkg,
                    initialColor = colorForPackage(pkg),
                )
            }
    }

    Box(Modifier.fillMaxSize().background(HandyDesign.Colors.PageBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            SettingsHeader(onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BrainSection(
                        selectedModelLabel = selectedModelLabel,
                        providerLine = if (useHaiku) {
                            "Faster · Anthropic"
                        } else {
                            "Best reasoning · Anthropic"
                        },
                        apiKeyMasked = state.claudeKeyMasked,
                        onApiKeyChange = onClaudeKeyChange,
                        requestsTodayLabel = "—",
                        connected = state.claudeKeyMasked != null,
                        speakVoiceRepliesAloud = state.settings?.speakVoiceRepliesAloud != false,
                        onOpenPicker = { brainSheetOpen = true },
                        onSpeakVoiceRepliesAloudToggle = onSpeakVoiceRepliesAloudToggle,
                    )
                    CapabilitiesSection(
                        expanded = capabilitiesOpen,
                        onToggleExpanded = { capabilitiesOpen = !capabilitiesOpen },
                        screenReadingOn = accessibilityEnabled,
                        voiceInputOn = micGranted,
                        notificationsOn = notificationsOn,
                        onOpenAccessibilitySettings = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onRequestMic = onRequestMic,
                        onOpenNotificationListenerSettings = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        webSearchOn = state.settings?.webSearchEnabled == true,
                        onWebSearchToggle = onWebSearchToggle,
                        braveKeyMasked = state.braveKeyMasked,
                        jinaKeyMasked = state.jinaKeyMasked,
                        githubKeyMasked = state.githubKeyMasked,
                        onBraveKeyChange = onBraveKeyChange,
                        onJinaKeyChange = onJinaKeyChange,
                        onGithubKeyChange = onGithubKeyChange,
                        tutorOn = state.settings?.tutorModeEnabled == true,
                        onTutorToggle = onTutorModeToggle,
                    )
                    AutomationsSection(
                        expanded = automationsOpen,
                        onToggleExpanded = { automationsOpen = !automationsOpen },
                        tapForMeOn = state.settings?.tapForMeEnabled == true,
                        onTapForMeToggle = onTapForMeToggle,
                        typeForMeOn = state.settings?.typeForMeEnabled != false,
                        onTypeForMeToggle = onTypeForMeToggle,
                        recipesOn = state.settings?.recipesEnabled != false,
                        onRecipesToggle = onRecipesToggle,
                        tapForMeAvailable = tapForMeAvailable,
                        onPanic1Hr = onTapForMePanicMute,
                        onStopUntilBackOn = onTapForMeStopUntilTurnedBackOn,
                        disabledApps = disabledApps,
                        onRestorePackage = onTapForMeRestorePackage,
                    )
                    PrivacySection(
                        expanded = privacyOpen,
                        onToggleExpanded = { privacyOpen = !privacyOpen },
                        blockInIncognito = state.settings?.noActionsInIncognito != false,
                        onBlockInIncognitoToggle = onNoActionsInIncognitoToggle,
                        clipboardAssist = state.settings?.clipboardAssistEnabled == true,
                        onClipboardAssistToggle = onClipboardAssistToggle,
                        auditEntriesCount = 0,
                        onOpenActivityLog = onOpenActivityLog,
                        onClearHistory = onClearHistory,
                    )
                }

                SettingsFooter(
                    versionName = BuildConfig.VERSION_NAME,
                    onResetOnboarding = onResetOnboarding,
                )
            }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        ) { data ->
            Snackbar(
                containerColor = HandyDesign.Colors.SurfaceElevated,
                contentColor = HandyDesign.Colors.TextPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    HandyDesign.Dimens.CornerCard
                ),
                snackbarData = data,
            )
        }

        if (brainSheetOpen) {
            ModelPickerSheet(
                selectedModelId = selectedModelId,
                onSelect = { id ->
                    when (id) {
                        "sonnet-4-5" -> {
                            onClaudeModelVariant(false)
                            brainSheetOpen = false
                        }
                        "haiku-4-5" -> {
                            onClaudeModelVariant(true)
                            brainSheetOpen = false
                        }
                        else -> Unit
                    }
                },
                onDismiss = { brainSheetOpen = false },
            )
        }
    }
}

private tailrec fun Context.findSettingsActivity(): SettingsActivity? =
    when (this) {
        is SettingsActivity -> this
        is ContextWrapper -> baseContext.findSettingsActivity()
        else -> null
    }

private fun isRecordAudioGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
