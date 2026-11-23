// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemsettings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.R as MochiR
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.SecretField
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.R
import org.mochios.settings.api.SystemSetting
import org.mochios.settings.api.SystemTheme
import org.mochios.settings.ui.login.METHOD_STATE_SLOTS
import org.mochios.settings.ui.login.MethodStatePicker
import org.mochios.settings.ui.login.StepUpHost

// Mirrors apps/settings/web/src/features/system/settings.tsx. A file setting
// (pattern == "text") is set from a document the administrator picks, whose
// text is saved as the value, as the web uploads one.

/** The documents a file setting is picked from: its values are JSON. */
private val FILE_TYPES = arrayOf("application/json", "text/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    onBack: () -> Unit,
    viewModel: SystemSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    StepUpHost(viewModel.stepUp)
    val snackbar = remember { SnackbarHostState() }
    // A refused save is said over the list; the full-screen error below is
    // for a list that never loaded.
    LaunchedEffect(state.error) {
        val failure = state.error
        if (failure != null && state.settings.isNotEmpty()) {
            snackbar.showSnackbar(failure.userMessage())
            viewModel.clearError()
        }
    }
    // The setting a document is being picked for, by name, so it outlasts the
    // activity being recreated behind the picker.
    var uploading by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val name = uploading
        uploading = null
        if (uri != null && name != null) viewModel.upload(name, uri)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_settings_title)) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.settings.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                state.error != null && state.settings.isEmpty() -> ErrorState(
                    error = state.error!!,
                    onRetry = viewModel::refresh,
                )
                else -> SystemSettingsContent(
                    state = state,
                    onSave = { name, value -> viewModel.setValue(name, value) },
                    onChooseFile = { name ->
                        uploading = name
                        picker.launch(FILE_TYPES)
                    },
                )
            }
        }
    }
}

private val HIDDEN_SETTINGS = setOf("server_version", "server_started")
private val USER_DEFAULT_SETTINGS = setOf("default_theme")
private val OPERATOR_SETTINGS = listOf(
    "operator_name",
    "operator_email",
    "operator_jurisdiction",
    "description",
)

// The other networks: the handle limit first, then the protocol switches in
// their standard order, with Bluesky's labellers after its switch. The server
// lists them only while it offers the networks.
private val NETWORK_SETTINGS = listOf(
    "handles",
    "fediverse",
    "bluesky",
    "labelers",
    "matrix",
    "xmpp",
)

private fun isLogin(name: String) =
    name.startsWith("auth_") || name == "signup_enabled"
private fun isOauth(name: String) = name.startsWith("oauth_")
private fun isOperator(name: String) = OPERATOR_SETTINGS.contains(name)
private fun isPush(name: String) = name.startsWith("fcm.")
private fun isNetwork(name: String) = NETWORK_SETTINGS.contains(name)

/** The settings of the other networks the server lists, in [NETWORK_SETTINGS]' order. */
internal fun networkSettings(all: List<SystemSetting>): List<SystemSetting> =
    NETWORK_SETTINGS.mapNotNull { name -> all.firstOrNull { it.name == name } }
private fun isUserDefault(name: String) = USER_DEFAULT_SETTINGS.contains(name)

private fun isBoolean(setting: SystemSetting) = setting.pattern == "^(true|false)$"
private fun isFileUpload(setting: SystemSetting) = setting.pattern == "text"

/**
 * Whether the row offers to clear the value. The server blanks a secret's
 * value, so for secrets `set` (computed before blanking) is the only signal.
 */
internal fun hasStoredValue(setting: SystemSetting): Boolean =
    if (setting.secret) setting.set else setting.value.isNotEmpty()

private fun enumOptions(setting: SystemSetting): List<String>? {
    val match = Regex("""^\^\(([^)]+)\)\$$""").find(setting.pattern) ?: return null
    val opts = match.groupValues[1].split('|').map { it.trim() }
    if (opts.size < 2) return null
    if (opts.size == 2 && opts.contains("true") && opts.contains("false")) return null
    return opts
}

private fun methodStateOptions(opts: List<String>?): Set<String>? {
    if (opts == null) return null
    if (!opts.all { it in METHOD_STATE_SLOTS }) return null
    return opts.toSet()
}

