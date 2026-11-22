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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.theme.LocalSpacing

/**
 * A labelled single-choice field: a caption above a button that drops down the
 * choices. The selection cannot be cleared; an unset field shows no value.
 *
 * @param label Caption above the field.
 * @param options Choices as value-to-label pairs.
 * @param selected Selected value.
 * @param onSelect Called with the chosen value.
 */
@Composable
fun LabeledSelectField(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    icon: ImageVector? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { option -> option.first == selected }?.second.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.gap(6.dp))) {
        FieldLabel(text = label, icon = icon)
        Box {
            MochiOutlinedButton(
                onClick = { expanded = true },
                // Named by its caption, with the choice as its state: unset,
                // the button shows nothing of its own to be read.
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = label
                    stateDescription = current
                },
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

/** A field's label above it, led by [icon] when there is one. */
@Composable
fun FieldLabel(text: String, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.gap(6.dp)),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}
