package org.mochios.settings.ui.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
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
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.settings.R
import org.mochios.settings.api.ApiToken
import org.mochios.settings.api.OAuthIdentity
import org.mochios.settings.api.Passkey
import org.mochios.settings.api.Session

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val totpSetup by viewModel.newTotpSetup.collectAsState()
    val recoveryCodes by viewModel.newRecoveryCodes.collectAsState()
    val newToken by viewModel.newApiToken.collectAsState()
    val launchUrl by viewModel.oauthLaunchUrl.collectAsState()
    val context = LocalContext.current

    // Fire the browser when the OAuth link flow returns a URL. Consumes the
    // signal immediately so back-navigation back to the screen doesn't
    // re-launch the browser. The return is processed via the host's
    // mochi:oauth-link-return scheme handler.
    androidx.compose.runtime.LaunchedEffect(launchUrl) {
        val url = launchUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        viewModel.consumeOAuthLaunchUrl()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.security_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(org.mochios.android.R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading && uiState.passkeys.isEmpty() && uiState.sessions.isEmpty()) {
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
            LoginMethodsSection(
                enabled = uiState.enabledMethods,
                hasPasskey = uiState.passkeys.isNotEmpty(),
                hasTotp = uiState.totpEnabled,
                onToggle = viewModel::toggleMethod,
            )
            HorizontalDivider()

            PasskeysSection(
                passkeys = uiState.passkeys,
                onRegister = viewModel::registerPasskey,
                onRename = viewModel::renamePasskey,
                onDelete = viewModel::deletePasskey,
            )
            HorizontalDivider()

            TotpSection(
                enabled = uiState.totpEnabled,
                onSetup = viewModel::beginTotpSetup,
                onDisable = viewModel::disableTotp,
            )
            HorizontalDivider()

            RecoveryCodesSection(
                count = uiState.recoveryCount,
                onGenerate = viewModel::generateRecovery,
            )
            HorizontalDivider()

            SessionsSection(
                sessions = uiState.sessions,
                onRevoke = viewModel::revokeSession,
            )
            HorizontalDivider()

            OAuthSection(
                identities = uiState.oauth,
                onUnlink = viewModel::unlinkOAuth,
                onLink = viewModel::linkOAuth,
            )
            HorizontalDivider()

            TokensSection(
                tokens = uiState.tokens,
                onCreate = viewModel::createToken,
                onDelete = viewModel::deleteToken,
            )

            uiState.error?.let { err ->
                Text(
                    text = err.toString(),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    // Show-once dialogs
    totpSetup?.let { setup ->
        TotpSetupDialog(
            secret = setup.secret,
            url = setup.url,
            issuer = setup.issuer,
            domain = setup.domain,
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
    newToken?.let { token ->
        NewTokenDialog(
            token = token,
            onCopy = { copyToClipboard(context, "api token", token) },
            onDone = viewModel::acknowledgeNewToken,
        )
    }
}

// ---------- Sections ----------

@Composable
private fun LoginMethodsSection(
    enabled: Set<String>,
    hasPasskey: Boolean,
    hasTotp: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    SectionHeader(stringResource(R.string.security_section_methods))
    MethodToggle(
        label = stringResource(R.string.security_method_email),
        checked = "email" in enabled,
        onChange = { onToggle("email", it) },
    )
    MethodToggle(
        label = stringResource(R.string.security_method_passkey),
        checked = "passkey" in enabled,
        enabled = hasPasskey,
        onChange = { onToggle("passkey", it) },
    )
    MethodToggle(
        label = stringResource(R.string.security_method_totp),
        checked = "totp" in enabled,
        enabled = hasTotp,
        onChange = { onToggle("totp", it) },
    )
}

@Composable
private fun MethodToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    SectionHeader(stringResource(R.string.security_section_passkeys))
    if (passkeys.isEmpty()) {
        Text(
            stringResource(R.string.security_passkeys_empty),
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
        Text(stringResource(R.string.security_passkey_register))
    }

    if (showRegister) {
        var draft by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRegister = false; draft = "" },
            title = { Text(stringResource(R.string.security_passkey_register_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.security_passkey_register_message),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(stringResource(R.string.security_passkey_name_label)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRegister = false
                    onRegister(draft)
                    draft = ""
                }) { Text(stringResource(R.string.security_passkey_register)) }
            },
            dismissButton = {
                TextButton(onClick = { showRegister = false; draft = "" }) {
                    Text(stringResource(R.string.security_cancel))
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
    val format = LocalFormat.current
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
                val createdText = if (passkey.created > 0) {
                    stringResource(R.string.security_created_at, format.formatDateTime(passkey.created))
                } else null
                val lastUsedText = if (passkey.lastUsed > 0) {
                    stringResource(R.string.security_last_used, format.formatRelativeTime(passkey.lastUsed))
                } else null
                listOfNotNull(createdText, lastUsedText).forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (renaming) {
                TextButton(onClick = {
                    onRename(passkey.id, draft.trim())
                    renaming = false
                }) { Text(stringResource(R.string.security_save)) }
                TextButton(onClick = { renaming = false; draft = passkey.name }) {
                    Text(stringResource(R.string.security_cancel))
                }
            } else {
                IconButton(onClick = { renaming = true }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.security_rename))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.security_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.security_passkey_delete_title)) },
            text = { Text(stringResource(R.string.security_passkey_delete_message, passkey.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(passkey.id)
                }) { Text(stringResource(R.string.security_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.security_cancel))
                }
            },
        )
    }
}

@Composable
private fun TotpSection(
    enabled: Boolean,
    onSetup: () -> Unit,
    onDisable: () -> Unit,
) {
    var confirmDisable by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.security_section_totp))
    Text(
        stringResource(R.string.security_totp_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    if (enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.security_totp_enabled),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(onClick = { confirmDisable = true }) {
                Text(stringResource(R.string.security_disable))
            }
        }
    } else {
        Button(onClick = onSetup) {
            Text(stringResource(R.string.security_totp_setup))
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(stringResource(R.string.security_totp_disable_title)) },
            text = { Text(stringResource(R.string.security_totp_disable_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    onDisable()
                }) { Text(stringResource(R.string.security_disable), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) {
                    Text(stringResource(R.string.security_cancel))
                }
            },
        )
    }
}

