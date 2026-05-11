package org.mochios.android.push

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * One-time dialog asking the user to disable battery optimization for the
 * Mochi shell. Without this, OEMs (Samsung One UI in particular) silently
 * kill the foreground service ~10 minutes after the screen turns off,
 * which kills push notifications.
 *
 * Strategy:
 *  - On first launch, if the device is restricting battery (i.e. our app
 *    is not on the OS ignore-list), show the dialog.
 *  - "Allow background" opens the system's standard
 *    [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] confirm
 *    dialog — single tap, system handles localization.
 *  - On Samsung devices the OS ignore-list is necessary but not
 *    sufficient (the separate "Sleeping apps" list still kills FG
 *    services). We surface a short supplemental note pointing the user
 *    at Device care → Sleeping apps.
 *  - "Don't show again" persists in SharedPreferences so we don't nag.
 *
 * Drop this in at the top of the shell's Composable hierarchy. It only
 * shows for users on Android 6+ (when Doze first appeared) and only when
 * battery optimization is currently restricting us.
 */
@Composable
fun OemBackgroundHintDialog() {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = shouldShow(context)
    }

    if (!visible) return

    AlertDialog(
        onDismissRequest = { visible = false },
        title = { Text(stringResource(R.string.oem_hint_title)) },
        text = {
            val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            val body = if (isSamsung) {
                stringResource(R.string.oem_hint_body) + "\n\n" +
                    stringResource(R.string.oem_hint_samsung_note)
            } else {
                stringResource(R.string.oem_hint_body)
            }
            Text(body)
        },
        confirmButton = {
            TextButton(onClick = {
                openBatteryOptimizationDialog(context)
                markShown(context)
                visible = false
            }) {
                Text(stringResource(R.string.oem_hint_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                markShown(context)
                visible = false
            }) {
                Text(stringResource(R.string.oem_hint_dismiss))
            }
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
