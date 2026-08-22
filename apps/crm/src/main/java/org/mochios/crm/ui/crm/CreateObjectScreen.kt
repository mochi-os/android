// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.files.rememberFileLabel
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.crm.R
import org.mochios.crm.model.CrmObject
import org.mochios.crm.ui.`object`.FieldEditor
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateObjectScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateObjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val details = uiState.details

    val classes = details?.classes.orEmpty()
    val hierarchy = details?.hierarchy.orEmpty()
    val fields = details?.fields.orEmpty()
    val options = details?.options.orEmpty()
    val objects = uiState.objects

    LaunchedEffect(uiState.createdObjectId) {
        uiState.createdObjectId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedObject()
        }
    }

    // A class the design gives parent classes cannot be created until an object
    // exists to parent it, and the form has nothing but the blocked message to
    // show for one. So the opening class is the first that can actually be
    // created, not simply the first on offer.
    val initialClassId = remember(details, uiState.activeView, objects) {
        fun creatable(classId: String): Boolean {
            val parentClasses = (hierarchy[classId] ?: emptyList())
                .filter { id -> id.isNotBlank() }
            return parentClasses.isEmpty() ||
                objects.any { obj -> obj.objectClass in parentClasses }
        }
        // Only classes the CRM still defines: a view may name a deleted class.
        val activeView = uiState.activeView
        val preferred = if (activeView != null && activeView.classes.isNotEmpty()) {
            activeView.classes.mapNotNull { id -> classes.find { cls -> cls.id == id } }
        } else {
            emptyList()
        }
        // What the view asked for first, every class after it as the fallback.
        val ordered = preferred + classes
        val chosen = ordered.firstOrNull { cls -> creatable(cls.id) } ?: ordered.firstOrNull()
        chosen?.id.orEmpty()
    }

    // Held rather than derived, because the type picker below writes to it. The
    // opening value can only be known once the design has loaded, so it is
    // seeded on arrival and left alone after that.
    var selectedClassId by remember { mutableStateOf("") }
    LaunchedEffect(initialClassId) {
        if (selectedClassId.isBlank()) {
            selectedClassId = initialClassId
        }
    }
    var classExpanded by remember { mutableStateOf(false) }

    // Parent picker state. Derived from the selected class's allowed parent
    // classes (hierarchy[selectedClassId]) intersected with the CRM's existing
    // objects.
    val allowedParentClasses = hierarchy[selectedClassId] ?: emptyList()
    val untitled = stringResource(R.string.crm_untitled)
    fun parentLabel(obj: CrmObject): String {
        val titleField = classes.find { cls -> cls.id == obj.objectClass }
            ?.title
            ?.takeIf { title -> title.isNotBlank() }
        return titleField?.let { field -> obj.stringValue(field) }.orEmpty().ifBlank { untitled }
    }
    val parentCandidates = remember(objects, allowedParentClasses, classes) {
        if (allowedParentClasses.isEmpty()) {
            emptyList()
        } else {
            objects.filter { obj -> obj.objectClass in allowedParentClasses }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { obj -> parentLabel(obj) })
        }
    }
    var selectedParentId by remember { mutableStateOf<String?>(null) }
    var parentExpanded by remember { mutableStateOf(false) }

    // The picker offers no "none" entry, so a child object always names its
    // parent. Nothing selected — the form just opened, or changing class
    // cleared a parent the new class cannot take — falls back to the first
    // candidate, the one the picker lists at the top.
    LaunchedEffect(selectedClassId, parentCandidates) {
        val stillValid = parentCandidates.any { candidate -> candidate.id == selectedParentId }
        if (!stillValid) {
            selectedParentId = parentCandidates.firstOrNull()?.id
        }
    }

    // Per-field values entered on the form, keyed by field id. The title
    // field's value is sent as the object's title on create; the rest are
    // applied one per request afterwards, mirroring web's create-object flow.
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    val classFields = fields[selectedClassId].orEmpty().sortedBy { field -> field.rank }
    val classOptions = options[selectedClassId].orEmpty()
    val titleFieldId = classes.firstOrNull { cls -> cls.id == selectedClassId }?.title.orEmpty()

    // Files picked to attach on create.
    val pendingFiles = remember { mutableStateListOf<Uri>() }
    val defaultName = stringResource(R.string.crm_attachment_default_name)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        pendingFiles.addAll(uris)
    }

    // Seed defaults when the selected class changes: clear prior values, then
    // auto-select the first option of required enumerated fields. The grouping
    // field is deliberately not pre-filled - a board column's "+" sets it via
    // presetValues.
    LaunchedEffect(selectedClassId) {
        fieldValues.clear()
        // Presets first (a board column's "+" sets its stage); the
        // required-field defaults below fill only what they left blank.
        viewModel.presetValues.forEach { (fieldId, value) ->
            if (viewModel.usableValue(selectedClassId, fieldId, value)) {
                fieldValues[fieldId] = value
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

    val missingRequired = classFields.any { field ->
        field.isRequired && fieldValues[field.id].isNullOrBlank()
    }

    // A required enumerated field with no options can never be filled in: the
    // seeding effect above only pre-selects when options exist, and the form
    // offers nothing to pick. Without naming them, Create is simply dead and
    // the reason is invisible.
    val unsatisfiableFields = classFields.filter { field ->
        field.isRequired && field.fieldtype == "enumerated" &&
            classOptions[field.id].orEmpty().isEmpty()
    }

    // The selected class is a child type — the design gives it parent classes —
    // and nothing exists to parent it, so no create can succeed. Every field
    // below would be busywork, so the form holds the message alone: Create is
    // dead and Back is the way out.
    val blockedNoParent = allowedParentClasses.any { id -> id.isNotBlank() } &&
        parentCandidates.isEmpty()

    val canCreate = details != null && selectedClassId.isNotBlank() &&
        !blockedNoParent && !missingRequired && !uiState.isCreating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crm_create_object_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack, enabled = !uiState.isCreating) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (details != null) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        uiState.createError?.let { error ->
                            Text(
                                text = error.userMessage(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        MochiButton(
                            onClick = {
                                val title = fieldValues[titleFieldId].orEmpty()
                                val initialValues = fieldValues
                                    .filter { (id, value) ->
                                        id != titleFieldId && value.isNotBlank()
                                    }
                                    .toMap()
                                viewModel.createObject(
                                    classId = selectedClassId,
                                    title = title,
                                    parent = selectedParentId,
                                    initialValues = initialValues,
                                    uris = pendingFiles.toList(),
                                )
                            },
                            enabled = canCreate,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(stringResource(R.string.crm_create_action))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                details == null && uiState.isLoading -> LoadingState()

                details == null -> {
                    uiState.loadError?.let { error ->
                        ErrorState(error = error, onRetry = { viewModel.load() })
                    }
                }

                blockedNoParent -> {
                    Text(
                        text = stringResource(R.string.crm_create_blocked_no_parents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        if (unsatisfiableFields.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.crm_create_blocked_no_options,
                                    unsatisfiableFields.joinToString(", ") { field -> field.name }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        if (classes.size > 1) {
                            ExposedDropdownMenuBox(
                                expanded = classExpanded,
                                onExpandedChange = { expanded -> classExpanded = expanded }
                            ) {
                                MochiTextField(
                                    value = classes.find { cls -> cls.id == selectedClassId }
                                        ?.name
                                        .orEmpty(),
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !uiState.isCreating,
                                    label = {
                                        Text(stringResource(R.string.crm_create_object_type))
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = classExpanded
                                        )
                                    },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = classExpanded,
                                    onDismissRequest = { classExpanded = false }
                                ) {
                                    classes.forEach { cls ->
                                        MochiDropdownMenuItem(
                                            text = { Text(cls.name) },
                                            onClick = {
                                                selectedClassId = cls.id
                                                // The old parent may not be
                                                // allowed for the new class.
                                                selectedParentId = null
                                                classExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Parent picker, only for classes the hierarchy gives
                        // parents. No root entry: such a class is a child type.
                        if (parentCandidates.isNotEmpty()) {
                            val selectedParentLabel = selectedParentId?.let { id ->
                                objects.firstOrNull { obj -> obj.id == id }
                                    ?.let { obj -> parentLabel(obj) }
                                    ?: untitled
                            } ?: stringResource(R.string.crm_create_object_parent_none)
                            ExposedDropdownMenuBox(
                                expanded = parentExpanded,
                                onExpandedChange = { expanded -> parentExpanded = expanded }
                            ) {
                                MochiTextField(
                                    value = selectedParentLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !uiState.isCreating,
                                    label = {
                                        Text(stringResource(R.string.crm_create_object_parent))
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = parentExpanded
                                        )
                                    },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = parentExpanded,
                                    onDismissRequest = { parentExpanded = false }
                                ) {
                                    parentCandidates.forEach { candidate ->
                                        MochiDropdownMenuItem(
                                            text = { Text(parentLabel(candidate)) },
                                            onClick = {
                                                selectedParentId = candidate.id
                                                parentExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Dynamic fields for the selected class, including the
                        // title field. Each editor writes into fieldValues;
                        // multi-value fields are stored comma-joined, as the
                        // value endpoint expects.
                        classFields.forEach { field ->
                            FieldEditor(
                                field = field,
                                value = fieldValues[field.id],
                                options = classOptions[field.id] ?: emptyList(),
                                canWrite = !uiState.isCreating,
                                people = uiState.people,
                                onValueChange = { value -> fieldValues[field.id] = value },
                                onMultiValueChange = { values ->
                                    fieldValues[field.id] = values.joinToString(",")
                                },
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
                            MochiOutlinedButton(
                                onClick = { filePicker.launch("*/*") },
                                enabled = !uiState.isCreating,
                            ) {
                                Icon(
                                    Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.crm_attachment_add))
                            }
                        }
                        pendingFiles.forEachIndexed { index, uri ->
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
                                    text = rememberFileLabel(uri, viewModel::fileName, defaultName),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                MochiIconButton(
                                    onClick = { pendingFiles.removeAt(index) },
                                    enabled = !uiState.isCreating,
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(
                                            R.string.crm_attachment_remove
                                        ),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
