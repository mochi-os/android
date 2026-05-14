package org.mochios.android.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import org.mochios.android.update.CheckOutcome
import org.mochios.android.update.UpdateChecker
import org.mochios.android.update.UpdateInstaller

private enum class UpdateCheckState { IDLE, CHECKING, UP_TO_DATE, NETWORK_ERROR, DOWNLOAD_FAILED }

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
    var state by remember { mutableStateOf(UpdateCheckState.IDLE) }

    // If checkNow stages an update, dismiss the dialog and hand off to the
    // installer. promptIfPending needs an Activity (it calls startActivity)
    // — context here is the Activity hosting the dialog.
    fun handleOutcome(outcome: CheckOutcome) {
        state = when (outcome) {
            CheckOutcome.UpToDate -> UpdateCheckState.UP_TO_DATE
            CheckOutcome.NetworkError -> UpdateCheckState.NETWORK_ERROR
            CheckOutcome.DownloadFailed -> UpdateCheckState.DOWNLOAD_FAILED
            CheckOutcome.UpdateStaged -> {
                (context as? Activity)?.let { UpdateInstaller.promptIfPending(it) }
                onDismiss()
                UpdateCheckState.IDLE
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column {
                Text(stringResource(R.string.about_version, version))
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            state = UpdateCheckState.CHECKING
                            scope.launch {
                                handleOutcome(UpdateChecker.checkNow(context))
                            }
                        },
                        enabled = state != UpdateCheckState.CHECKING,
                    ) {
                        Text(stringResource(R.string.about_check_updates))
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    when (state) {
                        UpdateCheckState.CHECKING -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        UpdateCheckState.UP_TO_DATE -> Text(
                            text = stringResource(R.string.about_up_to_date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        UpdateCheckState.NETWORK_ERROR -> Text(
                            text = stringResource(R.string.about_check_network_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        UpdateCheckState.DOWNLOAD_FAILED -> Text(
                            text = stringResource(R.string.about_check_download_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        UpdateCheckState.IDLE -> Unit
                    }
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
