// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.projectlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.mochios.projects.R
import org.mochios.projects.model.Template
import org.mochios.android.R as MochiR

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
 * Create dialog for a project, optionally seeded from a backup file.
 *
 * @param backupPrefill what the ViewModel read out of the picked backup, or
 *   null when no file has been chosen; seeds the name, prefix and payload.
 * @param onPickBackup hands the picked file's uri to the ViewModel to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    templates: List<Template>,
    isCreating: Boolean,
    backupPrefill: BackupPrefill?,
    onPickBackup: (Uri) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (name: String, prefix: String, privacy: String, template: String?, backupJson: String?) -> Unit
) {
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
    // Two-step flow: 0 = name/prefix details, 1 = template selection. When there
    // are no templates the flow stays on step 0 and creates directly.
    var step by remember { mutableStateOf(0) }
    val hasTemplates = templates.isNotEmpty()

    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickBackup(uri)
        }
    }

    // Seed the fields once the ViewModel has read the picked backup.
    LaunchedEffect(backupPrefill) {
        backupPrefill?.let { prefill ->
            backupJson = prefill.json
            backupName = prefill.fileName
            prefill.name?.let { value -> name = value }
            prefill.prefix?.let { value ->
                prefix = prefixFromName(value)
                prefixEdited = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (step == 1) {
                        R.string.projects_create_choose_template
                    } else {
                        R.string.projects_create_title
                    }
                )
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (step == 0) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { value ->
                            name = value
                            if (!prefixEdited) {
                                prefix = prefixFromName(value)
                            }
                        },
                        label = { Text(stringResource(R.string.projects_create_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { value ->
                            prefixEdited = true
                            prefix = prefixFromName(value)
                        },
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
                            onCheckedChange = { checked -> allowSearch = checked }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.projects_create_import_backup),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { backupPicker.launch("application/json") },
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
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { tmpl ->
                            TemplateCard(
                                icon = templateIcon(tmpl.icon),
                                name = tmpl.name,
                                description = tmpl.description,
                                selected = selectedTemplate == tmpl.id,
                                onClick = { selectedTemplate = tmpl.id }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // On the details step with templates available, advance to the
            // template picker; otherwise create the project.
            val goesToTemplate = step == 0 && hasTemplates && backupJson == null
            TextButton(
                onClick = {
                    if (goesToTemplate) {
                        step = 1
                    } else {
                        val privacy = if (allowSearch) "public" else "private"
                        onCreate(name, prefix, privacy, selectedTemplate, backupJson)
                    }
                },
                // On the details step only a name is needed to advance; on the
                // template step the user must have picked a template to create.
                enabled = !isCreating && if (step == 0) {
                    name.isNotBlank()
                } else {
                    selectedTemplate != null
                }
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
        },
        dismissButton = {
            // Back returns to the details step; Cancel dismisses the dialog.
            TextButton(
                onClick = { if (step == 1) step = 0 else onDismiss() },
                enabled = !isCreating
            ) {
                Text(
                    stringResource(
                        if (step == 1) MochiR.string.common_back else MochiR.string.common_cancel
                    )
                )
            }
        }
    )
}

/**
 * A selectable template card: an icon tile, the template name, and its
 * description. The chosen card is tinted and gets a primary border, matching the
 * web template picker.
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
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
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
