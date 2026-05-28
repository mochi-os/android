package org.mochios.settings.ui.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.R as MochiR
import org.mochios.settings.R
import org.mochios.settings.api.OAuthIdentity
import org.mochios.settings.api.Passkey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val totpSetup by viewModel.newTotpSetup.collectAsState()
    val recoveryCodes by viewModel.newRecoveryCodes.collectAsState()
    val launchUrl by viewModel.oauthLaunchUrl.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(launchUrl) {
        val url = launchUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
        viewModel.consumeOAuthLaunchUrl()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.passkeys.isEmpty()) {
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
            HorizontalDivider()

            LoginMethodsSection(
                enabled = state.enabledMethods,
                hasPasskey = state.passkeys.isNotEmpty(),
                hasTotp = state.totpEnabled,
                onToggle = viewModel::toggleMethod,
            )
            HorizontalDivider()

            PasskeysSection(
                passkeys = state.passkeys,
                onRegister = viewModel::registerPasskey,
                onRename = viewModel::renamePasskey,
                onDelete = viewModel::deletePasskey,
            )
            HorizontalDivider()

            TotpSection(
                enabled = state.totpEnabled,
                onSetup = viewModel::beginTotpSetup,
                onDisable = viewModel::disableTotp,
            )
            HorizontalDivider()

            RecoveryCodesSection(
                count = state.recoveryCount,
                onGenerate = viewModel::generateRecovery,
            )
            HorizontalDivider()

            OAuthSection(
                identities = state.oauth,
                onUnlink = viewModel::unlinkOAuth,
                onLink = viewModel::linkOAuth,
            )

            state.error?.let { err ->
                Text(
                    text = err.toString(),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    totpSetup?.let { setup ->
        TotpSetupDialog(
            secret = setup.secret,
            onCancel = viewModel::cancelTotpSetup,
            onVerify = { code -> viewModel.verifyTotp(code) },
            onCopySecret = { copyToClipboard(context, "totp", setup.secret) },
        )
    }
    recoveryCodes?.let { codes ->
        RecoveryCodesDialog(
            codes = codes,
            onCopyAll = { copyToClipboard(context, "recovery codes", codes.joinToString("\n")) },
            onDone = viewModel::acknowledgeRecoveryCodes,
        )
    }
}

// ---------- Identity (card-style design) ----------

@Composable
private fun IdentitySection(
    state: AccountUiState,
    onNameDraftChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onPrivacy: (String) -> Unit,
) {
    val id = state.identity
    var editingName by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.account_section_identity))

        IdentityFieldRow(label = stringResource(R.string.account_identity_name)) {
            if (editingName) {
                OutlinedTextField(
                    value = state.nameDraft,
                    onValueChange = onNameDraftChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    onSaveName()
                    editingName = false
                }) { Text(stringResource(R.string.account_save)) }
                TextButton(onClick = {
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
                IconButton(onClick = { editingName = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null)
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
            ValueChip(text = chunkedFingerprint(id.fingerprint), monospace = true)
        }
        IdentityFieldRow(label = stringResource(R.string.account_identity_identity)) {
            ValueChip(text = id.entity, monospace = true, modifier = Modifier.weight(1f))
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

// ---------- Sections (verbatim port from old SecurityScreen) ----------

@Composable
private fun LoginMethodsSection(
    enabled: Set<String>,
    hasPasskey: Boolean,
    hasTotp: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    SectionHeader(stringResource(R.string.account_section_methods))
    MethodToggle(
        label = stringResource(R.string.account_method_email),
        subtitle = stringResource(R.string.account_method_email_subtitle),
        checked = "email" in enabled,
        onChange = { onToggle("email", it) },
    )
    MethodToggle(
        label = stringResource(R.string.account_method_passkey),
        subtitle = stringResource(R.string.account_method_passkey_subtitle),
        checked = "passkey" in enabled,
        enabled = hasPasskey,
        onChange = { onToggle("passkey", it) },
    )
    MethodToggle(
        label = stringResource(R.string.account_method_totp),
        subtitle = stringResource(R.string.account_method_totp_subtitle),
        checked = "totp" in enabled,
        enabled = hasTotp,
        onChange = { onToggle("totp", it) },
    )
}

@Composable
private fun MethodToggle(
    label: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun PasskeysSection(
    passkeys: List<Passkey>,
    onRegister: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showRegister by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.account_section_passkeys))
    if (passkeys.isEmpty()) {
        Text(
            stringResource(R.string.account_passkeys_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        passkeys.forEach { p ->
            PasskeyRow(p, onRename, onDelete)
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { showRegister = true }) {
        Text(stringResource(R.string.account_passkey_register))
    }

    if (showRegister) {
        var draft by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRegister = false; draft = "" },
            title = { Text(stringResource(R.string.account_passkey_register_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.account_passkey_register_message),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(stringResource(R.string.account_passkey_name_label)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRegister = false
                    onRegister(draft)
                    draft = ""
                }) { Text(stringResource(R.string.account_passkey_register)) }
            },
            dismissButton = {
                TextButton(onClick = { showRegister = false; draft = "" }) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
}

@Composable
private fun PasskeyRow(
    passkey: Passkey,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(passkey.name) }
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (renaming) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(text = passkey.name, fontWeight = FontWeight.SemiBold)
                }
            }
            if (renaming) {
                TextButton(onClick = {
                    onRename(passkey.id, draft.trim())
                    renaming = false
                }) { Text(stringResource(R.string.account_save)) }
                TextButton(onClick = { renaming = false; draft = passkey.name }) {
                    Text(stringResource(R.string.account_cancel))
                }
            } else {
                IconButton(onClick = { renaming = true }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.account_rename))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.account_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.account_passkey_delete_title)) },
            text = { Text(stringResource(R.string.account_passkey_delete_message, passkey.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(passkey.id)
                }) {
                    Text(
                        stringResource(R.string.account_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
}

@Composable
private fun TotpSection(enabled: Boolean, onSetup: () -> Unit, onDisable: () -> Unit) {
    var confirmDisable by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.account_section_totp))
    Text(
        stringResource(R.string.account_totp_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    if (enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.account_totp_enabled),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(onClick = { confirmDisable = true }) {
                Text(stringResource(R.string.account_disable))
            }
        }
    } else {
        Button(onClick = onSetup) {
            Text(stringResource(R.string.account_totp_setup))
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(stringResource(R.string.account_totp_disable_title)) },
            text = { Text(stringResource(R.string.account_totp_disable_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    onDisable()
                }) {
                    Text(
                        stringResource(R.string.account_disable),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
}

@Composable
private fun TotpSetupDialog(
    secret: String,
    onCancel: () -> Unit,
    onVerify: (String) -> Unit,
    onCopySecret: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.account_totp_setup_title)) },
        text = {
            Column {
                Text(stringResource(R.string.account_totp_setup_step1))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = secret,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = onCopySecret) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.account_copy))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.account_totp_setup_step2))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.account_totp_code)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onVerify(code) }, enabled = code.length == 6) {
                Text(stringResource(R.string.account_totp_verify))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.account_cancel)) }
        },
    )
}

