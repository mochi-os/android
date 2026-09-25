// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.settings.api.ConnectedAccount
import org.mochios.settings.api.Device
import org.mochios.settings.api.Provider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.input.VisualTransformation
import org.mochios.settings.api.ProviderField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAccountsScreen(
    onBack: () -> Unit,
    viewModel: ConnectedAccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var snack by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        scope.launch { viewModel.toasts.collect { snack = it } }
    }
    var adding by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf<ConnectedAccount?>(null) }
    var settingsOf by remember { mutableStateOf<ConnectedAccount?>(null) }
    var deleting by remember { mutableStateOf<ConnectedAccount?>(null) }
    var forgetting by remember { mutableStateOf<Device?>(null) }

    val snackbar = remember { SnackbarHostState() }

    // An error while content is on screen is shown over it rather than

    // replacing it: the full-screen arm below only fires when there is

    // nothing to show, and nothing on it can reach a refresh to clear it.

    LaunchedEffect(state.error) {

        val failure = state.error

        if (failure != null && (state.accounts.isNotEmpty() || state.providers.isNotEmpty())) {

            snackbar.showSnackbar(failure.userMessage())

            viewModel.clearError()

        }

    }

    Scaffold(

        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.accounts.isEmpty() && state.providers.isEmpty() -> ErrorState(
                    error = state.error!!,
                    onRetry = viewModel::refresh,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("add") {
                        MochiOutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.accounts_add))
                        }
                    }
                    // An account bound to a listed device is that device's,
                    // shown in its row; forgetting the device removes it.
                    val deviceIds = state.devices.map { it.id }.toSet()
                    val visible = state.accounts.filter { it.device.isBlank() || it.device !in deviceIds }
                    if (state.devices.isNotEmpty()) {
                        item("devices") {
                            Text(
                                stringResource(R.string.accounts_devices),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(
                            state.devices.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }),
                            key = { "device:" + it.id },
                        ) { device ->
                            DeviceRow(
                                device = device,
                                accounts = state.accounts.filter { it.device == device.id },
                                onForget = { forgetting = device },
                            )
                        }
                    }
                    if (visible.isEmpty()) {
                        item("empty") {
                            Text(
                                stringResource(R.string.accounts_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(visible, key = { it.id }) { account ->
                            AccountRow(
                                account = account,
                                providers = state.providers,
                                onVerify = { verifying = account },
                                onSettings = { settingsOf = account },
                                onTest = { viewModel.test(account.id) },
                                onRemove = { deleting = account },
                                onToggleNotify = { viewModel.toggleNotifyDefault(account.id, it) },
                                onSetAiDefault = { viewModel.setAiDefault(account.id) },
                            )
                        }
                    }
                }
            }
            snack?.let { msg ->
                SnackBanner(msg) { snack = null }
            }
        }
    }

    if (adding) {
        AddAccountDialog(
            providers = state.providers,
            onDismiss = { adding = false },
            onSave = { type, fields ->
                viewModel.addAccount(type, fields)
                adding = false
            },
        )
    }
    verifying?.let { acc ->
        VerifyDialog(
            account = acc,
            onDismiss = { verifying = null },
            onVerify = { code ->
                viewModel.verify(acc.id, code)
                verifying = null
            },
            onResend = { viewModel.resend(acc.id) },
        )
    }
    settingsOf?.let { acc ->
        AccountSettingsDialog(
            account = acc,
            onDismiss = { settingsOf = null },
            onSave = { name, model ->
                val fields = HashMap<String, String>()
                fields["label"] = name
                if (acc.type == "openai" || acc.type == "claude") {
                    fields["model"] = model
                }
                viewModel.update(acc.id, fields)
                settingsOf = null
            },
        )
    }
    deleting?.let { acc ->
        // Removing an account the user signs in with revokes what it was
        // granted; the identity itself stays until it is unlinked on the
        // Login page, and the confirmation says so.
        val provider = state.providers.firstOrNull { it.type == acc.type }
        val revoking = provider?.flow == "oauth" && acc.granted.any { it != CAPABILITY_LOGIN }
        MochiAlertDialog(
            onDismissRequest = { deleting = null },
            title = if (revoking) {
                stringResource(R.string.accounts_revoke)
            } else {
                stringResource(R.string.accounts_remove_title)
            },
            text = if (revoking) {
                stringResource(
                    R.string.accounts_revoke_confirm,
                    displayName(acc),
                    providerTypeLabel(acc.type),
                )
            } else {
                stringResource(R.string.accounts_remove_message, displayName(acc))
            },
            confirmText = if (revoking) {
                stringResource(R.string.accounts_revoke)
            } else {
                stringResource(R.string.accounts_remove)
            },
            onConfirm = {
                viewModel.remove(acc.id)
                deleting = null
            },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
    forgetting?.let { device ->
        MochiAlertDialog(
            onDismissRequest = { forgetting = null },
            title = stringResource(R.string.accounts_forget_title),
            text = stringResource(R.string.accounts_forget_message, device.label.ifBlank { stringResource(R.string.notifprefs_dest_device) }),
            confirmText = stringResource(R.string.accounts_forget),
            onConfirm = {
                viewModel.forgetDevice(device.id)
                forgetting = null
            },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

// A device with the transport of the push account registered from it, and a
// Forget action that takes that account with the device.
@Composable
private fun DeviceRow(
    device: Device,
    accounts: List<ConnectedAccount>,
    onForget: () -> Unit,
) {
    val format = LocalFormat.current
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.label.ifBlank { stringResource(R.string.notifprefs_dest_device) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                val transports = accounts.map { providerTypeLabel(it.type) }
                if (transports.isNotEmpty()) {
                    Text(
                        transports.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.accounts_device_seen, format.formatTimestamp(device.seen)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MochiOutlinedButton(onClick = onForget) {
                Text(stringResource(R.string.accounts_forget))
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: ConnectedAccount,
    providers: List<Provider>,
    onVerify: () -> Unit,
    onSettings: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit,
    onToggleNotify: (Boolean) -> Unit,
    onSetAiDefault: () -> Unit,
) {
    val provider = providers.firstOrNull { it.type == account.type }
    val needsVerify = provider?.verify == true && account.verified == 0
    val isAi = account.type == "claude" || account.type == "openai"
    val notifyCapable = provider?.capabilities?.contains("notify") == true
    // An account the user signed in through carries what it has been granted
    // so far, which is the useful thing to say about it: "Sign-in" alone, or
    // "Sign-in, Calendar" once a calendar has been linked through it.
    val oauth = provider?.flow == "oauth"
    val granted = account.granted.map { capability -> capabilityLabel(capability) }.joinToString(", ")
    val revocable = oauth && account.granted.any { it != CAPABILITY_LOGIN }
    val loginOnly = oauth && account.granted == listOf(CAPABILITY_LOGIN)
    var menu by remember { mutableStateOf(false) }

    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName(account),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (isAi && account.default == "ai") {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.accounts_default_for_ai),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = providerTypeLabel(account.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val statusRes = when {
                    needsVerify -> R.string.accounts_status_pending
                    provider?.verify == true && account.verified > 0 -> R.string.accounts_status_verified
                    else -> R.string.accounts_status_connected
                }
                Text(
                    text = if (oauth && granted.isNotEmpty()) granted else stringResource(statusRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (notifyCapable) {
                Switch(
                    checked = account.enabled > 0,
                    onCheckedChange = onToggleNotify,
                )
            }
            Box {
                MochiIconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = null)
                }
                MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (needsVerify) {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.accounts_verify)) },
                            onClick = { menu = false; onVerify() },
                            leadingIcon = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null) },
                        )
                    }
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_test)) },
                        onClick = { menu = false; onTest() },
                        leadingIcon = { Icon(Icons.Outlined.MonitorHeart, contentDescription = null) },
                    )
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_settings)) },
                        onClick = { menu = false; onSettings() },
                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    )
                    if (isAi && account.default != "ai") {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.accounts_set_default_ai)) },
                            onClick = { menu = false; onSetAiDefault() },
                            leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                        )
                    }
                    // An account that only signs the user in is ended on the
                    // Login page, by unlinking it there; removing it here would
                    // say it goes when it stays.
                    if (!loginOnly) {
                        MochiDropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (revocable) R.string.accounts_revoke else R.string.accounts_remove,
                                    ),
                                )
                            },
                            onClick = { menu = false; onRemove() },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddAccountDialog(
    providers: List<Provider>,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, String>) -> Unit,
) {
    // Only form providers can be added here; browser-flow ones (push
    // registrations) arrive through the device's own registration.
    val formProviders = providers.filter { it.flow == "form" }
    var selectedType by remember(formProviders) {
        mutableStateOf(formProviders.firstOrNull()?.type ?: "")
    }
    val provider = formProviders.firstOrNull { it.type == selectedType }
    val values = remember(selectedType) { mutableStateMapOf<String, String>() }
    val canSave = provider != null &&
        provider.fields.all { !it.required || values[it.name].orEmpty().isNotBlank() }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.accounts_add_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.accounts_type_label), style = MaterialTheme.typography.labelMedium)
                for (p in formProviders) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selectedType == p.type, onClick = { selectedType = p.type })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedType == p.type, onClick = { selectedType = p.type })
                        Text(providerTypeLabel(p.type))
                    }
                }
                Spacer(Modifier.height(8.dp))
                provider?.fields?.forEach { field ->
                    // Credentials are masked and kept off the IME's
                    // personalised-learning path.
                    val secret = field.type == "password"
                    MochiTextField(
                        value = values[field.name].orEmpty(),
                        onValueChange = { values[field.name] = it },
                        label = { Text(fieldLabel(field)) },
                        singleLine = true,
                        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (field.type) {
                                "password" -> KeyboardType.Password
                                "email" -> KeyboardType.Email
                                "url" -> KeyboardType.Uri
                                else -> KeyboardType.Text
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = {
            val fields = HashMap<String, String>()
            provider?.fields?.forEach { field ->
                val value = values[field.name].orEmpty().trim()
                if (value.isNotEmpty()) fields[field.name] = value
            }
            onSave(selectedType, fields)
        },
        confirmEnabled = canSave,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

/** The server names each field by a label key; these are the same words the
 *  web form shows for them. */
@Composable
private fun fieldLabel(field: ProviderField): String = when (field.label) {
    "accounts.field.address" -> stringResource(R.string.accounts_field_email)
    "accounts.field.key" -> stringResource(R.string.accounts_field_api_key)
    "accounts.field.model" -> stringResource(R.string.accounts_field_model)
    "accounts.field.name" -> stringResource(R.string.accounts_field_name)
    "accounts.field.url" -> stringResource(R.string.accounts_field_url)
    "accounts.field.server" -> stringResource(R.string.accounts_field_server)
    "accounts.field.token" -> stringResource(R.string.accounts_field_token)
    "accounts.field.topic" -> stringResource(R.string.accounts_field_topic)
    "accounts.field.secret" -> stringResource(R.string.accounts_field_secret)
    else -> field.name
}

@Composable
private fun VerifyDialog(
    account: ConnectedAccount,
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.accounts_verify),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.accounts_verify_hint, account.identifier.ifBlank { account.label }),
                    style = MaterialTheme.typography.bodyMedium,
                )
                MochiTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.accounts_verify_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MochiTextButton(onClick = onResend) {
                    Text(stringResource(R.string.accounts_verify_resend))
                }
            }
        },
        confirmText = stringResource(R.string.accounts_verify),
        onConfirm = { onVerify(code.trim()) },
        confirmEnabled = code.trim().isNotEmpty(),
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

