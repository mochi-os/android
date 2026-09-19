// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.sync.ContactsSync
import org.mochios.android.sync.ContactsSyncState
import org.mochios.android.sync.SyncFailure
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerSwitchRow
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.people.R

/**
 * The drawer's contacts-sync rows: the "Sync to this phone" switch with its
 * status line, and "Sync now" while it is on. Turning the switch on confirms
 * once what a delete on the phone means, then asks for the contacts
 * permission; nothing asks for it earlier.
 */
@Composable
fun ContactsSyncRows(viewModel: ContactsSyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.refresh()
        if (ContactsSync.PERMISSIONS.all { results[it] == true }) {
            denied = false
            viewModel.enable()
        } else {
            denied = true
        }
    }

    val on = state.enabled && state.permitted
    DrawerSwitchRow(
        title = stringResource(R.string.people_sync_title),
        icon = Icons.Outlined.PhoneAndroid,
        checked = on,
        onCheckedChange = { checked ->
            if (checked) confirming = true else viewModel.disable()
        },
        status = status(state, denied),
    )
    if (on) {
        DrawerActionRow(
            title = stringResource(R.string.people_sync_now),
            icon = Icons.Outlined.Sync,
            onClick = viewModel::sync,
        )
    }

    if (confirming) {
        MochiAlertDialog(
            onDismissRequest = { confirming = false },
            title = stringResource(R.string.people_sync_title),
            text = stringResource(R.string.people_sync_confirm),
            confirmText = stringResource(R.string.people_sync_enable),
            onConfirm = {
                confirming = false
                if (ContactsSync.permitted(context)) {
                    viewModel.enable()
                } else {
                    launcher.launch(ContactsSync.PERMISSIONS)
                }
            },
            dismissText = stringResource(R.string.people_common_cancel),
        )
    }
}

/** The line under the switch: what is happening, what went wrong, or when it last synced. */
@Composable
private fun status(state: ContactsSyncState, denied: Boolean): String? {
    if (!state.permitted && (state.enabled || denied)) return stringResource(R.string.people_sync_permission)
    if (!state.enabled) return null
    if (state.running) return stringResource(R.string.people_sync_running)
    return when (state.failure) {
        SyncFailure.AUTHORIZATION -> stringResource(R.string.people_sync_authorization)
        SyncFailure.TRANSPORT -> stringResource(R.string.people_sync_transport)
        SyncFailure.REFUSED -> stringResource(R.string.people_sync_refused, state.message.orEmpty())
        SyncFailure.NONE, SyncFailure.PERMISSION -> if (state.time > 0) {
            stringResource(R.string.people_sync_time, LocalFormat.current.formatTimestamp(state.time))
        } else {
            stringResource(R.string.people_sync_never)
        }
    }
}
