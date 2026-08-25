// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * What the body will look like once posted, over the form rather than instead
 * of it.
 *
 * A preview that replaces the editor costs the writer their place: they lose
 * the cursor, the scroll position and the sight of what they were typing. A
 * sheet leaves all three where they were and closes with a swipe.
 *
 * The rendering is the caller's, because the same markdown is drawn by
 * different things - a wiki resolves its own attachments, a post does not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownPreviewSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    MochiBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Tall enough that a short body still reads as a page, capped so
                // a long one does not swallow the screen whole.
                .heightIn(min = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            content = content,
        )
    }
}
