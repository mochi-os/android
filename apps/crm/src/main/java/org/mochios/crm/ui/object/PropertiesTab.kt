package org.mochios.crm.ui.`object`

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.mochios.android.model.User
import org.mochios.android.ui.components.PersonPicker
import org.mochios.crm.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.crm.model.ChecklistItem
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmObject
import org.mochios.android.R as MochiR

@Composable
fun PropertiesTab(
    obj: CrmObject,
    crmDetails: CrmDetails,
    viewModel: ObjectDetailViewModel,
    onAddChild: () -> Unit = {},
    onNavigateToObject: (String) -> Unit = {},
    crmId: String = "",
    serverUrl: String = "",
) {
    val uiState by viewModel.uiState.collectAsState()
    val fields = crmDetails.fields[obj.objectClass] ?: emptyList()
    val classOptions = crmDetails.options[obj.objectClass] ?: emptyMap()
    val canWrite = canWriteAccess(uiState.access)
    // The title field is rendered editable in the detail header (parity with
    // web); exclude it here so it isn't shown twice.
    val titleFieldId = crmDetails.classes.find { it.id == obj.objectClass }?.title
    // "Can this object have children?" — true when at least one class
    // lists obj.objectClass in its allowed parent classes.
    val canHaveChildren = remember(crmDetails.hierarchy, obj.objectClass) {
        crmDetails.hierarchy.any { (_, parents) -> obj.objectClass in parents }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Parent picker
        val allowedParentClasses = (crmDetails.hierarchy[obj.objectClass] ?: emptyList())
            .filter { it.isNotBlank() }
        val descendants = remember(uiState.siblingObjects, obj.id) {
            collectDescendants(uiState.siblingObjects, obj.id)
        }
        val parentOptions = uiState.siblingObjects
            .filter { it.objectClass in allowedParentClasses && it.id !in descendants }
        val currentParent = uiState.siblingObjects.find { it.id == obj.parent }

        if (parentOptions.isNotEmpty() || currentParent != null) {
            ParentPicker(
                crmDetails = crmDetails,
                currentParent = currentParent,
                parentOptions = parentOptions,
                canWrite = canWrite,
                onSelect = { newParent -> viewModel.updateParent(newParent) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Dynamic fields
        fields.filter { it.id != titleFieldId }.sortedBy { it.rank }.forEach { field ->
            FieldEditor(
                field = field,
                value = obj.values[field.id],
                options = classOptions[field.id] ?: emptyList(),
                canWrite = canWrite,
                people = uiState.people,
                onValueChange = { viewModel.setValue(field.id, it) },
                onMultiValueChange = { viewModel.setMultiValue(field.id, it) },
                onSearchUsers = { query -> viewModel.searchPeople(query) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // "Add child" affordance — shown when this object's class is
        // listed as an allowed parent in any other class's hierarchy,
        // and the user has write access. Tap routes through CrmScreen
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
                Text(stringResource(R.string.crm_add_child))
            }
        }

        // Attachments + Links inlined here to match web's object-detail-panel
        // layout (both fold into Properties; no separate tabs).
        if (crmId.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            AttachmentsSection(
                attachments = uiState.attachments,
                crmId = crmId,
                serverUrl = serverUrl,
                onAddAttachment = { file -> viewModel.createAttachment(file) },
                onDeleteAttachment = { id -> viewModel.deleteAttachment(id) },
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            LinksSection(
                obj = obj,
                crmDetails = crmDetails,
                viewModel = viewModel,
                onNavigateToObject = onNavigateToObject,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

internal fun canWriteAccess(access: String): Boolean =
    access == "owner" || access == "design" || access == "write"

private fun collectDescendants(objects: List<CrmObject>, rootId: String): Set<String> {
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

private fun objectDisplayTitle(obj: CrmObject, crmDetails: CrmDetails): String {
    val cls = crmDetails.classes.find { it.id == obj.objectClass }
    val titleField = cls?.title.orEmpty()
    val titleVal = if (titleField.isNotBlank()) obj.values[titleField]?.toString().orEmpty() else ""
    if (titleVal.isNotBlank()) return titleVal
    val prefix = crmDetails.crm.prefix
    return if (prefix.isNotBlank()) "$prefix-${obj.number}" else "#${obj.number}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentPicker(
    crmDetails: CrmDetails,
    currentParent: CrmObject?,
    parentOptions: List<CrmObject>,
    canWrite: Boolean,
    onSelect: (String) -> Unit
) {
    val noParentLabel = stringResource(R.string.crm_parent_none)
    val displayText = currentParent?.let { objectDisplayTitle(it, crmDetails) } ?: noParentLabel

    if (!canWrite) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.crm_parent_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = displayText, style = MaterialTheme.typography.bodyLarge)
        }
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
            label = { Text(stringResource(R.string.crm_parent_label)) },
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
                placeholder = { Text(stringResource(R.string.crm_parent_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            // (no parent) option
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.crm_parent_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    onSelect("")
                    expanded = false
                    query = ""
                }
            )
            val q = query.trim().lowercase()
            parentOptions
                .map { it to objectDisplayTitle(it, crmDetails) }
                .filter { (_, title) -> q.isEmpty() || title.lowercase().contains(q) }
                .forEach { (parentObj, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            onSelect(parentObj.id)
                            expanded = false
                            query = ""
                        }
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FieldEditor(
    field: CrmField,
    value: Any?,
    options: List<FieldOption>,
    canWrite: Boolean,
    people: List<org.mochios.crm.model.Person>,
    onValueChange: (String) -> Unit,
    onMultiValueChange: (List<String>) -> Unit,
    onSearchUsers: suspend (String) -> List<User>
) {
    val stringValue = value?.toString() ?: ""
    val listValue = (value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
    // Effective read-only: field-readonly OR user lacks write access
    val readOnly = field.isReadonly || !canWrite

    Column(modifier = Modifier.fillMaxWidth()) {
        when (field.fieldtype) {
            "text" -> {
                if (readOnly) {
                    ReadOnlyDisplay(field.name, stringValue)
                } else {
                    OutlinedTextField(
                        value = stringValue,
                        onValueChange = onValueChange,
                        label = { Text(field.name) },
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
                    ReadOnlyDisplay(field.name, stringValue)
                } else {
                    OutlinedTextField(
                        value = stringValue,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal.toDoubleOrNull() != null) {
                                onValueChange(newVal)
                            }
                        },
                        label = { Text(field.name) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "enumerated" -> {
                if (field.isMulti) {
                    // Multi-select chips
                    Text(
                        text = field.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                        ReadOnlyDisplay(field.name, selectedOption?.name.orEmpty())
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
                                label = { Text(field.name) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.crm_property_option_none), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = {
                                        onValueChange("")
                                        expanded = false
                                    }
                                )
                                options.sortedBy { it.rank }.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.name) },
                                        onClick = {
                                            onValueChange(option.id)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "user" -> {
                Text(
                    text = field.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                val resolvedName = people.find { it.id == stringValue }?.name
                val displayName = when {
                    stringValue.isBlank() -> "—"
                    !resolvedName.isNullOrBlank() -> resolvedName
                    else -> stringValue
                }
                if (readOnly) {
                    Text(text = displayName, style = MaterialTheme.typography.bodyLarge)
                } else {
                    if (stringValue.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onValueChange("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.crm_property_remove),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    PersonPicker(
                        onSelect = { user ->
                            // Person.id is stored in fingerprint by the search adapter
                            val entityId = user.fingerprint.orEmpty()
                            if (entityId.isNotBlank()) onValueChange(entityId)
                        },
                        onSearch = onSearchUsers,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "date" -> {
                var showDatePicker by remember { mutableStateOf(false) }
                val format = LocalFormat.current
                val displayDate = if (stringValue.isNotBlank()) {
                    try {
                        val seconds = stringValue.toLongOrNull()
                        if (seconds != null) {
                            format.formatDate(seconds)
                        } else {
                            stringValue
                        }
                    } catch (_: Exception) {
                        stringValue
                    }
                } else ""

                if (readOnly) {
                    ReadOnlyDisplay(field.name, displayDate)
                } else {
                    OutlinedTextField(
                        value = displayDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(field.name) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.crm_property_pick_date))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                    )

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
                                initialSelectedDateMillis = stringValue.toLongOrNull()?.times(1000)
                            )
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val selectedMillis = datePickerState.selectedDateMillis
                                        if (selectedMillis != null) {
                                            onValueChange((selectedMillis / 1000).toString())
                                        }
                                        showDatePicker = false
                                    }) {
                                        Text(stringResource(R.string.crm_property_ok))
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
                            Text(
                                text = field.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (checked) stringResource(R.string.crm_field_yes) else stringResource(R.string.crm_field_no),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onValueChange(if (it) "1" else "0") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = field.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            "checklist" -> {
                ChecklistEditor(
                    fieldName = field.name,
                    value = stringValue,
                    isReadonly = readOnly,
                    onValueChange = onValueChange
                )
            }

            else -> {
                if (readOnly) {
                    ReadOnlyDisplay(field.name, stringValue)
                } else {
                    OutlinedTextField(
                        value = stringValue,
                        onValueChange = onValueChange,
                        label = { Text(field.name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (field.isRequired && stringValue.isBlank() && (value as? List<*>).isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.crm_property_required, field.name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ReadOnlyDisplay(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (value.isBlank()) "—" else value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ChecklistEditor(
    fieldName: String,
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
        Text(
            text = fieldName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

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
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.crm_property_remove), modifier = Modifier.size(16.dp))
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
                Text(stringResource(R.string.crm_property_add_item))
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
