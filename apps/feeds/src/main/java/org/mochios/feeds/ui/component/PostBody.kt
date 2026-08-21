// Copyright © 2026 Mochisoft OÜ
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
 * Renders a post's body. An RSS body is `title \n\n description \n\n link`; the
 * title is drawn as its own bold line and stripped from the Markwon remainder.
 * Do not wrap it in `**`: CommonMark flanking rules make the asterisks render
 * literally.
 */
@Composable
fun PostBody(
    post: Post,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    fillHeight: Boolean = false,
    passThroughTouches: Boolean = false,
    titleFontSize: TextUnit = 18.sp,
    titleBodyGap: Dp = 4.dp,
    includeTitle: Boolean = true,
    stripTrailingLink: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    var altText by remember { mutableStateOf<String?>(null) }
    val showAlt: (String) -> Unit = { altText = it }

    val rawTitle = post.data?.rss?.title.orEmpty()
    val title = rssDisplayTitle(post)
    // Body text with the RSS title line stripped when present, so the title
    // isn't repeated — whether it's rendered here or hoisted by the caller
    // (e.g. the feed card renders it above the byline via [PostTitle]).
    val bodyContent = run {
        val withoutTitle = if (title != null) {
            post.body.substring(rawTitle.length).trimStart('\n', ' ')
        } else {
            post.body
        }
        // For RSS posts the body ends with the raw article link. The feed card
        // drops it for a cleaner preview — the title and body still open the
        // article — while the detail screen keeps it.
        val rssLink = post.data?.rss?.link.orEmpty()
        if (stripTrailingLink && rssLink.isNotEmpty() && withoutTitle.endsWith(rssLink)) {
            withoutTitle.substring(0, withoutTitle.length - rssLink.length).trimEnd('\n', ' ')
        } else {
            withoutTitle
        }
    }
    val truncated = fillHeight || maxLines != Int.MAX_VALUE

    if (title == null || !includeTitle) {
        if (bodyContent.isNotEmpty()) {
            HtmlContent(
                html = bodyContent,
                modifier = modifier,
                maxLines = maxLines,
                passThroughTouches = passThroughTouches,
                clampToBoundedHeight = fillHeight,
                onClick = onClick,
                onImageLongPress = showAlt,
            )
        }
    } else {
        Column(modifier = modifier) {
            PostTitle(
                title = title,
                fontSize = titleFontSize,
                truncated = truncated,
                onClick = onClick,
            )
            if (bodyContent.isNotEmpty()) {
                Spacer(modifier = Modifier.height(titleBodyGap))
                HtmlContent(
                    html = bodyContent,
                    modifier = if (fillHeight) {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    maxLines = maxLines,
                    passThroughTouches = passThroughTouches,
                    clampToBoundedHeight = fillHeight,
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

/**
 * The RSS title to render as a heading, or null when the body does not start
 * with it (a plain post).
 */
fun rssDisplayTitle(post: Post): String? {
    val rawTitle = post.data?.rss?.title.orEmpty()
    val title = rawTitle.trim()
    return if (title.isNotEmpty() && post.body.startsWith(rawTitle)) title else null
}

@Composable
fun PostTitle(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    truncated: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        maxLines = if (truncated) 3 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    )
}