@Composable
private fun settingLabel(setting: SystemSetting): String {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val explicit = SETTING_LABEL_RESOURCES[setting.name]
    if (explicit != null) return resources.getString(explicit)
    return setting.name
        .split('_')
        .joinToString(" ") { word ->
            if (word.isEmpty()) word else word[0].uppercaseChar() + word.substring(1)
        }
}

// Web labels keyed by setting name → string resource for the localised label.
private val SETTING_LABEL_RESOURCES: Map<String, Int> = mapOf(
    "apps_install_user" to R.string.system_setting_apps_install_user,
    "auth_email" to R.string.system_setting_auth_email,
    "auth_passkey" to R.string.system_setting_auth_passkey,
    "auth_totp" to R.string.system_setting_auth_totp,
    "auth_recovery" to R.string.system_setting_auth_recovery,
    "auth_oauth" to R.string.system_setting_auth_oauth,
    "default_theme" to R.string.system_setting_default_theme,
    "domains_verification" to R.string.system_setting_domains_verification,
    "email_from" to R.string.system_setting_email_from,
    "hostname" to R.string.system_setting_hostname,
    "hostname_publish" to R.string.system_setting_hostname_publish,
    "oauth_public_url" to R.string.system_setting_oauth_public_url,
    "oauth_google_client_id" to R.string.system_setting_oauth_google_client_id,
    "oauth_google_client_secret" to R.string.system_setting_oauth_google_client_secret,
    "oauth_github_client_id" to R.string.system_setting_oauth_github_client_id,
    "oauth_github_client_secret" to R.string.system_setting_oauth_github_client_secret,
    "oauth_microsoft_client_id" to R.string.system_setting_oauth_microsoft_client_id,
    "oauth_microsoft_client_secret" to R.string.system_setting_oauth_microsoft_client_secret,
    "oauth_microsoft_tenant" to R.string.system_setting_oauth_microsoft_tenant,
    "oauth_facebook_client_id" to R.string.system_setting_oauth_facebook_client_id,
    "oauth_facebook_client_secret" to R.string.system_setting_oauth_facebook_client_secret,
    "oauth_x_client_id" to R.string.system_setting_oauth_x_client_id,
    "oauth_x_client_secret" to R.string.system_setting_oauth_x_client_secret,
    "fcm.firebase_config" to R.string.system_setting_fcm_firebase_config,
    "fcm.service_account" to R.string.system_setting_fcm_service_account,
    "operator_name" to R.string.system_setting_operator_name,
    "operator_email" to R.string.system_setting_operator_email,
    "operator_jurisdiction" to R.string.system_setting_operator_jurisdiction,
    "signup_enabled" to R.string.system_setting_signup_enabled,
    "description" to R.string.system_setting_description,
    "handles" to R.string.system_setting_handles,
    "fediverse" to R.string.system_setting_fediverse,
    "bluesky" to R.string.system_setting_bluesky,
    "labelers" to R.string.system_setting_labelers,
    "xmpp" to R.string.system_setting_xmpp,
    "matrix" to R.string.system_setting_matrix,
)

