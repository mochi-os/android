// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.encryption

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusTone
import org.mochios.android.util.NaturalCompare
import org.mochios.chat.R
import org.mochios.chat.model.EncryptionDevice
import org.mochios.chat.model.EncryptionStatus
import org.mochios.chat.model.OMEMO_CURRENT
import org.mochios.chat.model.fingerprintGroups
import org.mochios.chat.model.trusted
import org.mochios.android.R as MochiR

/** A Matrix or XMPP conversation's encryption, in its settings. */
@Composable
fun EncryptionSection(viewModel: EncryptionViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val format = LocalFormat.current
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is EncryptionEvent.Said -> event.text
                is EncryptionEvent.Restored -> context.resources.getQuantityString(
                    R.plurals.chat_encryption_restored, event.count, format.formatNumber(event.count),
                )
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    EncryptionContent(
        state = uiState,
        onRetry = viewModel::load,
        onToggle = viewModel::toggle,
        onTrust = viewModel::trust,
        onVerify = viewModel::verify,
        onSetup = viewModel::openSetup,
        onRecover = viewModel::openRecovery,
    )
    uiState.setup?.let { setup ->
        SetupDialog(
            setup = setup,
            onPassword = viewModel::setPassword,
            onSetup = viewModel::setup,
            onClose = viewModel::closeSetup,
        )
    }
    uiState.recovery?.let { key ->
        RecoverDialog(
            key = key,
            busy = uiState.recovering,
            onKey = viewModel::setRecovery,
            onRestore = viewModel::recover,
            onClose = viewModel::closeRecovery,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EncryptionContent(
    state: EncryptionUiState,
    onRetry: () -> Unit,
    onToggle: (String) -> Unit,
    onTrust: (String, String, String) -> Unit,
    onVerify: (String) -> Unit,
    onSetup: () -> Unit,
    onRecover: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.chat_encryption_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val overview = state.overview
        when {
            state.error != null && overview == null -> {
                Text(text = state.error.userMessage(), color = MaterialTheme.colorScheme.error)
                MochiTextButton(onClick = onRetry) { Text(stringResource(MochiR.string.common_retry)) }
            }
            overview == null -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else -> {
                val status = overview.status
                val matrix = status.network == "matrix"
                if (matrix) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!status.signing || !status.recovery) {
                            KeyButton(stringResource(R.string.chat_encryption_setup), onSetup)
                        }
                        KeyButton(stringResource(R.string.chat_encryption_recover), onRecover)
                    }
                }
                DeviceRows(status)
                val own = if (status.network == "xmpp") "xmpp:" + status.address else status.address
                PersonRow(
                    person = own,
                    name = stringResource(R.string.chat_encryption_own),
                    matrix = matrix,
                    own = true,
                    state = state,
                    onToggle = onToggle,
                    onTrust = onTrust,
                    onVerify = onVerify,
                )
                overview.people
                    .sortedWith(compareBy(NaturalCompare) { it.name.ifEmpty { it.id } })
                    .forEach { person ->
                        PersonRow(
                            person = person.id,
                            name = person.name.ifEmpty { person.id },
                            matrix = matrix,
                            own = false,
                            state = state,
                            onToggle = onToggle,
                            onTrust = onTrust,
                            onVerify = onVerify,
                        )
                    }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    MochiOutlinedButton(onClick = onClick) {
        Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

/** The server's device for the account: its fingerprints, and on Matrix whether it is verified. */
@Composable
private fun DeviceRows(status: EncryptionStatus) {
    if (status.network == "xmpp") {
        status.fingerprints.forEach { fingerprint ->
            Field(
                label = if (fingerprint.version == OMEMO_CURRENT) {
                    stringResource(R.string.chat_encryption_device_omemo2)
                } else {
                    stringResource(R.string.chat_encryption_device_omemo)
                },
            ) {
                DataChip(value = fingerprintGroups(fingerprint.fingerprint), wrap = true)
            }
        }
        return
    }
    Field(label = stringResource(R.string.chat_encryption_device)) {
        DataChip(value = fingerprintGroups(status.fingerprint), wrap = true)
        Spacer(Modifier.width(8.dp))
        TrustBadge(if (status.verified) "verified" else "undecided")
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun TrustBadge(trust: String) {
    when (trust) {
        "verified" -> StatusBadge(stringResource(R.string.chat_encryption_verified), StatusTone.Positive)
        "distrusted" -> StatusBadge(stringResource(R.string.chat_encryption_distrusted), StatusTone.Negative)
        else -> StatusBadge(stringResource(R.string.chat_encryption_unverified), StatusTone.Neutral)
    }
}

/** A person's devices, read when opened, and on Matrix comparing emoji with them. */
@Composable
private fun PersonRow(
    person: String,
    name: String,
    matrix: Boolean,
    own: Boolean,
    state: EncryptionUiState,
    onToggle: (String) -> Unit,
    onTrust: (String, String, String) -> Unit,
    onVerify: (String) -> Unit,
) {
    val open = person in state.open
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggle(person) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (open) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (matrix) {
                MochiOutlinedButton(onClick = { onVerify(person) }, enabled = state.verifying == null) {
                    if (state.verifying == person) {
                        CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.EmojiEmotions, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.chat_encryption_compare))
                }
            }
        }
        if (open) {
            val read = state.devices[person]
            val devices = read?.devices.orEmpty().filter { !it.own }
            Column(modifier = Modifier.padding(start = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    read == null || read.isLoading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    read.error != null -> Text(read.error.userMessage(), color = MaterialTheme.colorScheme.error)
                    devices.isEmpty() -> Text(
                        text = stringResource(if (own) R.string.chat_encryption_no_other else R.string.chat_encryption_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> devices.forEach { device -> DeviceRow(device) { trust -> onTrust(person, device.id, trust) } }
                }
            }
        }
    }
}

/** A device of a person's, with the user's word on it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(device: EncryptionDevice, onTrust: (String) -> Unit) {
    val trust = device.trusted()
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(device.name.ifEmpty { device.id }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (device.fingerprint.isNotEmpty()) DataChip(value = fingerprintGroups(device.fingerprint), wrap = true)
            TrustBadge(trust)
        }
        Box {
            MochiIconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.chat_encryption_device_options))
            }
            MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (trust != "verified") {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_encryption_matches)) },
                        onClick = { menu = false; onTrust("verified") },
                    )
                }
                if (trust != "distrusted") {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_encryption_distrust)) },
                        onClick = { menu = false; onTrust("distrusted") },
                    )
                }
                if (trust != "undecided") {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_encryption_forget)) },
                        onClick = { menu = false; onTrust("undecided") },
                    )
                }
            }
        }
    }
}

/** Setting up recovery: the account's password where its server wants it, then the recovery key, shown this once. */
@Composable
internal fun SetupDialog(
    setup: SetupState,
    onPassword: (String) -> Unit,
    onSetup: () -> Unit,
    onClose: () -> Unit,
) {
    val shown = setup.recovery.isNotEmpty()
    MochiAlertDialog(
        onDismissRequest = onClose,
        title = stringResource(if (shown) R.string.chat_encryption_key_title else R.string.chat_encryption_setup),
        content = when {
            shown -> ({
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.chat_encryption_key_keep))
                    DataChip(value = setup.recovery, wrap = true)
                }
            })
            setup.asked -> ({
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.chat_encryption_password_asked))
                    MochiTextField(
                        value = setup.password,
                        onValueChange = onPassword,
                        label = { Text(stringResource(R.string.chat_encryption_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            })
            else -> null
        },
        confirmText = stringResource(if (shown) R.string.chat_encryption_done else R.string.chat_encryption_setup),
        onConfirm = if (shown) onClose else onSetup,
        confirmEnabled = shown || !(setup.asked && setup.password.isEmpty()),
        confirmLoading = setup.busy,
        dismissText = if (shown) null else stringResource(MochiR.string.common_cancel),
        onDismiss = onClose,
    )
}

/** Entering a recovery key set up on another client. */
@Composable
internal fun RecoverDialog(
    key: String,
    busy: Boolean,
    onKey: (String) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onClose,
        title = stringResource(R.string.chat_encryption_recover),
        content = {
            MochiTextField(
                value = key,
                onValueChange = onKey,
                label = { Text(stringResource(R.string.chat_encryption_key)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmText = stringResource(R.string.chat_encryption_restore),
        onConfirm = onRestore,
        confirmEnabled = key.isNotBlank(),
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_cancel),
        onDismiss = onClose,
    )
}
