// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import org.mochios.android.util.NaturalCompare
import java.util.Locale

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
private fun prefSchema(
    languages: List<String>,
    currentLanguage: String,
): List<PrefSpec> = listOf(
    PrefSpec(
        key = "language",
        label = stringResource(R.string.settings_language),
        options = languageOptions(
            tags = languages,
            current = currentLanguage,
            defaultLabel = stringResource(R.string.settings_value_auto),
        ),
    ),
    PrefSpec(
        key = "timezone",
        label = stringResource(R.string.settings_time_zone),
        options = timezoneOptions(),
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
private val TIMEZONE_OPTIONS: List<String> by lazy {
    val zones = java.util.TimeZone.getAvailableIDs()
        .filter { it.contains('/') } // drop short aliases like "EST"
        .sorted()
    zones
}

/** The zone rows, with the localised automatic row in front. */
@Composable
private fun timezoneOptions(): List<Pair<String, String>> =
    listOf("auto" to stringResource(R.string.settings_value_auto)) +
        TIMEZONE_OPTIONS.map { it to it }

/**
 * Display-name overrides, keyed by lower-cased BCP 47 tag, where the platform's
 * own name is not the wording Mochi wants. Mirrors the same map in
 * apps/settings/web/src/features/user/preferences.tsx so the two clients agree:
 * `en` is Mochi's neutral English source catalogue, neither UK nor US.
 */
private val LANGUAGE_NAMES = mapOf(
    "en" to "English (international)",
    "en-us" to "English (USA)",
    "es" to "Español (España)",
    "es-419" to "Español (latinoamericano)",
)

/**
 * Each language renders as its own native name, so a reader recognises theirs
 * by sight without already being able to read the current UI language.
 */
private fun languageName(tag: String): String {
    LANGUAGE_NAMES[tag.lowercase()]?.let { return it }
    val locale = Locale.forLanguageTag(tag)
    val native = locale.getDisplayName(locale)
    if (native.isBlank()) return tag
    return native.replaceFirstChar { it.titlecase(locale) }
}

/**
 * Latin-script names first, then the rest, each bucket by native name. The
 * server returns tags alphabetically, which puts Arabic at the top — accurate
 * but not what a reader scanning for their own language expects.
 */
private fun scriptBucket(native: String): Int {
    val first = native.firstOrNull { it.isLetter() } ?: return 0
    return if (first.code < 0x0250) 0 else 1
}

/**
 * The picker's options, built from the tags the server reports installed.
 *
 * [defaultLabel] is the "use the server default" row. [current] is kept even if
 * the server does not list it, so a value already saved never silently vanishes
 * from the picker that is meant to show it.
 */
internal fun languageOptions(
    tags: List<String>,
    current: String,
    defaultLabel: String,
): List<Pair<String, String>> {
    val installed = (tags + current.takeIf { it.isNotBlank() }.orEmpty())
        .filter { it.isNotBlank() }
        .distinct()
    val sorted = installed
        .map { it to languageName(it) }
        .sortedWith(
            compareBy<Pair<String, String>> { scriptBucket(it.second) }
                .thenComparing({ it.second }, NaturalCompare),
        )
    return listOf("" to defaultLabel) + sorted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingsScreen(
    onBack: () -> Unit,
    viewModel: UserSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val schema = prefSchema(
        languages = uiState.languages,
        currentLanguage = uiState.values["language"].orEmpty(),
    )

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
