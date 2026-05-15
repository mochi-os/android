package org.mochios.forums.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.forums.R
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTab(viewModel: ForumSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val prompts = uiState.aiPrompts
    val settings = uiState.aiSettings
    val aiAccounts = uiState.aiAccounts

    LaunchedEffect(Unit) { viewModel.loadAi() }

    if (prompts == null || settings == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Mode selector
        val modes = listOf("" to stringResource(R.string.forums_ai_mode_off), "tag" to stringResource(R.string.forums_ai_mode_tag))
        Text(stringResource(R.string.forums_ai_mode_heading), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        var modeExpanded by remember { mutableStateOf(false) }
        val modeLabel = modes.firstOrNull { it.first == settings.mode }?.second
            ?: stringResource(R.string.forums_ai_mode_off)
        ExposedDropdownMenuBox(expanded = modeExpanded, onExpandedChange = { modeExpanded = it }) {
            OutlinedTextField(
                value = modeLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false },
            ) {
                modes.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.setAiSettings(code, settings.account.toIntOrNull() ?: 0)
                            modeExpanded = false
                        },
                    )
                }
            }
        }

        // Account picker (only when mode is on)
        if (settings.mode.isNotEmpty() && aiAccounts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.forums_ai_account), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            var accountExpanded by remember { mutableStateOf(false) }
            val currentAccountId = settings.account.toIntOrNull() ?: 0
            val accountLabel = when (currentAccountId) {
                0 -> stringResource(R.string.forums_ai_account_default)
                else -> aiAccounts.firstOrNull { it.id == currentAccountId }?.displayLabel
                    ?: stringResource(R.string.forums_ai_account_default)
            }
            ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = it }) {
                OutlinedTextField(
                    value = accountLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = accountExpanded,
                    onDismissRequest = { accountExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.forums_ai_account_default)) },
                        onClick = {
                            viewModel.setAiSettings(settings.mode, 0)
                            accountExpanded = false
                        },
                    )
                    aiAccounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.displayLabel) },
                            onClick = {
                                viewModel.setAiSettings(settings.mode, acc.id)
                                accountExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // Prompts — server exposes "tag" and "score". Each prompt is edited
        // and saved independently per the server's setAiPrompt contract.
        if (settings.mode.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            PromptEditor(
                label = stringResource(R.string.forums_ai_prompt_tag),
                type = "tag",
                value = prompts.prompts["tag"] ?: prompts.defaults["tag"].orEmpty(),
                defaultValue = prompts.defaults["tag"].orEmpty(),
                onSave = { viewModel.setAiPrompt("tag", it) },
            )
            Spacer(Modifier.height(12.dp))
            PromptEditor(
                label = stringResource(R.string.forums_ai_prompt_score),
                type = "score",
                value = prompts.prompts["score"] ?: prompts.defaults["score"].orEmpty(),
                defaultValue = prompts.defaults["score"].orEmpty(),
                onSave = { viewModel.setAiPrompt("score", it) },
            )
        }
    }
}

@Composable
private fun PromptEditor(
    label: String,
    type: String,
    value: String,
    defaultValue: String,
    onSave: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSave(draft) },
                enabled = draft != value,
            ) {
                Text(stringResource(MochiR.string.common_save))
            }
            if (draft != defaultValue && defaultValue.isNotBlank()) {
                TextButton(onClick = {
                    draft = defaultValue
                    onSave(defaultValue)
                }) {
                    Text(stringResource(R.string.forums_ai_prompt_reset))
                }
            }
        }
    }
}
