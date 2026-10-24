// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.util.AttachmentOpener
import org.mochios.android.util.OPAQUE_MIME

/**
 * What the user can still do with a file no app on the device can open: save
 * it somewhere they choose, where a file manager or a later-installed app can
 * reach it, or share it to another app. Neither path launches a viewer, so the
 * coerced type that kept the file from opening is not loosened.
 *
 * @param file The cached file that could not be opened.
 * @param onDismiss Called when the dialog closes, after a save or share or on cancel.
 */
@Composable
fun UnopenedFileDialog(
    file: AttachmentOpener.UnopenedFile,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val saver = rememberLauncherForActivityResult(SaveAsDocument) { target: Uri? ->
        if (target == null) {
            return@rememberLauncherForActivityResult
        }
        saving = true
        scope.launch {
            val saved = AttachmentOpener.save(context, file, target)
            saving = false
            toast(
                context,
                if (saved) R.string.common_saved else R.string.common_file_save_failed,
            )
            onDismiss()
        }
    }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.common_no_app_for_file),
        dismissText = stringResource(R.string.common_cancel),
        dismissEnabled = !saving,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.common_file_actions_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                MochiButton(
                    onClick = { saver.launch(file) },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Text(
                        text = stringResource(R.string.common_file_save_to_device),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                MochiOutlinedButton(
                    onClick = {
                        scope.launch {
                            if (!AttachmentOpener.share(context, file)) {
                                toast(context, R.string.common_file_open_failed)
                            }
                            onDismiss()
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text(
                        text = stringResource(R.string.common_file_share),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    )
}

private fun toast(context: Context, message: Int) {
    Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
}

/**
 * The system save picker for [AttachmentOpener.UnopenedFile], named after the
 * file. Its type comes from the name's extension so the picker keeps that
 * extension; nothing is launched to view it, so the type need not be coerced.
 */
private object SaveAsDocument : ActivityResultContract<AttachmentOpener.UnopenedFile, Uri?>() {

    override fun createIntent(context: Context, input: AttachmentOpener.UnopenedFile): Intent {
        val extension = input.name.substringAfterLast('.', "").lowercase()
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: OPAQUE_MIME
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type)
            .putExtra(Intent.EXTRA_TITLE, input.name)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
