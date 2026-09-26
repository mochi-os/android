// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.CopyButton
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.SecureWindow
import org.mochios.people.R
import org.mochios.people.model.DeviceToken
import org.mochios.android.R as MochiR

/**
 * Connect a device: name it, create its password, and copy the server,
 * address-book URL, username and password into the device's contacts
 * account. The password is shown once, here; below it every connected
 * device with when it was created and last used, each deletable alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectDeviceScreen(
    onBack: () -> Unit,
    viewModel: ConnectDeviceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConnectDeviceEvent.Failed -> snackbar.showSnackbar(event.error.userMessage())
                ConnectDeviceEvent.Deleted ->
                    snackbar.showSnackbar(resources.getString(R.string.people_device_deleted))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_device_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "create") {
                CreateSection(
                    name = uiState.name,
                    creating = uiState.isCreating,
                    onNameChange = viewModel::setName,
                    onCreate = viewModel::create,
                )
            }
            uiState.token?.let { token ->
                item(key = "credentials") {
                    CredentialsSection(
                        server = uiState.server,
                        address = uiState.address,
                        username = uiState.username,
                        token = token,
                        onDone = viewModel::done,
                    )
                }
            }
            item(key = "heading") {
                Text(
                    text = stringResource(R.string.people_device_list),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val error = uiState.error
            when {
                uiState.isLoading && uiState.tokens.isEmpty() -> item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null && uiState.tokens.isEmpty() -> item(key = "error") {
                    InlineErrorState(error = error, onRetry = viewModel::load)
                }
                uiState.tokens.isEmpty() -> item(key = "empty") {
                    Text(
                        text = stringResource(R.string.people_device_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> items(uiState.tokens, key = { it.hash }) { device ->
                    DeviceRow(
                        device = device,
                        onDelete = { viewModel.requestDelete(device) },
                    )
                }
            }
        }
    }

    val deleting = uiState.deleting
    if (deleting != null) {
        MochiAlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = stringResource(R.string.people_device_delete_title),
            text = stringResource(R.string.people_device_delete_message),
            confirmText = stringResource(R.string.people_common_delete),
            onConfirm = viewModel::confirmDelete,
            confirmLoading = uiState.isDeleting,
            destructive = true,
            dismissText = stringResource(R.string.people_common_cancel),
        )
    }
}

@Composable
private fun CreateSection(
    name: String,
    creating: Boolean,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MochiTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.people_device_name)) },
            singleLine = true,
            enabled = !creating,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCreate() }),
            modifier = Modifier.fillMaxWidth(),
        )
        MochiButton(
            onClick = onCreate,
            enabled = name.isNotBlank() && !creating,
            modifier = Modifier.align(Alignment.End),
        ) {
            if (creating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.people_device_create))
        }
    }
}

/**
 * What the new device's contacts account asks for. The window is kept out of
 * screenshots and the recent-apps thumbnail while the password is on screen.
 */
@Composable
private fun CredentialsSection(
    server: String,
    address: String,
    username: String,
    token: String,
    onDone: () -> Unit,
) {
    SecureWindow()
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CredentialRow(stringResource(R.string.people_device_server), server)
            CredentialRow(stringResource(R.string.people_device_address), address)
            CredentialRow(stringResource(R.string.people_device_username), username)
            CredentialRow(stringResource(R.string.people_device_password), token, sensitive = true)
            Text(
                text = stringResource(R.string.people_device_warning),
                style = MaterialTheme.typography.bodyMedium,
            )
            MochiOutlinedButton(
                onClick = onDone,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.people_device_done))
            }
        }
    }
}

/**
 * A labelled value with its copy button, in the account screen's field-row
 * shape. The value wraps rather than truncating: the user may have to read
 * it off this screen and type it on the other device.
 */
@Composable
private fun CredentialRow(label: String, value: String, sensitive: Boolean = false) {
    FieldRow(label = label) {
        DataChip(
            value = value,
            copyable = false,
            wrap = true,
            modifier = Modifier.weight(1f),
        )
        CopyButton(
            value = value,
            contentDescription = stringResource(R.string.people_device_copy, label),
            sensitive = sensitive,
        )
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        content()
    }
}

@Composable
private fun DeviceRow(device: DeviceToken, onDelete: () -> Unit) {
    val format = LocalFormat.current
    val never = stringResource(R.string.people_device_never)
    val created = if (device.created > 0) format.formatTimestamp(device.created) else never
    val used = if (device.used > 0) format.formatTimestamp(device.used) else never
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.people_device_created, created) + " · " +
                        stringResource(R.string.people_device_used, used),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MochiIconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.people_device_delete),
                )
            }
        }
    }
}
