// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.model.Account

/** Matches template placeholders like `{{posts}}` in a prompt. */
private val PROMPT_VARIABLE_REGEX = Regex("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}")

/** Distinct `{{name}}` placeholders found in [text], in first-seen order. */
private fun promptVariables(text: String): List<String> =
    PROMPT_VARIABLE_REGEX.findAll(text)
        .map { match -> match.groupValues[1] }
        .distinct()
        .toList()

/**
 * Label on the left, control on the right, as the AI settings tabs lay a row
 * out. The control gets the wider share of the row.
 *
 * @param label Name of the setting.
 * @param control The setting's control.
 */
@Composable
fun AiSettingRow(label: String, control: @Composable () -> Unit) {
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

/**
 * Read-only field showing [value]; tapping it picks from [options] by index.
 *
 * @param value Text currently shown in the field.
 * @param options Labels to choose between.
 * @param onSelect Called with the index of the chosen option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDropdownField(
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open -> expanded = open },
    ) {
        MochiTextField(
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
                MochiDropdownMenuItem(
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
 * An [AiSettingRow] whose control chooses one of [options] by its stored value,
 * as the AI mode and account rows do.
 *
 * @param label Name of the setting.
 * @param options Choices as stored-value-to-label pairs.
 * @param selected Stored value of the current choice.
 * @param fallbackLabel Shown when [selected] matches none of the [options].
 * @param onSelect Called with the stored value of the chosen option.
 */
@Composable
fun AiChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    fallbackLabel: String,
    onSelect: (String) -> Unit
) {
    AiSettingRow(label = label) {
        val selectedLabel = options.firstOrNull { entry -> entry.first == selected }?.second
            ?: fallbackLabel
        OptionDropdownField(
            value = selectedLabel,
            options = options.map { entry -> entry.second }
        ) { index ->
            onSelect(options[index].first)
        }
    }
}

/**
 * The account choices for an AI settings tab: the server's default ahead of
 * every account the viewer can use.
 *
 * @param defaultLabel Label of the entry that leaves the account unset.
 * @param accounts Accounts the viewer can pick.
 * @return Stored-value-to-label pairs for [AiChoiceRow].
 */
fun aiAccountOptions(defaultLabel: String, accounts: List<Account>): List<Pair<String, String>> =
    listOf("" to defaultLabel) + accounts.map { account -> account.id to account.displayLabel }

/**
 * Prompt row with a Default/Custom dropdown. [stored] is the saved custom
 * prompt (empty = default); [template] is the server default, an editing seed
 * only - never persisted or compared against [stored].
 *
 * @param label Name of the prompt.
 * @param stored Saved custom prompt, empty when the default is in use.
 * @param template Server's default prompt, used to seed a new custom one.
 * @param defaultLabel Label of the "default" choice.
 * @param customLabel Label of the "custom" choice.
 * @param variablesLabel Renders the note listing the `{{placeholders}}` a draft
 *   uses, given them already joined.
 * @param onSaveCustom Called with the draft when Save is pressed.
 * @param onResetDefault Called when the prompt is put back to the default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPromptField(
    label: String,
    stored: String,
    template: String,
    defaultLabel: String,
    customLabel: String,
    variablesLabel: (String) -> String,
    onSaveCustom: (String) -> Unit,
    onResetDefault: () -> Unit
) {
    val custom = stored.isNotBlank()
    var editing by remember(custom) { mutableStateOf(custom) }
    val seed = if (custom) stored else template
    var draft by remember(seed) { mutableStateOf(seed) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AiSettingRow(label = label) {
            var expanded by remember { mutableStateOf(false) }
            val valueLabel = if (editing) customLabel else defaultLabel
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { open -> expanded = open },
            ) {
                MochiTextField(
                    value = valueLabel,
                    onValueChange = { },
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    MochiDropdownMenuItem(
                        text = { Text(defaultLabel) },
                        onClick = {
                            expanded = false
                            editing = false
                            draft = template
                            onResetDefault()
                        },
                    )
                    MochiDropdownMenuItem(
                        text = { Text(customLabel) },
                        onClick = {
                            expanded = false
                            editing = true
                        },
                    )
                }
            }
        }

        if (editing) {
            MochiTextField(
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
                MochiButton(
                    onClick = { onSaveCustom(draft) },
                    enabled = draft.isNotBlank() && draft != stored,
                ) {
                    Text(stringResource(R.string.common_save))
                }
                val variables = promptVariables(draft)
                if (variables.isNotEmpty()) {
                    Text(
                        text = variablesLabel(
                            variables.joinToString(", ") { name -> "{{$name}}" }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
