// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.R

/** The touch target each action gets, and the glyph inside it. */
private val ACTION_SIZE = 32.dp
private val ICON_SIZE = 18.dp

/**
 * What can be done to one comment, in the order every thread in the suite
 * shows them: react, reply, edit, delete. An action with no callback is left
 * out, so a viewer who may not edit sees no pencil.
 *
 * The labels come from `lib`, which already carries all three, so a thread does
 * not have to supply its own words for the same four things.
 *
 * @param onReply Answer this comment.
 * @param onEdit Rewrite it. Null for anyone who may not.
 * @param onDelete Remove it. Null for anyone who may not.
 * @param modifier Modifier for the row.
 * @param reactions The reaction pills and their add button, for a thread whose
 *   app has reactions. Drawn first, as the widest of the four.
 */
@Composable
fun CommentActions(
    onReply: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    reactions: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        reactions?.invoke()
        if (onReply != null) {
            CommentAction(
                icon = Icons.AutoMirrored.Outlined.Reply,
                contentDescription = stringResource(R.string.comment_reply),
                onClick = onReply,
            )
        }
        if (onEdit != null) {
            CommentAction(
                icon = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.common_edit),
                onClick = onEdit,
            )
        }
        if (onDelete != null) {
            CommentAction(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.common_delete),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun CommentAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    MochiIconButton(onClick = onClick, modifier = Modifier.size(ACTION_SIZE)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
