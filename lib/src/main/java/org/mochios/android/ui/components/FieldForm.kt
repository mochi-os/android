// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A field type offered by [FieldForm].
 *
 * @property key Type key the server stores, such as `text` or `enumerated`.
 * @property label Translated name shown in the picker.
 */
data class FieldTypeOption(
    val key: String,
    val label: String
)

/**
 * Wording for [FieldForm], resolved by the feature from its own strings.
 *
 * @property name Label of the name field.
 * @property type Label of the type picker.
 * @property required Label of the required flag.
 * @property readonly Label of the read-only flag.
 * @property sortable Label of the sortable flag.
 * @property filterable Label of the filterable flag.
 * @property multi Label of the multi-select flag, shown for enumerated fields.
 */
data class FieldFormLabels(
    val name: String,
    val type: String,
    val required: String,
    val readonly: String,
    val sortable: String,
    val filterable: String,
    val multi: String
)

/**
 * Values of a new field: name, type and flags. Hold it with
 * [rememberFieldFormState] so the submit button can read it.
 */
@Stable
class FieldFormState {

    /** Name typed into the form. */
    var name by mutableStateOf("")

    /** Type key picked, `text` until changed. */
    var fieldtype by mutableStateOf("text")

    /** Whether objects must fill the field in. */
    var isRequired by mutableStateOf(false)

    /** Whether the field can only be set by the server. */
    var isReadonly by mutableStateOf(false)

    /** Whether views may sort on the field. */
    var isSortable by mutableStateOf(false)

    /** Whether views may filter on the field. */
    var isFilterable by mutableStateOf(false)

    /** Whether an enumerated field takes several values. */
    var isMulti by mutableStateOf(false)

    /** Whether the form holds enough to save. */
    val canSave: Boolean
        get() = name.isNotBlank()

    /** The flags as the server's comma-separated list, or null for none. */
    fun flags(): String? = buildList {
        if (isRequired) {
            add("required")
        }
        if (isReadonly) {
            add("readonly")
        }
        if (isSortable) {
            add("sort")
        }
        if (isFilterable) {
            add("filter")
        }
    }.joinToString(",").ifEmpty { null }

    /** True for a multi-select enumerated field, null for anything else. */
    fun multi(): Boolean? = if (fieldtype == "enumerated" && isMulti) true else null

    companion object {

        /** Keeps the form's values across configuration changes. */
        val Saver = listSaver<FieldFormState, Any>(
            save = { state ->
                listOf(
                    state.name,
                    state.fieldtype,
                    state.isRequired,
                    state.isReadonly,
                    state.isSortable,
                    state.isFilterable,
                    state.isMulti
                )
            },
            restore = { values ->
                FieldFormState().apply {
                    name = values[0] as String
                    fieldtype = values[1] as String
                    isRequired = values[2] as Boolean
                    isReadonly = values[3] as Boolean
                    isSortable = values[4] as Boolean
                    isFilterable = values[5] as Boolean
                    isMulti = values[6] as Boolean
                }
            }
        )
    }
}

/**
 * Remembers a [FieldFormState] that survives configuration changes.
 *
 * @return The remembered form state.
 */
@Composable
fun rememberFieldFormState(): FieldFormState =
    rememberSaveable(saver = FieldFormState.Saver) { FieldFormState() }

/**
 * The fields of a new class field: name, a type picker and the flags, with
 * multi-select offered once the type is enumerated. Does not scroll.
 *
 * @param state Values the form edits.
 * @param types Types to offer, in picker order.
 * @param labels Feature-specific wording.
 * @param modifier Modifier applied to the form's column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldForm(
    state: FieldFormState,
    types: List<FieldTypeOption>,
    labels: FieldFormLabels,
    modifier: Modifier = Modifier
) {
    var typeExpanded by remember { mutableStateOf(false) }
    val typeLabel = types.find { type -> type.key == state.fieldtype }?.label ?: state.fieldtype

    Column(modifier = modifier) {
        MochiTextField(
            value = state.name,
            onValueChange = { value -> state.name = value },
            label = { Text(labels.name) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { expanded -> typeExpanded = expanded }
        ) {
            MochiTextField(
                value = typeLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(labels.type) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false }
            ) {
                types.forEach { type ->
                    MochiDropdownMenuItem(
                        text = { Text(type.label) },
                        onClick = {
                            state.fieldtype = type.key
                            typeExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        CheckboxRow(
            label = labels.required,
            checked = state.isRequired,
            onCheckedChange = { checked -> state.isRequired = checked }
        )
        CheckboxRow(
            label = labels.readonly,
            checked = state.isReadonly,
            onCheckedChange = { checked -> state.isReadonly = checked }
        )
        CheckboxRow(
            label = labels.sortable,
            checked = state.isSortable,
            onCheckedChange = { checked -> state.isSortable = checked }
        )
        CheckboxRow(
            label = labels.filterable,
            checked = state.isFilterable,
            onCheckedChange = { checked -> state.isFilterable = checked }
        )
        if (state.fieldtype == "enumerated") {
            CheckboxRow(
                label = labels.multi,
                checked = state.isMulti,
                onCheckedChange = { checked -> state.isMulti = checked }
            )
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
