// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import org.mochios.android.ui.components.FileKindPreview
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.rememberFileKind
import org.mochios.android.util.NaturalCompare
import org.mochios.projects.R
import org.mochios.projects.ui.`object`.FieldEditor
import org.mochios.projects.util.creatableClasses
import org.mochios.projects.util.defaultParent
import org.mochios.projects.util.formValues
import org.mochios.projects.util.initialClass
import org.mochios.projects.util.objectTitle
import org.mochios.projects.util.parentRequired
import org.mochios.projects.util.requiredFilled
import org.mochios.projects.util.unsatisfiable
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
    val prefix = details?.project?.prefix.orEmpty()
    val hierarchy = details?.hierarchy.orEmpty()
    val fields = details?.fields.orEmpty()
    val options = details?.options.orEmpty()
    val objects = uiState.objects
    val activeView = uiState.activeView
    val presetParent = viewModel.presetParent

    // A class that needs a parent is only offered once one exists to put it
    // under; the server refuses it anywhere else.
    val creatable = remember(classes, hierarchy, objects) {
        creatableClasses(classes, hierarchy, objects)
    }

    LaunchedEffect(uiState.createdObjectId) {
        uiState.createdObjectId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedObject()
        }
    }

    val presetParentObj = remember(presetParent, objects) {
        presetParent?.let { id -> objects.firstOrNull { obj -> obj.id == id } }
    }
    val initialClassId = remember(presetParentObj, activeView, creatable, hierarchy) {
        initialClass(
            creatable,
            hierarchy,
            presetParentObj?.objectClass,
            activeView?.classes.orEmpty(),
        )
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
    // classes (hierarchy[selectedClassId]) intersected with the project's
    // existing objects.
    val allowedParentClasses = hierarchy[selectedClassId] ?: emptyList()
    val parentCandidates = remember(objects, allowedParentClasses, classes, prefix) {
        if (allowedParentClasses.isEmpty()) {
            emptyList()
        } else {
            // Named and ordered as the web dialog names and orders them.
            objects.filter { obj -> obj.objectClass in allowedParentClasses }
                .sortedWith(compareBy(NaturalCompare) { obj -> objectTitle(obj, classes, prefix) })
        }
    }
    var selectedParentId by remember { mutableStateOf<String?>(null) }
    var parentExpanded by remember { mutableStateOf(false) }

    // The parent that opened the form, once the objects it belongs to are in.
    // Changing class clears it, since the new class may not take it.
    LaunchedEffect(initialClassId) {
        selectedParentId = presetParent.takeIf {
            presetParentObj != null &&
                presetParentObj.objectClass in hierarchy[initialClassId].orEmpty()
        }
    }

    // A class that cannot sit at the top level offers no "(none)", so until
    // the user picks a parent it has one picked for it.
    val required = parentRequired(hierarchy, selectedClassId)
    val parentId = selectedParentId ?: if (required) {
        defaultParent(parentCandidates, viewModel.presetValues)?.id
    } else {
        null
    }

    // Per-field values entered on the form, keyed by field id. The title
    // field's value is sent as the object's title on create; the rest are
    // applied one per request afterwards, as the web dialog does.
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    val classFields = remember(fields, selectedClassId) {
        fields[selectedClassId].orEmpty().sortedBy { field -> field.rank }
    }
    val classOptions = options[selectedClassId].orEmpty()
    val titleFieldId = classes.firstOrNull { cls -> cls.id == selectedClassId }?.title.orEmpty()
    // The board's column field: an object blank there would land in
    // "Unassigned", so the form seeds the first column unless the caller
    // named one (the tapped column header).
    val column = activeView?.takeIf { view -> view.viewtype == "board" }?.columns.orEmpty()
    LaunchedEffect(selectedClassId, classFields, classOptions) {
        val next = formValues(classFields, classOptions, fieldValues.toMap(), viewModel.presetValues, column)
        fieldValues.clear()
        fieldValues.putAll(next)
    }

    // Files picked to attach, uploaded once the object exists.
    val pendingFiles = remember { mutableStateListOf<Uri>() }
    val defaultName = stringResource(R.string.projects_attachment_default_name)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        pendingFiles.addAll(uris)
    }

    val missingRequired = !requiredFilled(classFields, fieldValues)
    // A required enumerated field with no options can never be filled in, and
    // the form offers nothing to pick. Without naming them, Create is simply
    // dead and the reason is invisible.
    val unsatisfiableFields = unsatisfiable(classFields, classOptions)

    val canCreate = details != null && creatable.isNotEmpty() && selectedClassId.isNotBlank() &&
        (!required || parentId != null) && !missingRequired && !uiState.isCreating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_create_object_title)) },
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
                                val initialValues = fieldValues.filter { (id, value) ->
                                    id != titleFieldId && value.isNotBlank()
                                }
                                viewModel.createObject(
                                    classId = selectedClassId,
                                    title = title,
                                    parent = parentId,
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
                                Text(stringResource(R.string.projects_create_action))
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

                // No class can be created, so no create can succeed and every
                // field would be busywork: the message stands alone and Back
                // is the way out.
                creatable.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.projects_create_object_no_types),
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
                                    R.string.projects_create_blocked_no_options,
                                    unsatisfiableFields.joinToString(", ") { field -> field.name }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        if (creatable.size > 1) {
                            ExposedDropdownMenuBox(
                                expanded = classExpanded,
                                onExpandedChange = { expanded -> classExpanded = expanded }
                            ) {
                                MochiTextField(
                                    value = creatable.find { cls -> cls.id == selectedClassId }
                                        ?.name
                                        .orEmpty(),
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !uiState.isCreating,
                                    label = {
                                        Text(stringResource(R.string.projects_create_object_type))
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = classExpanded
                                        )
                                    },
                                    modifier = Modifier
                                        .menuAnchor(
                                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        )
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = classExpanded,
                                    onDismissRequest = { classExpanded = false }
                                ) {
                                    creatable.forEach { cls ->
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

                        // Parent picker — only shown when the selected class has
                        // allowed parent classes per project.hierarchy. "None"
                        // is offered only to a class that may sit at the top
                        // level.
                        if (parentCandidates.isNotEmpty()) {
                            val selectedParentLabel = parentId
                                ?.let { id -> objects.firstOrNull { obj -> obj.id == id } }
                                ?.let { obj -> objectTitle(obj, classes, prefix) }
                                ?: stringResource(R.string.projects_create_object_parent_none)
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
                                        Text(
                                            stringResource(
                                                R.string.projects_create_object_parent
                                            )
                                        )
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = parentExpanded
                                        )
                                    },
                                    modifier = Modifier
                                        .menuAnchor(
                                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        )
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = parentExpanded,
                                    onDismissRequest = { parentExpanded = false }
                                ) {
                                    if (!required) {
                                        MochiDropdownMenuItem(
                                            text = { Text(stringResource(
                                                        R.string.projects_create_object_parent_none
                                                    )) },
                                            onClick = {
                                                selectedParentId = null
                                                parentExpanded = false
                                            },
                                        )
                                    }
                                    parentCandidates.forEach { candidate ->
                                        MochiDropdownMenuItem(
                                            text = { Text(objectTitle(candidate, classes, prefix)) },
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

                        // The class's fields, the title among them, in design
                        // order. Each editor writes into fieldValues.
                        classFields.forEach { field ->
                            FieldEditor(
                                field = field,
                                value = fieldValues[field.id],
                                options = classOptions[field.id].orEmpty(),
                                canWrite = !uiState.isCreating,
                                people = uiState.people,
                                onValueChange = { value -> fieldValues[field.id] = value },
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
                                stringResource(R.string.projects_attachments),
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
                                Text(stringResource(R.string.projects_attachment_add))
                            }
                        }
                        pendingFiles.forEachIndexed { index, uri ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val label =
                                    rememberFileLabel(uri, viewModel::fileName, defaultName)
                                FileKindPreview(
                                    kind = rememberFileKind(uri, label),
                                    model = uri,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = label,
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
                                            R.string.projects_attachment_remove
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
