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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.R

/**
 * Grid of image attachments in the conventional Mochi layout: one full-width,
 * two side by side, or a large image over a row of up to `maxDisplay` cells
 * with a "+N" overlay. `thumbnailUrls` (same order as `urls`) is used when
 * present.
 */
@Composable
fun MediaGrid(
    urls: List<String>,
    onClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailUrls: List<String>? = null,
    contentDescriptions: List<String>? = null,
    // Per-image captions (same length / order as `urls`); an empty or absent
    // entry draws no scrim. The "+N" overlay cell keeps its count instead —
    // the caption is still in the lightbox, the count is nowhere else.
    captions: List<String>? = null,
    maxDisplay: Int = 4,
) {
    if (urls.isEmpty()) return

    val displayCount = minOf(urls.size, maxDisplay)
    val shape = RoundedCornerShape(10.dp)

    fun thumbOrUrl(i: Int): String = thumbnailUrls?.getOrNull(i) ?: urls[i]
    fun describe(i: Int): String? = contentDescriptions?.getOrNull(i)
    fun captionFor(i: Int): String? = captions?.getOrNull(i)?.takeIf { it.isNotEmpty() }

    when (displayCount) {
        1 -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(shape)
                    .clickable { onClick(0) }
            ) {
                AttachmentImage(
                    model = thumbOrUrl(0),
                    contentDescription = describe(0),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                captionFor(0)?.let { AttachmentCaptionScrim(it, Modifier.align(Alignment.BottomCenter)) }
            }
        }
        2 -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier.fillMaxWidth()
            ) {
                for (i in 0 until 2) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .clip(shape)
                            .clickable { onClick(i) }
                    ) {
                        AttachmentImage(
                            model = thumbOrUrl(i),
                            contentDescription = describe(i),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        captionFor(i)?.let { AttachmentCaptionScrim(it, Modifier.align(Alignment.BottomCenter)) }
                    }
                }
            }
        }
        else -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(shape)
                        .clickable { onClick(0) }
                ) {
                    AttachmentImage(
                        model = thumbOrUrl(0),
                        contentDescription = describe(0),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    captionFor(0)?.let { AttachmentCaptionScrim(it, Modifier.align(Alignment.BottomCenter)) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 1 until displayCount) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp)
                                .clip(shape)
                                .clickable { onClick(i) }
                        ) {
                            AttachmentImage(
                                model = thumbOrUrl(i),
                                contentDescription = describe(i),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            val overflow = i == displayCount - 1 && urls.size > maxDisplay
                            if (overflow) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.media_grid_more_count,
                                            urls.size - maxDisplay
                                        ),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            } else {
                                captionFor(i)?.let {
                                    AttachmentCaptionScrim(it, Modifier.align(Alignment.BottomCenter))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The caption drawn over a media cell's bottom edge, on a fading scrim. Public
 * so app modules' own tile layouts render it identically.
 */
@Composable
fun AttachmentCaptionScrim(caption: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                )
            )
    ) {
        Text(
            text = caption,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
