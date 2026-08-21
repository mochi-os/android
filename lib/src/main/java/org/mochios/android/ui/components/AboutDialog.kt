// Copyright © 2026 Mochisoft OÜ
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.update.DownloadState
import org.mochios.android.update.UpdateChecker
import org.mochios.android.update.UpdateInstaller
import org.mochios.android.update.UpdateStatus

private sealed interface CheckUi {
    data object Idle : CheckUi
    data object Checking : CheckUi
    data object UpToDate : CheckUi
    data object Offline : CheckUi
    data object DownloadFailed : CheckUi
}

/**
 * "About" dialog with the client version and a "Check for updates" button. The
 * download belongs to [UpdateChecker], so closing the dialog leaves it running
 * and re-opening re-attaches.
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

    // The process-wide download, so a transfer another dialog (or the daily
    // worker) started shows here rather than reading as "nothing happening".
    val update by UpdateChecker.state.collectAsState()
    val running = update as? DownloadState.Running

    // True once this dialog has seen the download running, so it reacts to the
    // outcome. Without it, a Staged left over from an earlier check would fire
    // the installer the moment the dialog opened, unasked.
    var watching by remember { mutableStateOf(false) }

    // A staged APK is ready — hand off to the system installer. forcePrompt (vs
    // promptIfPending) because the user explicitly asked, so an
    // already-declined-this-version suppression shouldn't apply. Needs an
    // Activity (it calls startActivity); context here is the hosting Activity.
    fun promptInstall() {
        (context as? Activity)?.let { UpdateInstaller.forcePrompt(it) }
        onDismiss()
    }

    LaunchedEffect(update) {
        when (update) {
            is DownloadState.Running -> {
                watching = true
                state = CheckUi.Idle
            }
            is DownloadState.Staged -> if (watching) promptInstall()
            is DownloadState.Failed -> if (watching) {
                watching = false
                state = CheckUi.DownloadFailed
            }
            is DownloadState.Idle -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = {
            // The process can still be killed mid-download, so hand it to
            // WorkManager to resume on the next start.
            if (running != null) UpdateChecker.enqueueBackgroundDownload(context)
            onDismiss()
        },
        properties = DialogProperties(
            // A tap on the scrim is the one gesture that dismisses without
            // meaning to, and losing sight of a 40 MB download to it reads as
            // the download having stopped. Back press and the button still
            // close the dialog; both are deliberate.
            dismissOnClickOutside = running == null,
        ),
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
                                when (UpdateChecker.checkForUpdate(context)) {
                                    is UpdateStatus.UpToDate -> state = CheckUi.UpToDate
                                    is UpdateStatus.Offline -> state = CheckUi.Offline
                                    is UpdateStatus.Ready -> promptInstall()
                                    is UpdateStatus.Available -> {
                                        // Owned by UpdateChecker so it survives
                                        // the dialog closing and runs in the
                                        // foreground - a WorkManager job's
                                        // network is throttled as background
                                        // data. Only the join is cancelled.
                                        UpdateChecker.startDownload(context).join()
                                        state = when (UpdateChecker.state.value) {
                                            // Staged is handled by the effect above,
                                            // which hands off to the installer.
                                            is DownloadState.Staged -> CheckUi.Idle
                                            is DownloadState.Failed -> CheckUi.DownloadFailed
                                            else -> CheckUi.Idle
                                        }
                                    }
                                }
                            }
                        },
                        enabled = state !is CheckUi.Checking && running == null,
                    ) {
                        Text(stringResource(R.string.about_check_updates))
                    }
                    // Only until the download starts reporting — after that the
                    // progress bar below is the indicator, and two at once is noise.
                    if (state is CheckUi.Checking && running == null) {
                        Spacer(modifier = Modifier.size(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                // Status line below the button — spells out whether a new version
                // was found and that it's downloading, so a tap is never silent.
                // A live download wins over the local state: it is the thing the
                // user most needs to see, whoever started it.
                if (running != null) {
                    AboutStatus(
                        stringResource(R.string.about_check_downloading, running.version),
                        isError = false,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LinearProgressIndicator(
                            progress = { running.percent / 100f },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "${running.percent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    when (state) {
                        is CheckUi.UpToDate ->
                            AboutStatus(stringResource(R.string.about_up_to_date), isError = false)
                        is CheckUi.Offline ->
                            AboutStatus(stringResource(R.string.about_check_network_error), isError = true)
                        is CheckUi.DownloadFailed ->
                            AboutStatus(stringResource(R.string.about_check_download_failed), isError = true)
                        is CheckUi.Idle, is CheckUi.Checking -> Unit
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (running != null) UpdateChecker.enqueueBackgroundDownload(context)
                    onDismiss()
                },
            ) {
                // Say what closing does while a download is running, so the
                // answer to "is it still going?" is on the button itself.
                Text(
                    stringResource(
                        if (running != null) R.string.about_check_background else R.string.about_close
                    )
                )
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
