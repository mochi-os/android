// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import org.mochios.android.model.Account
import org.mochios.forums.R
import org.mochios.forums.model.AiPrompts
import org.mochios.forums.model.AiSettings
import org.mochios.android.R as MochiR

/**
 * AI settings tab: a row-based form to pick the AI mode, account, and prompt
 * source. Only reachable when the account has at least one AI-capable account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTab(viewModel: ForumSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Load the prompt defaults in the background; the mode/account rows come from
    // the already-loaded forum row and render immediately.
    LaunchedEffect(Unit) { viewModel.loadAiPrompts() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Extra bottom padding so the last prompt's Save button can scroll up
            // clear of the screen edge instead of sitting flush against it.
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        AiSettingsContent(
            settings = AiSettings(
                mode = uiState.forum.aiMode,
                account = uiState.forum.aiAccount,
            ),
            prompts = uiState.aiPrompts,
            aiAccounts = uiState.aiAccounts,
            onSetSettings = { mode, account -> viewModel.setAiSettings(mode, account) },
            onSetPrompt = { type, prompt -> viewModel.setAiPrompt(type, prompt) },
        )
    }
}

@Composable
private fun AiSettingsContent(
    settings: AiSettings,
    prompts: AiPrompts?,
    aiAccounts: List<Account>,
    onSetSettings: (mode: String, account: String) -> Unit,
    onSetPrompt: (type: String, prompt: String) -> Unit,
) {
    val mode = settings.mode
    val account = settings.account
    val modes = listOf(
        "" to stringResource(R.string.forums_ai_mode_off),
        "tag" to stringResource(R.string.forums_ai_mode_tag),
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // AI actions on posts (mode)
        AiSettingRow(label = stringResource(R.string.forums_ai_actions_label)) {
            val modeLabel = modes.firstOrNull { entry -> entry.first == mode }?.second
                ?: stringResource(R.string.forums_ai_mode_off)
            DropdownField(value = modeLabel, options = modes.map { entry -> entry.second }) { index ->
                onSetSettings(modes[index].first, account)
            }
        }

        // The account and prompt rows only appear once AI is switched on (not off).
        if (mode.isNotEmpty()) {
            val defaultAccount = stringResource(R.string.forums_ai_account_default)
            AiSettingRow(label = stringResource(R.string.forums_ai_account_label)) {
                val accountLabel = when {
                    account.isEmpty() -> defaultAccount
                    else -> aiAccounts.firstOrNull { acc -> acc.id == account }?.displayLabel
                        ?: defaultAccount
                }
                val options = listOf(defaultAccount) + aiAccounts.map { acc -> acc.displayLabel }
                DropdownField(value = accountLabel, options = options) { index ->
                    onSetSettings(mode, if (index == 0) "" else aiAccounts[index - 1].id)
                }
            }

            if (prompts != null) {
                PromptField(
                    label = stringResource(R.string.forums_ai_prompt_tag_label),
                    stored = prompts.prompts["tag"].orEmpty(),
                    template = prompts.defaults["tag"].orEmpty(),
                    onSaveCustom = { text -> onSetPrompt("tag", text) },
                    onResetDefault = { onSetPrompt("tag", "") },
                )
                PromptField(
                    label = stringResource(R.string.forums_ai_prompt_score_label),
                    stored = prompts.prompts["score"].orEmpty(),
                    template = prompts.defaults["score"].orEmpty(),
                    onSaveCustom = { text -> onSetPrompt("score", text) },
                    onResetDefault = { onSetPrompt("score", "") },
                )
            }
        }
    }
}

/** Matches template placeholders like `{{posts}}` in a prompt. */
private val PROMPT_VARIABLE_REGEX = Regex("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}")

/** Distinct `{{name}}` placeholders found in [text], in first-seen order. */
private fun promptVariables(text: String): List<String> =
    PROMPT_VARIABLE_REGEX.findAll(text)
        .map { match -> match.groupValues[1] }
        .distinct()
        .toList()

/** Label + right-hand control laid out as a settings row (matches the AI mockup). */
@Composable
private fun AiSettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1.4f)) { control() }
    }
}

/** Read-only dropdown showing [value]; taps pick from [options] by index. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open -> expanded = open },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Prompt row with a Default/Custom dropdown.
 *
 * @param stored the saved custom prompt — empty when the forum uses the default.
 * @param template the server default, shown only as an editable starting point;
 *                 it is never persisted and never compared against [stored].
 *
 * Choosing Custom expands an inline editor (seeded with [stored] or [template]);
 * saving persists the edited text as the custom prompt. Choosing Default collapses
 * the editor and clears the custom prompt server-side (saves an empty value).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptField(
    label: String,
    stored: String,
    template: String,
    onSaveCustom: (String) -> Unit,
    onResetDefault: () -> Unit,
) {
    val custom = stored.isNotBlank()
    var editing by remember(custom) { mutableStateOf(custom) }
    val seed = if (custom) stored else template
    var draft by remember(seed) { mutableStateOf(seed) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AiSettingRow(label = label) {
            var expanded by remember { mutableStateOf(false) }
            val valueLabel = if (editing) {
                stringResource(R.string.forums_ai_prompt_custom)
            } else {
                stringResource(R.string.forums_ai_prompt_default)
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { open -> expanded = open },
            ) {
                OutlinedTextField(
                    value = valueLabel,
                    onValueChange = { },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.forums_ai_prompt_default)) },
                        onClick = {
                            expanded = false
                            editing = false
                            draft = template
                            onResetDefault()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.forums_ai_prompt_custom)) },
                        onClick = {
                            expanded = false
                            editing = true
                        },
                    )
                }
            }
        }

        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { text -> draft = text },
                minLines = 5,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onSaveCustom(draft) },
                    enabled = draft.isNotBlank() && draft != stored,
                ) {
                    Text(stringResource(MochiR.string.common_save))
                }
                val variables = promptVariables(draft)
                if (variables.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.forums_ai_prompt_variables,
                            variables.joinToString(", ") { name -> "{{$name}}" },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
