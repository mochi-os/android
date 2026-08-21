// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.projects.R
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
    val objects = uiState.objects
    val activeView = uiState.activeView
    val presetParent = viewModel.presetParent

    var title by remember { mutableStateOf("") }

    LaunchedEffect(uiState.createdObjectId) {
        uiState.createdObjectId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedObject()
        }
    }

    val presetParentObj = remember(presetParent, objects) {
        presetParent?.let { id -> objects.firstOrNull { obj -> obj.id == id } }
    }
    val initialClassId = remember(presetParentObj, activeView, classes, hierarchy) {
        when {
            // If pre-selected from "Add child", pick a class that permits the
            // parent's class as a parent (first match).
            presetParentObj != null -> {
                classes.firstOrNull { cls ->
                    (hierarchy[cls.id] ?: emptyList()).contains(presetParentObj.objectClass)
                }?.id ?: classes.firstOrNull()?.id.orEmpty()
            }
            activeView != null && activeView.classes.isNotEmpty() -> activeView.classes.first()
            else -> classes.firstOrNull()?.id.orEmpty()
        }
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
    val parentCandidates = remember(objects, allowedParentClasses) {
        if (allowedParentClasses.isEmpty()) {
            emptyList()
        } else {
            // The dropdown shows readable ids (PREFIX-number); ordering by the
            // number gives their natural order, which a lexical sort would not
            // (PROJ-10 would sort before PROJ-2).
            objects.filter { obj -> obj.objectClass in allowedParentClasses }
                .sortedBy { obj -> obj.number }
        }
    }
    var selectedParentId by remember { mutableStateOf<String?>(null) }
    var parentExpanded by remember { mutableStateOf(false) }

    // The parent that opened the form, once the objects it belongs to are in.
    // Changing class clears it, since the new class may not take it.
    LaunchedEffect(initialClassId) {
        selectedParentId = presetParent.takeIf { presetParentObj != null }
    }

    val canCreate = details != null && title.isNotBlank() &&
        selectedClassId.isNotBlank() && !uiState.isCreating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_create_object_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.isCreating) {
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
                        Button(
                            onClick = {
                                // A preset from a mixed-class board can carry
                                // another class's option; keep only values the
                                // chosen class takes.
                                val initialValues = viewModel.presetValues
                                    .filter { (field, value) ->
                                        viewModel.usableValue(selectedClassId, field, value)
                                    }
                                    .toMutableMap()
                                // On a board, an object with no column value
                                // would land in "Unassigned" — fall back to the
                                // first column unless the caller already said
                                // which one (the tapped column header).
                                if (activeView?.viewtype == "board" &&
                                    activeView.columns.isNotBlank() &&
                                    !initialValues.containsKey(activeView.columns)
                                ) {
                                    val options = viewModel.optionsForField(
                                        selectedClassId,
                                        activeView.columns
                                    )
                                    if (options.isNotEmpty()) {
                                        initialValues[activeView.columns] = options.first().id
                                    }
                                }
                                viewModel.createObject(
                                    classId = selectedClassId,
                                    title = title,
                                    parent = selectedParentId,
                                    initialValues = initialValues,
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

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { value -> title = value },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.projects_create_object_title_field
                                    )
                                )
                            },
                            singleLine = true,
                            enabled = !uiState.isCreating,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (classes.size > 1) {
                            Spacer(modifier = Modifier.height(16.dp))
                            ExposedDropdownMenuBox(
                                expanded = classExpanded,
                                onExpandedChange = { expanded -> classExpanded = expanded }
                            ) {
                                OutlinedTextField(
                                    value = classes.find { cls -> cls.id == selectedClassId }
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
                        }

                        // Parent picker — only shown when the selected class has
                        // allowed parent classes per project.hierarchy. "None"
                        // is always an option so root-level objects can still be
                        // created from the form.
                        if (parentCandidates.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            val selectedParentLabel = selectedParentId?.let { id ->
                                objects.firstOrNull { obj -> obj.id == id }
                                    ?.let { obj -> obj.readable.ifBlank { obj.id } }
                                    ?: id
                            } ?: stringResource(R.string.projects_create_object_parent_none)
                            ExposedDropdownMenuBox(
                                expanded = parentExpanded,
                                onExpandedChange = { expanded -> parentExpanded = expanded }
                            ) {
                                OutlinedTextField(
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
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = parentExpanded,
                                    onDismissRequest = { parentExpanded = false }
                                ) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(
                                                    R.string.projects_create_object_parent_none
                                                )) },
                                        onClick = {
                                            selectedParentId = null
                                            parentExpanded = false
                                        },
                                    )
                                    parentCandidates.forEach { candidate ->
                                        MochiDropdownMenuItem(
                                            text = { Text(candidate.readable.ifBlank { candidate.id }) },
                                            onClick = {
                                                selectedParentId = candidate.id
                                                parentExpanded = false
                                            },
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
}
