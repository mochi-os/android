// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButtonTone
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.rememberCopier
import org.mochios.android.util.characters
import org.mochios.calendars.R
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.ui.calendar.Tally
import org.mochios.calendars.ui.editor.REMINDER_LEADS
import org.mochios.calendars.ui.editor.reminderLeads
import org.mochios.android.R as MochiR
import org.mochios.android.i18n.LocalFormat

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
                onValueChange = { name = characters(it, NAME_MAXIMUM) },
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
 * An import into a calendar: how far through the file it is while the rounds
 * run, then what came of it. Until it finishes it has no button and ignores
 * back and a tap outside, since closing it would not stop the import. One
 * that fails stays open on what went wrong, as the web's does, and offers to
 * try the same file again.
 */
@Composable
fun ImportDialog(
    tally: Tally,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    val error = tally.error
    val settled = tally.finished || error != null
    MochiAlertDialog(
        onDismissRequest = { if (settled) onClose() },
        title = stringResource(R.string.calendars_import),
        subtitle = tally.name,
        confirmText = when {
            error != null -> stringResource(MochiR.string.common_retry)
            tally.finished -> stringResource(MochiR.string.common_close)
            else -> null
        },
        onConfirm = if (error != null) onRetry else onClose,
        dismissText = if (error != null) stringResource(MochiR.string.common_cancel) else null,
        onDismiss = onClose,
        properties = DialogProperties(dismissOnBackPress = settled, dismissOnClickOutside = settled),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    error != null -> Text(
                        text = error.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                    )
                    tally.finished -> importCounts(tally).forEach { Text(it) }
                    // The first round's answer brings the file's size; until
                    // then there is nothing to measure against.
                    tally.total == 0 -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else -> {
                        LinearProgressIndicator(
                            progress = { tally.done.toFloat() / tally.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(importProgress(tally))
                    }
                }
            }
        },
    )
}

/** How far an import has read: so many of the file's objects. */
@Composable
fun importProgress(tally: Tally): String {
    val format = LocalFormat.current
    return stringResource(R.string.calendars_import_progress, format.formatNumber(tally.done), format.formatNumber(tally.total))
}

/**
 * What an import came to: the objects written, and those already in the
 * calendar and those that could not be read, when there were any.
 */
@Composable
fun importCounts(tally: Tally): List<String> {
    val format = LocalFormat.current
    return listOfNotNull(
        pluralStringResource(R.plurals.calendars_import_imported, tally.imported, format.formatNumber(tally.imported)),
        if (tally.skipped > 0) {
            pluralStringResource(R.plurals.calendars_import_skipped, tally.skipped, format.formatNumber(tally.skipped))
        } else {
            null
        },
        if (tally.failed > 0) {
            pluralStringResource(R.plurals.calendars_import_failed, tally.failed, format.formatNumber(tally.failed))
        } else {
            null
        },
    )
}

/**
 * The calendar's ICS link, which anyone can subscribe to. The address is
 * shown once, when it is first minted, with Copy and Close; afterwards the
 * dialog says it was already issued and offers to replace it, which stops
 * the old one working. The dialog draws its content in place of its text,
 * so the new address carries its own line saying it is shown once.
 */
@Composable
fun LinkDialog(
    calendar: Calendar,
    url: String?,
    exists: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
) {
    val copier = rememberCopier()
    val label = stringResource(R.string.calendars_link_title)
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = label,
        text = if (exists && url == null) stringResource(R.string.calendars_link_exists) else null,
        confirmText = when {
            url != null -> stringResource(MochiR.string.common_copy)
            exists -> stringResource(R.string.calendars_link_replace)
            else -> null
        },
        onConfirm = {
            if (url != null) {
                copier.copy(url, label = label, sensitive = true)
            } else {
                onReplace()
            }
        },
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_close),
        content = if (url != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.calendars_link_once))
                    DataChip(
                        value = url,
                        copyable = false,
                        wrap = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            null
        },
    )
}

/**
 * Confirms replacing a calendar's ICS link, which stops every subscriber's
 * copy updating. It stays open while the new one is minted, and after a
 * failure, so the replace can be tried again.
 */
