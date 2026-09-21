// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.CopyButton
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.LabeledSelectField
import org.mochios.android.ui.components.LabeledSwitchRow
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiTextField
import org.mochios.calendars.R
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Hours
import org.mochios.calendars.model.Multiweek
import org.mochios.calendars.model.Preferences
import org.mochios.android.R as MochiR

/** Renames a calendar. */
@Composable
fun RenameCalendarDialog(
    calendar: Calendar,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(calendar.id) { mutableStateOf(calendar.name) }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendars_rename),
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = { onConfirm(name.trim()) },
        confirmEnabled = name.isNotBlank() && name.trim() != calendar.name,
        confirmLoading = saving,
        dismissText = stringResource(MochiR.string.common_cancel),
        content = {
            MochiTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.calendars_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** Changes a calendar's colour. */
@Composable
fun ColourCalendarDialog(
    calendar: Calendar,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var colour by rememberSaveable(calendar.id) { mutableStateOf(calendar.colour) }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendars_colour),
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = { onConfirm(colour.trim().lowercase()) },
        confirmEnabled = colour.isNotBlank() && colour != calendar.colour,
        confirmLoading = saving,
        dismissText = stringResource(MochiR.string.common_cancel),
        content = {
            ColorPicker(hex = colour, onHexChange = { colour = it }, modifier = Modifier.fillMaxWidth())
        },
    )
}

/**
 * The calendar's ICS link, which anyone can subscribe to. The address is
 * shown once, when it is first minted; afterwards the dialog offers to
 * replace it — which stops the old one working — or to revoke it outright.
 */
@Composable
fun LinkDialog(
    calendar: Calendar,
    url: String?,
    exists: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onRevoke: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendars_link_title),
        text = when {
            url != null -> stringResource(R.string.calendars_link_once)
            exists -> stringResource(R.string.calendars_link_exists)
            else -> null
        },
        confirmText = if (exists && url == null) {
            stringResource(R.string.calendars_link_replace)
        } else {
            null
        },
        onConfirm = onReplace,
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_close),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (url != null) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        DataChip(value = url, copyable = false, wrap = true, modifier = Modifier.weight(1f))
                        CopyButton(
                            value = url,
                            contentDescription = stringResource(R.string.calendars_link_copy),
                            sensitive = true,
                        )
                    }
                }
                if (exists || url != null) {
                    org.mochios.android.ui.components.MochiTextButton(onClick = onRevoke) {
                        Text(stringResource(R.string.calendars_link_revoke))
                    }
                }
            }
        },
    )
}

/**
 * The preferences the views read: the working hours they shade, the work
 * days, how many weeks a multiweek view shows, the default event length and
 * the default reminder. Shared with the web, so a change here shows there.
 */
