// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.files.Upload
import org.mochios.android.i18n.LocalFormat

/**
 * Byte progress of an upload, as the web draws it: a filling bar with "1.2 MB
 * of 4 MB" while the bytes are leaving the device, then a running bar with
 * "Processing…" once the server has them all. While the picked files are
 * still being read the bar runs with no label. Draws nothing for a null
 * [upload], so it can sit permanently in a layout.
 */
@Composable
fun UploadProgress(upload: Upload?, modifier: Modifier = Modifier) {
    if (upload == null) return
    val format = LocalFormat.current
    val description = stringResource(R.string.upload_progress)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val bar = Modifier
            .weight(1f)
            .semantics { contentDescription = description }
        if (upload.preparing || upload.processing) {
            LinearProgressIndicator(modifier = bar)
        } else {
            LinearProgressIndicator(progress = { upload.fraction }, modifier = bar)
        }
        val label = when {
            upload.preparing -> null
            upload.processing -> stringResource(R.string.upload_processing)
            else -> stringResource(
                R.string.upload_sent,
                format.formatFileSize(upload.sent),
                format.formatFileSize(upload.total),
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * The files of an upload in flight, one row each with its size and its own
 * progress ring, over the [UploadProgress] bar. For a field whose picker
 * uploads straight away, so the picked files have nowhere else to show until
 * the server has them. Draws nothing for a null [upload].
 */
@Composable
fun UploadFiles(upload: Upload?, modifier: Modifier = Modifier) {
    if (upload == null) return
    val format = LocalFormat.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        upload.files.forEach { file ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FileKindIcon(type = "", name = file.name, modifier = Modifier.size(20.dp))
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = format.formatFileSize(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CircularProgressIndicator(
                    progress = { file.fraction },
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        UploadProgress(upload)
    }
}

/**
 * An upload as a ring, for a spot too small for a bar such as over the image
 * being replaced: it fills with the bytes sent, and runs while the files are
 * read and once the server has them all. Draws nothing for a null [upload].
 */
@Composable
fun UploadRing(
    upload: Upload?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
) {
    if (upload == null) return
    val description = stringResource(R.string.upload_progress)
    val ring = modifier.semantics { contentDescription = description }
    if (upload.preparing || upload.processing) {
        CircularProgressIndicator(modifier = ring, color = color, strokeWidth = strokeWidth)
    } else {
        CircularProgressIndicator(
            progress = { upload.fraction },
            modifier = ring,
            color = color,
            strokeWidth = strokeWidth,
        )
    }
}
