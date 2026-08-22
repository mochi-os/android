// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButtonTone
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.settings.ui.login.StepUpHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.R as MochiR
import org.mochios.android.ui.components.CompactTextField
import org.mochios.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onClosed: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Closing soft-deletes the account and revokes every session; sign out so
    // the app drops to login (the user reactivates from the login flow).
    LaunchedEffect(Unit) {
        viewModel.closed.collect { onClosed() }
    }

    // The export bundle is built server-side; once ready, stream it to the
    // public Downloads directory via Android's DownloadManager (the auth token
    // is carried inline because DownloadManager runs out-of-process).
    LaunchedEffect(Unit) {
        viewModel.exportReady.collect { download ->
            startExportDownload(context, download)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.identity.entity.isBlank()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IdentitySection(
                state = state,
                onNameDraftChange = viewModel::updateName,
                onSaveName = viewModel::saveName,
                onPrivacy = viewModel::setPrivacy,
            )

            DataSection(onExport = viewModel::exportData)

            // Administrators can't close their own account (a self-closed sole
            // admin would strand the server — enforced server-side too).
            if (state.identity.role != "administrator") {
                CloseAccountSection(onClose = viewModel::closeAccount)
            }

            state.error?.let { err ->
                Text(text = err.userMessage(), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    StepUpHost(viewModel.stepUp)
}

@Composable
private fun DataSection(onExport: (passphrase: String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.account_data_section))
        Text(
            text = stringResource(R.string.account_data_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MochiOutlinedButton(onClick = { showDialog = true }) {
            Text(stringResource(R.string.account_data_download))
        }
    }

    if (showDialog) {
        var passphrase by remember { mutableStateOf("") }
        MochiAlertDialog(
            onDismissRequest = { showDialog = false },
            title = stringResource(R.string.account_data_dialog_title),
            content = {
                Column {
                    Text(
                        text = stringResource(R.string.account_data_dialog_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    MochiTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.account_data_passphrase)) },
                        // Masked, and typed on a password keyboard so the IME
                        // does not learn it or offer it back as a suggestion.
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.account_data_passphrase_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmText = stringResource(R.string.account_data_download),
            onConfirm = {
                val pass = passphrase.trim()
                showDialog = false
                onExport(pass)
            },
            confirmEnabled = passphrase.trim().isNotEmpty(),
            dismissText = stringResource(R.string.account_cancel),
        )
    }
}

/**
 * Hands the export to [DownloadManager], which runs out-of-process and bypasses
 * the app's interceptors, so the bearer token goes in the request header
 * directly.
 */
private fun startExportDownload(context: Context, download: ExportDownload) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val name = sanitiseFilename(download.filename)
    val req = DownloadManager.Request(Uri.parse(download.url))
        .setTitle(name)
        .setMimeType("application/zip")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
    if (!download.token.isNullOrBlank()) {
        req.addRequestHeader("Authorization", "Bearer ${download.token}")
    }
    dm.enqueue(req)
}

/** DownloadManager rejects names containing path separators or NULs. */
private fun sanitiseFilename(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "mochi-export.zip"
    return trimmed.replace(Regex("[/\\\\ ]"), "_")
}

@Composable
private fun CloseAccountSection(onClose: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.account_close_section))
        MochiOutlinedButton(
            onClick = { confirming = true },
            tone = MochiButtonTone.Neutral,
        ) {
            Text(stringResource(R.string.account_close_action))
        }
    }

    if (confirming) {
        MochiAlertDialog(
            onDismissRequest = { confirming = false },
            title = stringResource(R.string.account_close_confirm_title),
            text = stringResource(R.string.account_close_confirm_body),
            confirmText = stringResource(R.string.account_close_action),
            onConfirm = {
                confirming = false
                onClose()
            },
            destructive = true,
            dismissText = stringResource(R.string.account_cancel),
        )
    }
}

@Composable
private fun IdentitySection(
    state: AccountUiState,
    onNameDraftChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onPrivacy: (String) -> Unit,
) {
    val id = state.identity
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var editingName by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.account_section_identity))

        IdentityFieldRow(label = stringResource(R.string.account_identity_name)) {
            if (editingName) {
                CompactTextField(
                    value = state.nameDraft,
                    onValueChange = onNameDraftChange,
                    modifier = Modifier.weight(1f),
                )
                MochiTextButton(onClick = {
                    onSaveName()
                    editingName = false
                }) { Text(stringResource(R.string.account_save)) }
                MochiTextButton(onClick = {
                    onNameDraftChange(id.name)
                    editingName = false
                }) { Text(stringResource(R.string.account_cancel)) }
            } else {
                Text(
                    text = id.name.ifBlank { id.username },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                MochiIconButton(
                    onClick = { editingName = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        IdentityFieldRow(label = stringResource(R.string.account_identity_username)) {
            Text(
                text = id.username,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        IdentityFieldRow(label = stringResource(R.string.account_identity_fingerprint)) {
            ValueChip(
                text = chunkedFingerprint(id.fingerprint),
                monospace = true,
                modifier = Modifier.weight(1f),
            )
            MochiIconButton(
                onClick = { clipboard.setClip(ClipData.newPlainText("fingerprint", id.fingerprint).toClipEntry()) },
                enabled = id.fingerprint.isNotBlank(),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.account_copy),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IdentityFieldRow(label = stringResource(R.string.account_identity_identity)) {
            ValueChip(text = id.entity, monospace = true, modifier = Modifier.weight(1f))
            MochiIconButton(
                onClick = { clipboard.setClip(ClipData.newPlainText("identity", id.entity).toClipEntry()) },
                enabled = id.entity.isNotBlank(),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.account_copy),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.account_identity_directory_toggle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = id.privacy == "public",
                onCheckedChange = { on -> onPrivacy(if (on) "public" else "private") },
                enabled = !state.isSaving,
            )
        }
    }
}

@Composable
private fun IdentityFieldRow(
    label: String,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        content()
    }
}

@Composable
private fun ValueChip(text: String, monospace: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

/** Insert a hyphen every 3 chars — matches the web UI's fingerprint chunking. */
private fun chunkedFingerprint(raw: String): String {
    if (raw.isBlank()) return raw
    return raw.chunked(3).joinToString("-")
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
