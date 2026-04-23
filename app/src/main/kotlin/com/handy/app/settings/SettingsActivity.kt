package com.handy.app.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.core.model.AssistantMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HandyTheme(darkTheme = true) {
                val state by viewModel.state.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // Surface one-shot save / clear toasts (DL-007). `collect`
                // on the VM's SharedFlow — since `replay = 0`, rotations
                // don't replay "saved" when the user comes back.
                LaunchedEffect(Unit) {
                    viewModel.messages.collect { text ->
                        snackbarHostState.showSnackbar(text)
                    }
                }

                @OptIn(ExperimentalMaterial3Api::class)
                SettingsScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onClaudeKeyChange = viewModel::setClaudeKey,
                    onBraveKeyChange = viewModel::setBraveKey,
                    onWebSearchToggle = { enabled ->
                        viewModel.updateSettings { it.copy(webSearchEnabled = enabled) }
                    },
                    onAssistantModeChange = { mode ->
                        viewModel.updateSettings { it.copy(assistantMode = mode) }
                    },
                    onClearHistory = viewModel::clearAllHistory,
                    onBack = { finish() },
                )
            }
        }
    }
}

@ExperimentalMaterial3Api
@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onClaudeKeyChange: (String) -> Unit,
    onBraveKeyChange: (String) -> Unit,
    onWebSearchToggle: (Boolean) -> Unit,
    onAssistantModeChange: (AssistantMode) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HandyColors.Background,
        contentColor = HandyColors.TextPrimary,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = HandyColors.Accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HandyColors.Background,
                    titleContentColor = HandyColors.TextPrimary,
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                // Dark snackbar that fits the Apple-class theme — the
                // default Material3 snackbar is nearly-white on our
                // background and feels jarring.
                Snackbar(
                    containerColor = HandyColors.SurfaceElevated,
                    contentColor = HandyColors.TextPrimary,
                    snackbarData = data,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(HandyDimens.Space16),
            verticalArrangement = Arrangement.spacedBy(HandyDimens.Space16),
        ) {
                SectionHeader(stringResource(R.string.settings_api_key_header))
                CredentialField(
                    label = stringResource(R.string.settings_anthropic_label),
                    placeholder = stringResource(R.string.settings_anthropic_placeholder),
                    savedMasked = state.claudeKeyMasked,
                    onCommit = onClaudeKeyChange,
                )
                CredentialField(
                    label = stringResource(R.string.settings_brave_label),
                    placeholder = "brv-…",
                    savedMasked = state.braveKeyMasked,
                    onCommit = onBraveKeyChange,
                )

                HorizontalDivider(color = HandyColors.Border)

                ToggleRow(
                    title = stringResource(R.string.settings_web_search_label),
                    description = stringResource(R.string.settings_web_search_description),
                    checked = state.settings?.webSearchEnabled == true,
                    onCheckedChange = onWebSearchToggle,
                )

                HorizontalDivider(color = HandyColors.Border)

                SectionHeader(stringResource(R.string.settings_assistant_mode_header))
                state.assistantModes.forEach { mode ->
                    ModeRow(
                        mode = mode,
                        selected = state.settings?.assistantMode == mode,
                        onSelect = { onAssistantModeChange(mode) },
                    )
                }

                HorizontalDivider(color = HandyColors.Border)

            TextButton(onClick = onClearHistory) {
                Text(
                    text = stringResource(R.string.settings_reset_history),
                    color = HandyColors.Danger,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = HandyColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = HandyDimens.Space8),
    )
}

/**
 * Credential entry field with three must-haves (DL-006 / DL-007):
 *  - an in-field Paste IconButton that reads `LocalClipboardManager`
 *    directly, bypassing the emulator's flaky long-press paste;
 *  - a show / hide visibility toggle so the user can verify what they
 *    pasted before committing;
 *  - a persistent "Saved" badge above the field showing the masked
 *    preview ([savedMasked], non-null when a credential is already on
 *    disk) so the user is never left wondering whether Save worked.
 *
 * The raw value is scrubbed from UI state after commit — we never keep
 * plaintext credentials in Compose state.
 */
@ExperimentalMaterial3Api
@Composable
private fun CredentialField(
    label: String,
    placeholder: String,
    savedMasked: String?,
    onCommit: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(HandyDimens.Space8)) {
        if (savedMasked != null) {
            SavedBadge(masked = savedMasked)
        }

        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(label) },
            placeholder = { Text(placeholder, color = HandyColors.TextSecondary) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            trailingIcon = {
                Row {
                    IconButton(onClick = {
                        val pasted = clipboard.getText()?.text?.trim().orEmpty()
                        if (pasted.isNotEmpty()) value = pasted
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = "Paste from clipboard",
                            tint = HandyColors.TextSecondary,
                        )
                    }
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (visible) "Hide value" else "Show value",
                            tint = HandyColors.TextSecondary,
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = HandyColors.Surface,
                unfocusedContainerColor = HandyColors.Surface,
                disabledContainerColor = HandyColors.Surface,
                focusedTextColor = HandyColors.TextPrimary,
                unfocusedTextColor = HandyColors.TextPrimary,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val canSave = value.isNotBlank()
            TextButton(
                onClick = {
                    onCommit(value)
                    value = ""
                    visible = false
                },
                enabled = canSave,
            ) {
                Text(
                    text = if (savedMasked != null) "Update" else "Save",
                    color = if (canSave) HandyColors.Accent else HandyColors.TextSecondary,
                )
            }
            if (savedMasked != null) {
                TextButton(
                    onClick = {
                        // Clears the stored key — the VM distinguishes
                        // blank-raw → `KeyStore.remove(…)`.
                        onCommit("")
                        value = ""
                        visible = false
                    },
                ) {
                    Text("Remove", color = HandyColors.Danger)
                }
            }
        }
    }
}

@Composable
private fun SavedBadge(masked: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
        modifier = Modifier
            .background(
                color = HandyColors.SurfaceElevated,
                shape = RoundedCornerShape(HandyDimens.RadiusSm),
            )
            .padding(horizontal = HandyDimens.Space12, vertical = HandyDimens.Space8),
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = HandyColors.Success,
            modifier = Modifier.height(18.dp),
        )
        Text(
            text = "Saved",
            color = HandyColors.Success,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(Modifier.weight(1f))
        Text(
            text = masked,
            color = HandyColors.TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HandyColors.TextPrimary)
            Spacer(Modifier.height(HandyDimens.Space4))
            Text(description, color = HandyColors.TextSecondary, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModeRow(mode: AssistantMode, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        color = if (selected) HandyColors.SurfaceElevated else HandyColors.Surface,
        contentColor = HandyColors.TextPrimary,
        shape = RoundedCornerShape(HandyDimens.RadiusMd),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(HandyDimens.Space12),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mode.displayName, color = HandyColors.TextPrimary)
                Text(mode.description, color = HandyColors.TextSecondary, fontSize = 13.sp)
            }
            TextButton(onClick = onSelect) {
                Text(if (selected) "Selected" else "Select", color = HandyColors.Accent)
            }
        }
    }
}
