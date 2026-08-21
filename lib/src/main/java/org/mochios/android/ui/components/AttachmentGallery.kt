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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.model.Attachment
import org.mochios.android.model.FileKind
import org.mochios.android.util.AttachmentOpener

/** Fixed thumbnail height for [AttachmentGallery]'s compact (comment) layout. */
private val COMPACT_IMAGE_HEIGHT = 120.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentGallery(
    attachments: List<Attachment>,
    urlBuilder: (Attachment) -> String,
    thumbnailUrlBuilder: ((Attachment) -> String)? = null,
    // Larger image variant for the full-width grid; the compact (comment)
    // layout always stays on thumbnails. Falls back to thumbnailUrlBuilder.
    previewUrlBuilder: ((Attachment) -> String)? = null,
    compact: Boolean = false,
    onDelete: ((Attachment) -> Unit)? = null,
    modifier: Modifier = Modifier,
    // The lightbox comments slot, per image attachment: the count on the
    // button and the panel's content (the app's own thread and composer).
    commentCount: ((Attachment) -> Int)? = null,
    comments: (@Composable (Attachment) -> Unit)? = null,
) {
    if (attachments.isEmpty()) return

    val images = attachments.filter { it.isImage }
    val videos = attachments.filter { it.isVideo }
    val audios = attachments.filter { it.isAudio }
    val files = attachments.filter { !it.isImage && !it.isVideo && !it.isAudio }

    var showViewer by rememberSaveable { mutableStateOf(false) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(0) }
    var showVideoUrl by remember { mutableStateOf<String?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Resolve the caller's (possibly relative) paths to absolute URLs once, for
    // every consumer. Coil could map relative image URLs itself, but the video
    // frame decoder, ExoPlayer and the file downloader aren't Coil, so resolve
    // here. Absolute URLs pass through unchanged.
    val serverUrl = rememberServerUrl()
    val resolvedUrl: (Attachment) -> String = { att ->
        resolveAttachmentUrl(serverUrl, urlBuilder(att))
    }
    val resolvedThumb: ((Attachment) -> String)? = thumbnailUrlBuilder?.let { tb ->
        { att -> resolveAttachmentUrl(serverUrl, tb(att)) }
    }
    val resolvedPreview: ((Attachment) -> String)? =
        (previewUrlBuilder ?: thumbnailUrlBuilder)?.let { pb ->
            { att -> resolveAttachmentUrl(serverUrl, pb(att)) }
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (images.isNotEmpty()) {
            // The wrap-thumbnail layout is used for tight surfaces (comments) and
            // whenever a per-item delete is offered, since a delete badge needs to
            // overlay each image (the magazine MediaGrid has no per-cell slot).
            if (compact || onDelete != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    images.forEachIndexed { index, image ->
                        Box {
                            AttachmentImage(
                                model = resolvedThumb?.let { tb -> tb(image) } ?: resolvedUrl(image),
                                contentDescription = image.caption.ifEmpty { image.name },
                                // Fixed height; width follows the image's aspect ratio.
                                contentScale = ContentScale.FillHeight,
                                modifier = Modifier
                                    .height(COMPACT_IMAGE_HEIGHT)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewerIndex = index
                                        showViewer = true
                                    }
                            )
                            if (onDelete != null) {
                                DeleteBadge(
                                    onClick = { onDelete(image) },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            } else {
                MediaGrid(
                    urls = images.map { resolvedUrl(it) },
                    thumbnailUrls = resolvedPreview?.let { pb -> images.map { pb(it) } },
                    contentDescriptions = images.map { it.caption.ifEmpty { it.name } },
                    captions = images.map { it.caption },
                    onClick = { index ->
                        viewerIndex = index
                        showViewer = true
                    }
                )
            }
        }

        for (video in videos) {
            VideoThumbnail(
                video = video,
                // The server has no video-thumbnail route, so decode a frame
                // from the video URL itself (VideoFrameDecoder in the loader).
                frameUrl = resolvedUrl(video),
                onClick = { showVideoUrl = resolvedUrl(video) },
                onDelete = onDelete?.let { cb -> { cb(video) } }
            )
        }

        for (audio in audios) {
            AudioAttachment(
                audio = audio,
                url = resolvedUrl(audio),
                onDelete = onDelete?.let { cb -> { cb(audio) } }
            )
        }

        if (files.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (file in files) {
                    FileChip(
                        attachment = file,
                        loading = downloadingId == file.id,
                        onDelete = onDelete?.let { cb -> { cb(file) } },
                        onClick = {
                            // One download at a time; ignore taps while busy.
                            if (downloadingId == null) {
                                downloadingId = file.id
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.common_opening_file),
                                    Toast.LENGTH_SHORT
                                ).show()
                                scope.launch {
                                    val result = AttachmentOpener.open(
                                        context, resolvedUrl(file), file
                                    )
                                    downloadingId = null
                                    val message = when (result) {
                                        AttachmentOpener.OpenResult.NO_APP ->
                                            R.string.common_no_app_for_file

                                        AttachmentOpener.OpenResult.FAILED ->
                                            R.string.common_file_open_failed

                                        AttachmentOpener.OpenResult.UNAVAILABLE ->
                                            R.string.common_attachment_unavailable

                                        AttachmentOpener.OpenResult.OPENED -> null
                                    }
                                    if (message != null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(message),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showViewer && images.isNotEmpty()) {
        AttachmentLightbox(
            images = images,
            urlBuilder = urlBuilder,
            initialIndex = viewerIndex,
            onDismiss = { showViewer = false },
            commentCount = commentCount,
            comments = comments,
        )
    }

    val videoUrl = showVideoUrl
    if (videoUrl != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showVideoUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                VideoPlayer(
                    url = videoUrl,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun VideoThumbnail(
    video: Attachment,
    frameUrl: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // VideoFrame routes to VideoFrameFetcher, which range-extracts the
        // opening frame and caches it in Coil's memory cache.
        AsyncImage(
            model = VideoFrame(frameUrl),
            contentDescription = video.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = stringResource(R.string.common_play_video),
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(64.dp)
        )
        if (onDelete != null) {
            DeleteBadge(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun AudioAttachment(
    audio: Attachment,
    url: String,
    onDelete: (() -> Unit)? = null
) {
    val format = LocalFormat.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${audio.name} (${format.formatFileSize(audio.size)})",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onDelete != null) {
                DeleteIcon(onClick = onDelete)
            }
        }
        AudioPlayer(
            url = url,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FileChip(
    attachment: Attachment,
    loading: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val format = LocalFormat.current
    AssistChip(
        onClick = onClick,
        label = {
            Text("${attachment.name} (${format.formatFileSize(attachment.size)})")
        },
        leadingIcon = {
            if (loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = fileKindIcon(attachment.fileKind),
                    contentDescription = null,
                    tint = fileKindTint(attachment.fileKind),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        trailingIcon = onDelete?.let { cb ->
            { DeleteIcon(onClick = cb) }
        },
        border = null
    )
}

/** A circular translucent delete button, overlaid on image/video thumbnails. */
@Composable
private fun DeleteBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.common_delete),
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
    }
}

/** A plain error-tinted delete icon button for file/audio rows. */
@Composable
private fun DeleteIcon(onClick: () -> Unit) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = stringResource(R.string.common_delete),
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    )
}

/** Representative icon for a non-media file kind. */
private fun fileKindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.PDF -> Icons.Default.PictureAsPdf
    FileKind.WORD -> Icons.AutoMirrored.Filled.Article
    FileKind.EXCEL -> Icons.Default.TableChart
    FileKind.TEXT -> Icons.AutoMirrored.Filled.TextSnippet
    FileKind.AUDIO -> Icons.Default.Audiotrack
    else -> Icons.Default.Description
}

/** Drive-style accent colour for a file kind's icon. */
@Composable
private fun fileKindTint(kind: FileKind): Color = when (kind) {
    FileKind.PDF -> Color(0xFFE53935)    // red
    FileKind.WORD -> Color(0xFF1E88E5)   // blue
    FileKind.EXCEL -> Color(0xFF2E9E50)  // green
    FileKind.TEXT -> Color(0xFF607D8B)   // blue-grey
    FileKind.AUDIO -> Color(0xFF8E24AA)  // purple
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Resolve a possibly-relative attachment path against [serverUrl]. Coil maps
 * relative image URLs itself, but the video decoder, ExoPlayer and downloader
 * do not.
 */
private fun resolveAttachmentUrl(serverUrl: String, path: String): String {
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val base = serverUrl.trimEnd('/')
    return if (path.startsWith("/")) "$base$path" else "$base/$path"
}

/**
 * Lightbox over a post's image attachments. A screen can show it directly to
 * open on one attachment, since the gallery may sit in a lazy item that has
 * scrolled out of composition.
 */
@Composable
fun AttachmentLightbox(
    images: List<Attachment>,
    urlBuilder: (Attachment) -> String,
    initialIndex: Int,
    onDismiss: () -> Unit,
    commentCount: ((Attachment) -> Int)? = null,
    comments: (@Composable (Attachment) -> Unit)? = null,
    commentsInitiallyOpen: Boolean = false,
) {
    val serverUrl = rememberServerUrl()
    LightboxScreen(
        images = images.map { resolveAttachmentUrl(serverUrl, urlBuilder(it)) },
        initialIndex = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        onDismiss = onDismiss,
        captions = images.map { it.caption },
        commentCount = commentCount?.let { count -> { index -> images.getOrNull(index)?.let(count) ?: 0 } },
        comments = comments?.let { slot -> { index -> images.getOrNull(index)?.let { slot(it) } } },
        commentsInitiallyOpen = commentsInitiallyOpen,
    )
}