@Composable
private fun AccountSettingsDialog(
    account: ConnectedAccount,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    // Resolved outside the remember: displayName reads string resources, and a
    // remember calculation is not a composable context.
    val fallbackName = displayName(account)
    var name by remember { mutableStateOf(account.label.ifBlank { fallbackName }) }
    val isAi = account.type == "openai" || account.type == "claude"
    var model by remember {
        mutableStateOf(if (account.identifier == "default") "" else account.identifier)
    }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.accounts_settings),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MochiTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.accounts_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isAi) {
                    MochiTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(R.string.accounts_field_model)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = { onSave(name.trim(), model.trim()) },
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

@Composable
private fun SnackBanner(message: String, onDismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(message) {
        kotlinx.coroutines.delay(3000)
        onDismiss()
    }
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        MochiCard(colors = CardDefaults.elevatedCardColors()) {
            Text(message, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun displayName(account: ConnectedAccount): String {
    if (account.label.isNotBlank()) return account.label
    if (account.identifier.isNotBlank() && account.type in IDENTIFIED) return account.identifier
    return providerTypeLabel(account.type)
}

/**
 * The account types whose identifier is worth showing as a name: the address
 * or handle the user knows the account by.
 */
private val IDENTIFIED = setOf(
    "email", "google", "microsoft", "github", "facebook", "x", "apple", "caldav",
)

/** The capability an account holds only to sign the user in. */
private const val CAPABILITY_LOGIN = "login"

/** One granted capability, in words. */
@Composable
private fun capabilityLabel(capability: String): String = when (capability) {
    CAPABILITY_LOGIN -> stringResource(R.string.accounts_capability_login)
    "calendar" -> stringResource(R.string.accounts_capability_calendar)
    "notify" -> stringResource(R.string.accounts_capability_notify)
    "ai" -> stringResource(R.string.accounts_capability_ai)
    "mcp" -> stringResource(R.string.accounts_capability_mcp)
    else -> capability
}

// Mirrors providerLabels() in lib/web/src/features/accounts/types.ts, which
// translates the descriptive labels — including "Mochi web", where only the
// brand is fixed. The bare product and company names (Apple, Claude, Facebook,
// GitHub, Google, Microsoft, ntfy, OpenAI, Pushbullet, X) stay verbatim across
// locales per the glossary.
@Composable
private fun providerTypeLabel(type: String): String = when (type) {
    "apple" -> "Apple"
    "browser" -> stringResource(R.string.accounts_provider_browser)
    "caldav" -> stringResource(R.string.accounts_provider_caldav)
    "claude" -> "Claude"
    "email" -> stringResource(R.string.accounts_field_email)
    "facebook" -> "Facebook"
    "fcm" -> stringResource(R.string.accounts_provider_fcm)
    "github" -> "GitHub"
    "google" -> "Google"
    "mcp" -> stringResource(R.string.accounts_provider_mcp)
    "microsoft" -> "Microsoft"
    "ntfy" -> "ntfy"
    "openai" -> "OpenAI"
    "pushbullet" -> "Pushbullet"
    "unifiedpush" -> stringResource(R.string.accounts_provider_unifiedpush)
    "url" -> stringResource(R.string.accounts_provider_url)
    "web" -> stringResource(R.string.accounts_provider_web)
    "x" -> "X"
    else -> type
}
