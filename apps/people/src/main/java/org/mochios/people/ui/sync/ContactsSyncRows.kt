// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.sync

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
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
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.sync.ContactsSync
import org.mochios.android.sync.ContactsSyncState
import org.mochios.android.sync.SyncFailure
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerSwitchRow
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.people.R
import org.mochios.android.R as MochiR

/**
 * The drawer's contacts-sync rows: the "Sync to this phone" switch with its
 * status line, and "Sync now" while it is on. Turning the switch on asks for
 * the contacts permission, then confirms what a delete on the phone means
 * before sync starts; nothing asks for the permission earlier. After one
 * refusal it explains why before asking again. When a request is refused and
 * Android will not show it again, a dialog offers the app's system settings,
 * and the confirmation follows when the reader comes back with the
 * permission granted.
 */
@Composable
fun ContactsSyncRows(viewModel: ContactsSyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var explaining by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current

    fun rationale() = activity != null && ContactsSync.PERMISSIONS.any { permission ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.refresh()
        if (ContactsSync.PERMISSIONS.all { permission -> results[permission] == true }) {
            denied = false
            confirming = true
        } else {
            denied = true
            blocked = !rationale()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
        if (settings) {
            settings = false
            if (ContactsSync.permitted(context)) {
                denied = false
                confirming = true
            }
        }
    }

    val on = state.enabled && state.permitted
    DrawerSwitchRow(
        title = stringResource(R.string.people_sync_title),
        icon = Icons.Outlined.PhoneAndroid,
        checked = on,
        onCheckedChange = { checked ->
            when {
                !checked -> viewModel.disable()
                ContactsSync.permitted(context) -> confirming = true
                rationale() -> explaining = true
                else -> launcher.launch(ContactsSync.PERMISSIONS)
            }
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
                viewModel.enable()
            },
            dismissText = stringResource(R.string.people_common_cancel),
        )
    }

    if (explaining) {
        MochiAlertDialog(
            onDismissRequest = { explaining = false },
            title = stringResource(R.string.people_sync_title),
            text = stringResource(R.string.people_sync_permission_reason),
            confirmText = stringResource(MochiR.string.common_continue),
            onConfirm = {
                explaining = false
                launcher.launch(ContactsSync.PERMISSIONS)
            },
            dismissText = stringResource(R.string.people_common_cancel),
        )
    }

    if (blocked) {
        MochiAlertDialog(
            onDismissRequest = { blocked = false },
            title = stringResource(R.string.people_sync_title),
            text = stringResource(R.string.people_sync_permission_blocked),
            confirmText = stringResource(MochiR.string.common_open_settings),
            onConfirm = {
                blocked = false
                settings = true
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
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
