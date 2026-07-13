// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.widget.Toast
import org.mochios.android.R

/**
 * How to truncate a value that's too long for the available width.
 *
 *  - [NONE]   render the full string; let the surrounding Row scroll/wrap
 *  - [MIDDLE] replace the middle with an ellipsis ("1abc…wxyz") — best for
 *             long identifiers where the head and tail are both meaningful
 *             (entity IDs, fingerprints)
 *  - [END]    standard end-truncation handled by [Text] (single line, ellipsis)
 */
enum class Truncate { NONE, MIDDLE, END }

/**
 * Monospace pill rendering a copyable identifier (entity ID, fingerprint,
 * replica ID, hash, etc.). Tap to copy; long-press also copies and triggers
 * haptic feedback. Mirrors web's `DataChip`.
 *
 * Web measures the chip width with a JS resize observer to decide where to
 * truncate; on Android we use a simpler fixed-character rule because Compose
 * doesn't expose pre-layout text measurement at this level. The web rule is
 * "first 10 chars … last 10 chars" once the value exceeds 24 characters —
 * matched here.
 *
 * @param value     The full text. Always copied to the clipboard in full,
 *                  regardless of how it's displayed.
 * @param truncate  How to truncate the visible text. Defaults to [Truncate.NONE].
 * @param copyable  When true (default), tap/long-press copies and shows a toast.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun DataChip(
    value: String,
    modifier: Modifier = Modifier,
    truncate: Truncate = Truncate.NONE,
    copyable: Boolean = true,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val copiedMessage = stringResource(R.string.common_copied)

    val displayValue = when (truncate) {
        Truncate.MIDDLE -> middleTruncate(value)
        // Truncate.END / NONE use Text's built-in ellipsis (maxLines = 1) +
        // overflow handling below; the string itself is unchanged.
        Truncate.END, Truncate.NONE -> value
    }

    val onCopy: () -> Unit = {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }

    val interactionModifier = if (copyable) {
        Modifier.combinedClickable(
            onClick = onCopy,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCopy()
            },
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(interactionModifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayValue,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current,
            maxLines = 1,
            // Truncate.END / NONE both rely on Text's ellipsis when the
            // Row's parent constrains the width. Truncate.MIDDLE already
            // produced its own ellipsis, so the visible string fits.
            overflow = if (truncate == Truncate.END) {
                androidx.compose.ui.text.style.TextOverflow.Ellipsis
            } else {
                androidx.compose.ui.text.style.TextOverflow.Clip
            },
        )
    }
}

private const val MAX_FULL_LENGTH = 24
private const val HEAD_TAIL_CHARS = 10

private fun middleTruncate(value: String): String {
    if (value.length <= MAX_FULL_LENGTH) return value
    val head = value.take(HEAD_TAIL_CHARS)
    val tail = value.takeLast(HEAD_TAIL_CHARS)
    return "$head…$tail"
}
