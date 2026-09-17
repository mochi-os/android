// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Wording for [OptionForm], resolved by the feature from its own strings.
 *
 * @property name Label of the name field.
 * @property colour Heading above the colour picker.
 * @property icon Heading above the icon field.
 * @property iconPlaceholder Placeholder of the icon field.
 */
data class OptionFormLabels(
    val name: String,
    val colour: String,
    val icon: String,
    val iconPlaceholder: String
)

/**
 * Values of an enumerated field's option. Hold it with
 * [rememberOptionFormState] so the submit button can read it.
 *
 * @param initialColour Colour a new option starts on, blank for none.
 */
@Stable
class OptionFormState(initialColour: String = "") {

    /** Name typed into the form. */
    var name by mutableStateOf("")

    /** Hex colour picked, blank for none. */
    var colour by mutableStateOf(initialColour)

    /** Icon name typed into the form, blank for none. */
    var icon by mutableStateOf("")

    /** Whether the form has been filled from an existing option. */
    var isSeeded by mutableStateOf(false)
        private set

    /** Whether the form holds enough to save. */
    val canSave: Boolean
        get() = name.isNotBlank()

    /**
     * Fills the form from an existing option, once, so a later reload of that
     * option does not overwrite what the user has typed since.
     */
    fun seed(name: String, colour: String, icon: String) {
        if (isSeeded) {
            return
        }
        this.name = name
        this.colour = colour
        this.icon = icon
        isSeeded = true
    }

    companion object {

        /** Keeps the form's values across configuration changes. */
        val Saver = listSaver<OptionFormState, Any>(
            save = { state -> listOf(state.name, state.colour, state.icon, state.isSeeded) },
            restore = { values ->
                OptionFormState().apply {
                    name = values[0] as String
                    colour = values[1] as String
                    icon = values[2] as String
                    isSeeded = values[3] as Boolean
                }
            }
        )
    }
}

/**
 * Remembers an [OptionFormState] that survives configuration changes.
 *
 * @param initialColour Colour a new option starts on, blank for none.
 * @return The remembered form state.
 */
@Composable
fun rememberOptionFormState(initialColour: String = ""): OptionFormState =
    rememberSaveable(saver = OptionFormState.Saver) { OptionFormState(initialColour) }

/**
 * The fields of an enumerated field's option: name, colour and, unless
 * [showIcon] is off, an icon name. A board column is an option drawn without
 * its icon, so its form hides that field. Does not scroll.
 *
 * @param state Values the form edits.
 * @param labels Feature-specific wording.
 * @param modifier Modifier applied to the form's column.
 * @param showIcon Whether to offer the icon field.
 */
@Composable
fun OptionForm(
    state: OptionFormState,
    labels: OptionFormLabels,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true
) {
    Column(modifier = modifier) {
        MochiTextField(
            value = state.name,
            onValueChange = { value -> state.name = value },
            label = { Text(labels.name) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(labels.colour, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        ColorPicker(
            hex = state.colour,
            onHexChange = { hex -> state.colour = hex },
        )
        if (showIcon) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(labels.icon, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            MochiTextField(
                value = state.icon,
                onValueChange = { value -> state.icon = value },
                placeholder = { Text(labels.iconPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
