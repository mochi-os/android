// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.ComposeBarAttachments
import org.mochios.android.ui.components.ReplyComposerBanner
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
 *
 * @param replyingTo The comment being answered, resolved from the thread.
 * @param onCancelReply Drops the reply and returns the composer to the post.
 * @return The banner, or null when nothing is being replied to.
 */
@Composable
internal fun feedsReplyBanner(
    replyingTo: Comment?,
    onCancelReply: () -> Unit,
): (@Composable () -> Unit)? = replyingTo?.let { replied ->
    {
        ReplyComposerBanner(
            label = stringResource(
                R.string.feeds_replying_to_author,
                replied.name.ifBlank { replied.authorId },
            ),
            preview = replied.markdownSource.ifBlank { replied.text },
            cancelLabel = stringResource(R.string.feeds_cancel_reply),
            onCancel = onCancelReply,
        )
    }
}

/**
 * The comment with this id, wherever it sits in the thread.
 *
 * @param comments The thread roots to search.
 * @param id The comment being looked for, or null.
 * @return The comment, or null when there is no id or it has gone.
 */
internal fun findComment(comments: List<Comment>, id: String?): Comment? {
    if (id == null) return null
    for (comment in comments) {
        if (comment.id == id) return comment
        val found = findComment(comment.children, id)
        if (found != null) return found
    }
    return null
}
