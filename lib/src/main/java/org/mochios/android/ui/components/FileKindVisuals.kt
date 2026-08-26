// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.mochios.android.model.FileKind

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
