// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mochios.android.ui.components.HtmlContent
import org.mochios.feeds.model.Post
import org.mochios.android.R as MochiR

/**
 * Renders a post's body.
 *
 * For RSS-source posts the stored body is `title \n\n description \n\n link`.
 * The leading title is rendered as its own bold, slightly-larger line and
 * stripped from the Markwon-rendered remainder. We do NOT wrap it in `**…**`:
 * Markwon parses that as CommonMark strong-emphasis, which only renders bold
 * when the markers satisfy the flanking rules — a title with a trailing space
 * or surrounding punctuation breaks them, so the asterisks render literally.
 * Bolding structurally avoids that and matches the web client, which shows the
 * RSS title as a separate `text-lg font-semibold` element.
 *
 * Long-pressing an image shows its alt/title text (e.g. a web comic's
 * punchline) in a dialog.
 */
@Composable
fun PostBody(
    post: Post,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    titleFontSize: TextUnit = 18.sp,
    titleBodyGap: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
) {
    var altText by remember { mutableStateOf<String?>(null) }
    val showAlt: (String) -> Unit = { altText = it }

    val rawTitle = post.data?.rss?.title.orEmpty()
    val title = rawTitle.trim()
    val body = post.body
    val hasTitle = title.isNotEmpty() && body.startsWith(rawTitle)

    if (!hasTitle) {
        HtmlContent(
            html = body,
            modifier = modifier,
            maxLines = maxLines,
            onClick = onClick,
            onImageLongPress = showAlt,
        )
    } else {
        val rest = body.substring(rawTitle.length).trimStart('\n', ' ')
        val truncated = maxLines != Int.MAX_VALUE
        val titleModifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

        Column(modifier = modifier) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontSize = titleFontSize,
                maxLines = if (truncated) 3 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier,
            )
            if (rest.isNotEmpty()) {
                Spacer(modifier = Modifier.height(titleBodyGap))
                HtmlContent(
                    html = rest,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = maxLines,
                    onClick = onClick,
                    onImageLongPress = showAlt,
                )
            }
        }
    }

    altText?.let { text ->
        AlertDialog(
            onDismissRequest = { altText = null },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { altText = null }) {
                    Text(stringResource(MochiR.string.common_close))
                }
            },
        )
    }
}
