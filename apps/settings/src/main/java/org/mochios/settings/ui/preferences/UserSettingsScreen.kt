package org.mochios.settings.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.R

/** Public, shared by the dropdown row. Display screen owns its own dropdown. */
internal data class PrefSpec(
    val key: String,
    val label: String,
    val options: List<Pair<String, String>>,
)

/** Keys this screen renders. The Display screen has its own list; we use this
 *  to scope the reset button so it only touches regional preferences. */
internal val REGIONAL_PREF_KEYS: List<String> = listOf(
    "language",
    "timezone",
    "date_format",
    "time_format",
    "timestamp_display",
    "week_start",
    "number_format",
    "units",
)

@Composable
private fun prefSchema(): List<PrefSpec> = listOf(
    PrefSpec(
        key = "language",
        label = stringResource(R.string.settings_language),
        options = LANGUAGE_OPTIONS,
    ),
    PrefSpec(
        key = "timezone",
        label = stringResource(R.string.settings_time_zone),
        options = TIMEZONE_OPTIONS,
    ),
    PrefSpec(
        key = "date_format",
        label = stringResource(R.string.settings_date_format),
        options = listOf(
            "auto" to stringResource(R.string.settings_value_auto),
            "YYYY-MM-DD" to "YYYY-MM-DD",
            "DD/MM/YYYY" to "DD/MM/YYYY",
            "DD.MM.YYYY" to "DD.MM.YYYY",
            "MM/DD/YYYY" to "MM/DD/YYYY",
            "D MMM YYYY" to "D MMM YYYY",
        ),
    ),
    PrefSpec(
        key = "time_format",
        label = stringResource(R.string.settings_time_format),
        options = listOf(
            "auto" to stringResource(R.string.settings_value_auto),
            "24h" to "24h",
            "12h" to "12h",
        ),
    ),
    PrefSpec(
        key = "timestamp_display",
        label = stringResource(R.string.settings_timestamp_display),
        options = listOf(
            "auto" to stringResource(R.string.settings_value_auto),
            "relative" to stringResource(R.string.settings_timestamp_relative),
            "absolute" to stringResource(R.string.settings_timestamp_absolute),
        ),
    ),
    PrefSpec(
        key = "week_start",
        label = stringResource(R.string.settings_week_start),
        options = listOf(
            "auto" to stringResource(R.string.settings_value_auto),
            "monday" to stringResource(R.string.settings_week_monday),
            "tuesday" to stringResource(R.string.settings_week_tuesday),
            "wednesday" to stringResource(R.string.settings_week_wednesday),
            "thursday" to stringResource(R.string.settings_week_thursday),
            "friday" to stringResource(R.string.settings_week_friday),
            "saturday" to stringResource(R.string.settings_week_saturday),
            "sunday" to stringResource(R.string.settings_week_sunday),
        ),
    ),
    PrefSpec(
        key = "number_format",
        label = stringResource(R.string.settings_number_format),
        options = listOf(
            "auto" to stringResource(R.string.settings_value_auto),
            "1,000.00" to "1,000.00",
            "1.000,00" to "1.000,00",
            "1 000,00" to "1 000,00",
            "1'000.00" to "1'000.00",
            "1,00,000.00" to "1,00,000.00",
        ),
    ),
    PrefSpec(
        key = "units",
        label = stringResource(R.string.settings_units),
        options = listOf(
            "auto" to stringResource(R.string.settings_value_auto),
            "metric" to stringResource(R.string.settings_units_metric),
            "imperial" to stringResource(R.string.settings_units_imperial),
            "usa" to stringResource(R.string.settings_units_usa),
        ),
    ),
)

/**
 * Time-zone options. We rely on Android's `java.util.TimeZone.getAvailableIDs()`
 * for the full IANA list and prepend "auto" so users can keep the device
 * default. Computed lazily at first read.
 */
private val TIMEZONE_OPTIONS: List<Pair<String, String>> by lazy {
    val zones = java.util.TimeZone.getAvailableIDs()
        .filter { it.contains('/') } // drop short aliases like "EST"
        .sorted()
    val auto = "auto" to "Auto-detect"
    listOf(auto) + zones.map { it to it }
}

private val LANGUAGE_OPTIONS: List<Pair<String, String>> = listOf(
    "" to "(default)",
    "ar" to "العربية",
    "bn" to "বাংলা",
    "ca" to "Català",
    "cs" to "Čeština",
    "da" to "Dansk",
    "de" to "Deutsch",
    "el" to "Ελληνικά",
    "en" to "English",
    "en-US" to "English (US)",
    "es" to "Español",
    "es-419" to "Español (Latinoamérica)",
    "et" to "Eesti",
    "eu" to "Euskara",
    "fa" to "فارسی",
    "fi" to "Suomi",
    "fr" to "Français",
    "fr-CA" to "Français (Canada)",
    "ga" to "Gaeilge",
    "he" to "עברית",
    "hi" to "हिन्दी",
    "hr" to "Hrvatski",
    "hu" to "Magyar",
    "id" to "Bahasa Indonesia",
    "is" to "Íslenska",
    "it" to "Italiano",
    "ja" to "日本語",
    "ka" to "ქართული",
    "ko" to "한국어",
    "lt" to "Lietuvių",
    "lv" to "Latviešu",
    "ms" to "Bahasa Melayu",
    "nb" to "Norsk bokmål",
    "nl" to "Nederlands",
    "nn" to "Nynorsk",
    "pl" to "Polski",
    "pt" to "Português",
    "pt-BR" to "Português (Brasil)",
    "ro" to "Română",
    "ru" to "Русский",
    "sk" to "Slovenčina",
    "sl" to "Slovenščina",
    "sr" to "Српски",
    "sv" to "Svenska",
    "sw" to "Kiswahili",
    "ta" to "தமிழ்",
    "th" to "ไทย",
    "tr" to "Türkçe",
    "uk" to "Українська",
    "ur" to "اردو",
    "vi" to "Tiếng Việt",
    "zh-Hans" to "简体中文",
    "zh-Hant" to "繁體中文",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingsScreen(
    onBack: () -> Unit,
    viewModel: UserSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val schema = prefSchema()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        var showResetConfirm by remember { mutableStateOf(false) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(schema, key = { it.key }) { spec ->
                PrefRow(
                    spec = spec,
                    current = uiState.values[spec.key] ?: "",
                    onChange = { value -> viewModel.set(spec.key, value) },
                )
                HorizontalDivider()
            }
            item(key = "reset") {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_reset_to_defaults))
                }
            }
        }

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
                text = { Text(stringResource(R.string.settings_reset_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetConfirm = false
                            viewModel.reset(REGIONAL_PREF_KEYS)
                        },
                    ) { Text(stringResource(R.string.settings_reset)) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrefRow(
    spec: PrefSpec,
    current: String,
    onChange: (String) -> Unit,
) {
    val selectedLabel = spec.options.firstOrNull { it.first == current }?.second
        ?: spec.options.firstOrNull()?.second
        ?: ""
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = spec.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                spec.options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onChange(value)
                        },
                    )
                }
            }
        }
    }
}
