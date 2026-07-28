// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mochios.android.api.userMessage
import org.mochios.android.util.Uploads
import org.mochios.projects.R
import org.mochios.projects.lib.backupFileName
import org.mochios.projects.lib.readTextFromUri
import org.mochios.projects.lib.writeTextToUri
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
    var confirmImport by remember { mutableStateOf<PendingImport?>(null) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardLabel = stringResource(R.string.projects_design_clipboard_label)
    val exportSubject = stringResource(R.string.projects_design_export_subject)
    val shareChooser = stringResource(R.string.projects_design_share_chooser)
    val exportCopiedMsg = stringResource(R.string.projects_design_export_copied)
    val importedMsg = stringResource(R.string.projects_design_imported)
    val pastedJsonLabel = stringResource(R.string.projects_design_pasted_json_label)

    // Held between picking "Save to file" and the save dialog coming back with
    // a destination, so the JSON survives the trip out to the file picker.
    var pendingFileExport by remember { mutableStateOf<String?>(null) }
    val exportSavedMsg = stringResource(R.string.projects_design_export_saved)
    val exportFailedMsg = stringResource(R.string.projects_export_failed)
    val saveExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingFileExport
        pendingFileExport = null
        if (uri != null && json != null) {
            val ok = writeTextToUri(context, uri, json)
            Toast.makeText(context, if (ok) exportSavedMsg else exportFailedMsg, Toast.LENGTH_SHORT)
                .show()
        }
    }

    LaunchedEffect(uiState.exportedJson) {
        uiState.exportedJson?.let { json ->
            if (uiState.exportToFile) {
                pendingFileExport = json
                val name = uiState.projectDetails?.project?.name
                saveExport.launch(backupFileName(name, "design"))
            } else {
                // Copy to clipboard and offer share
                clipboard.setClip(ClipData.newPlainText(clipboardLabel, json).toClipEntry())
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, json)
                    putExtra(Intent.EXTRA_SUBJECT, exportSubject)
                }
                context.startActivity(Intent.createChooser(shareIntent, shareChooser))
                snackbarHostState.showSnackbar(exportCopiedMsg)
            }
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
                title = { Text(stringResource(R.string.projects_design_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.projects_design_more_options))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_design_export)) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.exportDesign()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_design_export_file)) },
                                leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.exportDesign(toFile = true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_design_import)) },
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
                uiState.isLoading && uiState.projectDetails == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null && uiState.projectDetails == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.error!!.userMessage(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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
            onPasteJson = { json ->
                showImportDialog = false
                confirmImport = PendingImport(json = json, label = pastedJsonLabel)
            },
            onPickFile = { json, fileName ->
                showImportDialog = false
                confirmImport = PendingImport(json = json, label = fileName)
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

    confirmImport?.let { pending ->
        ConfirmReplaceDialog(
            label = pending.label,
            onDismiss = { confirmImport = null },
            onConfirm = {
                viewModel.importFromJson(pending.json)
                confirmImport = null
            }
        )
    }
}

/** Design JSON waiting on the user to confirm it may replace the current design. */
private data class PendingImport(val json: String, val label: String)

@Composable
private fun ImportDesignDialog(
    templates: List<Template>,
    isLoadingTemplates: Boolean,
    onDismiss: () -> Unit,
    onSelectTemplate: (Template) -> Unit,
    onPasteJson: (String) -> Unit,
    onPickFile: (json: String, fileName: String) -> Unit
) {
    var pastedJson by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) { readTextFromUri(context, uri) }
                if (content != null) {
                    onPickFile(content, Uploads.fileName(context.contentResolver, uri, ""))
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.projects_design_import_dialog_title)) },
        text = {
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
                                        Text(stringResource(R.string.projects_design_use))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.projects_design_or_paste_json),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pastedJson,
                    onValueChange = { value -> pastedJson = value },
                    placeholder = { Text(stringResource(R.string.projects_design_paste_placeholder)) },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.projects_design_or_upload_json),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
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
        confirmButton = {
            TextButton(
                onClick = { onPasteJson(pastedJson) },
                enabled = pastedJson.isNotBlank()
            ) {
                Text(stringResource(R.string.projects_design_import_json))
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
        title = { Text(stringResource(R.string.projects_design_replace_title)) },
        text = {
            Text(stringResource(R.string.projects_design_replace_message, label))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.projects_design_replace), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}
