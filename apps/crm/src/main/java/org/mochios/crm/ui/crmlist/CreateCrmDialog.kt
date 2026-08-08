// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crmlist

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.files.MIME_JSON
import org.mochios.android.files.MIME_ZIP
import org.mochios.crm.R
import org.mochios.android.R as MochiR

/**
 * Create dialog for a CRM, optionally seeded from a backup file. There is no
 * template step: a CRM is always created with the CRM template, and a picked
 * backup supplies the design instead.
 *
 * @param backupPrefill what the ViewModel read out of the picked backup, or
 *   null when no file has been chosen; seeds the name and payload.
 * @param onPickBackup hands the picked file's uri to the ViewModel to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCrmDialog(
    isCreating: Boolean,
    backupPrefill: BackupPrefill?,
    onPickBackup: (Uri) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (name: String, privacy: String, backupJson: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    // The toggle reads "Allow anyone to search for CRM" — on means public.
    var allowSearch by remember { mutableStateOf(true) }
    var backupJson by remember { mutableStateOf<String?>(null) }
    var backupName by remember { mutableStateOf<String?>(null) }

    // OpenDocument, not GetContent: exports are zipped now, and only this
    // contract takes more than one type, so both the zip and a bare .json
    // from an older backup stay selectable.
    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.crm_create_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value },
                    label = { Text(stringResource(R.string.crm_create_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.crm_create_allow_search),
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
                    text = stringResource(R.string.crm_create_import_backup),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { backupPicker.launch(arrayOf(MIME_ZIP, MIME_JSON)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(backupName ?: stringResource(R.string.crm_create_upload_json))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val privacy = if (allowSearch) "public" else "private"
                    onCreate(name, privacy, backupJson)
                },
                enabled = name.isNotBlank() && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.crm_create_action))
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
