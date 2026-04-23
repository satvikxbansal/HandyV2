package com.handy.app.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
                @OptIn(ExperimentalMaterial3Api::class)
                SettingsScreen(
                    state = state,
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
    onClaudeKeyChange: (String) -> Unit,
    onBraveKeyChange: (String) -> Unit,
    onWebSearchToggle: (Boolean) -> Unit,
    onAssistantModeChange: (AssistantMode) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        color = HandyColors.Background,
        contentColor = HandyColors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(HandyDimens.Space16),
                verticalArrangement = Arrangement.spacedBy(HandyDimens.Space16),
            ) {
                SectionHeader(stringResource(R.string.settings_api_key_header))
                PasswordField(
                    label = stringResource(R.string.settings_anthropic_label),
                    placeholder = state.claudeKeyMasked ?: stringResource(R.string.settings_anthropic_placeholder),
                    onCommit = onClaudeKeyChange,
                )
                PasswordField(
                    label = stringResource(R.string.settings_brave_label),
                    placeholder = state.braveKeyMasked ?: "",
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

@ExperimentalMaterial3Api
@Composable
private fun PasswordField(
    label: String,
    placeholder: String,
    onCommit: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        placeholder = { Text(placeholder, color = HandyColors.TextSecondary) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = HandyColors.Surface,
            unfocusedContainerColor = HandyColors.Surface,
            disabledContainerColor = HandyColors.Surface,
            focusedTextColor = HandyColors.TextPrimary,
            unfocusedTextColor = HandyColors.TextPrimary,
        ),
    )
    TextButton(onClick = { onCommit(value); value = "" }) { Text("Save", color = HandyColors.Accent) }
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
