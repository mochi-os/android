package org.mochi.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochi.android.R
import org.mochi.android.i18n.LocalFormat
import org.mochi.android.model.Attachment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentGallery(
    attachments: List<Attachment>,
    urlBuilder: (Attachment) -> String,
    thumbnailUrlBuilder: ((Attachment) -> String)? = null,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    val images = attachments.filter { it.isImage }
    val videos = attachments.filter { it.isVideo }
    val files = attachments.filter { !it.isImage && !it.isVideo }

    var showViewer by rememberSaveable { mutableStateOf(false) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(0) }
    var showVideoUrl by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (images.isNotEmpty()) {
            MediaGrid(
                urls = images.map { urlBuilder(it) },
                thumbnailUrls = thumbnailUrlBuilder?.let { tb -> images.map { tb(it) } },
                contentDescriptions = images.map { it.name },
                onClick = { index ->
                    viewerIndex = index
                    showViewer = true
                }
            )
        }

        for (video in videos) {
            VideoThumbnail(
                video = video,
                thumbnailUrl = thumbnailUrlBuilder?.invoke(video),
                onClick = { showVideoUrl = urlBuilder(video) }
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
                        onClick = { /* Download via urlBuilder */ }
                    )
                }
            }
        }
    }

    if (showViewer && images.isNotEmpty()) {
        LightboxScreen(
            images = images.map { urlBuilder(it) },
            initialIndex = viewerIndex,
            onDismiss = { showViewer = false }
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
    thumbnailUrl: String?,
    onClick: () -> Unit
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
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = video.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = stringResource(R.string.common_play_video),
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(64.dp)
        )
    }
}

@Composable
private fun FileChip(
    attachment: Attachment,
    onClick: () -> Unit
) {
    val format = LocalFormat.current
    AssistChip(
        onClick = onClick,
        label = {
            Text("${attachment.name} (${format.formatFileSize(attachment.size)})")
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}
