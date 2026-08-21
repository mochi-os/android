// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import org.mochios.android.R

/**
 * Fullscreen image lightbox: swipe between images, pinch-zoom each one,
 * swipe down (vertical pan when not zoomed in) to dismiss.
 *
 * Hosted in a `Dialog` so it sits above the whole UI without needing a
 * navigation entry. Use `onDismiss` to clear the surrounding open-state.
 *
 * The comments slot: given [comments], the top chrome carries a comments
 * button with the current image's [commentCount], and pressing it opens a
 * panel below the image showing whatever the caller composes for that
 * image - the app's own thread and composer, not something the lightbox
 * invents. Whether the panel is open is remembered per user across
 * lightboxes; [commentsInitiallyOpen] forces it open for one showing (a
 * comment's chip that opened the lightbox on its image).
 */
@Composable
fun LightboxScreen(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    // Per-image captions (same length / order as `images`); an empty or
    // absent entry shows none. Shares the bottom chrome with the position
    // counter so the two never fight for the same edge.
    captions: List<String> = emptyList(),
    commentCount: ((index: Int) -> Int)? = null,
    comments: (@Composable (index: Int) -> Unit)? = null,
    commentsInitiallyOpen: Boolean = false,
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val hasComments = comments != null
    var commentsOpen by remember(hasComments) {
        mutableStateOf(hasComments && (commentsInitiallyOpen || rememberedCommentsOpen(context)))
    }
    val toggleComments = {
        val next = !commentsOpen
        commentsOpen = next
        rememberCommentsOpen(context, next)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialIndex,
            pageCount = { images.size }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    ZoomableImage(
                        url = images[page],
                        onDismiss = onDismiss
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasComments) {
                        val count = commentCount?.invoke(pagerState.currentPage) ?: 0
                        MochiIconButton(onClick = toggleComments) {
                            BadgedBox(
                                badge = {
                                    if (count > 0) Badge { Text(count.toString()) }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Comment,
                                    contentDescription = stringResource(R.string.lightbox_comments),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    MochiIconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = Color.White
                        )
                    }
                }

                val caption = captions.getOrNull(pagerState.currentPage)?.takeIf { it.isNotEmpty() }
                if (caption != null || images.size > 1) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        if (caption != null) {
                            Text(
                                text = caption,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        if (images.size > 1) {
                            Text(
                                text = stringResource(
                                    R.string.lightbox_position,
                                    pagerState.currentPage + 1,
                                    images.size
                                ),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (commentsOpen && comments != null) {
                // The panel takes up to a little over half the screen; the
                // image keeps the rest, so both stay in view.
                val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxPanelHeight),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.lightbox_comments),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            MochiIconButton(onClick = toggleComments) {
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = stringResource(R.string.common_close)
                                )
                            }
                        }
                        comments(pagerState.currentPage)
                    }
                }
            }
        }
    }
}

private const val LIGHTBOX_PREFERENCES = "mochi_lightbox"
private const val COMMENTS_OPEN = "comments"

/** Whether the user last left the comments panel open - a per-user preference, kept locally. */
private fun rememberedCommentsOpen(context: Context): Boolean =
    context.getSharedPreferences(LIGHTBOX_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(COMMENTS_OPEN, false)

private fun rememberCommentsOpen(context: Context, open: Boolean) {
    context.getSharedPreferences(LIGHTBOX_PREFERENCES, Context.MODE_PRIVATE)
        .edit().putBoolean(COMMENTS_OPEN, open).apply()
}

@Composable
private fun ZoomableImage(
    url: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dismissProgress by remember { mutableFloatStateOf(0f) }
    // Full-size images can take many seconds on a slow connection, and the
    // page is otherwise pure black — show a spinner while loading and a
    // broken-image glyph when the fetch fails.
    var state by remember(url) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    if (scale > 1f) {
                        offset = Offset(
                            x = offset.x + pan.x,
                            y = offset.y + pan.y
                        )
                    } else {
                        val newY = offset.y + pan.y
                        dismissProgress = newY / 500f
                        if (kotlin.math.abs(dismissProgress) > 0.5f) {
                            onDismiss()
                        } else {
                            offset = Offset(x = offset.x, y = newY)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { state = it },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    alpha = 1f - kotlin.math.abs(dismissProgress) * 0.5f
                }
        )
        when (state) {
            is AsyncImagePainter.State.Error -> Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(64.dp)
            )

            is AsyncImagePainter.State.Success -> {}

            else -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