@Composable
internal fun SystemSettingsContent(
    state: SystemSettingsUiState,
    onSave: (String, String) -> Unit,
    onChooseFile: (String) -> Unit = {},
) {
    // Resolve labels once so we can sort by user-visible name (matching the
    // web naturalCompare order).
    val resources = androidx.compose.ui.platform.LocalResources.current
    val displayName = remember(state.settings, resources) {
        state.settings.associateBy({ it.name }) { setting ->
            SETTING_LABEL_RESOURCES[setting.name]?.let { resources.getString(it) }
                ?: setting.name.split('_').joinToString(" ") { w ->
                    if (w.isEmpty()) w else w[0].uppercaseChar() + w.substring(1)
                }
        }
    }
    val all = remember(state.settings, displayName) {
        state.settings
            .filter { it.name !in HIDDEN_SETTINGS }
            .sortedWith(compareBy(NaturalCompare) { displayName[it.name] ?: it.name })
    }
    val login = all.filter { isLogin(it.name) }
    val oauth = all.filter { isOauth(it.name) }
    val userDefaults = all.filter { isUserDefault(it.name) }
    val operator = OPERATOR_SETTINGS.mapNotNull { name -> all.firstOrNull { it.name == name } }
    val networks = networkSettings(all)
    val push = all.filter { isPush(it.name) }
    val other = all.filter { setting ->
        !isLogin(setting.name) &&
            !isOauth(setting.name) &&
            !isOperator(setting.name) &&
            !isNetwork(setting.name) &&
            !isPush(setting.name) &&
            !isUserDefault(setting.name)
    }

    val loginTitle = stringResource(R.string.system_settings_section_login)
    val oauthTitle = stringResource(R.string.system_settings_section_oauth)
    val userDefaultsTitle = stringResource(R.string.system_settings_section_user_defaults)
    val operatorTitle = stringResource(R.string.system_settings_section_operator)
    val networksTitle = stringResource(R.string.system_status_other_networks)
    val pushTitle = stringResource(R.string.system_settings_section_push)
    val otherTitle = stringResource(R.string.system_settings_section_other)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        section(loginTitle, login, state, onSave, onChooseFile)
        if (oauth.isNotEmpty()) section(oauthTitle, oauth, state, onSave, onChooseFile)
        section(userDefaultsTitle, userDefaults, state, onSave, onChooseFile)
        if (operator.isNotEmpty()) section(operatorTitle, operator, state, onSave, onChooseFile)
        if (networks.isNotEmpty()) section(networksTitle, networks, state, onSave, onChooseFile)
        if (push.isNotEmpty()) section(pushTitle, push, state, onSave, onChooseFile)
        section(otherTitle, other, state, onSave, onChooseFile)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    settings: List<SystemSetting>,
    state: SystemSettingsUiState,
    onSave: (String, String) -> Unit,
    onChooseFile: (String) -> Unit,
) {
    item("section-header-$title") {
        SectionHeader(title = title)
    }
    items(settings, key = { "row-${it.name}" }) { setting ->
        SettingRow(
            setting = setting,
            pending = state.pending[setting.name],
            isSaving = state.savingName == setting.name,
            themes = state.themes,
            onSave = { value -> onSave(setting.name, value) },
            onChooseFile = { onChooseFile(setting.name) },
        )
    }
    item("section-spacer-$title") { Spacer(Modifier.height(8.dp)) }
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * One setting. A control that saves on the tap shows [pending], the value
 * asked for, until it is saved; once the step-up is cancelled or the server
 * refuses, the view model drops it and the row shows the stored value again.
 * Only the text field keeps text of its own, which stays on a failure for its
 * Save to try again.
 */
@Composable
internal fun SettingRow(
    setting: SystemSetting,
    pending: String?,
    isSaving: Boolean,
    themes: List<SystemTheme>,
    onSave: (String) -> Unit,
    onChooseFile: () -> Unit = {},
) {
    var localValue by remember(setting.name, setting.value) { mutableStateOf(setting.value) }
    val shown = pending ?: setting.value
    val label = settingLabel(setting)
    val isDefault = setting.value == setting.default
    val hasChanged = localValue != setting.value

    val enumOpts = enumOptions(setting)
    val methodStates = methodStateOptions(enumOpts)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))

        when {
            setting.readOnly -> ReadOnlyValue(setting)
            // Before the pattern branches: a secret's pattern is "line", which
            // would otherwise land it in the plain text field.
            setting.secret -> Row(verticalAlignment = Alignment.CenterVertically) {
                SecretField(
                    configured = setting.set,
                    onSave = onSave,
                    enabled = !isSaving,
                    saving = isSaving,
                    modifier = Modifier.weight(1f),
                )
                if (hasStoredValue(setting)) {
                    Spacer(Modifier.size(8.dp))
                    MochiOutlinedButton(
                        onClick = { onSave("") },
                        enabled = !isSaving,
                    ) {
                        Text(stringResource(R.string.system_settings_clear))
                    }
                }
            }
            methodStates != null -> MethodStatePicker(
                value = shown,
                slots = methodStates,
                busy = isSaving,
                onPick = onSave,
            )
            // default_theme holds an entity:theme id, which no one should read
            // or type, so it is picked from the installed themes by label.
            setting.name == "default_theme" && themes.isNotEmpty() -> Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeDropdown(
                    value = shown,
                    themes = themes,
                    disabled = isSaving,
                    onPick = onSave,
                    modifier = Modifier.weight(1f),
                )
                if (!isDefault) {
                    Spacer(Modifier.size(8.dp))
                    ResetButton(
                        setting = setting,
                        label = label,
                        disabled = isSaving,
                        defaultLabel = themeLabel(themes, setting.default)
                            ?: stringResource(R.string.system_settings_theme_unknown),
                    ) {
                        onSave(setting.default)
                    }
                }
            }
            isBoolean(setting) -> Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = shown == "true",
                    enabled = !isSaving,
                    onCheckedChange = { checked -> onSave(if (checked) "true" else "false") },
                )
                if (!isDefault) {
                    Spacer(Modifier.size(8.dp))
                    ResetButton(setting = setting, label = label, disabled = isSaving) {
                        onSave(setting.default)
                    }
                }
            }
            enumOpts != null -> EnumDropdown(
                value = shown,
                options = enumOpts,
                disabled = isSaving,
                onPick = onSave,
            )
            isFileUpload(setting) -> Row(verticalAlignment = Alignment.CenterVertically) {
                if (shown.isNotBlank()) {
                    Text(
                        stringResource(R.string.system_settings_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    MochiOutlinedButton(onClick = onChooseFile, enabled = !isSaving) {
                        Text(stringResource(R.string.system_settings_replace))
                    }
                    Spacer(Modifier.size(8.dp))
                    MochiOutlinedButton(onClick = { onSave("") }, enabled = !isSaving) {
                        Text(stringResource(R.string.system_settings_clear))
                    }
                } else {
                    MochiOutlinedButton(onClick = onChooseFile, enabled = !isSaving) {
                        Text(stringResource(R.string.system_settings_choose_file))
                    }
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                MochiTextField(
                    value = pending ?: localValue,
                    onValueChange = { localValue = it },
                    enabled = !isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (
                            setting.name.endsWith("_email") || setting.name == "email_from"
                        ) KeyboardType.Email else KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                when {
                    hasChanged -> MochiButton(
                        onClick = { onSave(localValue) },
                        enabled = !isSaving,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.system_settings_save))
                    }
                    !isDefault -> ResetButton(setting = setting, label = label, disabled = isSaving) {
                        onSave(setting.default)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyValue(setting: SystemSetting) {
    val text = when {
        setting.value.isEmpty() -> stringResource(R.string.system_settings_empty)
        else -> setting.value
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    value: String,
    options: List<String>,
    disabled: Boolean,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!disabled) expanded = it },
    ) {
        MochiTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = !disabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { opt ->
                MochiDropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        expanded = false
                        onPick(opt)
                    },
                )
            }
        }
    }
}

