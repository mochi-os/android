// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.ComposeBarAttachments
import org.mochios.feeds.R

/**
 * The attachment wiring every feeds comment composer shares.
 *
 * A builder rather than a wrapper composable: each screen calls
 * [org.mochios.android.ui.components.ComposeBar] itself — so its host, its
 * insets and its placeholder stay visible at the call site — and only the
 * labels and callbacks, which are identical everywhere, come from here.
 */
@Composable
internal fun feedsCommentAttachments(
    attachments: List<Uri>,
    onAdd: (List<Uri>) -> Unit,
    onRemove: (Uri) -> Unit,
    resolveFileName: suspend (Uri) -> String,
): ComposeBarAttachments = ComposeBarAttachments(
    pending = attachments,
    onAdd = onAdd,
    onRemove = onRemove,
    resolveFileName = resolveFileName,
    addLabel = stringResource(R.string.feeds_attach_file),
    fallbackLabel = stringResource(R.string.feeds_file),
    removeLabel = stringResource(R.string.feeds_remove),
)

/**
 * The "replying to…" strip, for the composer's banner slot. Null when the
 * composer is not a reply, which is what the slot expects.
 */
@Composable
internal fun feedsReplyBanner(
    replyingTo: String?,
    onCancelReply: () -> Unit,
): (@Composable () -> Unit)? = replyingTo?.let {
    {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feeds_replying_to_comment),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCancelReply, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.feeds_cancel_reply),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
