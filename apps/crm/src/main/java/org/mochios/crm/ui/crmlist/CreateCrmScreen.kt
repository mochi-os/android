// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crmlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.mochios.android.files.MIME_JSON
import org.mochios.android.files.MIME_ZIP
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.LabeledSwitchRow
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.crm.R

@Composable
fun CreateCrmScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateCrmViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
            viewModel.readBackup(uri)
        }
    }

    // Seed the fields once the ViewModel has read the picked backup.
    LaunchedEffect(uiState.backupPrefill) {
        uiState.backupPrefill?.let { prefill ->
            backupJson = prefill.json
            backupName = prefill.fileName
            prefill.name?.let { value -> name = value }
        }
    }

    LaunchedEffect(uiState.createdCrmId) {
        uiState.createdCrmId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedCrm()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.crm_create_title),
        submitLabel = stringResource(R.string.crm_create_action),
        submitEnabled = name.isNotBlank() && !uiState.isCreating,
        isBusy = uiState.isCreating,
        error = uiState.error,
        onBack = onBack,
        onSubmit = {
            val privacy = if (allowSearch) "public" else "private"
            viewModel.createCrm(name, privacy, backupJson)
        }
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = name,
                onValueChange = { value -> name = value },
                label = { Text(stringResource(R.string.crm_create_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            LabeledSwitchRow(
                label = stringResource(R.string.crm_create_allow_search),
                checked = allowSearch,
                onCheckedChange = { checked -> allowSearch = checked }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.crm_create_import_backup),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            MochiOutlinedButton(
                onClick = { backupPicker.launch(arrayOf(MIME_ZIP, MIME_JSON)) },
                enabled = !uiState.isCreating,
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
    }
}
