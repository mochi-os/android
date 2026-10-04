// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.weekLocale
import org.mochios.android.ui.components.MochiTextButton
import java.time.LocalDate
import org.mochios.android.R as MochiR

/**
 * The date picker, opened on [day]. Material's picker takes no first day of
 * the week, so the locale it reads is swapped for one whose week starts
 * where the user's does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(day: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val weekStart = LocalFormat.current.preferences.weekStartsOn
    val configuration = LocalConfiguration.current
    val localised = remember(configuration, weekStart) {
        android.content.res.Configuration(configuration).apply { setLocale(weekLocale(weekStart, configuration.locales[0])) }
    }
    CompositionLocalProvider(LocalConfiguration provides localised) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                MochiTextButton(onClick = {
                    state.selectedDateMillis?.let { millis -> onPick(LocalDate.ofEpochDay(millis / 86_400_000L)) }
                        ?: onDismiss()
                }) {
                    Text(stringResource(MochiR.string.common_save))
                }
            },
            dismissButton = {
                MochiTextButton(onClick = onDismiss) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
