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
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val upload by viewModel.upload.collectAsState()

    // The form is the view model's, so a rotation or a theme change keeps it.
    // Public lets everyone view the CRM and every signed-in person comment on
    // it, so it starts off and says so.
    val name = uiState.name
    val allowPublic = uiState.privacy == "public"
    val context = LocalContext.current
    val backup = uiState.backupPrefill

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

    // An unreadable or non-JSON pick used to do nothing at all.
    val importFailed = stringResource(R.string.crm_design_import_failed)
    LaunchedEffect(uiState.backupReadFailed) {
        if (uiState.backupReadFailed) {
            Toast.makeText(context, importFailed, Toast.LENGTH_LONG).show()
            viewModel.consumeBackupReadFailed()
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
        submitEnabled = name.isNotBlank(),
        isBusy = uiState.isCreating,
        upload = upload,
        error = uiState.error,
        onBack = onBack,
        onSubmit = {
            viewModel.createCrm(name, uiState.privacy, backup?.json, backup?.archive)
        }
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = name,
                onValueChange = { value -> viewModel.setName(value) },
                label = { Text(stringResource(R.string.crm_create_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            LabeledSwitchRow(
                label = stringResource(R.string.crm_create_public),
                checked = allowPublic,
                onCheckedChange = { checked -> viewModel.setPrivacy(if (checked) "public" else "private") }
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
                Text(backup?.fileName ?: stringResource(R.string.crm_create_upload_json))
            }
        }
    }
}
