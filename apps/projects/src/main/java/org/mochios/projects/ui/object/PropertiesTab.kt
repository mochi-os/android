// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.mochios.android.model.User
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.PersonPicker
import org.mochios.projects.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.projects.model.ChecklistItem
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.ProjectField
import org.mochios.projects.model.ProjectObject
import org.mochios.android.R as MochiR

@Composable
fun PropertiesTab(
    obj: ProjectObject,
    projectDetails: ProjectDetails,
    viewModel: ObjectDetailViewModel,
    /**
     * Field ids the active view pins. Empty means the view pins none, so every
     * field of the object's class is shown instead.
     */
    viewFieldIds: List<String> = emptyList(),
    onAddChild: () -> Unit = {},
    onNavigateToObject: (String) -> Unit = {},
    projectId: String = "",
) {
    val uiState by viewModel.uiState.collectAsState()
    val fields = projectDetails.fields[obj.objectClass] ?: emptyList()
    val classOptions = projectDetails.options[obj.objectClass] ?: emptyMap()
    val canWrite = canWriteAccess(uiState.access)
    val titleFieldId = projectDetails.classes.find { cls -> cls.id == obj.objectClass }?.title
        .orEmpty()
    // "Can this object have children?" — true when at least one class
    // lists obj.objectClass in its allowed parent classes.
    val canHaveChildren = remember(projectDetails.hierarchy, obj.objectClass) {
        projectDetails.hierarchy.any { (_, parents) -> obj.objectClass in parents }
    }
    // Which fields the form shows: the active view's selection when it pins
    // any, otherwise the whole class. Either way they run in rank order, the
    // same ordering the board cards use. The title field is always kept — a
    // view that leaves it out would otherwise make the object's name
    // uneditable, since nothing else in the sheet edits it.
    val visibleFields = remember(fields, viewFieldIds, titleFieldId) {
        val pinned = viewFieldIds.toSet()
        fields
            .filter { field ->
                pinned.isEmpty() || field.id in pinned || field.id == titleFieldId
            }
            .sortedBy { field -> field.rank }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .padding(top = 8.dp)
    ) {
        val allowedParentClasses = (projectDetails.hierarchy[obj.objectClass] ?: emptyList())
            .filter { it.isNotBlank() }
        val descendants = remember(uiState.siblingObjects, obj.id) {
            collectDescendants(uiState.siblingObjects, obj.id)
        }
        val parentOptions = uiState.siblingObjects
            .filter { it.objectClass in allowedParentClasses && it.id !in descendants }
        val currentParent = uiState.siblingObjects.find { it.id == obj.parent }
        val showParent = parentOptions.isNotEmpty() || currentParent != null
        val parentLabel = stringResource(R.string.projects_parent_label)

        // The parent picker sits directly under the title field, matching the
        // web form. With no title field among the visible ones it leads instead
        // — `parentPending` tracks whether it still owes a slot.
        var parentPending = showParent
        if (parentPending && visibleFields.none { field -> field.id == titleFieldId }) {
            PropertyRow(label = parentLabel) {
                ParentPicker(
                    projectDetails = projectDetails,
                    currentParent = currentParent,
                    parentOptions = parentOptions,
                    canWrite = canWrite,
                    onSelect = { newParent -> viewModel.updateParent(newParent) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            parentPending = false
        }

        visibleFields.forEach { field ->
            PropertyRow(label = field.name) {
                FieldEditor(
                    field = field,
                    value = obj.values[field.id],
                    options = classOptions[field.id] ?: emptyList(),
                    canWrite = canWrite,
                    people = uiState.people,
                    showLabel = false,
                    onValueChange = { viewModel.setValue(field.id, it) },
                    onMultiValueChange = { viewModel.setMultiValue(field.id, it) },
                    onSearchUsers = { query -> viewModel.searchPeople(query) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (parentPending && field.id == titleFieldId) {
                PropertyRow(label = parentLabel) {
                    ParentPicker(
                        projectDetails = projectDetails,
                        currentParent = currentParent,
                        parentOptions = parentOptions,
                        canWrite = canWrite,
                        onSelect = { newParent -> viewModel.updateParent(newParent) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                parentPending = false
            }
        }

        // "Add child" affordance — shown when this object's class is
        // listed as an allowed parent in any other class's hierarchy,
        // and the user has write access. Tap routes through ProjectScreen
        // to open CreateObjectDialog with parent pre-selected to this
        // object's id.
        if (canHaveChildren && canWrite) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAddChild,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.projects_add_child))
            }
        }

        // Attachments section — inlined here to match web's
        // object-detail-panel layout (attachments live inside Properties,
        // not as a separate tab).
        if (projectId.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            AttachmentsSection(
                attachments = uiState.attachments,
                projectId = projectId,
                onAddAttachment = { uri -> viewModel.createAttachment(uri) },
                onDeleteAttachment = { id -> viewModel.deleteAttachment(id) },
            )

            // Links section — same web-parity reasoning as Attachments.
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            LinksSection(
                obj = obj,
                projectDetails = projectDetails,
                viewModel = viewModel,
                onNavigateToObject = onNavigateToObject,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

internal fun canWriteAccess(access: String): Boolean =
    access == "owner" || access == "design" || access == "write"

/** Width of the label column in the object-detail form. */
private val PROPERTY_LABEL_WIDTH = 96.dp

/**
 * One row of the object-detail form: the property's name in a fixed-width
 * column on the left, its editor filling the rest — the same shape as the web
 * object-detail panel. The label is padded down so it sits against the middle
 * of a single-line text field rather than its top edge.
 */
@Composable
private fun PropertyRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(PROPERTY_LABEL_WIDTH)
                .padding(top = 18.dp, end = 8.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

private fun collectDescendants(objects: List<ProjectObject>, rootId: String): Set<String> {
    val result = mutableSetOf<String>()
    fun walk(id: String) {
        if (id in result) return
        result.add(id)
        for (o in objects) {
            if (o.parent == id) walk(o.id)
        }
    }
    walk(rootId)
    return result
}

private fun objectDisplayTitle(obj: ProjectObject, projectDetails: ProjectDetails): String {
    val cls = projectDetails.classes.find { it.id == obj.objectClass }
    val titleField = cls?.title.orEmpty()
    val titleVal = if (titleField.isNotBlank()) obj.values[titleField]?.toString().orEmpty() else ""
    if (titleVal.isNotBlank()) return titleVal
    val prefix = projectDetails.project.prefix
    return if (prefix.isNotBlank()) "$prefix-${obj.number}" else "#${obj.number}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentPicker(
    projectDetails: ProjectDetails,
    currentParent: ProjectObject?,
    parentOptions: List<ProjectObject>,
    canWrite: Boolean,
    onSelect: (String) -> Unit
) {
    val noParentLabel = stringResource(R.string.projects_parent_none)
    val displayText = currentParent?.let { objectDisplayTitle(it, projectDetails) } ?: noParentLabel

    // The label lives in the enclosing PropertyRow, so nothing here repeats it.
    if (!canWrite) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = ""
            }
        ) {
            // Search filter
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.projects_parent_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            // (no parent) option
            MochiDropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.projects_parent_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    onSelect("")
                    expanded = false
                    query = ""
                },
            )
            val q = query.trim().lowercase()
            parentOptions
                .map { it to objectDisplayTitle(it, projectDetails) }
                .filter { (_, title) -> q.isEmpty() || title.lowercase().contains(q) }
                .forEach { (parentObj, title) ->
                    MochiDropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            onSelect(parentObj.id)
                            expanded = false
                            query = ""
                        },
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FieldEditor(
    field: ProjectField,
    value: Any?,
    options: List<FieldOption>,
    canWrite: Boolean,
    people: List<org.mochios.projects.model.Person>,
    /**
     * Whether the editor draws the field's name itself. False in the
     * object-detail form, where the name already sits in the label column of
     * the enclosing row; true for the create dialog's stacked layout.
     */
    showLabel: Boolean = true,
    onValueChange: (String) -> Unit,
    onMultiValueChange: (List<String>) -> Unit,
    onSearchUsers: suspend (String) -> List<User>
) {
    val stringValue = value?.toString() ?: ""
    val listValue = (value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
    // Effective read-only: field-readonly OR user lacks write access
    val readOnly = field.isReadonly || !canWrite
    val fieldLabel: (@Composable () -> Unit)? = if (showLabel) {
        { Text(field.name) }
    } else {
        null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        when (field.fieldtype) {
            "text" -> {
                if (readOnly) {
                    ReadOnlyDisplay(labelOrNull(showLabel, field.name), stringValue)
                } else {
                    OutlinedTextField(
                        value = stringValue,
                        onValueChange = onValueChange,
                        label = fieldLabel,
                        readOnly = false,
                        singleLine = field.rows <= 1,
                        maxLines = if (field.rows > 1) field.rows else 1,
                        minLines = if (field.rows > 1) field.rows.coerceAtMost(3) else 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "number" -> {
                if (readOnly) {
                    ReadOnlyDisplay(labelOrNull(showLabel, field.name), stringValue)
                } else {
                    OutlinedTextField(
                        value = stringValue,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal.toDoubleOrNull() != null) {
                                onValueChange(newVal)
                            }
                        },
                        label = fieldLabel,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "enumerated" -> {
                if (field.isMulti) {
                    // Multi-select chips
                    if (showLabel) {
                        Text(
                            text = field.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (readOnly) {
                        val selectedNames = options
                            .filter { it.id in listValue || it.id == stringValue }
                            .sortedBy { it.rank }
                            .joinToString(", ") { it.name }
                        Text(
                            text = if (selectedNames.isBlank()) "—" else selectedNames,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            options.sortedBy { it.rank }.forEach { option ->
                                val isSelected = option.id in listValue || option.id == stringValue
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val current = listValue.toMutableList()
                                        if (isSelected) {
                                            current.remove(option.id)
                                        } else {
                                            current.add(option.id)
                                        }
                                        onMultiValueChange(current)
                                    },
                                    label = { Text(option.name) }
                                )
                            }
                        }
                    }
                } else {
                    // Single select dropdown
                    val selectedOption = options.find { it.id == stringValue }
                    if (readOnly) {
                        ReadOnlyDisplay(labelOrNull(showLabel, field.name), selectedOption?.name.orEmpty())
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedOption?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = fieldLabel,
                                placeholder = { Text(stringResource(R.string.projects_property_select)) },
                                leadingIcon = if (selectedOption != null && selectedOption.colour.isNotBlank()) {
                                    { OptionColourSwatch(selectedOption.colour) }
                                } else {
                                    null
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                options.sortedBy { it.rank }.forEach { option ->
                                    MochiDropdownMenuItem(
                                        text = { Text(option.name) },
                                        onClick = {
                                            onValueChange(option.id)
                                            expanded = false
                                        },
                                        leadingIcon = if (option.colour.isNotBlank()) {
                                            { OptionColourSwatch(option.colour) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "user" -> {
                if (showLabel) {
                    Text(
                        text = field.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val resolvedName = people.find { it.id == stringValue }?.name
                if (readOnly) {
                    val displayName = when {
                        stringValue.isBlank() -> "—"
                        !resolvedName.isNullOrBlank() -> resolvedName
                        else -> stringValue
                    }
                    Text(text = displayName, style = MaterialTheme.typography.bodyLarge)
                } else {
                    // Person.id round-trips through User.fingerprint (User.id is
                    // a numeric Int), matching the search adapter's mapping.
                    val members = people.map { person ->
                        User(id = 0, name = person.name, fingerprint = person.id)
                    }
                    PersonPicker(
                        selectedId = stringValue,
                        selectedName = resolvedName,
                        members = members,
                        onSelect = { user ->
                            val entityId = user.fingerprint.orEmpty()
                            if (entityId.isNotBlank()) onValueChange(entityId)
                        },
                        onClear = { onValueChange("") },
                        onSearch = onSearchUsers,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "date" -> {
                var showDatePicker by remember { mutableStateOf(false) }
                val format = LocalFormat.current
                val dateSeconds = dateFieldSeconds(stringValue)
                val displayDate = when {
                    stringValue.isBlank() -> ""
                    dateSeconds != null -> format.formatDate(dateSeconds)
                    else -> stringValue
                }

                if (readOnly) {
                    ReadOnlyDisplay(labelOrNull(showLabel, field.name), displayDate)
                } else {
                    // A read-only text field swallows taps, so an overlay on top
                    // makes the whole box (not just the icon) open the picker.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = displayDate,
                            onValueChange = {},
                            readOnly = true,
                            label = fieldLabel,
                            trailingIcon = {
                                Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.projects_property_pick_date))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showDatePicker = true }
                        )
                    }

                    if (showDatePicker) {
                        // Reroute the date picker through a Configuration with a
                        // locale that produces the user's preferred first-day-of-week.
                        // Material 3 1.3.x DatePicker doesn't accept a firstDayOfWeek
                        // parameter directly, so we lean on the calendar locale.
                        val weekStartsOn = LocalFormat.current.preferences.weekStartsOn
                        val baseConfig = androidx.compose.ui.platform.LocalConfiguration.current
                        val localizedConfig = remember(baseConfig, weekStartsOn) {
                            android.content.res.Configuration(baseConfig).apply {
                                setLocale(localeForWeekStart(weekStartsOn))
                            }
                        }
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalConfiguration provides localizedConfig
                        ) {
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis = dateSeconds?.times(1000)
                            )
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val selectedMillis = datePickerState.selectedDateMillis
                                        if (selectedMillis != null) {
                                            // The server's values endpoint expects an ISO
                                            // date string (yyyy-MM-dd), not epoch seconds.
                                            onValueChange(
                                                java.time.LocalDate
                                                    .ofEpochDay(selectedMillis / 86_400_000L)
                                                    .toString()
                                            )
                                        }
                                        showDatePicker = false
                                    }) {
                                        Text(stringResource(R.string.projects_property_ok))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDatePicker = false }) {
                                        Text(stringResource(MochiR.string.common_cancel))
                                    }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }
                    }
                }
            }

            "checkbox" -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val checked = stringValue == "1" || stringValue.equals("true", ignoreCase = true)
                    if (readOnly) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (showLabel) {
                                Text(
                                    text = field.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (checked) stringResource(R.string.projects_field_yes) else stringResource(R.string.projects_field_no),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onValueChange(if (it) "1" else "0") }
                        )
                        // Without the label column the checkbox is the whole
                        // control, so the name isn't repeated beside it.
                        if (showLabel) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = field.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            "checklist" -> {
                ChecklistEditor(
                    fieldName = labelOrNull(showLabel, field.name),
                    value = stringValue,
                    isReadonly = readOnly,
                    onValueChange = onValueChange
                )
            }

            else -> {
                if (readOnly) {
                    ReadOnlyDisplay(labelOrNull(showLabel, field.name), stringValue)
                } else {
                    OutlinedTextField(
                        value = stringValue,
                        onValueChange = onValueChange,
                        label = fieldLabel,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (field.isRequired && stringValue.isBlank() && (value as? List<*>).isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.projects_property_required, field.name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ReadOnlyDisplay(label: String?, value: String) {
    Column {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = if (value.isBlank()) "—" else value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = if (label == null) Modifier.padding(top = 16.dp) else Modifier
        )
    }
}

/** The field name when the editor draws its own label, null when it doesn't. */
private fun labelOrNull(showLabel: Boolean, name: String): String? =
    if (showLabel) name else null

/**
 * Parse a date field value into epoch seconds. Accepts either epoch seconds (the
 * server's read format) or an ISO `yyyy-MM-dd` string, so a value just written as
 * ISO still displays correctly before the next refresh. Returns null when neither
 * form parses.
 */
private fun dateFieldSeconds(value: String): Long? {
    value.toLongOrNull()?.let { return it }
    return try {
        java.time.LocalDate.parse(value).toEpochDay() * 86_400L
    } catch (_: Exception) {
        null
    }
}

/** A small colour dot shown to the left of a select option. */
@Composable
private fun OptionColourSwatch(colour: String) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(parseOptionColour(colour))
    )
}

/** Parse a `#RRGGBB` (or bare `RRGGBB`) option colour, falling back to grey. */
private fun parseOptionColour(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor("#${hex.removePrefix("#")}"))
    } catch (_: Exception) {
        Color.Gray
    }
}

@Composable
private fun ChecklistEditor(
    fieldName: String?,
    value: String,
    isReadonly: Boolean,
    onValueChange: (String) -> Unit
) {
    val gson = remember { Gson() }
    val items = remember(value) {
        try {
            if (value.isBlank()) emptyList()
            else {
                val type = object : TypeToken<List<ChecklistItem>>() {}.type
                gson.fromJson<List<ChecklistItem>>(value, type)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun updateItems(newItems: List<ChecklistItem>) {
        onValueChange(gson.toJson(newItems))
    }

    Column {
        if (fieldName != null) {
            Text(
                text = fieldName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { checked ->
                        if (isReadonly) return@Checkbox
                        val updated = items.toMutableList()
                        updated[index] = item.copy(checked = checked)
                        updateItems(updated)
                    },
                    enabled = !isReadonly
                )
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { text ->
                        if (isReadonly) return@OutlinedTextField
                        val updated = items.toMutableList()
                        updated[index] = item.copy(text = text)
                        updateItems(updated)
                    },
                    readOnly = isReadonly,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (!isReadonly) {
                    IconButton(
                        onClick = {
                            val updated = items.toMutableList()
                            updated.removeAt(index)
                            updateItems(updated)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.projects_property_remove), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (!isReadonly) {
            TextButton(
                onClick = {
                    updateItems(items + ChecklistItem(text = "", checked = false))
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.projects_property_add_item))
            }
        }
    }
}

/** Map weekStartsOn (0=Sun … 6=Sat) to a representative Locale that gives the
 *  DatePicker the right firstDayOfWeek. Beyond Sun/Mon/Sat there's no widely
 *  used locale with the required day, so we fall back to the device default. */
private fun localeForWeekStart(weekStartsOn: Int): java.util.Locale = when (weekStartsOn) {
    0 -> java.util.Locale.US               // Sunday
    1 -> java.util.Locale("en", "GB")      // Monday
    6 -> java.util.Locale("ar", "SA")      // Saturday
    else -> java.util.Locale.getDefault()
}
