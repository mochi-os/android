// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A list filter: a button reading `label: value` that drops down the choices,
 * with an "any" entry at the top that clears the filter.
 *
 * @param label Name of the thing being filtered, shown before the value.
 * @param current Selected value, or null when the filter is off.
 * @param options Choices as value-to-label pairs.
 * @param anyLabel Label of the entry that clears the filter.
 * @param onSelect Called with the chosen value, or null for [anyLabel].
 * @param modifier Modifier applied to the control.
 */
@Composable
fun FilterDropdown(
    label: String,
    current: String?,
    options: List<Pair<String, String>>,
    anyLabel: String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { option -> option.first == current }?.second ?: anyLabel

    Box(modifier = modifier) {
        MochiOutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "$label: $currentLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        MochiDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MochiDropdownMenuItem(
                text = { Text(anyLabel) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
                selected = current == null,
            )
            options.forEach { (value, optionLabel) ->
                MochiDropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                    selected = current == value,
                )
            }
        }
    }
}

/**
 * A labelled single-choice field: a caption above a button that drops down the
 * choices. Unlike [FilterDropdown] the selection cannot be cleared, so an unset
 * field reads as [placeholder].
 *
 * @param label Caption above the field.
 * @param placeholder Shown while [selected] matches none of the [options].
 * @param options Choices as value-to-label pairs.
 * @param selected Selected value.
 * @param onSelect Called with the chosen value.
 */
@Composable
fun LabeledSelectField(
    label: String,
    placeholder: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { option -> option.first == selected }?.second ?: placeholder

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        Box {
            MochiOutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = current, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            MochiDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, optionLabel) ->
                    MochiDropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                        selected = selected == value,
                    )
                }
            }
        }
    }
}

/**
 * A detail row pushing a small caption and its value to opposite ends, for the
 * metadata a review dialog shows above its controls.
 *
 * @param label Caption on the left.
 * @param value Value on the right, wrapping to at most two lines.
 */
@Composable
fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
