// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiButtonTone
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.crm.R
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmField
import org.mochios.android.R as MochiR
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.mochios.android.ui.components.ErrorState

@Composable
private fun fieldTypeLabel(type: String): String = when (type) {
    "text" -> stringResource(R.string.crm_field_type_text)
    "number" -> stringResource(R.string.crm_field_type_number)
    "enumerated" -> stringResource(R.string.crm_field_type_enumerated)
    "user" -> stringResource(R.string.crm_field_type_user)
    "date" -> stringResource(R.string.crm_field_type_date)
    "checkbox" -> stringResource(R.string.crm_field_type_checkbox)
    "checklist" -> stringResource(R.string.crm_field_type_checklist)
    else -> type
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    onBack: () -> Unit,
    onAddField: () -> Unit,
    onFieldClick: (String) -> Unit,
    viewModel: ClassDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val crmDetails = uiState.crmDetails
    val classId = viewModel.classId
    val cls = crmDetails?.classes?.find { candidate -> candidate.id == classId }
    val fields = crmDetails?.fields?.get(classId) ?: emptyList()
    val hierarchy = crmDetails?.hierarchy?.get(classId) ?: emptyList()
    val allClasses = crmDetails?.classes ?: emptyList()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadCrm()
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (cls != null) {
                            stringResource(R.string.crm_class_label, cls.name)
                        } else {
                            stringResource(R.string.crm_design_title)
                        }
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (cls == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val error = uiState.error
                if (error != null) {
                    ErrorState(error = error, onRetry = { viewModel.loadCrm() })
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            var editName by remember(cls.id) { mutableStateOf(cls.name) }
            var titleFieldId by remember(cls.id) { mutableStateOf(cls.title) }
            var titleExpanded by remember(cls.id) { mutableStateOf(false) }
            var showDeleteConfirm by remember(cls.id) { mutableStateOf(false) }

            val sortedViews = crmDetails.views.sortedBy { view -> view.rank }
            val previewView = sortedViews.firstOrNull { view ->
                view.classes.isEmpty() || cls.id in view.classes
            } ?: sortedViews.firstOrNull()

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                DesignPreview(
                    crm = crmDetails,
                    view = previewView,
                    classFilter = cls
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Name
                MochiTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.crm_class_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (editName != cls.name && editName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    MochiButton(onClick = {
                        viewModel.updateClass(name = editName)
                    }) {
                        Text(stringResource(MochiR.string.common_save))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title field selector
                Text(stringResource(R.string.crm_class_title_field), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.crm_class_title_field_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val defaultReadableLabel = stringResource(R.string.crm_class_title_field_default)
                ExposedDropdownMenuBox(
                    expanded = titleExpanded,
                    onExpandedChange = { titleExpanded = it }
                ) {
                    val titleFieldName = fields.find { it.id == titleFieldId }?.name ?: defaultReadableLabel
                    MochiTextField(
                        value = titleFieldName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = titleExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = titleExpanded,
                        onDismissRequest = { titleExpanded = false }
                    ) {
                        MochiDropdownMenuItem(
                            text = { Text(defaultReadableLabel) },
                            onClick = {
                                titleFieldId = ""
                                titleExpanded = false
                                viewModel.updateClass(title = "")
                            },
                        )
                        fields.forEach { field ->
                            MochiDropdownMenuItem(
                                text = { Text(field.name) },
                                onClick = {
                                    titleFieldId = field.id
                                    titleExpanded = false
                                    viewModel.updateClass(title = field.id)
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Hierarchy
                Text(stringResource(R.string.crm_class_parents), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                val otherClasses = allClasses.filter { it.id != cls.id }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    otherClasses.forEach { otherCls ->
                        FilterChip(
                            selected = otherCls.id in hierarchy,
                            onClick = {
                                val newHierarchy = if (otherCls.id in hierarchy) {
                                    hierarchy - otherCls.id
                                } else {
                                    hierarchy + otherCls.id
                                }
                                viewModel.setHierarchy(newHierarchy)
                            },
                            label = { Text(otherCls.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.crm_class_fields), style = MaterialTheme.typography.titleSmall)
                    MochiIconButton(onClick = onAddField) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.crm_class_add_field))
                    }
                }

                val sortedFields = fields.sortedBy { it.rank }
                sortedFields.forEachIndexed { index, field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFieldClick(field.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reorder buttons
                        if (sortedFields.size > 1) {
                            Column {
                                if (index > 0) {
                                    MochiIconButton(
                                        onClick = {
                                            val newOrder = sortedFields.toMutableList()
                                            newOrder.removeAt(index)
                                            newOrder.add(index - 1, field)
                                            viewModel.reorderFields(newOrder.joinToString(",") { it.id })
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.crm_class_move_up), modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < sortedFields.lastIndex) {
                                    MochiIconButton(
                                        onClick = {
                                            val newOrder = sortedFields.toMutableList()
                                            newOrder.removeAt(index)
                                            newOrder.add(index + 1, field)
                                            viewModel.reorderFields(newOrder.joinToString(",") { it.id })
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.crm_class_move_down), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = field.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = fieldTypeLabel(field.fieldtype),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Delete class
                MochiOutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    tone = MochiButtonTone.Neutral,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.crm_class_delete))
                }
            }

            if (showDeleteConfirm) {
                MochiAlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = stringResource(R.string.crm_class_delete_title),
                    text = stringResource(R.string.crm_class_delete_message, cls.name),
                    confirmText = stringResource(MochiR.string.common_delete),
                    onConfirm = {
                        showDeleteConfirm = false
                        viewModel.deleteClass()
                    },
                    destructive = true,
                    dismissText = stringResource(MochiR.string.common_cancel),
                )
            }
        }
    }
}
