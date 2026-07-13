// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.model.Attachment

/**
 * A single comment row: header (avatar · name · time · edited), body,
 * attachments, and a trailing [actions] row. Nested replies ([depth] > 0) are
 * indented and marked with a coloured vertical thread bar whose colour cycles by
 * depth.
 *
 * The actions row is a slot because each feature reacts differently — feeds
 * renders an emoji `ReactionBar`, forums renders up/down votes — while the
 * header, body, and threading are identical everywhere.
 *
 * @param seed              Stable value the avatar's fallback initials colour from.
 * @param horizontalPadding Leading/trailing inset; pass 0 when the host already
 *                          provides horizontal padding.
 */
@Composable
fun CommentItem(
    name: String,
    body: String,
    created: Long,
    depth: Int,
    seed: String,
    attachments: List<Attachment>,
    attachmentUrl: (Attachment) -> String,
    attachmentThumbnailUrl: (Attachment) -> String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    edited: Long = 0,
    isEditing: Boolean = false,
    editText: String = "",
    onEditTextChange: (String) -> Unit = {},
    onSaveEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    horizontalPadding: Dp = 16.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Replies are indented and marked with a coloured vertical bar so nested
    // conversations read as connected threads; the colour cycles by depth.
    val threadPalette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )
    val anonymous = stringResource(R.string.comment_anonymous)
    val displayName = name.ifEmpty { anonymous }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        if (depth > 0) {
            Spacer(
                modifier = Modifier.width(
                    horizontalPadding + 16.dp * (depth - 1).coerceAtMost(4)
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(threadPalette[(depth - 1) % threadPalette.size])
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else if (horizontalPadding > 0.dp) {
            Spacer(modifier = Modifier.width(horizontalPadding))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = horizontalPadding, top = 8.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityAvatar(
                    name = displayName,
                    src = avatarUrl,
                    seed = seed,
                    size = 20.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = LocalFormat.current.formatRelativeTime(created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (edited > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.comment_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = onEditTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancelEdit) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(onClick = onSaveEdit) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            } else {
                HtmlContent(
                    html = body,
                    modifier = Modifier.fillMaxWidth()
                )

                if (attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AttachmentGallery(
                        attachments = attachments,
                        urlBuilder = attachmentUrl,
                        thumbnailUrlBuilder = attachmentThumbnailUrl,
                        compact = true
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}
