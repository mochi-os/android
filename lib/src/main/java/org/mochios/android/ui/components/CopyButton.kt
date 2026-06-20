// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mochios.android.R

/**
 * Small icon button that writes [value] to the system clipboard on tap and
 * shows a brief check-mark confirmation before reverting to the copy icon.
 * Mirrors web's `CopyButton` (used on every code block, DataChip, and any
 * one-tap "give me that token" affordance).
 *
 * @param value             Text to place on the clipboard.
 * @param contentDescription Accessibility label; defaults to the localised
 *                          "Copy" string. Pass a more specific label when the
 *                          button is one of several copy targets in the same
 *                          screen (e.g. "Copy peer ID").
 */
@Composable
fun CopyButton(
    value: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    if (copied) {
        // Revert after a short feedback window. The key on `value` resets the
        // timer if the caller re-renders with a new value mid-feedback.
        LaunchedEffect(value, copied) {
            delay(2000)
            copied = false
        }
    }

    val label = contentDescription ?: stringResource(R.string.common_copy)

    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(value))
            copied = true
        },
        modifier = modifier.size(28.dp),
    ) {
        if (copied) {
            // Material 3 doesn't ship a semantic "success" colour; use the
            // primary tint so the feedback reads as "something good happened"
            // against any theme without hard-coding green.
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
