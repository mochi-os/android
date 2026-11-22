// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.mochios.android.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.theme.LocalSpacing

/**
 * Fullscreen image lightbox: swipe between images, pinch-zoom, swipe down to
 * dismiss. Hosted in a `Dialog`, so it needs no navigation entry. The comments
 * panel's open state is remembered unless [commentsInitiallyOpen] forces it.
 * Each of [images] is a Coil model: an [org.mochios.android.api.OwnedAddress],
 * fetched with a credential, or a URL fetched without.
 */
@Composable
fun LightboxScreen(
    images: List<Any>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    // Per-image captions (same length / order as `images`); an empty or
    // absent entry shows none. Shares the bottom chrome with the position
    // counter so the two never fight for the same edge.
    captions: List<String> = emptyList(),
    // What a screen reader announces for each image (same order); an empty or
    // absent entry falls back to its caption.
    descriptions: List<String> = emptyList(),
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
    val spacing = LocalSpacing.current

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
                        description = descriptions.getOrNull(page)?.takeIf { it.isNotEmpty() }
                            ?: captions.getOrNull(page)?.takeIf { it.isNotEmpty() },
                        onDismiss = onDismiss
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(spacing.gap(16.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasComments) {
                        val count = commentCount?.invoke(pagerState.currentPage) ?: 0
                        MochiIconButton(onClick = toggleComments) {
                            BadgedBox(
                                badge = {
                                    if (count > 0) Badge { Text(LocalFormat.current.formatNumber(count)) }
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
                        verticalArrangement = Arrangement.spacedBy(spacing.gap(8.dp)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(spacing.gap(16.dp))
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
                                    .padding(horizontal = spacing.gap(12.dp), vertical = spacing.gap(6.dp))
                            )
                        }
                        if (images.size > 1) {
                            Text(
                                text = stringResource(
                                    R.string.lightbox_position,
                                    LocalFormat.current.formatNumber(pagerState.currentPage + 1),
                                    LocalFormat.current.formatNumber(images.size)
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
                                .padding(start = spacing.gap(16.dp), end = spacing.gap(4.dp)),
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

/**
 * Where a lightbox image sits under the reader's fingers: its zoom, its pan and
 * how far a swipe down has taken it towards closing. Each gesture moves it;
 * [settle] brings it back to rest when the fingers lift, so a swipe that stops
 * short of closing, or a pinch below full size, does not leave the image
 * shifted, dimmed or shrunk.
 */
@Stable
internal class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** How far a swipe down has gone, as a fraction of [DISMISS_DISTANCE]. */
    var dismissProgress by mutableFloatStateOf(0f)
        private set

    /** The page's size, which bounds how far a zoomed image may be panned. */
    var size: IntSize = IntSize.Zero

    /** Whether the swipe, as it stands, closes the lightbox when the finger lifts. */
    val dismissing: Boolean get() = abs(dismissProgress) > DISMISS_THRESHOLD

    /** A pinch, or a pan while zoomed in. */
    fun transform(zoom: Float, pan: Offset) {
        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        if (scale > 1f) offset = panned(offset + pan, scale, size)
    }

    /** A swipe down (or up) at full size, carrying the image with the finger. */
    fun drag(distance: Float) {
        offset = Offset(x = offset.x, y = offset.y + distance)
        dismissProgress = offset.y / DISMISS_DISTANCE
    }

    /** Brings the image back to rest: full size at least, centred unless zoomed, the swipe undone. */
    suspend fun settle() {
        val fromScale = scale
        val fromOffset = offset
        val fromProgress = dismissProgress
        val toScale = fromScale.coerceAtLeast(1f)
        val toOffset = if (toScale <= 1f) Offset.Zero else panned(fromOffset, toScale, size)
        if (fromScale == toScale && fromOffset == toOffset && fromProgress == 0f) return
        animate(0f, 1f) { fraction, _ ->
            scale = fromScale + (toScale - fromScale) * fraction
            offset = fromOffset + (toOffset - fromOffset) * fraction
            dismissProgress = fromProgress * (1f - fraction)
        }
    }

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 5f

        /** The swipe, in pixels, that would take the image fully away. */
        const val DISMISS_DISTANCE = 500f

        /** The share of [DISMISS_DISTANCE] past which lifting the finger closes. */
        const val DISMISS_THRESHOLD = 0.5f
    }
}

/**
 * [offset] held so an image zoomed to [scale] still covers a page of [size]:
 * the scaled image overhangs each edge by half its growth, and no further pan
 * than that is allowed, so the image cannot be dragged off the page.
 */
internal fun panned(offset: Offset, scale: Float, size: IntSize): Offset {
    val x = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
    val y = (size.height * (scale - 1f) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
}

@Composable
internal fun ZoomableImage(
    url: Any,
    description: String?,
    onDismiss: () -> Unit,
    zoom: ZoomState = remember { ZoomState() },
) {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    var settling by remember { mutableStateOf<Job?>(null) }
    // Full-size images can take many seconds on a slow connection, and the
    // page is otherwise pure black: a spinner shows while it loads, and the
    // reason when it fails, as on the tile it was opened from.
    var state by remember(url) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val load = rememberAttachmentLoad(url)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size -> zoom.size = size }
            .pointerInput(zoom) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // A finger put down mid-settle takes the image from where it is.
                    settling?.cancel()
                    var travel = Offset.Zero
                    var verticalDrag = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { change -> change.pressed }
                        if (pressed >= 2 || zoom.scale > 1f) {
                            zoom.transform(event.calculateZoom(), event.calculatePan())
                            event.changes.forEach { change -> change.consume() }
                        } else {
                            val pan = event.calculatePan()
                            if (!verticalDrag) {
                                travel += pan
                                if (travel.getDistance() > viewConfiguration.touchSlop) {
                                    // Sideways is the pager's, to the next image.
                                    if (abs(travel.x) > abs(travel.y)) {
                                        break
                                    }
                                    verticalDrag = true
                                }
                            }
                            if (verticalDrag) {
                                zoom.drag(pan.y)
                                event.changes.forEach { change -> change.consume() }
                            }
                        }
                    } while (event.changes.any { change -> change.pressed })
                    // Decided once, as the finger lifts: a swipe carried back
                    // short of the threshold before lifting does not close.
                    if (verticalDrag && zoom.dismissing) {
                        dismiss()
                    } else {
                        settling = scope.launch { zoom.settle() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        key(load.attempt) {
            AsyncImage(
                model = url,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                onState = { next ->
                    state = next
                    if (next is AsyncImagePainter.State.Error) load.fail(next.result.throwable)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                        alpha = (1f - abs(zoom.dismissProgress) * 0.5f).coerceIn(0f, 1f)
                    }
            )
        }
        val failure = load.failure
        when {
            failure != null -> AttachmentFailureNotice(
                failure = failure,
                onRetry = load::retry,
                color = Color.White,
                large = true,
            )

            state is AsyncImagePainter.State.Success -> {}

            else -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
