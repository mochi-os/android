// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import org.mochios.market.R

/**
 * Photo carousel for listing detail. A full-width [HorizontalPager] over
 * the listing's photos with a thumbnails strip underneath; tapping any
 * thumbnail jumps the pager to that index and tapping the main image
 * fires [onPhotoTap] (the parent screen decides whether to open a
 * lightbox).
 *
 * @param photoUrls Absolute URLs for each photo, in display order.
 * @param onPhotoTap Invoked with the page index when the main image is tapped.
 */
@Composable
fun PhotoCarousel(
    photoUrls: List<String>,
    modifier: Modifier = Modifier,
    onPhotoTap: (Int) -> Unit = {},
) {
    if (photoUrls.isEmpty()) {
        EmptyCarouselPlaceholder(modifier = modifier)
        return
    }

    val pagerState = rememberPagerState(pageCount = { photoUrls.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) { page ->
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPhotoTap(page) },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photoUrls[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(
                        R.string.market_carousel_main, page + 1, photoUrls.size,
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (photoUrls.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            ) {
                itemsIndexed(photoUrls) { index, url ->
                    val isCurrent = pagerState.currentPage == index
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(
                                R.string.market_carousel_thumbnail, index + 1,
                            ),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(photoUrls) {
        if (pagerState.currentPage >= photoUrls.size && photoUrls.isNotEmpty()) {
            pagerState.scrollToPage(0)
        }
    }
}

@Composable
private fun EmptyCarouselPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ImageNotSupported,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Suppress("unused")
private fun Modifier.translucent(): Modifier = background(Color.Black.copy(alpha = 0.4f))