@Composable
fun ReplaceLinkDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendars_link_replace_title),
        text = stringResource(R.string.calendars_link_revoke_message),
        confirmText = stringResource(R.string.calendars_link_replace),
        onConfirm = onConfirm,
        confirmLoading = busy,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

/**
 * Confirms revoking a calendar's ICS address, which stops every subscriber's
 * copy updating. It stays open while the address is revoked, and after a
 * failure, so the revoke can be tried again.
 */
@Composable
fun RevokeLinkDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendars_link_revoke_title),
        text = stringResource(R.string.calendars_link_revoke_message),
        confirmText = stringResource(R.string.calendars_link_revoke),
        onConfirm = onConfirm,
        confirmLoading = busy,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

/** The default event lengths offered, in minutes; zero ends an event when it starts. The web offers the same. */
val DURATIONS = listOf(0, 15, 30, 45, 60, 90, 120, 180, 240)

/** The default reminder's choices, none among them, as value-to-label pairs for a select. */
@Composable
fun reminderOptions(): List<Pair<String, String>> =
    listOf("-1" to stringResource(R.string.calendars_reminder_none)) +
        REMINDER_LEADS.map { it.toString() to reminderLabel(it) }

/** The choices for one of an event's reminders, as value-to-label pairs for a select. */
@Composable
fun reminderChoices(current: Int): List<Pair<String, String>> =
    reminderLeads(current).map { it.toString() to reminderLabel(it) }

/** A reminder as the editor names it: at the time, or so long before the start. */
@Composable
fun reminderLabel(minutes: Int): String {
    val format = LocalFormat.current
    return when {
        minutes == 0 -> stringResource(R.string.calendars_reminder_time)
        minutes % 1440 == 0 -> pluralStringResource(R.plurals.calendars_reminder_days, minutes / 1440, format.formatNumber(minutes / 1440))
        minutes % 60 == 0 -> pluralStringResource(R.plurals.calendars_reminder_hours, minutes / 60, format.formatNumber(minutes / 60))
        else -> pluralStringResource(R.plurals.calendars_reminder_minutes, minutes, format.formatNumber(minutes))
    }
}

/**
 * "This event", "This and following" or "All events" for a recurring
 * occurrence, under a [title] that says what is done: "Save this event",
 * "Move this event", "Delete this event", "Copy this event", as the web
 * asks. An override changes or removes the one occurrence; the series cut at
 * it changes or removes it and every one after it; the whole series changes
 * or removes every one of them. A copy is of the one occurrence or of the
 * whole series, and asks without the middle choice, which [following] leaves
 * out. A delete's choices are [destructive].
 */
@Composable
fun ScopeDialog(
    title: String,
    destructive: Boolean = false,
    following: Boolean = true,
    onDismiss: () -> Unit,
    onOne: () -> Unit,
    onFollowing: () -> Unit = {},
    onAll: () -> Unit,
) {
    val tone = if (destructive) MochiButtonTone.Destructive else MochiButtonTone.Primary
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissText = stringResource(MochiR.string.common_cancel),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((label, choose) in listOfNotNull(
                    R.string.calendars_scope_one to onOne,
                    (R.string.calendars_scope_following to onFollowing).takeIf { following },
                    R.string.calendars_scope_all to onAll,
                )) {
                    MochiOutlinedButton(onClick = choose, tone = tone, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(label))
                    }
                }
            }
        },
    )
}

/**
 * Confirms deleting a calendar, saying that its events go with it. A linked or
 * subscribed calendar is only removed from Mochi: the original and its events
 * stay where they are.
 */
@Composable
fun DeleteCalendarDialog(
    calendar: Calendar,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val detached = calendar.linked || calendar.subscription
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = if (detached) {
            stringResource(R.string.calendars_remove_title, calendar.name)
        } else {
            stringResource(R.string.calendars_delete_title, calendar.name)
        },
        text = stringResource(if (detached) R.string.calendars_remove_message else R.string.calendars_delete_message),
        confirmText = stringResource(if (detached) R.string.calendars_remove else R.string.calendars_delete),
        onConfirm = onConfirm,
        confirmLoading = deleting,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

/** Confirms deleting an event. */
@Composable
fun DeleteEventDialog(
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendars_scope_delete),
        confirmText = stringResource(R.string.calendars_delete),
        onConfirm = onConfirm,
        confirmLoading = deleting,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}
