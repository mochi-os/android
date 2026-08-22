// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import org.mochios.android.R
import org.mochios.android.ui.components.MochiAlertDialog

/**
 * One-time dialog asking the user to exempt the shell from battery
 * optimization; OEMs (Samsung in particular) kill the foreground service ~10
 * minutes after screen-off. On Samsung the ignore-list is not enough, so the
 * dialog also names Sleeping apps.
 */
@Composable
fun OemBackgroundHintDialog() {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = shouldShow(context)
    }

    if (!visible) return

    MochiAlertDialog(
        onDismissRequest = { visible = false },
        title = stringResource(R.string.oem_hint_title),
        content = {
            val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            val body = if (isSamsung) {
                stringResource(R.string.oem_hint_body) + "\n\n" +
                    stringResource(R.string.oem_hint_samsung_note)
            } else {
                stringResource(R.string.oem_hint_body)
            }
            Text(body)
        },
        confirmText = stringResource(R.string.oem_hint_allow),
        onConfirm = {
            openBatteryOptimizationDialog(context)
            markShown(context)
            visible = false
        },
        dismissText = stringResource(R.string.oem_hint_dismiss),
        onDismiss = {
            markShown(context)
            visible = false
        },
    )
}

private const val PREFS = "mochi_oem_hint"
private const val KEY_SHOWN = "shown"

private fun shouldShow(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_SHOWN, false)) return false
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun markShown(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
        putBoolean(KEY_SHOWN, true)
    }
}

@Suppress("BatteryLife") // We're asking for whitelist to keep push reliable; this is the documented case for the permission.
private fun openBatteryOptimizationDialog(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        // Fallback to the per-app settings page on the rare OEM that
        // strips this intent.
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }
}
