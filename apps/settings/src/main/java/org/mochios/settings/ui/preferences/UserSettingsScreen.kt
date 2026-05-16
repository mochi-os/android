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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
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

private data class PrefSpec(
    val key: String,
    val label: String,
    val options: List<Pair<String, String>>,
)

@Composable
private fun prefSchema(): List<PrefSpec> = listOf(
    PrefSpec(
        key = "appearance",
        label = stringResource(R.string.settings_appearance),
        options = listOf(
            "auto" to stringResource(R.string.settings_value_auto),
            "light" to stringResource(R.string.settings_appearance_light),
            "dark" to stringResource(R.string.settings_appearance_dark),
        ),
    ),
    PrefSpec(
        key = "density",
        label = stringResource(R.string.settings_density),
        options = listOf(
            "theme" to stringResource(R.string.settings_value_theme),
            "compact" to stringResource(R.string.settings_density_compact),
            "comfortable" to stringResource(R.string.settings_density_comfortable),
            "spacious" to stringResource(R.string.settings_density_spacious),
        ),
    ),
    PrefSpec(
        key = "radius",
        label = stringResource(R.string.settings_radius),
        options = listOf(
            "theme" to stringResource(R.string.settings_value_theme),
            "0rem" to stringResource(R.string.settings_radius_none),
            "0.375rem" to stringResource(R.string.settings_radius_small),
            "0.75rem" to stringResource(R.string.settings_radius_medium),
            "1.75rem" to stringResource(R.string.settings_radius_large),
        ),
    ),
    PrefSpec(
        key = "font",
        label = stringResource(R.string.settings_font),
        options = listOf(
            "theme" to stringResource(R.string.settings_value_theme),
            "system" to stringResource(R.string.settings_font_system),
            "serif" to stringResource(R.string.settings_font_serif),
            "dyslexia" to stringResource(R.string.settings_font_dyslexia),
        ),
    ),
    PrefSpec(
        key = "font_size",
        label = stringResource(R.string.settings_font_size),
        options = listOf(
            "theme" to stringResource(R.string.settings_value_theme),
            "small" to stringResource(R.string.settings_font_size_small),
            "normal" to stringResource(R.string.settings_font_size_normal),
            "large" to stringResource(R.string.settings_font_size_large),
            "extra-large" to stringResource(R.string.settings_font_size_extra_large),
        ),
    ),
    PrefSpec(
        key = "language",
        label = stringResource(R.string.settings_language),
        options = LANGUAGE_OPTIONS,
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrefRow(
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