/** A theme's label by id, or null for an id no installed theme has. */
internal fun themeLabel(themes: List<SystemTheme>, id: String): String? =
    themes.firstOrNull { it.id == id }?.label

/** The themes in the order the picker lists them: by label. */
internal fun themeOrder(themes: List<SystemTheme>): List<SystemTheme> =
    themes.sortedWith(compareBy(NaturalCompare) { it.label })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(
    value: String,
    themes: List<SystemTheme>,
    disabled: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!disabled) expanded = it },
        modifier = modifier,
    ) {
        MochiTextField(
            // A stored id no installed theme has shows nothing, never the id.
            value = themeLabel(themes, value).orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = !disabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            themeOrder(themes).forEach { theme ->
                MochiDropdownMenuItem(
                    text = { Text(theme.label) },
                    onClick = {
                        expanded = false
                        onPick(theme.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ResetButton(
    setting: SystemSetting,
    label: String,
    disabled: Boolean,
    // What the confirmation names as the default; the stored value unless the
    // row shows its values by another name.
    defaultLabel: String = setting.default,
    onConfirm: () -> Unit,
) {
    var confirm by remember(setting.name) { mutableStateOf(false) }
    MochiIconButton(onClick = { confirm = true }, enabled = !disabled) {
        Icon(
            Icons.Default.Restore,
            contentDescription = stringResource(R.string.system_settings_reset_to_default),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirm) {
        MochiAlertDialog(
            onDismissRequest = { confirm = false },
            title = stringResource(R.string.system_settings_reset_title),
            text = if (setting.default.isNotEmpty()) {
                stringResource(
                    R.string.system_settings_reset_message,
                    label,
                    defaultLabel,
                )
            } else {
                stringResource(R.string.system_settings_reset_message_empty, label)
            },
            confirmText = stringResource(R.string.system_settings_reset),
            onConfirm = {
                confirm = false
                onConfirm()
            },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}
