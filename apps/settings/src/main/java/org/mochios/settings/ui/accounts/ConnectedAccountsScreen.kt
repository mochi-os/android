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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.ConnectedAccount
import org.mochios.settings.api.Provider

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error!!.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("add") {
                        FilledTonalButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.accounts_add))
                        }
                    }
                    if (state.accounts.isEmpty()) {
                        item("empty") {
                            Text(
                                stringResource(R.string.accounts_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.accounts, key = { it.id }) { account ->
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
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.accounts_remove_title)) },
            text = { Text(stringResource(R.string.accounts_remove_message, displayName(acc))) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(acc.id)
                    deleting = null
                }) { Text(stringResource(R.string.accounts_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(MochiR.string.common_cancel)) }
            },
        )
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
    var menu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
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
                    text = stringResource(statusRes),
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
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (needsVerify) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.accounts_verify)) },
                            onClick = { menu = false; onVerify() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_test)) },
                        onClick = { menu = false; onTest() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_settings)) },
                        onClick = { menu = false; onSettings() },
                    )
                    if (isAi && account.default != "ai") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.accounts_set_default_ai)) },
                            onClick = { menu = false; onSetAiDefault() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_remove)) },
                        onClick = { menu = false; onRemove() },
                    )
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
    // Visible types in the brief — others are added auto via push registration.
    val visibleTypes = listOf("email", "openai", "claude", "mcp", "fcm", "unifiedpush", "pushbullet")
    var selectedType by remember { mutableStateOf("email") }
    var address by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    val canSave = when (selectedType) {
        "email" -> address.trim().isNotEmpty()
        "openai", "claude" -> apiKey.trim().isNotEmpty()
        "mcp" -> url.trim().isNotEmpty()
        else -> false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.accounts_type_label), style = MaterialTheme.typography.labelMedium)
                for (t in visibleTypes) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selectedType == t, onClick = { selectedType = t })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedType == t, onClick = { selectedType = t })
                        Text(providerTypeLabel(t))
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (selectedType) {
                    "email" -> OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.accounts_field_email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "openai", "claude" -> {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(stringResource(R.string.accounts_field_api_key)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text(stringResource(R.string.accounts_field_model)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "mcp" -> OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.accounts_field_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> Text(
                        stringResource(R.string.accounts_device_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fields = HashMap<String, String>()
                    when (selectedType) {
                        "email" -> fields["address"] = address.trim()
                        "openai", "claude" -> {
                            fields["api_key"] = apiKey.trim()
                            if (model.isNotBlank()) fields["model"] = model.trim()
                        }
                        "mcp" -> fields["url"] = url.trim()
                    }
                    onSave(selectedType, fields)
                },
                enabled = canSave,
            ) { Text(stringResource(MochiR.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MochiR.string.common_cancel)) }
        },
    )
}

@Composable
private fun VerifyDialog(
    account: ConnectedAccount,
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_verify)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.accounts_verify_hint, account.identifier.ifBlank { account.label }),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.accounts_verify_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onResend) {
                    Text(stringResource(R.string.accounts_verify_resend))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onVerify(code.trim()) }, enabled = code.trim().isNotEmpty()) {
                Text(stringResource(R.string.accounts_verify))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MochiR.string.common_cancel)) }
        },
    )
}

@Composable
private fun AccountSettingsDialog(
    account: ConnectedAccount,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(account.label.ifBlank { displayName(account) }) }
    val isAi = account.type == "openai" || account.type == "claude"
    var model by remember {
        mutableStateOf(if (account.identifier == "default") "" else account.identifier)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.accounts_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isAi) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(R.string.accounts_field_model)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), model.trim()) }) {
                Text(stringResource(MochiR.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MochiR.string.common_cancel)) }
        },
    )
}

@Composable
private fun SnackBanner(message: String, onDismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(message) {
        kotlinx.coroutines.delay(3000)
        onDismiss()
    }
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Card(colors = CardDefaults.elevatedCardColors()) {
            Text(message, modifier = Modifier.padding(12.dp))
        }
    }
}

private fun displayName(account: ConnectedAccount): String {
    if (account.label.isNotBlank()) return account.label
    if (account.type == "email" && account.identifier.isNotBlank()) return account.identifier
    return providerTypeLabel(account.type)
}

// Mirrors PROVIDER_LABELS in lib/web/src/features/accounts/types.ts. These are
// proper-noun product names (Mochi web, Claude, OpenAI, etc.) that stay verbatim
// across locales per the glossary; no i18n needed.
private fun providerTypeLabel(type: String): String = when (type) {
    "browser" -> "Browser notifications"
    "claude" -> "Claude"
    "email" -> "Email"
    "fcm" -> "Android push"
    "mcp" -> "MCP server"
    "ntfy" -> "ntfy"
    "openai" -> "OpenAI"
    "pushbullet" -> "Pushbullet"
    "unifiedpush" -> "Push notification"
    "url" -> "External URL"
    "web" -> "Mochi web"
    else -> type
}
