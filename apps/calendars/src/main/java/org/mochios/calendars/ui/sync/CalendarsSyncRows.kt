// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.sync.CalendarsSync
import org.mochios.android.sync.CalendarsSyncState
import org.mochios.android.sync.SyncFailure
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerSwitchRow
import org.mochios.android.ui.components.LabeledSwitchRow
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.calendars.R
import org.mochios.android.R as MochiR

/**
 * The drawer's calendar-sync rows: the "Sync to this phone" switch with its
 * status line, and while it is on, "Sync now" and the per-calendar toggles
 * saying which calendars reach the phone's own calendar app. Turning the
 * switch on asks for the calendar permission; nothing asks for it earlier.
 *
 * A calendar's toggle is separate from the checkbox that shows it in this
 * app's own views: one is what the phone holds, the other what is drawn.
 */
@Composable
fun CalendarsSyncRows(viewModel: CalendarsSyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.refresh()
        if (CalendarsSync.PERMISSIONS.all { results[it] == true }) {
            denied = false
            viewModel.enable()
        } else {
            denied = true
        }
    }

    val on = state.enabled && state.permitted
    DrawerSwitchRow(
        title = stringResource(R.string.calendars_sync_title),
        icon = Icons.Outlined.PhoneAndroid,
        checked = on,
        onCheckedChange = { checked ->
            when {
                !checked -> viewModel.disable()
                CalendarsSync.permitted(context) -> viewModel.enable()
                else -> launcher.launch(CalendarsSync.PERMISSIONS)
            }
        },
        status = status(state, denied),
    )
    if (on) {
        DrawerActionRow(
            title = stringResource(R.string.calendars_sync_now),
            icon = Icons.Outlined.Sync,
            onClick = viewModel::sync,
        )
        // The per-calendar toggles live in a dialog rather than in the drawer
        // itself: the bottom slot does not scroll, so a row per calendar would
        // push the other actions off the screen once there were a few.
        DrawerActionRow(
            title = stringResource(R.string.calendars_sync_choose),
            icon = Icons.Outlined.PhoneAndroid,
            onClick = { choosing = true },
        )
    }

    if (choosing) {
        MochiAlertDialog(
            onDismissRequest = { choosing = false },
            title = stringResource(R.string.calendars_sync_choose),
            dismissText = stringResource(MochiR.string.common_close),
            onDismiss = { choosing = false },
            content = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (calendar in state.calendars) {
                        LabeledSwitchRow(
                            label = calendar.name,
                            checked = calendar.sync,
                            onCheckedChange = { viewModel.calendar(calendar.local, it) },
                        )
                    }
                }
            },
        )
    }
}

/** The line under the switch: what is happening, what went wrong, or when it last synced. */
@Composable
private fun status(state: CalendarsSyncState, denied: Boolean): String? {
    if (!state.permitted && (state.enabled || denied)) return stringResource(R.string.calendars_sync_permission)
    if (!state.enabled) return null
    if (state.running) return stringResource(R.string.calendars_sync_running)
    return when (state.failure) {
        SyncFailure.AUTHORIZATION -> stringResource(R.string.calendars_sync_authorization)
        SyncFailure.TRANSPORT -> stringResource(R.string.calendars_sync_transport)
        // The server localises its own refusal, so it is shown as it came.
        SyncFailure.REFUSED -> state.message.orEmpty()
        SyncFailure.NONE, SyncFailure.PERMISSION -> if (state.time > 0) {
            stringResource(R.string.calendars_sync_time, LocalFormat.current.formatTimestamp(state.time))
        } else {
            stringResource(R.string.calendars_sync_never)
        }
    }
}
