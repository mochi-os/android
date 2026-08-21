// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.files.rememberFileSaveLauncher
import org.mochios.android.files.shareExportFile
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.projects.R
import org.mochios.projects.model.Template
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

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importedMsg = stringResource(R.string.projects_design_imported)
    val importFailedMsg = stringResource(R.string.projects_design_import_failed)
    val exportSavedMsg = stringResource(R.string.projects_design_export_saved)
    val exportFailedMsg = stringResource(R.string.projects_export_failed)

    // The picker only reports where the file goes; the ViewModel writes it.
    val saveExport = rememberFileSaveLauncher { uri ->
        if (uri != null) viewModel.writeExportTo(uri) else viewModel.cancelExport()
    }

    LaunchedEffect(uiState.pendingExport) {
        uiState.pendingExport?.let { pending ->
            saveExport.launch(pending.suggestedName)
        }
    }

    // A saved design goes straight to the share sheet, the same as a data
    // export. The file is already on disk either way, so backing out of the
    // sheet costs the user nothing.
    LaunchedEffect(uiState.savedExport, uiState.exportFailed) {
        val saved = uiState.savedExport
        if (saved != null) {
            snackbarHostState.showSnackbar(exportSavedMsg)
            shareExportFile(context, saved)
            viewModel.clearExportResult()
        } else if (uiState.exportFailed) {
            snackbarHostState.showSnackbar(exportFailedMsg)
            viewModel.clearExportResult()
        }
    }

    LaunchedEffect(uiState.importSuccess) {
        if (uiState.importSuccess) {
            snackbarHostState.showSnackbar(importedMsg)
            viewModel.clearImportSuccess()
        }
    }

    LaunchedEffect(uiState.importFailed) {
        if (uiState.importFailed) {
            snackbarHostState.showSnackbar(importFailedMsg)
            viewModel.clearImportFailed()
        }
    }

    LaunchedEffect(uiState.error) {
        // Only transient failures (export, import) belong in a snackbar, and
        // only those have content to sit over. A failed initial load is owned
        // by the error state below: clearing it here would dismiss that state
        // and its retry when the snackbar times out, leaving a blank screen.
        if (uiState.projectDetails != null) {
            uiState.error?.let {
                snackbarHostState.showSnackbar(it.userMessage())
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_design_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
                    }
                },
                actions = {
                    Box {
                        MochiIconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.projects_design_more_options))
                        }
                        MochiDropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_design_export)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.exportDesign()
                                },
                                leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                            )
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_design_import)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showImportDialog = true
                                    viewModel.loadTemplates()
                                },
                                leadingIcon = { Icon(Icons.Outlined.Upload, contentDescription = null) },
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
                uiState.isLoading && uiState.projectDetails == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null && uiState.projectDetails == null -> {
                    ErrorState(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadProject() }
                    )
                }

                uiState.projectDetails != null -> {
                    val details = uiState.projectDetails!!

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
                                stringResource(R.string.projects_design_tab_classes),
                                stringResource(R.string.projects_design_tab_views)
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
            onPickFile = { uri ->
                showImportDialog = false
                viewModel.readImportFile(uri)
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

    uiState.pendingImport?.let { pending ->
        ConfirmReplaceDialog(
            label = pending.label,
            onDismiss = { viewModel.cancelPendingImport() },
            onConfirm = { viewModel.confirmPendingImport() }
        )
    }
}

@Composable
private fun ImportDesignDialog(
    templates: List<Template>,
    isLoadingTemplates: Boolean,
    onDismiss: () -> Unit,
    onSelectTemplate: (Template) -> Unit,
    onPickFile: (Uri) -> Unit
) {
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickFile(uri)
        }
    }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_design_import_dialog_title),
        content = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.projects_design_import_choose_template),
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
                        text = stringResource(R.string.projects_design_no_templates),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(templates) { template ->
                            MochiCard(
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
                                    MochiTextButton(onClick = { onSelectTemplate(template) }) {
                                        Text(stringResource(R.string.projects_design_use))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.projects_design_or_upload_json),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                MochiOutlinedButton(
                    onClick = { filePicker.launch("application/json") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.projects_create_upload_json))
                }
            }
        },
        confirmText = stringResource(MochiR.string.common_close),
        onConfirm = onDismiss,
    )
}

@Composable
private fun ConfirmReplaceDialog(
    label: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_design_replace_title),
        text = stringResource(R.string.projects_design_replace_message, label),
        confirmText = stringResource(R.string.projects_design_replace),
        onConfirm = onConfirm,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}