@Composable
private fun TotpSetupDialog(
    secret: String,
    url: String,
    issuer: String,
    domain: String,
    onCancel: () -> Unit,
    onVerify: (String) -> Unit,
    onCopySecret: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.security_totp_setup_title)) },
        text = {
            Column {
                Text(stringResource(R.string.security_totp_setup_step1))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = secret,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = onCopySecret) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.security_copy))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.security_totp_setup_step2))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.security_totp_code)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onVerify(code) }, enabled = code.length == 6) {
                Text(stringResource(R.string.security_totp_verify))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.security_cancel)) }
        },
    )
}

@Composable
private fun RecoveryCodesSection(count: Int, onGenerate: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.security_section_recovery))
    Text(
        if (count > 0) stringResource(R.string.security_recovery_count, count)
            else stringResource(R.string.security_recovery_none),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Button(onClick = {
        if (count > 0) confirm = true else onGenerate()
    }) {
        Text(if (count > 0) stringResource(R.string.security_recovery_regenerate)
            else stringResource(R.string.security_recovery_generate))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.security_recovery_regen_title)) },
            text = { Text(stringResource(R.string.security_recovery_regen_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onGenerate()
                }) { Text(stringResource(R.string.security_recovery_regenerate), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text(stringResource(R.string.security_cancel))
                }
            },
        )
    }
}

@Composable
private fun RecoveryCodesDialog(
    codes: List<String>,
    onCopyAll: () -> Unit,
    onDone: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.security_recovery_codes_title)) },
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
                        stringResource(R.string.security_recovery_codes_warning),
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
            TextButton(onClick = onCopyAll) {
                Text(stringResource(R.string.security_copy_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.security_done)) }
        },
    )
}

