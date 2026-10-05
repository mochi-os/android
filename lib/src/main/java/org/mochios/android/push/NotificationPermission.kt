// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.mochios.android.R
import org.mochios.android.ui.components.MochiAlertDialog

/**
 * Requests the Android 13+ POST_NOTIFICATIONS permission once per launch. Drop
 * in at the top of a Composable shell; the manifest declaration alone does not
 * allow posting. After one refusal it explains why before asking again. When a
 * request is refused and Android will not show it again, a dialog offers the
 * app's notification settings instead.
 */
@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val activity = LocalActivity.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var explaining by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }

    fun rationale() = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted && !rationale()) {
            blocked = true
        }
    }

    LaunchedEffect(asked) {
        if (!asked && !granted(context)) {
            asked = true
            if (rationale()) {
                explaining = true
            } else {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (explaining) {
        MochiAlertDialog(
            onDismissRequest = { explaining = false },
            title = stringResource(R.string.common_notifications),
            text = stringResource(R.string.notifications_permission_reason),
            confirmText = stringResource(R.string.common_continue),
            onConfirm = {
                explaining = false
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            dismissText = stringResource(R.string.common_cancel),
        )
    }

    if (blocked) {
        MochiAlertDialog(
            onDismissRequest = { blocked = false },
            title = stringResource(R.string.common_notifications),
            text = stringResource(R.string.notifications_permission_blocked),
            confirmText = stringResource(R.string.common_open_settings),
            onConfirm = {
                blocked = false
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
            dismissText = stringResource(R.string.common_cancel),
        )
    }
}

private fun granted(context: Context) = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.POST_NOTIFICATIONS,
) == PackageManager.PERMISSION_GRANTED