@Composable
private fun RecoveryCodesSection(count: Int, onGenerate: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.account_section_recovery))
    Text(
        if (count > 0) stringResource(R.string.account_recovery_count, count)
        else stringResource(R.string.account_recovery_none),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Button(onClick = {
        if (count > 0) confirm = true else onGenerate()
    }) {
        Text(
            if (count > 0) stringResource(R.string.account_recovery_regenerate)
            else stringResource(R.string.account_recovery_generate),
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.account_recovery_regen_title)) },
            text = { Text(stringResource(R.string.account_recovery_regen_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onGenerate()
                }) {
                    Text(
                        stringResource(R.string.account_recovery_regenerate),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
}

@Composable
private fun RecoveryCodesDialog(codes: List<String>, onCopyAll: () -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.account_recovery_codes_title)) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(10.dp),
                ) {
                    Text(
                        stringResource(R.string.account_recovery_codes_warning),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                codes.forEach { code ->
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopyAll) { Text(stringResource(R.string.account_copy_all)) }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.account_done)) }
        },
    )
}

@Composable
private fun OAuthSection(
    identities: List<OAuthIdentity>,
    onUnlink: (String) -> Unit,
    onLink: (String) -> Unit,
) {
    SectionHeader(stringResource(R.string.account_section_oauth))
    if (identities.isEmpty()) {
        Text(
            stringResource(R.string.account_oauth_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        identities.forEach { id ->
            var confirm by remember(id.provider) { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.outlinedCardColors()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(id.provider.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                        if (id.email.isNotBlank()) {
                            Text(
                                id.email,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(onClick = { confirm = true }) {
                        Text(stringResource(R.string.account_unlink))
                    }
                }
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text(stringResource(R.string.account_oauth_unlink_title)) },
                    text = { Text(stringResource(R.string.account_oauth_unlink_message, id.provider)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            onUnlink(id.provider)
                        }) {
                            Text(
                                stringResource(R.string.account_unlink),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirm = false }) {
                            Text(stringResource(R.string.account_cancel))
                        }
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val linkedProviders = identities.map { it.provider.lowercase() }.toSet()
    val available = OAUTH_PROVIDERS.filter { it !in linkedProviders }
    if (available.isNotEmpty()) {
        var showLink by remember { mutableStateOf(false) }
        Button(onClick = { showLink = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(stringResource(R.string.account_oauth_link))
        }
        if (showLink) {
            AlertDialog(
                onDismissRequest = { showLink = false },
                title = { Text(stringResource(R.string.account_oauth_link_title)) },
                text = {
                    Column {
                        available.forEach { provider ->
                            TextButton(
                                onClick = {
                                    showLink = false
                                    onLink(provider)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(provider.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLink = false }) {
                        Text(stringResource(R.string.account_cancel))
                    }
                },
            )
        }
    }
}

private val OAUTH_PROVIDERS = listOf("github", "google")

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cb.setPrimaryClip(ClipData.newPlainText(label, value))
}
