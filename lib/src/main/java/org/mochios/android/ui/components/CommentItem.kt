// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.model.Attachment

/**
 * A single comment row: header, body, attachments, and a trailing [actions]
 * slot each feature fills differently. Nested replies ([depth] > 0) indent
 * behind a depth-coloured thread bar; an anchored comment shows its image as a
 * chip.
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
    anchorThumbnailUrl: String? = null,
    anchorCaption: String = "",
    onOpenAnchor: (() -> Unit)? = null,
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
                if (anchorThumbnailUrl != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AnchorChip(
                        thumbnailUrl = anchorThumbnailUrl,
                        caption = anchorCaption,
                        onClick = onOpenAnchor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isEditing) {
                MochiTextField(
                    value = editText,
                    onValueChange = onEditTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    MochiTextButton(onClick = onCancelEdit) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    MochiTextButton(onClick = onSaveEdit) {
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

/**
 * The anchored image, small, beside a comment's time: its thumbnail and, when
 * it has one, its caption. Tappable when the host can open the image.
 */
@Composable
private fun AnchorChip(
    thumbnailUrl: String,
    caption: String,
    onClick: (() -> Unit)?,
) {
    val description = stringResource(R.string.comment_anchor_open)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description }
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        if (caption.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 128.dp)
            )
        }
    }
}
