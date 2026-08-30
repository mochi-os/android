// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Collapses the runs of whitespace a markdown body carries into single spaces. */
private fun flatten(text: String): String = text.replace(Regex("\\s+"), " ").trim()

/**
 * The strip above a composer while a reply is being written: who is being
 * answered, what they said, and a way out. Goes in [ComposeBar]'s `banner`
 * slot.
 *
 * The labels are the caller's because `lib` carries no translated strings and
 * every app already has its own - the same contract [ComposeBarAttachments]
 * keeps.
 *
 * @param label Who is being replied to, e.g. "Replying to Ada".
 * @param preview What they said. Whitespace is flattened and the line is cut
 *   short rather than wrapped; blank leaves the line out, which is what an
 *   attachment-only message wants unless the caller words one.
 * @param cancelLabel Description of the cancel button, for accessibility.
 * @param onCancel Drops the reply and returns the composer to the thread.
 * @param modifier Modifier for the strip.
 */
@Composable
fun ReplyComposerBanner(
    label: String,
    preview: String,
    cancelLabel: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flattened = flatten(preview)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (flattened.isNotEmpty()) {
                Text(
                    text = flattened,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MochiIconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = cancelLabel)
        }
    }
}
