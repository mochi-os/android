// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.calendars.R
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.ui.calendar.toColour
import org.mochios.calendars.ui.sync.CalendarsSyncRows

/** What a calendar's overflow menu can ask for. */
enum class CalendarAction {
    ONLY,
    RENAME,
    COLOUR,
    LINK,
    POLL,
    DELETE,
}

/**
 * The calendars app's drawer. There is no "All calendars" row: the view is
 * always the overlay of the checked calendars, so each row is a checkbox in
 * the calendar's own colour rather than a link. Each row carries an overflow
 * menu; beneath them sit Create, Subscribe, Connect device, Preferences
 * and the phone-sync switch.
 */
@Composable
fun CalendarDrawer(
    drawerState: DrawerState,
    calendars: List<Calendar>,
    hidden: Set<String>,
    onToggle: (String) -> Unit,
    onAction: (CalendarAction, Calendar) -> Unit,
    onCreate: () -> Unit,
    onSubscribe: () -> Unit,
    onPreferences: () -> Unit,
    onConnectDevice: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    DrawerTitle(stringResource(R.string.calendars_drawer_title))
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(calendars, key = { it.id }) { calendar ->
                            CalendarRow(
                                calendar = calendar,
                                shown = calendar.id !in hidden,
                                onToggle = { onToggle(calendar.id) },
                                onAction = { onAction(it, calendar) },
                            )
                        }
                    }
                    HorizontalDivider()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DrawerActionRow(
                            title = stringResource(R.string.calendars_create),
                            icon = Icons.Outlined.Add,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onCreate()
                            },
                        )
                        DrawerActionRow(
                            title = stringResource(R.string.calendars_subscribe),
                            icon = Icons.Outlined.RssFeed,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSubscribe()
                            },
                        )
                        DrawerActionRow(
                            title = stringResource(R.string.calendars_connect_device),
                            icon = Icons.Outlined.Smartphone,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onConnectDevice()
                            },
                        )
                        DrawerActionRow(
                            title = stringResource(R.string.calendars_preferences),
                            icon = Icons.Outlined.Settings,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onPreferences()
                            },
                        )
                        CalendarsSyncRows()
                    }
                }
            }
        },
        content = content,
    )
}

/**
 * One calendar: a checkbox in its own colour, its name, the linked marker
 * where the calendar mirrors one on another server, and its overflow menu.
 * The row itself is the checkbox, so a tap anywhere on it shows or hides the
 * calendar.
 */
@Composable
private fun CalendarRow(
    calendar: Calendar,
    shown: Boolean,
    onToggle: () -> Unit,
    onAction: (CalendarAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = shown, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = 24.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColourCheckbox(calendar.colour, shown)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = calendar.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // A linked calendar is the user's to write in, so the only
                // thing to say about it is where its events also live.
                if (calendar.linked) {
                    Spacer(Modifier.size(6.dp))
                    Icon(
                        Icons.Outlined.Sync,
                        contentDescription = stringResource(R.string.calendars_link_marker),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Why a calendar has stopped updating, under its name. The row's
            // own menu carries the fetch that is the way out of it.
            val failure = pollFailure(calendar.failure)
            if (failure != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = failure,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box {
            MochiIconButton(onClick = { expanded = true }) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = stringResource(R.string.calendars_calendar_menu, calendar.name),
                )
            }
            MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.calendars_only_this)) },
                    leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAction(CalendarAction.ONLY)
                    },
                )
                if (!calendar.readonly) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.calendars_rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onAction(CalendarAction.RENAME)
                        },
                    )
                }
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.calendars_colour)) },
                    leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAction(CalendarAction.COLOUR)
                    },
                )
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.calendars_link_copy)) },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAction(CalendarAction.LINK)
                    },
                )
                if (calendar.subscription || calendar.linked) {
                    MochiDropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (calendar.linked) {
                                        R.string.calendars_link_sync
                                    } else {
                                        R.string.calendars_poll
                                    },
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (calendar.linked) Icons.Outlined.Sync else Icons.Outlined.Refresh,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            expanded = false
                            onAction(CalendarAction.POLL)
                        },
                    )
                }
                if (!calendar.default && !calendar.birthdays) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.calendars_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        destructive = true,
                        onClick = {
                            expanded = false
                            onAction(CalendarAction.DELETE)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Why a calendar's last fetch or sync failed, in words, or null when it did
 * not.
 */
@Composable
private fun pollFailure(failure: String): String? = when (val reason = pollReason(failure)) {
    null -> null
    PollReason.Large -> stringResource(R.string.calendars_poll_large)
    PollReason.Invalid -> stringResource(R.string.calendars_poll_invalid)
    PollReason.Unreachable -> stringResource(R.string.calendars_poll_unreachable)
    PollReason.Unauthorised -> stringResource(R.string.calendars_poll_unauthorised)
    PollReason.Conflict -> stringResource(R.string.calendars_poll_conflict)
    PollReason.Missing -> stringResource(R.string.calendars_poll_missing)
    is PollReason.Status -> stringResource(R.string.calendars_poll_status, reason.code)
    is PollReason.Other -> reason.token
}

/**
 * The calendar's own checkbox: a rounded square filled in its colour with a
 * tick when the calendar is shown, and an outline of the same colour when it
 * is hidden.
 */
@Composable
fun ColourCheckbox(colour: String, shown: Boolean, size: androidx.compose.ui.unit.Dp = 20.dp) {
    val tint = colour.toColour(MaterialTheme.colorScheme.primary)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .then(if (shown) Modifier.background(tint) else Modifier.border(2.dp, tint, RoundedCornerShape(4.dp))),
        contentAlignment = Alignment.Center,
    ) {
        if (shown) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = onColour(tint),
                modifier = Modifier.size(size - 4.dp),
            )
        }
    }
}

/** Black or white, whichever reads on [colour]. */
private fun onColour(colour: Color): Color =
    if (colour.red * 0.299f + colour.green * 0.587f + colour.blue * 0.114f > 0.6f) Color.Black else Color.White
