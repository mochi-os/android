// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.CopyButton
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownField
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.R
import org.mochios.settings.api.AccountKey
import org.mochios.settings.api.Availability
import org.mochios.settings.api.Handle
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworkFeed
import org.mochios.settings.api.NetworkProvider
import org.mochios.android.R as MochiR

/** A progress ring the size of a button's icon, for a button whose action is running. */
@Composable
private fun ButtonProgress() {
    CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
}

/**
 * Linking an existing account: the network, then what its provider's form
 * asks; a server's consent takes the user to the browser and back.
 */
@Composable
internal fun LinkDialog(
    providers: List<NetworkProvider>,
    busy: Boolean,
    onLink: (type: String, fields: Map<String, String>, oauth: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(providers.firstOrNull()?.type.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    val values = remember(type) { mutableStateMapOf<String, String>() }
    val provider = providers.firstOrNull { it.type == type }
    val fields = provider?.link?.fields.orEmpty()
    val oauth = provider?.link?.flow == "oauth"
    val complete = provider != null && fields.all { !it.required || values[it.name].orEmpty().isNotBlank() }

    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.networks_link),
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MochiDropdownField(
                    value = networkLabel(type),
                    expanded = picking,
                    onExpandedChange = { picking = it },
                    label = stringResource(R.string.networks_network),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (option in providers) {
                        MochiDropdownMenuItem(
                            text = { Text(networkLabel(option.type)) },
                            onClick = {
                                picking = false
                                type = option.type
                            },
                            selected = option.type == type,
                        )
                    }
                }
                for (field in fields) {
                    // Credentials are masked and kept off the keyboard's learning.
                    val secret = field.type == "password"
                    MochiTextField(
                        value = values[field.name].orEmpty(),
                        onValueChange = { values[field.name] = it },
                        label = { Text(field.label.ifEmpty { field.name }) },
                        placeholder = if (field.placeholder.isNotEmpty()) {
                            { Text(field.placeholder) }
                        } else {
                            null
                        },
                        singleLine = true,
                        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri,
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmText = stringResource(if (oauth) MochiR.string.common_continue else R.string.networks_link_action),
        onConfirm = {
            val filled = fields.mapNotNull { field ->
                values[field.name]?.trim()?.takeIf { it.isNotEmpty() }?.let { field.name to it }
            }.toMap()
            onLink(type, filled, oauth)
        },
        confirmEnabled = complete && !busy,
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_cancel),
        dismissEnabled = !busy,
    )
}

/** A name under a handle, by its suffix: alice_news under alice. */
@Composable
internal fun AddNameDialog(
    parent: Handle,
    busy: Boolean,
    check: suspend (String, String) -> Availability?,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var suffix by remember { mutableStateOf("") }
    val name = if (suffix.isNotEmpty()) "${parent.name}_$suffix" else ""
    val (result, checking) = rememberAvailability(name, parent.domain, check)
    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.networks_add_name_title, parent.name),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MochiTextField(
                    value = suffix,
                    onValueChange = { typed -> suffix = typed.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' } },
                    label = { Text(stringResource(R.string.networks_name)) },
                    prefix = { Text("${parent.name}_") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(modifier = Modifier.heightIn(min = 20.dp)) {
                    AvailabilityNote(result = result, checking = checking && suffix.isNotEmpty())
                }
            }
        },
        confirmText = stringResource(MochiR.string.common_add),
        onConfirm = { onAdd(name) },
        confirmEnabled = result?.available == true && !checking && !busy,
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_cancel),
        dismissEnabled = !busy,
    )
}

/** The name an account's profile shows. */
@Composable
internal fun ProfileDialog(
    account: NetworkAccount,
    busy: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(account.label.ifEmpty { account.name }) }
    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.networks_profile),
        content = {
            MochiTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.networks_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = { onSave(label) },
        confirmEnabled = !busy && label.trim() != account.label,
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_cancel),
        dismissEnabled = !busy,
    )
}

/**
 * A list kept whole on the server, each entry removed or added in place: a
 * hosted fediverse account's aliases, or the labellers a hosted Bluesky
 * account reads.
 */
@Composable
internal fun ListDialog(
    title: String,
    initial: List<String>,
    field: String,
    placeholder: String,
    busy: Boolean,
    onSave: (List<String>, () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var entry by remember { mutableStateOf("") }
    fun save(next: List<String>) = onSave(next) {
        entries.clear()
        entries.addAll(next)
        entry = ""
    }
    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = title,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in entries) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        MochiIconButton(onClick = { save(entries.filter { it != item }) }, enabled = !busy) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.networks_remove_entry, item),
                            )
                        }
                    }
                }
                MochiTextField(
                    value = entry,
                    onValueChange = { entry = it },
                    label = { Text(field) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (entry.isNotBlank() && !busy) save(entries + entry.trim())
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
                MochiOutlinedButton(
                    onClick = { save(entries + entry.trim()) },
                    enabled = entry.isNotBlank() && !busy,
                ) {
                    if (busy) ButtonProgress() else Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(MochiR.string.common_add))
                }
            }
        },
        dismissText = stringResource(MochiR.string.common_close),
        dismissEnabled = !busy,
    )
}

/**
 * Where posts made from other apps signed in to an account land: one of the
 * user's public feeds, a new one named after the account, or none.
 */
