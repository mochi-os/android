// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.ComposeBarAttachments
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.ReplyComposerBanner
import org.mochios.android.util.NaturalCompare
import org.mochios.feeds.R
import org.mochios.feeds.model.PublishAccount
import org.mochios.android.R as MochiR

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

/**
 * The composer's banner slot holding both strips: which hosted account the
 * comment also goes out from, and what it replies to. Null when neither shows.
 */
internal fun feedsComposerBanner(
    replyFrom: (@Composable () -> Unit)?,
    reply: (@Composable () -> Unit)?,
): (@Composable () -> Unit)? = when {
    replyFrom == null -> reply
    reply == null -> replyFrom
    else -> {
        {
            Column {
                replyFrom()
                reply()
            }
        }
    }
}

/**
 * Which of the user's hosted fediverse accounts a comment also goes out from,
 * as a reply to the post's copy there; "No account" sends it to the feed alone.
 * Null when the user has none or the post has no such copy.
 */
internal fun replyFromBanner(
    accounts: List<PublishAccount>,
    shown: Boolean,
    chosen: String,
    onChoose: (String) -> Unit,
): (@Composable () -> Unit)? = if (!shown || accounts.isEmpty()) null else {
    {
        var open by remember { mutableStateOf(false) }
        val none = stringResource(R.string.feeds_reply_from_none)
        val label = accounts.firstOrNull { it.id == chosen }?.identifier ?: none
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.feeds_reply_from),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                MochiTextButton(onClick = { open = true }) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                MochiDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    MochiDropdownMenuItem(
                        text = { Text(none) },
                        onClick = {
                            open = false
                            onChoose("")
                        },
                    )
                    accounts.sortedWith(compareBy(NaturalCompare) { it.identifier }).forEach { account ->
                        MochiDropdownMenuItem(
                            text = { Text(account.identifier) },
                            onClick = {
                                open = false
                                onChoose(account.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The owner's confirmation before muting, blocking or reporting the person on
 * another network behind a comment; a report carries the reason given.
 */
@Composable
internal fun CommenterDialog(
    action: String,
    author: String,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    when (action) {
        "mute" -> MochiAlertDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.feeds_mute_commenter_title, author),
            text = stringResource(R.string.feeds_mute_commenter_message),
            confirmText = stringResource(R.string.feeds_mute),
            onConfirm = { onConfirm("") },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
        "block" -> MochiAlertDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.feeds_block_commenter_title, author),
            text = stringResource(R.string.feeds_block_commenter_message),
            confirmText = stringResource(R.string.feeds_block),
            onConfirm = { onConfirm("") },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
        else -> MochiAlertDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.feeds_report_commenter_title, author),
            content = {
                Column {
                    Text(
                        text = stringResource(R.string.feeds_report_commenter_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MochiTextField(
                        value = reason,
                        onValueChange = { reason = it.take(2000) },
                        label = { Text(stringResource(R.string.feeds_report_reason)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            },
            confirmText = stringResource(R.string.feeds_report),
            onConfirm = { onConfirm(reason) },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}
