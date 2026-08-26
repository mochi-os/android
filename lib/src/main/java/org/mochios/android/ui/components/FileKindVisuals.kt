// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.net.Uri
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.android.model.FileKind
import org.mochios.android.model.fileKindOf

/**
 * How a file of each kind is drawn when there is no preview of it to show.
 * One table, so a PDF is the same red in a wiki's attachments list as it is in
 * a chat message - a reader learns the colours once.
 */

/** Representative icon for a file kind. */
fun fileKindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.IMAGE -> Icons.Default.Image
    FileKind.VIDEO -> Icons.Default.Videocam
    FileKind.PDF -> Icons.Default.PictureAsPdf
    FileKind.WORD -> Icons.AutoMirrored.Filled.Article
    FileKind.EXCEL -> Icons.Default.TableChart
    FileKind.TEXT -> Icons.AutoMirrored.Filled.TextSnippet
    FileKind.AUDIO -> Icons.Default.Audiotrack
    else -> Icons.Default.Description
}

/**
 * Drive-style accent colour for a file kind's icon. Kinds that have no colour
 * of their own fall back to [MaterialTheme]'s, which keeps them legible in
 * either theme rather than fixing them to one.
 */
@Composable
fun fileKindTint(kind: FileKind): Color = when (kind) {
    FileKind.PDF -> Color(0xFFE53935)    // red
    FileKind.WORD -> Color(0xFF1E88E5)   // blue
    FileKind.EXCEL -> Color(0xFF2E9E50)  // green
    FileKind.TEXT -> Color(0xFF607D8B)   // blue-grey
    FileKind.AUDIO -> Color(0xFF8E24AA)  // purple
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * A file's icon in its kind's colour - the one call a chip, a list row or a
 * tile makes, so none of them has to remember the table above.
 *
 * @param kind The kind of file being drawn.
 * @param modifier Modifier for the icon, which is where its size comes from.
 * @param contentDescription Description for accessibility; null where the name
 *   of the file is already read out beside the icon.
 */
@Composable
fun FileKindIcon(
    kind: FileKind,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = fileKindIcon(kind),
        contentDescription = contentDescription,
        tint = fileKindTint(kind),
        modifier = modifier,
    )
}

/**
 * The same icon for a file the caller knows by MIME type and name rather than
 * by kind - a market asset or a piece of dispute evidence, say, which is not
 * an attachment.
 *
 * @param type The file's MIME type, which may be blank.
 * @param name The file's name, whose extension answers for a blank type.
 * @param modifier Modifier for the icon, which is where its size comes from.
 * @param contentDescription Description for accessibility; null where the name
 *   of the file is already read out beside the icon.
 */
@Composable
fun FileKindIcon(
    type: String,
    name: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    FileKindIcon(
        kind = fileKindOf(type, name),
        modifier = modifier,
        contentDescription = contentDescription,
    )
}

/**
 * The kind of a file that has been picked but not yet uploaded. The type comes
 * from the content resolver, so a picker that hands over a name without an
 * extension still lands on the right icon.
 *
 * @param uri The picked file.
 * @param name The file's name where one has been resolved already.
 * @return The kind to draw the pending file as.
 */
@Composable
fun rememberFileKind(uri: Uri, name: String = ""): FileKind {
    val context = LocalContext.current
    return remember(uri, name) {
        fileKindOf(context.contentResolver.getType(uri).orEmpty(), name)
    }
}

/**
 * A file drawn as itself where it can be: an image shows its own picture, a
 * video its opening frame, and every other kind falls back to [FileKindIcon].
 * A picture that will not load falls back too, so a chip or a row never ends up
 * with a hole in it.
 *
 * @param kind The kind of file being drawn.
 * @param model What to load an image from - a picked `content://` [Uri], an
 *   absolute URL, or a server-relative path. Null where the caller has no
 *   preview to offer, which draws the icon.
 * @param videoModel Where a video's frame is decoded from, which is the clip
 *   itself rather than a thumbnail: the server serves no thumbnail for a video.
 *   Defaults to [model], which is what a file picked on the device wants.
 * @param modifier Modifier for the preview, which is where its size comes from.
 * @param contentDescription Description for accessibility; null where the name
 *   of the file is already read out beside it.
 * @param shape Corner shape the picture is clipped to.
 */
@Composable
fun FileKindPreview(
    kind: FileKind,
    model: Any?,
    videoModel: Any? = model,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(3.dp),
) {
    val serverUrl = rememberServerUrl()
    // A video frame comes from a fetcher of our own, and that one needs an
    // absolute URL: it reads the clip itself rather than going through the
    // loader's relative-URL mapping.
    val source = when (kind) {
        FileKind.IMAGE -> model
        FileKind.VIDEO -> videoModel?.let { clip ->
            VideoFrame(resolveAttachmentUrl(serverUrl, clip.toString()))
        }
        else -> null
    }

    var failed by remember(source) { mutableStateOf(false) }

    if (source == null || failed) {
        FileKindIcon(
            kind = kind,
            modifier = modifier,
            contentDescription = contentDescription,
        )
        return
    }

    AsyncImage(
        model = source,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = modifier.clip(shape),
    )
}

/**
 * The same preview for a file the caller knows by MIME type and name rather
 * than by kind.
 *
 * @param type The file's MIME type, which may be blank.
 * @param name The file's name, whose extension answers for a blank type.
 * @param model What to load an image from; see [FileKindPreview].
 * @param videoModel Where a video's frame comes from; see [FileKindPreview].
 * @param modifier Modifier for the preview, which is where its size comes from.
 * @param contentDescription Description for accessibility; null where the name
 *   of the file is already read out beside it.
 */
@Composable
fun FileKindPreview(
    type: String,
    name: String,
    model: Any?,
    videoModel: Any? = model,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    FileKindPreview(
        kind = fileKindOf(type, name),
        model = model,
        videoModel = videoModel,
        modifier = modifier,
        contentDescription = contentDescription,
    )
}
