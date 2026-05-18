package org.mochios.settings.ui.systemsettings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.R as MochiR
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.R
import org.mochios.settings.api.SystemSetting

// Mirrors apps/settings/web/src/features/system/settings.tsx. Each row renders
// per-pattern: a Switch for booleans, a segmented control for the
// required/allowed/disabled enum family, a dropdown for other enums, a chip
// for read-only values, and an OutlinedTextField for everything else. File
// upload (pattern == "text") is not yet supported on Android — the row
// degrades to a multi-line text field.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    onBack: () -> Unit,
    viewModel: SystemSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_settings_title)) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                state.error != null && state.settings.isEmpty() -> Text(
                    text = state.error!!.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> SystemSettingsContent(
                    state = state,
                    onSave = { name, value -> viewModel.setValue(name, value) },
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
)
private val METHOD_STATE_SLOTS = listOf("disabled", "allowed", "required")

private fun isLogin(name: String) =
    name.startsWith("auth_") || name == "signup_enabled"
private fun isOauth(name: String) = name.startsWith("oauth_")
private fun isOperator(name: String) = OPERATOR_SETTINGS.contains(name)
private fun isPush(name: String) = name.startsWith("fcm.")
private fun isUserDefault(name: String) = USER_DEFAULT_SETTINGS.contains(name)

private fun isBoolean(setting: SystemSetting) = setting.pattern == "^(true|false)$"
private fun isFileUpload(setting: SystemSetting) = setting.pattern == "text"

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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val explicit = SETTING_LABEL_RESOURCES[setting.name]
    if (explicit != null) return ctx.getString(explicit)
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
    "server_started" to R.string.system_setting_server_started,
    "server_version" to R.string.system_setting_server_version,
    "signup_enabled" to R.string.system_setting_signup_enabled,
)

@Composable
private fun methodStateLabel(slot: String): String = when (slot) {
    "disabled" -> stringResource(R.string.system_settings_state_disabled)
    "allowed" -> stringResource(R.string.system_settings_state_allowed)
    "required" -> stringResource(R.string.system_settings_state_required)
    else -> slot
}

@Composable
private fun SystemSettingsContent(
    state: SystemSettingsUiState,
    onSave: (String, String) -> Unit,
) {
    // Resolve labels once so we can sort by user-visible name (matching the
    // web naturalCompare order).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val displayName = remember(state.settings) {
        state.settings.associateBy({ it.name }) { setting ->
            SETTING_LABEL_RESOURCES[setting.name]?.let { ctx.getString(it) }
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
    val push = all.filter { isPush(it.name) }
    val other = all.filter { setting ->
        !isLogin(setting.name) &&
            !isOauth(setting.name) &&
            !isOperator(setting.name) &&
            !isPush(setting.name) &&
            !isUserDefault(setting.name)
    }

    val loginTitle = stringResource(R.string.system_settings_section_login)
    val oauthTitle = stringResource(R.string.system_settings_section_oauth)
    val userDefaultsTitle = stringResource(R.string.system_settings_section_user_defaults)
    val operatorTitle = stringResource(R.string.system_settings_section_operator)
    val pushTitle = stringResource(R.string.system_settings_section_push)
    val otherTitle = stringResource(R.string.system_settings_section_other)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        section(loginTitle, login, state, onSave)
        if (oauth.isNotEmpty()) section(oauthTitle, oauth, state, onSave)
        section(userDefaultsTitle, userDefaults, state, onSave)
        if (operator.isNotEmpty()) section(operatorTitle, operator, state, onSave)
        if (push.isNotEmpty()) section(pushTitle, push, state, onSave)
        section(otherTitle, other, state, onSave)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    settings: List<SystemSetting>,
    state: SystemSettingsUiState,
    onSave: (String, String) -> Unit,
) {
    item("section-header-$title") {
        SectionHeader(title = title)
    }
    items(settings, key = { "row-${'$'}{it.name}" }) { setting ->
        SettingRow(
            setting = setting,
            isSaving = state.savingName == setting.name,
            onSave = { value -> onSave(setting.name, value) },
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

@Composable
private fun SettingRow(
    setting: SystemSetting,
    isSaving: Boolean,
    onSave: (String) -> Unit,
) {
    var localValue by remember(setting.name, setting.value) { mutableStateOf(setting.value) }
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
            methodStates != null -> MethodStatePicker(
                value = localValue,
                slots = methodStates,
                disabled = isSaving,
                onPick = {
                    localValue = it
                    onSave(it)
                },
            )
            isBoolean(setting) -> Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = localValue == "true",
                    enabled = !isSaving,
                    onCheckedChange = { checked ->
                        val v = if (checked) "true" else "false"
                        localValue = v
                        onSave(v)
                    },
                )
                if (!isDefault) {
                    Spacer(Modifier.size(8.dp))
                    ResetButton(setting = setting, label = label, disabled = isSaving) {
                        localValue = setting.default
                        onSave(setting.default)
                    }
                }
            }
            enumOpts != null -> EnumDropdown(
                value = localValue,
                options = enumOpts,
                disabled = isSaving,
                onPick = {
                    localValue = it
                    onSave(it)
                },
            )
            isFileUpload(setting) -> Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = if (localValue.isBlank()) ""
                            else stringResource(R.string.system_settings_configured),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                )
                if (localValue.isNotBlank()) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = {
                            localValue = ""
                            onSave("")
                        },
                        enabled = !isSaving,
                    ) {
                        Text(stringResource(R.string.system_settings_clear))
                    }
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = localValue,
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
                    hasChanged -> Button(
                        onClick = { onSave(localValue) },
                        enabled = !isSaving,
                    ) {
                        Text(stringResource(R.string.system_settings_save))
                    }
                    !isDefault -> ResetButton(setting = setting, label = label, disabled = isSaving) {
                        localValue = setting.default
                        onSave(setting.default)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyValue(setting: SystemSetting) {
    val format = LocalFormat.current
    val text = when {
        setting.name == "server_started" -> {
            val seconds = setting.value.toLongOrNull() ?: 0L
            if (seconds > 0) format.formatDateTime(seconds) else setting.value
        }
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

@Composable
private fun MethodStatePicker(
    value: String,
    slots: Set<String>,
    disabled: Boolean,
    onPick: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        METHOD_STATE_SLOTS.filter { it in slots }.forEach { slot ->
            val selected = value == slot
            if (selected) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.padding(end = 4.dp),
                ) { Text(methodStateLabel(slot)) }
            } else {
                OutlinedButton(
                    onClick = { onPick(slot) },
                    enabled = !disabled,
                    modifier = Modifier.padding(end = 4.dp),
                ) { Text(methodStateLabel(slot)) }
            }
        }
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
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = !disabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
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

@Composable
private fun ResetButton(
    setting: SystemSetting,
    label: String,
    disabled: Boolean,
    onConfirm: () -> Unit,
) {
    var confirm by remember(setting.name) { mutableStateOf(false) }
    IconButton(onClick = { confirm = true }, enabled = !disabled) {
        Icon(
            Icons.Default.Restore,
            contentDescription = stringResource(R.string.system_settings_reset_to_default),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.system_settings_reset_title)) },
            text = {
                Text(
                    if (setting.default.isNotEmpty()) {
                        stringResource(
                            R.string.system_settings_reset_message,
                            label,
                            setting.default,
                        )
                    } else {
                        stringResource(R.string.system_settings_reset_message_empty, label)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onConfirm()
                }) { Text(stringResource(R.string.system_settings_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        )
    }
}