@Composable
internal fun HomeDialog(
    account: NetworkAccount,
    feeds: List<NetworkFeed>,
    busy: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = feeds.filter { it.privacy == "public" }.sortedWith(compareBy(NaturalCompare) { it.name })
    var choice by remember { mutableStateOf(if (choices.any { it.id == account.home }) account.home else "") }
    var picking by remember { mutableStateOf(false) }
    val none = stringResource(R.string.networks_home_none)
    val fresh = stringResource(R.string.networks_home_new)
    val shown = when (choice) {
        "" -> none
        NetworksViewModel.HOME_NEW -> fresh
        else -> choices.firstOrNull { it.id == choice }?.name.orEmpty()
    }
    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.networks_home),
        content = {
            MochiDropdownField(
                value = shown,
                expanded = picking,
                onExpandedChange = { picking = it },
                label = stringResource(R.string.networks_home),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MochiDropdownMenuItem(
                    text = { Text(none) },
                    onClick = { picking = false; choice = "" },
                    selected = choice == "",
                )
                for (feed in choices) {
                    MochiDropdownMenuItem(
                        text = { Text(feed.name) },
                        onClick = { picking = false; choice = feed.id },
                        selected = choice == feed.id,
                    )
                }
                MochiDropdownMenuItem(
                    text = { Text(fresh) },
                    onClick = { picking = false; choice = NetworksViewModel.HOME_NEW },
                    selected = choice == NetworksViewModel.HOME_NEW,
                )
            }
        },
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = { onSave(choice) },
        confirmEnabled = !busy,
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_cancel),
        dismissEnabled = !busy,
    )
}

/**
 * Moving a hosted fediverse account to an account elsewhere, which must name
 * this one among its aliases first; the followers' servers are told to
 * follow it instead.
 */
@Composable
internal fun MoveDialog(
    account: NetworkAccount,
    busy: Boolean,
    onMove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.networks_move),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MochiTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.networks_move_field)) },
                    placeholder = { Text("@name@server.example") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.networks_move_hint, account.identifier),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmText = stringResource(R.string.networks_move_action),
        onConfirm = { onMove(address) },
        confirmEnabled = address.isNotBlank() && !busy,
        confirmLoading = busy,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
        dismissEnabled = !busy,
    )
}

/** A value shown to be copied, read-only, with its copy button. */
@Composable
private fun CopyField(label: String, value: String, sensitive: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MochiTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        CopyButton(value = value, contentDescription = stringResource(MochiR.string.common_copy), sensitive = sensitive)
    }
}

/**
 * A hosted account's keys: what it signs with, replaced here, and on Bluesky
 * the recovery key the user keeps - offered as soon as the account is made,
 * with the dialog opening on the question.
 */
@Composable
internal fun KeysDialog(
    account: NetworkAccount,
    offer: Boolean,
    busy: String?,
    load: suspend (NetworkAccount) -> List<AccountKey>?,
    onReplace: (() -> Unit) -> Unit,
    onRecovery: ((String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val format = LocalFormat.current
    var keys by remember { mutableStateOf<List<AccountKey>?>(null) }
    var round by remember { mutableStateOf(0) }
    var replacing by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(offer) }
    var secret by remember { mutableStateOf("") }
    LaunchedEffect(account.id, round) { keys = load(account) }

    MochiAlertDialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        title = stringResource(R.string.networks_keys),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val listed = keys
                if (listed == null) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    for (key in listed) {
                        Column {
                            Text("${key.kind} · ${key.algorithm}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.account_created, format.formatTimestamp(key.created)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (secret.isNotEmpty()) {
                    CopyField(stringResource(R.string.networks_recovery), secret, sensitive = true)
                    Text(
                        stringResource(R.string.networks_recovery_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (account.type == "bluesky") {
                    MochiOutlinedButton(onClick = { recovering = true }, enabled = busy == null) {
                        if (busy == "recovery") ButtonProgress() else Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.networks_recovery))
                    }
                }
                MochiOutlinedButton(onClick = { replacing = true }, enabled = busy == null) {
                    if (busy == "keys") ButtonProgress() else Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.networks_replace_keys))
                }
            }
        },
        dismissText = stringResource(MochiR.string.common_close),
        dismissEnabled = busy == null,
    )

    if (replacing) {
        MochiAlertDialog(
            onDismissRequest = { replacing = false },
            title = stringResource(R.string.networks_replace_title),
            text = stringResource(R.string.networks_replace_message),
            confirmText = stringResource(R.string.networks_replace),
            onConfirm = {
                replacing = false
                onReplace { round++ }
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
    if (recovering) {
        MochiAlertDialog(
            onDismissRequest = { recovering = false },
            title = stringResource(R.string.networks_recovery_title),
            text = stringResource(R.string.networks_recovery_message),
            confirmText = stringResource(R.string.networks_recovery_make),
            onConfirm = {
                recovering = false
                onRecovery { made ->
                    secret = made
                    round++
                }
            },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/**
 * Signing in from another app: the server to give it, the account's address,
 * and a password minted for the device, shown once.
 */
@Composable
internal fun PasswordDialog(
    account: NetworkAccount,
    busy: Boolean,
    onMake: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var device by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.networks_password_title),
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CopyField(stringResource(R.string.networks_server), serverAddress(account))
                CopyField(stringResource(R.string.networks_address), account.identifier)
                if (password.isNotEmpty()) {
                    CopyField(stringResource(R.string.networks_password), password, sensitive = true)
                    Text(
                        stringResource(R.string.networks_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MochiTextField(
                        value = device,
                        onValueChange = { device = it },
                        label = { Text(stringResource(R.string.networks_device)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmText = if (password.isEmpty()) stringResource(R.string.networks_password_make) else null,
        onConfirm = { onMake(device) { password = it } },
        confirmEnabled = device.isNotBlank() && !busy,
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_close),
        dismissEnabled = !busy,
    )
}
