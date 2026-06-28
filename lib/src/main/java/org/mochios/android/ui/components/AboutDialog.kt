// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.update.UpdateChecker
import org.mochios.android.update.UpdateInstaller
import org.mochios.android.update.UpdateStatus

private sealed interface CheckUi {
    data object Idle : CheckUi
    data object Checking : CheckUi
    data object UpToDate : CheckUi
    /** A newer [version] was found and is downloading in the background. */
    data class Downloading(val version: String) : CheckUi
    data object Offline : CheckUi
    data object DownloadFailed : CheckUi
}

/**
 * Simple "About" dialog shown from each feature's drawer footer. Reads the
 * installed Mochi client's versionName via PackageManager so a single
 * dialog works for every feature — no need to thread BuildConfig through.
 *
 * Also surfaces a "Check for updates" button that runs
 * [UpdateChecker.checkNow] inline. On UpdateStaged the dialog dismisses
 * itself and immediately calls [UpdateInstaller.promptIfPending], which
 * hands the downloaded APK to the system installer.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    var state by remember { mutableStateOf<CheckUi>(CheckUi.Idle) }

    // A staged APK is ready — hand off to the system installer. forcePrompt (vs
    // promptIfPending) because the user explicitly asked, so an
    // already-declined-this-version suppression shouldn't apply. Needs an
    // Activity (it calls startActivity); context here is the hosting Activity.
    fun promptInstall() {
        (context as? Activity)?.let { UpdateInstaller.forcePrompt(it) }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.about_version, version))
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            state = CheckUi.Checking
                            scope.launch {
                                when (val result = UpdateChecker.checkForUpdate(context)) {
                                    is UpdateStatus.UpToDate -> state = CheckUi.UpToDate
                                    is UpdateStatus.Offline -> state = CheckUi.Offline
                                    is UpdateStatus.Ready -> promptInstall()
                                    is UpdateStatus.Downloading -> {
                                        state = CheckUi.Downloading(result.version)
                                        // Wait for the background download, then prompt
                                        // to install the instant it stages. If the dialog
                                        // is closed first this is cancelled — the download
                                        // keeps running and the next foreground entry's
                                        // promptIfPending still catches it.
                                        if (UpdateChecker.awaitOneShotDownload(context)) {
                                            promptInstall()
                                        } else {
                                            state = CheckUi.DownloadFailed
                                        }
                                    }
                                }
                            }
                        },
                        enabled = state !is CheckUi.Checking && state !is CheckUi.Downloading,
                    ) {
                        Text(stringResource(R.string.about_check_updates))
                    }
                    if (state is CheckUi.Checking || state is CheckUi.Downloading) {
                        Spacer(modifier = Modifier.size(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                // Status line below the button — spells out whether a new version
                // was found and that it's downloading, so a tap is never silent.
                when (val s = state) {
                    is CheckUi.Downloading ->
                        AboutStatus(stringResource(R.string.about_check_downloading, s.version), isError = false)
                    is CheckUi.UpToDate ->
                        AboutStatus(stringResource(R.string.about_up_to_date), isError = false)
                    is CheckUi.Offline ->
                        AboutStatus(stringResource(R.string.about_check_network_error), isError = true)
                    is CheckUi.DownloadFailed ->
                        AboutStatus(stringResource(R.string.about_check_download_failed), isError = true)
                    is CheckUi.Idle, is CheckUi.Checking -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}

@Composable
private fun AboutStatus(text: String, isError: Boolean) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
