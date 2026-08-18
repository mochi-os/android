// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import org.mochios.android.R

/**
 * The longest caption the editor accepts. Matches the bound the server's
 * attachment library holds peer captions to, so a caption that saves locally
 * is never silently truncated when it federates.
 */
private const val CAPTION_MAXIMUM = 1000

/**
 * The caption editor for one attachment. Saving an empty text removes the
 * caption; there is no separate control for that. Shared by the feeds and
 * forums compose screens and the wikis attachments browser, so the three
 * apps caption the same way.
 */
@Composable
fun AttachmentCaptionDialog(
    name: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(name, initial) { mutableStateOf(initial) }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.attachment_caption),
        content = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(CAPTION_MAXIMUM) },
                label = { Text(name) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmText = stringResource(R.string.common_save),
        onConfirm = { onSave(value.trim()) },
        dismissText = stringResource(R.string.common_cancel),
    )
}
