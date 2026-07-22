// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.projectlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    templates: List<Template>,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, prefix: String, privacy: String, template: String?, backupJson: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    // The prefix auto-tracks the name until the user edits it by hand.
    var prefixEdited by remember { mutableStateOf(false) }
    // The toggle reads "Allow anyone to search for project" — on means public.
    var allowSearch by remember { mutableStateOf(true) }
    var selectedTemplate by remember { mutableStateOf<String?>(null) }
    var templateExpanded by remember { mutableStateOf(false) }
    var backupJson by remember { mutableStateOf<String?>(null) }
    var backupName by remember { mutableStateOf<String?>(null) }

    val templateNoneLabel = stringResource(R.string.projects_create_template_none)
    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { reader -> reader.readText() }
                }
                if (content != null) {
                    backupJson = content
                    backupName = uri.lastPathSegment
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.projects_create_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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

                if (templates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ExposedDropdownMenuBox(
                        expanded = templateExpanded,
                        onExpandedChange = { templateExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = templates.find { it.id == selectedTemplate }?.name ?: templateNoneLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.projects_create_template)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = templateExpanded,
                            onDismissRequest = { templateExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(templateNoneLabel) },
                                onClick = {
                                    selectedTemplate = null
                                    templateExpanded = false
                                }
                            )
                            templates.forEach { tmpl ->
                                DropdownMenuItem(
                                    text = { Text(tmpl.name) },
                                    onClick = {
                                        selectedTemplate = tmpl.id
                                        templateExpanded = false
                                    }
                                )
                            }
                        }
                    }
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val privacy = if (allowSearch) "public" else "private"
                    onCreate(name, prefix, privacy, selectedTemplate, backupJson)
                },
                enabled = name.isNotBlank() && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.projects_create_action))
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
