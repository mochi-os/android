// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import org.mochios.feeds.R
import org.mochios.android.R as MochiR

/**
 * AI settings tab: a row-based form to pick the AI processing mode, account, and
 * per-prompt source (default or custom). Only reachable when the account has at
 * least one AI-capable account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTab(viewModel: FeedSettingsViewModel) {
    val aiMode by viewModel.aiMode.collectAsState()
    val aiAccount by viewModel.aiAccount.collectAsState()
    val aiAccounts by viewModel.aiAccounts.collectAsState()
    val aiOverrides by viewModel.aiOverrides.collectAsState()
    val aiDefaults by viewModel.aiDefaults.collectAsState()

    // Load the prompt defaults in the background; the mode/account rows come from
    // the already-loaded feed row and render immediately.
    LaunchedEffect(Unit) { viewModel.loadAiPrompts() }

    val modes = listOf(
        "" to stringResource(R.string.feeds_ai_mode_off),
        "tag" to stringResource(R.string.feeds_ai_mode_tag),
        "tag+deduplicate" to stringResource(R.string.feeds_ai_mode_tag_deduplicate),
    )
    val promptTypes = listOf(
        "new" to stringResource(R.string.feeds_ai_prompt_new),
        "batch" to stringResource(R.string.feeds_ai_prompt_batch),
        "rank" to stringResource(R.string.feeds_ai_prompt_rank),
        "credibility" to stringResource(R.string.feeds_ai_prompt_credibility),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // AI processing mode
        AiSettingRow(label = stringResource(R.string.feeds_ai_processing_mode)) {
            val modeLabel = modes.firstOrNull { entry -> entry.first == aiMode }?.second
                ?: stringResource(R.string.feeds_ai_mode_off)
            DropdownField(value = modeLabel, options = modes.map { entry -> entry.second }) { index ->
                viewModel.setAiMode(modes[index].first)
            }
        }

        // Account and prompt rows only appear once AI is switched on (not off).
        if (aiMode.isNotEmpty()) {
            val defaultAccount = stringResource(R.string.feeds_ai_account_default)
            AiSettingRow(label = stringResource(R.string.feeds_ai_account)) {
                val accountLabel = when {
                    aiAccount.isEmpty() -> defaultAccount
                    else -> aiAccounts.firstOrNull { acc -> acc.id == aiAccount }?.displayLabel
                        ?: defaultAccount
                }
                val options = listOf(defaultAccount) + aiAccounts.map { acc -> acc.displayLabel }
                DropdownField(value = accountLabel, options = options) { index ->
                    viewModel.setAiAccount(if (index == 0) "" else aiAccounts[index - 1].id)
                }
            }

            promptTypes.forEach { (type, label) ->
                PromptField(
                    label = label,
                    stored = aiOverrides[type].orEmpty(),
                    template = aiDefaults[type].orEmpty(),
                    onSaveCustom = { text -> viewModel.saveAiPrompt(type, text) },
                    onResetDefault = { viewModel.resetAiPrompt(type) },
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
 * @param stored the saved custom prompt — empty when the feed uses the default.
 * @param template the server default, shown only as an editable starting point;
 *                 it is never persisted and never compared against [stored].
 *
 * Choosing Custom expands an inline editor (seeded with [stored] or [template]);
 * saving persists the edited text as the custom prompt. Choosing Default collapses
 * the editor and clears the custom prompt server-side.
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
                stringResource(R.string.feeds_ai_prompt_custom)
            } else {
                stringResource(R.string.feeds_ai_prompt_default)
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
                        text = { Text(stringResource(R.string.feeds_ai_prompt_default)) },
                        onClick = {
                            expanded = false
                            editing = false
                            draft = template
                            onResetDefault()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feeds_ai_prompt_custom)) },
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
                            R.string.feeds_ai_prompt_variables,
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