@Composable
fun PreferencesDialog(
    preferences: Preferences,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Preferences) -> Unit,
) {
    var start by rememberSaveable { mutableIntStateOf(preferences.hours.start) }
    var finish by rememberSaveable { mutableIntStateOf(preferences.hours.finish) }
    var days by remember { mutableStateOf(preferences.days.toSet()) }
    var weeks by rememberSaveable { mutableIntStateOf(preferences.multiweek.weeks) }
    var previous by rememberSaveable { mutableIntStateOf(preferences.multiweek.previous) }
    var duration by rememberSaveable { mutableIntStateOf(preferences.duration) }
    var reminder by rememberSaveable { mutableIntStateOf(preferences.reminder) }

    val hours = (0..23).map { it.toString() to it.toString().padStart(2, '0') + ":00" }
    val ends = (1..24).map { it.toString() to it.toString().padStart(2, '0') + ":00" }
    val weekdays = listOf(
        0 to R.string.calendars_day_sunday,
        1 to R.string.calendars_day_monday,
        2 to R.string.calendars_day_tuesday,
        3 to R.string.calendars_day_wednesday,
        4 to R.string.calendars_day_thursday,
        5 to R.string.calendars_day_friday,
        6 to R.string.calendars_day_saturday,
    )

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendars_preferences),
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = {
            onConfirm(
                Preferences(
                    hours = Hours(start, finish),
                    days = days.sorted(),
                    multiweek = Multiweek(weeks, previous),
                    duration = duration,
                    reminder = reminder,
                    view = preferences.view,
                ),
            )
        },
        confirmEnabled = finish > start,
        confirmLoading = saving,
        dismissText = stringResource(MochiR.string.common_cancel),
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledSelectField(
                    label = stringResource(R.string.calendars_hours_start),
                    placeholder = "",
                    options = hours,
                    selected = start.toString(),
                    onSelect = { start = it.toIntOrNull() ?: start },
                )
                LabeledSelectField(
                    label = stringResource(R.string.calendars_hours_finish),
                    placeholder = "",
                    options = ends,
                    selected = finish.toString(),
                    onSelect = { finish = it.toIntOrNull() ?: finish },
                )
                if (finish <= start) {
                    Text(
                        text = stringResource(R.string.calendars_hours_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(R.string.calendars_work_days),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                for ((index, label) in weekdays) {
                    LabeledSwitchRow(
                        label = stringResource(label),
                        checked = index in days,
                        onCheckedChange = { on ->
                            days = if (on) days + index else days - index
                        },
                    )
                }
                LabeledSelectField(
                    label = stringResource(R.string.calendars_multiweek_weeks),
                    placeholder = "",
                    options = (2..8).map { it.toString() to it.toString() },
                    selected = weeks.toString(),
                    onSelect = { weeks = it.toIntOrNull() ?: weeks },
                )
                LabeledSelectField(
                    label = stringResource(R.string.calendars_multiweek_previous),
                    placeholder = "",
                    options = (0..2).map { it.toString() to it.toString() },
                    selected = previous.toString(),
                    onSelect = { previous = it.toIntOrNull() ?: previous },
                )
                LabeledSelectField(
                    label = stringResource(R.string.calendars_default_duration),
                    placeholder = "",
                    // Zero is a real choice: an event that ends when it starts.
                    options = listOf(0, 15, 30, 45, 60, 90, 120).map {
                        it.toString() to pluralStringResource(R.plurals.calendars_minutes, it, it)
                    },
                    selected = duration.toString(),
                    onSelect = { duration = it.toIntOrNull() ?: duration },
                )
                LabeledSelectField(
                    label = stringResource(R.string.calendars_default_reminder),
                    placeholder = "",
                    options = reminderOptions(),
                    selected = reminder.toString(),
                    onSelect = { reminder = it.toIntOrNull() ?: reminder },
                )
            }
        },
    )
}

/** The reminder choices, as value-to-label pairs for a select. */
@Composable
fun reminderOptions(): List<Pair<String, String>> = listOf(
    "-1" to stringResource(R.string.calendars_reminder_none),
    "0" to stringResource(R.string.calendars_reminder_time),
    "5" to stringResource(R.string.calendars_reminder_minutes, 5),
    "15" to stringResource(R.string.calendars_reminder_minutes, 15),
    "30" to stringResource(R.string.calendars_reminder_minutes, 30),
    "60" to stringResource(R.string.calendars_reminder_hour),
    "1440" to stringResource(R.string.calendars_reminder_day),
)

/**
 * "This event" or "All events" for a recurring occurrence. An override
 * changes or removes the one occurrence; the whole series changes or removes
 * every one of them.
 */
@Composable
fun ScopeDialog(
    deleting: Boolean,
    onDismiss: () -> Unit,
    onOne: () -> Unit,
    onAll: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = if (deleting) {
            stringResource(R.string.calendars_scope_delete)
        } else {
            stringResource(R.string.calendars_scope_edit)
        },
        confirmText = stringResource(R.string.calendars_scope_one),
        onConfirm = onOne,
        destructive = deleting,
        dismissText = stringResource(R.string.calendars_scope_all),
        onDismiss = onAll,
    )
}

/** Confirms deleting a calendar, saying that its events go with it. */
@Composable
fun DeleteCalendarDialog(
    calendar: Calendar,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendars_delete_title, calendar.name),
        text = stringResource(R.string.calendars_delete_message),
        confirmText = stringResource(R.string.calendars_delete),
        onConfirm = onConfirm,
        confirmLoading = deleting,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

/** Confirms deleting an event. */
@Composable
fun DeleteEventDialog(
    summary: String,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendars_event_delete_title, summary),
        text = stringResource(R.string.calendars_event_delete_message),
        confirmText = stringResource(R.string.calendars_delete),
        onConfirm = onConfirm,
        confirmLoading = deleting,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}
