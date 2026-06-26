// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.crm.R
import org.mochios.crm.model.Template
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignScreen(
    onBack: () -> Unit,
    viewModel: DesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var confirmTemplate by remember { mutableStateOf<Template?>(null) }
    var confirmPastedJson by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardLabel = stringResource(R.string.crm_design_clipboard_label)
    val exportSubject = stringResource(R.string.crm_design_export_subject)
    val shareChooser = stringResource(R.string.crm_design_share_chooser)
    val exportCopiedMsg = stringResource(R.string.crm_design_export_copied)
    val importedMsg = stringResource(R.string.crm_design_imported)

    LaunchedEffect(uiState.exportedJson) {
        uiState.exportedJson?.let { json ->
            // Copy to clipboard and offer share
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, json))
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_SUBJECT, exportSubject)
            }
            context.startActivity(Intent.createChooser(shareIntent, shareChooser))
            snackbarHostState.showSnackbar(exportCopiedMsg)
            viewModel.clearExportedJson()
        }
    }

    LaunchedEffect(uiState.importSuccess) {
        if (uiState.importSuccess) {
            snackbarHostState.showSnackbar(importedMsg)
            viewModel.clearImportSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it.userMessage())
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crm_design_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.crm_design_more_options))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.crm_design_export)) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.exportDesign()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.crm_design_import)) },
                                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showImportDialog = true
                                    viewModel.loadTemplates()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading && uiState.crmDetails == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null && uiState.crmDetails == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.error!!.userMessage(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                uiState.crmDetails != null -> {
                    val details = uiState.crmDetails!!

                    // Show class detail or field detail if selected
                    when {
                        uiState.selectedFieldId != null && uiState.selectedClassId != null -> {
                            val classId = uiState.selectedClassId!!
                            val fieldId = uiState.selectedFieldId!!
                            val field = details.fields[classId]?.find { it.id == fieldId }
                            val options = details.options[classId]?.get(fieldId) ?: emptyList()
                            if (field != null) {
                                FieldDetailScreen(
                                    classId = classId,
                                    field = field,
                                    options = options,
                                    viewModel = viewModel,
                                    onBack = { viewModel.selectField(null) }
                                )
                            }
                        }

                        uiState.selectedClassId != null -> {
                            val classId = uiState.selectedClassId!!
                            val cls = details.classes.find { it.id == classId }
                            val fields = details.fields[classId] ?: emptyList()
                            val hierarchy = details.hierarchy[classId] ?: emptyList()
                            if (cls != null) {
                                ClassDetailScreen(
                                    cls = cls,
                                    fields = fields,
                                    hierarchy = hierarchy,
                                    allClasses = details.classes,
                                    viewModel = viewModel,
                                    onBack = { viewModel.selectClass(null) },
                                    onFieldClick = { viewModel.selectField(it) }
                                )
                            }
                        }

                        else -> {
                            val tabs = listOf(
                                stringResource(R.string.crm_design_tab_classes),
                                stringResource(R.string.crm_design_tab_views)
                            )
                            TabRow(selectedTabIndex = selectedTab) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        text = { Text(title) }
                                    )
                                }
                            }

                            when (selectedTab) {
                                0 -> ClassesTab(
                                    classes = details.classes,
                                    viewModel = viewModel,
                                    onClassClick = { viewModel.selectClass(it) }
                                )
                                1 -> ViewsTab(
                                    views = details.views,
                                    classes = details.classes,
                                    fields = details.fields,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showImportDialog) {
        ImportDesignDialog(
            templates = uiState.templates,
            isLoadingTemplates = uiState.isLoadingTemplates,
            onDismiss = { showImportDialog = false },
            onSelectTemplate = { template ->
                showImportDialog = false
                confirmTemplate = template
            },
            onPasteJson = { json ->
                showImportDialog = false
                confirmPastedJson = json
            }
        )
    }

    confirmTemplate?.let { template ->
        ConfirmReplaceDialog(
            label = template.name,
            onDismiss = { confirmTemplate = null },
            onConfirm = {
                viewModel.importFromTemplate(template.id, template.version)
                confirmTemplate = null
            }
        )
    }

    confirmPastedJson?.let { json ->
        ConfirmReplaceDialog(
            label = stringResource(R.string.crm_design_pasted_json_label),
            onDismiss = { confirmPastedJson = null },
            onConfirm = {
                viewModel.importFromJson(json)
                confirmPastedJson = null
            }
        )
    }
}

@Composable
private fun ImportDesignDialog(
    templates: List<Template>,
    isLoadingTemplates: Boolean,
    onDismiss: () -> Unit,
    onSelectTemplate: (Template) -> Unit,
    onPasteJson: (String) -> Unit
) {
    var pastedJson by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crm_design_import_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.crm_design_import_choose_template),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoadingTemplates) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (templates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.crm_design_no_templates),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(templates) { template ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = template.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (template.description.isNotEmpty()) {
                                            Text(
                                                text = template.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TextButton(onClick = { onSelectTemplate(template) }) {
                                        Text(stringResource(R.string.crm_design_use))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.crm_design_or_paste_json),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pastedJson,
                    onValueChange = { pastedJson = it },
                    placeholder = { Text(stringResource(R.string.crm_design_paste_placeholder)) },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPasteJson(pastedJson) },
                enabled = pastedJson.isNotBlank()
            ) {
                Text(stringResource(R.string.crm_design_import_json))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

@Composable
private fun ConfirmReplaceDialog(
    label: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crm_design_replace_title)) },
        text = {
            Text(stringResource(R.string.crm_design_replace_message, label))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.crm_design_replace), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}
