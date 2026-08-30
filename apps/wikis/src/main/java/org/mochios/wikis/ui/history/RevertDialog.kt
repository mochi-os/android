// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.R as MochiR
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiTextField
import org.mochios.wikis.R

/**
 * Putting a revision back is a question asked where it is asked for, not a
 * screen of its own: what it needs is a confirmation and a comment. Shared by
 * the history list and the revision view, so the question reads the same from
 * either.
 *
 * @param slug The page being restored.
 * @param version The revision to restore.
 * @param isReverting Whether the revert is in flight.
 * @param onConfirm Called with the edit comment to record.
 * @param onDismiss Called when the question is dropped.
 */
@Composable
internal fun RevertDialog(
    slug: String,
    version: Int,
    isReverting: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultComment = stringResource(R.string.wikis_revert_page_default_comment, version)
    var comment by remember(version) { mutableStateOf(defaultComment) }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.wikis_revert_page_title),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.wikis_revert_page_message, slug, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                MochiTextField(
                    value = comment,
                    onValueChange = { value -> comment = value },
                    label = { Text(stringResource(R.string.wikis_revert_page_comment_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmText = stringResource(R.string.wikis_revert_page_confirm),
        onConfirm = { onConfirm(comment) },
        confirmEnabled = !isReverting,
        confirmLoading = isReverting,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
        dismissEnabled = !isReverting,
    )
}