@Composable
private fun SessionsSection(sessions: List<Session>, onRevoke: (String) -> Unit) {
    val format = LocalFormat.current
    SectionHeader(stringResource(R.string.security_section_sessions))
    if (sessions.isEmpty()) {
        Text(
            stringResource(R.string.security_sessions_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        sessions.forEach { session ->
            var confirm by remember(session.id) { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.outlinedCardColors()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.agent.ifBlank { stringResource(R.string.security_session_unknown_agent) }, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.security_last_used, format.formatRelativeTime(session.accessed)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (session.address.isNotBlank()) {
                            Text(
                                session.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(onClick = { confirm = true }) {
                        Text(stringResource(R.string.security_revoke))
                    }
                }
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text(stringResource(R.string.security_session_revoke_title)) },
                    text = { Text(stringResource(R.string.security_session_revoke_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            onRevoke(session.id)
                        }) { Text(stringResource(R.string.security_revoke), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirm = false }) {
                            Text(stringResource(R.string.security_cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OAuthSection(
    identities: List<OAuthIdentity>,
    onUnlink: (String) -> Unit,
    onLink: (String) -> Unit,
) {
    val format = LocalFormat.current
    SectionHeader(stringResource(R.string.security_section_oauth))
    if (identities.isEmpty()) {
        Text(
            stringResource(R.string.security_oauth_empty),
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
                        if (id.used > 0) {
                            Text(
                                stringResource(R.string.security_last_used, format.formatRelativeTime(id.used)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(onClick = { confirm = true }) {
                        Text(stringResource(R.string.security_unlink))
                    }
                }
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text(stringResource(R.string.security_oauth_unlink_title)) },
                    text = { Text(stringResource(R.string.security_oauth_unlink_message, id.provider)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            onUnlink(id.provider)
                        }) { Text(stringResource(R.string.security_unlink), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirm = false }) {
                            Text(stringResource(R.string.security_cancel))
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
            Text(stringResource(R.string.security_oauth_link))
        }
        if (showLink) {
            AlertDialog(
                onDismissRequest = { showLink = false },
                title = { Text(stringResource(R.string.security_oauth_link_title)) },
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
                        Text(stringResource(R.string.security_cancel))
                    }
                },
            )
        }
    }
}

/** Same provider list the web settings page offers. Server may have a
 *  different subset enabled at run time — the server returns a 4xx and
 *  shows up in [SecurityUiState.error] in that case. */
private val OAUTH_PROVIDERS = listOf("github", "google", "microsoft", "facebook", "x")

@Composable
private fun TokensSection(
    tokens: List<ApiToken>,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val format = LocalFormat.current
    var showCreate by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.security_section_tokens),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { showCreate = true }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.security_token_create))
        }
    }
    if (tokens.isEmpty()) {
        Text(
            stringResource(R.string.security_tokens_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        tokens.forEach { t ->
            var confirm by remember(t.hash) { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.outlinedCardColors()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.name, fontWeight = FontWeight.SemiBold)
                        if (t.created > 0) {
                            Text(
                                stringResource(R.string.security_created_at, format.formatDateTime(t.created)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (t.lastUsed > 0) {
                            Text(
                                stringResource(R.string.security_last_used, format.formatRelativeTime(t.lastUsed)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { confirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.security_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text(stringResource(R.string.security_token_delete_title)) },
                    text = { Text(stringResource(R.string.security_token_delete_message, t.name)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            onDelete(t.hash)
                        }) { Text(stringResource(R.string.security_delete), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirm = false }) {
                            Text(stringResource(R.string.security_cancel))
                        }
                    },
                )
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false; draft = "" },
            title = { Text(stringResource(R.string.security_token_create_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(R.string.security_token_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = draft.trim()
                        if (n.isNotBlank()) {
                            onCreate(n)
                            showCreate = false
                            draft = ""
                        }
                    },
                    enabled = draft.trim().isNotEmpty(),
                ) { Text(stringResource(R.string.security_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false; draft = "" }) {
                    Text(stringResource(R.string.security_cancel))
                }
            },
        )
    }
}

@Composable
private fun NewTokenDialog(
    token: String,
    onCopy: () -> Unit,
    onDone: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.security_token_new_title)) },
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
                        stringResource(R.string.security_token_new_warning),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = token,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.security_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.security_done)) }
        },
    )
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

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cb.setPrimaryClip(ClipData.newPlainText(label, value))
}
