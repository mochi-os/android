package org.mochios.wikis.ui.comments

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.wikis.R
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.ui.components.LocalWikiContext

/**
 * Renders the attachments shown beneath a comment body. Mirrors web's
 * `comment-attachments.tsx`:
 *
 *  - Image attachments: a [FlowRow] of 80dp-high thumbnails. Tapping any one
 *    opens an in-app fullscreen [LightboxScreen] swipeable across all images
 *    in the comment.
 *  - Non-image attachments: a vertical list of "icon - name (size)" rows.
 *    Tapping a row launches an `ACTION_VIEW` intent against the absolute
 *    download URL — the system picks an appropriate handler.
 *
 * Pulls the per-wiki `baseURL` from [LocalWikiContext] so attachment URLs are
 * always absolute even when this composable is rendered from a deep-link
 * route that hasn't loaded the wiki info yet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentAttachments(
    attachments: List<Attachment>,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return

    val wiki = LocalWikiContext.current
    val baseURL = wiki?.baseURL ?: return
    val context = LocalContext.current
    val format = LocalFormat.current

    val images = remember(attachments) { attachments.filter { it.type.startsWith("image/") } }
    val files = remember(attachments) { attachments.filter { !it.type.startsWith("image/") } }

    var showLightbox by rememberSaveable { mutableStateOf(false) }
    var lightboxIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (images.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                images.forEachIndexed { index, img ->
                    Box(
                        modifier = Modifier
                            .height(80.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                lightboxIndex = index
                                showLightbox = true
                            },
                    ) {
                        AsyncImage(
                            model = "${baseURL}attachments/${img.id}/thumbnail",
                            contentDescription = img.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.height(80.dp),
                        )
                    }
                }
            }
        }

        if (files.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                files.forEach { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                val url = "${baseURL}attachments/${file.id}"
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                R.string.wikis_comment_attachment_size,
                                format.formatFileSize(file.size),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }

    if (showLightbox && images.isNotEmpty()) {
        LightboxScreen(
            images = images.map { "${baseURL}attachments/${it.id}" },
            initialIndex = lightboxIndex,
            onDismiss = { showLightbox = false },
        )
    }
}
