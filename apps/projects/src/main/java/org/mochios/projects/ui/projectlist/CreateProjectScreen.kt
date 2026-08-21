// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.projectlist

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.files.MIME_JSON
import org.mochios.android.files.MIME_ZIP
import org.mochios.projects.R
import org.mochios.projects.model.Template
import org.mochios.android.R as MochiR

// The two steps of the create flow.
private const val STEP_DETAILS = 0
private const val STEP_TEMPLATE = 1

// Derive a URL-friendly prefix from the project name: lowercase, non-alphanumeric
// runs collapsed to "-", capped at 20 chars (so a long name like
// "Android project Testing Name" becomes "android-project-test").
private fun prefixFromName(name: String): String =
    name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(20)
        .trimEnd('-')

/**
 * Two-step create form: details, then template. Both steps share one
 * destination so typed fields survive; a backup carries its own design and
 * skips the template step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var step by remember { mutableStateOf(STEP_DETAILS) }
    var name by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    // The prefix auto-tracks the name until the user edits it by hand.
    var prefixEdited by remember { mutableStateOf(false) }
    // The toggle reads "Allow anyone to search for project" — on means public.
    var allowSearch by remember { mutableStateOf(true) }
    // Nothing is preselected — the user must pick a template before creating.
    var selectedTemplate by remember { mutableStateOf<String?>(null) }
    var backupJson by remember { mutableStateOf<String?>(null) }
    var backupName by remember { mutableStateOf<String?>(null) }

    // OpenDocument, not GetContent: exports are zipped now, and only this
    // contract takes more than one type, so both the zip and a bare .json
    // from an older backup stay selectable.
    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.readBackup(uri)
        }
    }

    // Seed the fields once the ViewModel has read the picked backup.
    LaunchedEffect(uiState.backupPrefill) {
        uiState.backupPrefill?.let { prefill ->
            backupJson = prefill.json
            backupName = prefill.fileName
            prefill.name?.let { value -> name = value }
            prefill.prefix?.let { value ->
                prefix = prefixFromName(value)
                prefixEdited = true
            }
        }
    }

    LaunchedEffect(uiState.createdProjectId) {
        uiState.createdProjectId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedProject()
        }
    }

    // An imported backup brings its own design, and a server with no templates
    // has nothing to show — either way step two is skipped.
    val usesTemplate = backupJson == null && uiState.templates.isNotEmpty()
    val goesToTemplate = step == STEP_DETAILS && usesTemplate
    val canContinue = !uiState.isCreating && if (step == STEP_DETAILS) {
        name.isNotBlank()
    } else {
        selectedTemplate != null
    }
    val goBack = {
        if (step == STEP_TEMPLATE) {
            step = STEP_DETAILS
        } else {
            onBack()
        }
    }

    // System back retreats a step rather than leaving the half-filled form.
    BackHandler(enabled = step == STEP_TEMPLATE) {
        step = STEP_DETAILS
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (step == STEP_TEMPLATE) {
                                R.string.projects_create_choose_template
                            } else {
                                R.string.projects_create_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = goBack, enabled = !uiState.isCreating) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    uiState.error?.let { error ->
                        Text(
                            text = error.userMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            if (goesToTemplate) {
                                step = STEP_TEMPLATE
                            } else {
                                val privacy = if (allowSearch) "public" else "private"
                                viewModel.createProject(
                                    name,
                                    prefix,
                                    privacy,
                                    if (usesTemplate) selectedTemplate else null,
                                    backupJson
                                )
                            }
                        },
                        enabled = canContinue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                stringResource(
                                    if (goesToTemplate) {
                                        R.string.projects_create_next
                                    } else {
                                        R.string.projects_create_action
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (step == STEP_DETAILS) {
            DetailsStep(
                name = name,
                prefix = prefix,
                allowSearch = allowSearch,
                backupName = backupName,
                enabled = !uiState.isCreating,
                onNameChange = { value ->
                    name = value
                    if (!prefixEdited) {
                        prefix = prefixFromName(value)
                    }
                },
                onPrefixChange = { value ->
                    prefixEdited = true
                    prefix = prefixFromName(value)
                },
                onAllowSearchChange = { checked -> allowSearch = checked },
                onPickBackup = { backupPicker.launch(arrayOf(MIME_ZIP, MIME_JSON)) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            TemplateStep(
                templates = uiState.templates,
                selectedTemplate = selectedTemplate,
                onSelect = { templateId -> selectedTemplate = templateId },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

/** Step one: the name, prefix, privacy toggle and the optional backup import. */
@Composable
private fun DetailsStep(
    name: String,
    prefix: String,
    allowSearch: Boolean,
    backupName: String?,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onPrefixChange: (String) -> Unit,
    onAllowSearchChange: (Boolean) -> Unit,
    onPickBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.projects_create_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = prefix,
            onValueChange = onPrefixChange,
            label = { Text(stringResource(R.string.projects_create_prefix)) },
            singleLine = true,
            supportingText = {
                val sample = prefix.ifBlank { "project" }
                Text(stringResource(R.string.projects_create_prefix_hint, sample))
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.projects_create_allow_search),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            )
            Switch(
                checked = allowSearch,
                onCheckedChange = onAllowSearchChange
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.projects_create_import_backup),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPickBackup,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(backupName ?: stringResource(R.string.projects_create_upload_json))
        }
    }
}

/** Step two: the template picker, one card per template the server offers. */
@Composable
private fun TemplateStep(
    templates: List<Template>,
    selectedTemplate: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(templates, key = { template -> template.id }) { template ->
            TemplateCard(
                icon = templateIcon(template.icon),
                name = template.name,
                description = template.description,
                selected = selectedTemplate == template.id,
                onClick = { onSelect(template.id) }
            )
        }
    }
}

/**
 * Template card; selection changes only the border colour, never its width, so
 * the list does not shift.
 */
@Composable
private fun TemplateCard(
    icon: ImageVector,
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Best-effort glyph for a template's icon name. The server sends short icon
// keys; unknown ones fall back to a generic dashboard glyph so a card always
// has an icon. Matching ignores case and any -/_ separators.
private fun templateIcon(icon: String): ImageVector =
    when (icon.lowercase().replace("-", "").replace("_", "").replace(" ", "")) {
        "file", "document", "description", "blank", "note", "insertdrivefile" ->
            Icons.Outlined.Description
        "zap", "bolt", "flash", "flashon", "agile", "lightning" ->
            Icons.Outlined.Bolt
        "grid", "gridview", "layoutgrid", "kanban", "viewkanban", "board" ->
            Icons.Outlined.GridView
        "ticket", "confirmationnumber", "localactivity", "tag", "support" ->
            Icons.Outlined.ConfirmationNumber
        else -> Icons.Outlined.Dashboard
    }
