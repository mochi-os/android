// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 *
 * A comment with replies collapses, as on the web: [onToggleCollapse] given, a
 * tap on the avatar or the header line folds its [replies] away, and a tap
 * anywhere on the folded row opens them again. The bar beside a reply sits
 * under its parent's avatar and is that comment's thread line, so a tap on it
 * calls [onCollapseParent]. The host keeps which comments are folded and
 * leaves their replies out of the list.
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
    // Opens the author's page, for a comment by a person on another network.
    onName: (() -> Unit)? = null,
    // Whether the replies are folded away, and how many there are at every
    // depth, for the folded row's summary.
    collapsed: Boolean = false,
    replies: Int = 0,
    // Folds or opens this comment's replies; null for a comment without any.
    onToggleCollapse: (() -> Unit)? = null,
    // Folds the comment this one replies to, from the thread line beside it.
    onCollapseParent: (() -> Unit)? = null,
    // The comment a thread is opened on, as a shared link opens it.
    highlighted: Boolean = false,
    // A short mark after the name, such as the post author's in a forum.
    badge: String? = null,
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
    val folded = collapsed && onToggleCollapse != null
    val collapseLabel = stringResource(R.string.common_collapse)
    val expandLabel = stringResource(R.string.common_expand)
    val highlight = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        if (depth > 0) {
            val lead = horizontalPadding + 16.dp * (depth - 1).coerceAtMost(4)
            // The bar is 2dp; its touch area reaches a little to either side,
            // ending where the comment's own content begins.
            val reach = minOf(lead, 4.dp)
            Spacer(modifier = Modifier.width(lead - reach))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(reach + 12.dp)
                    .then(
                        if (onCollapseParent != null) {
                            Modifier.clickable(
                                onClickLabel = collapseLabel,
                                role = Role.Button,
                                onClick = onCollapseParent,
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = reach)
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(threadPalette[(depth - 1) % threadPalette.size])
                )
            }
        } else if (horizontalPadding > 0.dp) {
            Spacer(modifier = Modifier.width(horizontalPadding))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = horizontalPadding, top = 8.dp, bottom = 8.dp)
                .then(
                    if (highlighted) {
                        Modifier
                            .clip(shape)
                            .background(highlight.copy(alpha = 0.1f))
                            .border(1.dp, highlight.copy(alpha = 0.3f), shape)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // The avatar and the header line fold the replies away; the
                // name and the image chip keep their own taps. Folded, the
                // row is all there is, and a tap on it opens it again.
                modifier = if (onToggleCollapse != null && !isEditing) {
                    Modifier.then(if (folded) Modifier.fillMaxWidth() else Modifier).clickable(
                        onClickLabel = if (folded) expandLabel else collapseLabel,
                        onClick = onToggleCollapse,
                    )
                } else {
                    Modifier
                },
            ) {
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
                    fontWeight = FontWeight.SemiBold,
                    color = if (folded) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    modifier = if (onName != null) Modifier.clickable(onClick = onName) else Modifier,
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = highlight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(highlight.copy(alpha = 0.1f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = LocalFormat.current.formatRelativeTime(created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (folded) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.comment_collapsed_replies,
                            replies,
                            LocalFormat.current.formatNumber(replies),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = highlight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!folded && edited > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.comment_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!folded && anchorThumbnailUrl != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AnchorChip(
                        thumbnailUrl = anchorThumbnailUrl,
                        caption = anchorCaption,
                        onClick = onOpenAnchor,
                    )
                }
            }

            if (folded) return@Column

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
        MochiImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
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

/**
 * Which comments of a thread are folded away. Saved with the screen, so a
 * rotation keeps them folded.
 */
@Stable
class CollapsedComments(initial: Set<String> = emptySet()) {
    var ids by mutableStateOf(initial)
        private set

    operator fun contains(id: String): Boolean = id in ids

    /** Fold [id]'s replies away, or open them again. */
    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
    }

    /** Fold [id]'s replies away. */
    fun collapse(id: String) {
        ids = ids + id
    }
}

/** A [CollapsedComments] that lives as long as the screen, rotations included. */
@Composable
fun rememberCollapsedComments(): CollapsedComments = rememberSaveable(
    saver = listSaver(save = { it.ids.toList() }, restore = { CollapsedComments(it.toSet()) }),
) { CollapsedComments() }

/**
 * One row of a thread flattened for a LazyColumn.
 *
 * @property depth How deep the comment sits, 0 for one on the post.
 * @property parent The id of the comment it replies to, null at the top.
 * @property replies Its replies at every depth.
 */
data class CommentRow<T>(val comment: T, val depth: Int, val parent: String?, val replies: Int)

/**
 * A thread in reading order, each comment followed by its replies, leaving out
 * the replies of every comment in [collapsed].
 */
fun <T> commentRows(
    roots: List<T>,
    id: (T) -> String,
    children: (T) -> List<T>,
    collapsed: Set<String> = emptySet(),
): List<CommentRow<T>> {
    val rows = mutableListOf<CommentRow<T>>()
    fun walk(comments: List<T>, depth: Int, parent: String?) {
        for (comment in comments) {
            val key = id(comment)
            rows.add(CommentRow(comment, depth, parent, replyCount(comment, children)))
            if (key !in collapsed) walk(children(comment), depth + 1, key)
        }
    }
    walk(roots, 0, null)
    return rows
}

/** A comment's replies at every depth. */
fun <T> replyCount(comment: T, children: (T) -> List<T>): Int =
    children(comment).sumOf { 1 + replyCount(it, children) }

/**
 * The comment [target] names and its replies, wherever it sits in the thread;
 * null when it is not there.
 */
fun <T> findInThread(roots: List<T>, target: String, id: (T) -> String, children: (T) -> List<T>): T? {
    for (comment in roots) {
        if (id(comment) == target) return comment
        findInThread(children(comment), target, id, children)?.let { return it }
    }
    return null
}

/**
 * Above a thread opened on one comment: back to the whole discussion.
 */
@Composable
fun CommentThreadBack(onClick: () -> Unit, modifier: Modifier = Modifier) {
    MochiTextButton(onClick = onClick, modifier = modifier, tone = MochiButtonTone.Primary) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.comment_all))
    }
}
