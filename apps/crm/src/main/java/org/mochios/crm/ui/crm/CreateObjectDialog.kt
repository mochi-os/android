// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.crm.R
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.FieldOption
import org.mochios.crm.ui.`object`.FieldEditor
import java.io.File
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateObjectDialog(
    classes: List<CrmClass>,
    /** className/parentClassIds map from [CrmDetails.hierarchy]. */
    hierarchy: Map<String, List<String>>,
    /** Per-class field definitions from [CrmDetails.fields]. */
    fields: Map<String, List<CrmField>>,
    /** Per-class field options from [CrmDetails.options]. */
    options: Map<String, Map<String, List<FieldOption>>>,
    /** Crm members, for resolving user-type field pickers. */
    people: List<org.mochios.crm.model.Person>,
    /** All objects in the crm — used to populate the parent picker. */
    objects: List<CrmObject>,
    /**
     * When non-null, the dialog opens with this object as the pre-selected
     * parent (and the class auto-set to a class that allows that parent).
     * Set by `viewModel.showCreateObjectDialog(parent = ...)` from an
     * "Add child" affordance on an existing object.
     */
    presetParent: String?,
    isCreating: Boolean,
    activeView: CrmView?,
    viewModel: CrmViewModel,
    onDismiss: () -> Unit,
    onCreate: (classId: String, title: String, parent: String?, initialValues: Map<String, String>, files: List<File>) -> Unit
) {
    val presetParentObj = remember(presetParent, objects) {
        presetParent?.let { p -> objects.firstOrNull { it.id == p } }
    }
    val initialClassId = remember(presetParentObj, activeView, classes, hierarchy) {
        when {
            // If pre-selected from "Add child", pick a class that permits
            // the parent's class as a parent (first match).
            presetParentObj != null -> {
                classes.firstOrNull { cls ->
                    (hierarchy[cls.id] ?: emptyList()).contains(presetParentObj.objectClass)
                }?.id ?: classes.firstOrNull()?.id ?: ""
            }
            activeView != null && activeView.classes.isNotEmpty() -> activeView.classes.first()
            else -> classes.firstOrNull()?.id ?: ""
        }
    }
    var selectedClassId by remember { mutableStateOf(initialClassId) }
    var classExpanded by remember { mutableStateOf(false) }

    // Parent picker state. Derived from the selected class's allowed
    // parent classes (hierarchy[selectedClassId]) intersected with the
    // crm's existing objects.
    val allowedParentClasses = hierarchy[selectedClassId] ?: emptyList()
    val parentCandidates = remember(objects, allowedParentClasses) {
        if (allowedParentClasses.isEmpty()) emptyList()
        else objects.filter { it.objectClass in allowedParentClasses }
    }
    val untitled = stringResource(R.string.crm_untitled)
    fun parentLabel(o: CrmObject): String {
        val titleField = classes.find { it.id == o.objectClass }?.title?.takeIf { it.isNotBlank() }
        return titleField?.let { o.stringValue(it) }.orEmpty().ifBlank { untitled }
    }
    var selectedParentId by remember(initialClassId) {
        mutableStateOf(presetParent.takeIf { presetParentObj != null })
    }
    var parentExpanded by remember { mutableStateOf(false) }

    // Per-field values entered in the dialog, keyed by field id. The title
    // field's value is sent as the object's title on create; the rest are
    // applied via setValues, mirroring web's create-object flow.
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    val classFields = fields[selectedClassId].orEmpty().sortedBy { it.rank }
    val classOptions = options[selectedClassId].orEmpty()
    val titleFieldId = classes.firstOrNull { it.id == selectedClassId }?.title.orEmpty()

    // Files picked to attach on create.
    val pendingFiles = remember { mutableStateListOf<File>() }
    val context = LocalContext.current
    val defaultName = stringResource(R.string.crm_attachment_default_name)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        for (uri in uris) {
            val input = context.contentResolver.openInputStream(uri) ?: continue
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: defaultName
            val temp = File(context.cacheDir, name)
            temp.outputStream().use { output -> input.copyTo(output) }
            input.close()
            pendingFiles.add(temp)
        }
    }

    // Seed defaults whenever the selected class changes: clear prior values,
    // pre-fill the board column from the active view, and auto-select the
    // first option for any required enumerated field (matches web).
    LaunchedEffect(selectedClassId) {
        fieldValues.clear()
        val classFieldIds = fields[selectedClassId].orEmpty().map { it.id }.toSet()
        if (activeView?.viewtype == "board" && activeView.columns.isNotBlank() &&
            activeView.columns in classFieldIds
        ) {
            val columnOptions = viewModel.getAllOptionsForField(activeView.columns)
            if (columnOptions.isNotEmpty()) {
                fieldValues[activeView.columns] = columnOptions.first().id
            }
        }
        fields[selectedClassId].orEmpty().forEach { field ->
            if (field.fieldtype == "enumerated" && field.isRequired &&
                fieldValues[field.id].isNullOrBlank()
            ) {
                val fieldOptions = classOptions[field.id].orEmpty()
                if (fieldOptions.isNotEmpty()) fieldValues[field.id] = fieldOptions.first().id
            }
        }
    }

    val missingRequired = classFields.any { it.isRequired && fieldValues[it.id].isNullOrBlank() }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.crm_create_object_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (classes.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = classExpanded,
                        onExpandedChange = { classExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = classes.find { it.id == selectedClassId }?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.crm_create_object_type)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = classExpanded,
                            onDismissRequest = { classExpanded = false }
                        ) {
                            classes.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text(cls.name) },
                                    onClick = {
                                        selectedClassId = cls.id
                                        // Reset parent when class changes; old
                                        // selection may no longer be allowed.
                                        selectedParentId = null
                                        classExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Parent picker — only shown when the selected class has
                // allowed parent classes per crm.hierarchy. "None" is
                // always an option so root-level objects can still be
                // created from the dialog.
                if (parentCandidates.isNotEmpty()) {
                    val selectedParentLabel = selectedParentId?.let { id ->
                        objects.firstOrNull { it.id == id }?.let { parentLabel(it) } ?: untitled
                    } ?: stringResource(R.string.crm_create_object_parent_none)
                    ExposedDropdownMenuBox(
                        expanded = parentExpanded,
                        onExpandedChange = { parentExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedParentLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.crm_create_object_parent)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = parentExpanded,
                            onDismissRequest = { parentExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.crm_create_object_parent_none)) },
                                onClick = {
                                    selectedParentId = null
                                    parentExpanded = false
                                }
                            )
                            parentCandidates.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(parentLabel(p)) },
                                    onClick = {
                                        selectedParentId = p.id
                                        parentExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Dynamic fields for the selected class, including the title
                // field. Each editor writes into fieldValues; multi-value
                // fields are stored comma-joined to match setValues.
                classFields.forEach { field ->
                    FieldEditor(
                        field = field,
                        value = fieldValues[field.id],
                        options = classOptions[field.id] ?: emptyList(),
                        canWrite = true,
                        people = people,
                        onValueChange = { fieldValues[field.id] = it },
                        onMultiValueChange = { fieldValues[field.id] = it.joinToString(",") },
                        onSearchUsers = { query -> viewModel.searchPeople(query) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // File attachments, uploaded after the object is created.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.crm_attachments),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.crm_attachment_add))
                    }
                }
                pendingFiles.forEachIndexed { index, file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { pendingFiles.removeAt(index) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.crm_attachment_remove),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val title = fieldValues[titleFieldId].orEmpty()
                    val initialValues = fieldValues
                        .filter { (id, value) -> id != titleFieldId && value.isNotBlank() }
                        .toMap()
                    onCreate(selectedClassId, title, selectedParentId, initialValues, pendingFiles.toList())
                },
                enabled = selectedClassId.isNotBlank() && !missingRequired && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.crm_create_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}
