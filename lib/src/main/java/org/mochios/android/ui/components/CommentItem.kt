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
import androidx.compose.material3.TextButton
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
 * A single comment row: header (avatar · name · time · edited), body,
 * attachments, and a trailing [actions] row. Nested replies ([depth] > 0) are
 * indented and marked with a coloured vertical thread bar whose colour cycles by
 * depth.
 *
 * The actions row is a slot because each feature reacts differently — feeds
 * renders an emoji `ReactionBar`, forums renders up/down votes — while the
 * header, body, and threading are identical everywhere.
 *
 * An anchored comment - one about a particular image of the post - carries a
 * chip after the time: the image's thumbnail, with its caption as text when it
 * has one (a bare file name is not shown; readers care what an image is,
 * rarely what it was called). Tapping the chip runs [onOpenAnchor], which the
 * host uses to open the lightbox on that image with the comments showing.
 *
 * @param seed              Stable value the avatar's fallback initials colour from.
 * @param horizontalPadding Leading/trailing inset; pass 0 when the host already
 *                          provides horizontal padding.
 * @param anchorThumbnailUrl The anchored image's thumbnail; null when unanchored.
 * @param anchorCaption      Its caption, shown as the chip's text when non-empty.
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
