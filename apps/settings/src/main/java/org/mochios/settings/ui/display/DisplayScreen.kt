// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.R
import org.mochios.android.i18n.ThemeInfo
import org.mochios.settings.ui.preferences.PrefRow
import org.mochios.settings.ui.preferences.PrefSpec

private val DISPLAY_PREF_KEYS: List<String> = listOf(
    "appearance",
    "theme",
    "density",
    "radius",
    "background",
    "font",
    "font_size",
)

/** Keys reset by the Display "Reset display" button. Mirrors the web feature. */
private val DISPLAY_RESET_KEYS: List<String> = DISPLAY_PREF_KEYS

@Composable
private fun displaySchemaTop(): List<PrefSpec> = listOf(
    PrefSpec(
        key = "appearance",
        label = stringResource(R.string.settings_appearance),
        options = listOf(
            "auto" to stringResource(R.string.settings_value_auto),
            "light" to stringResource(R.string.settings_appearance_light),
            "dark" to stringResource(R.string.settings_appearance_dark),
        ),
    ),
)

@Composable
private fun displaySchemaBottom(): List<PrefSpec> = listOf(
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
        key = "background",
        label = stringResource(R.string.settings_background),
        options = listOf(
            "theme" to stringResource(R.string.settings_background_theme),
            "off" to stringResource(R.string.settings_background_off),
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayScreen(
    onBack: () -> Unit,
    viewModel: DisplayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val topSchema = displaySchemaTop()
    val bottomSchema = displaySchemaBottom()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_display_title)) },
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
        var showThemeSheet by remember { mutableStateOf(false) }
        val currentTheme = uiState.values["theme"].orEmpty()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(topSchema, key = { it.key }) { spec ->
                PrefRow(
                    spec = spec,
                    current = uiState.values[spec.key] ?: "",
                    onChange = { value -> viewModel.set(spec.key, value) },
                )
                HorizontalDivider()
            }

            if (uiState.themes.isNotEmpty()) {
                item(key = "theme") {
                    ThemeRow(
                        themes = uiState.themes,
                        currentThemeId = currentTheme,
                        onClick = { showThemeSheet = true },
                    )
                    HorizontalDivider()
                }
            }

            items(bottomSchema, key = { it.key }) { spec ->
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
                    Text(stringResource(R.string.settings_display_reset))
                }
            }
        }

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text(stringResource(R.string.settings_display_reset_confirm_title)) },
                text = { Text(stringResource(R.string.settings_display_reset_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetConfirm = false
                            viewModel.reset(DISPLAY_RESET_KEYS)
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

        if (showThemeSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showThemeSheet = false },
                sheetState = sheetState,
            ) {
                ThemePickerContent(
                    themes = uiState.themes,
                    currentThemeId = currentTheme,
                    onPick = { themeId ->
                        // Tapping the already-selected card resets to default
                        // (empty string), matching the web behaviour.
                        val next = if (themeId == currentTheme) "" else themeId
                        viewModel.set("theme", next)
                        showThemeSheet = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(
    themes: List<ThemeInfo>,
    currentThemeId: String,
    onClick: () -> Unit,
) {
    val current = themes.firstOrNull { it.id == currentThemeId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current != null) {
                    ThemeSwatch(theme = current, size = 18.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(current.id, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        stringResource(R.string.settings_theme_default),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemePickerContent(
    themes: List<ThemeInfo>,
    currentThemeId: String,
    onPick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
        ) {
            items(themes, key = { it.id }) { theme ->
                ThemeCard(
                    theme = theme,
                    selected = theme.id == currentThemeId,
                    onClick = { onPick(theme.id) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ThemeCard(
    theme: ThemeInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 2.dp else 1.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeSwatch(theme = theme, size = 24.dp)
            Spacer(Modifier.size(8.dp))
            ThemeSwatch(theme = theme, size = 16.dp, useChroma = false)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = theme.id,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ThemeSwatch(
    theme: ThemeInfo,
    size: androidx.compose.ui.unit.Dp,
    useChroma: Boolean = true,
) {
    // Build a Color approximation from the OKLCH-style anchors. We use HSL so
    // the swatch matches what ColorSchemeGenerator renders on a real device:
    // chroma → saturation (capped at ~0.20 OKLCH = full HSL saturation),
    // lightness fixed at 0.55 so swatches read well against either appearance.
    val sat = if (useChroma) (theme.chroma / 0.20f).coerceIn(0.15f, 1f) else 0.05f
    val color = Color.hsl(theme.hue.mod(360f), sat, 0.55f)
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
    )
}
