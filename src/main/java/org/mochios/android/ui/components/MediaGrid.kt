package org.mochi.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochi.android.R

/**
 * Grid of image attachments using the conventional Mochi layout:
 *  - 1 image: full-width, 200dp tall, fillCrop, rounded.
 *  - 2 images: side-by-side, weight 1f each, 160dp tall.
 *  - 3+ images: large image on top (160dp), remaining capped at `maxDisplay`
 *    laid out in a row of equal-weight cells (100dp tall) below; if there are
 *    more than `maxDisplay` images, the bottom-right cell shows a "+N" overlay.
 *
 * `urls` is the list of fully-resolved image URLs (already authenticated).
 * Optionally pass `thumbnailUrls` (same length / order as `urls`) to use
 * lower-resolution images for the grid; falls back to `urls` if `null`.
 *
 * `onClick` receives the index into `urls` of the tapped image (useful for
 * driving a lightbox).
 */
@Composable
fun MediaGrid(
    urls: List<String>,
    onClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailUrls: List<String>? = null,
    contentDescriptions: List<String>? = null,
    maxDisplay: Int = 4,
) {
    if (urls.isEmpty()) return

    val displayCount = minOf(urls.size, maxDisplay)
    val shape = RoundedCornerShape(10.dp)

    fun thumbOrUrl(i: Int): String = thumbnailUrls?.getOrNull(i) ?: urls[i]
    fun describe(i: Int): String? = contentDescriptions?.getOrNull(i)

    when (displayCount) {
        1 -> {
            AsyncImage(
                model = thumbOrUrl(0),
                contentDescription = describe(0),
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(shape)
                    .clickable { onClick(0) }
            )
        }
        2 -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier.fillMaxWidth()
            ) {
                for (i in 0 until 2) {
                    AsyncImage(
                        model = thumbOrUrl(i),
                        contentDescription = describe(i),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .clip(shape)
                            .clickable { onClick(i) }
                    )
                }
            }
        }
        else -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier
            ) {
                AsyncImage(
                    model = thumbOrUrl(0),
                    contentDescription = describe(0),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(shape)
                        .clickable { onClick(0) }
                )
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
                            AsyncImage(
                                model = thumbOrUrl(i),
                                contentDescription = describe(i),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (i == displayCount - 1 && urls.size > maxDisplay) {
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
                            }
                        }
                    }
                }
            }
        }
    }
}
