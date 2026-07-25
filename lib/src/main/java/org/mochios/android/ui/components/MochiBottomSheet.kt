// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * App-wide [ModalBottomSheet] wrapper that keeps a sheet clear of the system
 * bars.
 *
 * The app runs edge to edge, so a plain expanded `ModalBottomSheet` reaches the
 * very top of the window and slides its first row under the status bar. This
 * stops the sheet below the status bar and pads its content above the
 * navigation bar. Use it instead of `ModalBottomSheet` everywhere.
 *
 * @param onDismissRequest Called when the user swipes the sheet away or taps the scrim.
 * @param sheetState Sheet state; defaults to a collapsible sheet.
 * @param containerColor Sheet background.
 * @param dragHandle Handle drawn at the top; pass null for sheets that own their header.
 * @param content Sheet body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MochiBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.statusBarsPadding(),
        sheetState = sheetState,
        containerColor = containerColor,
        dragHandle = dragHandle,
        // Top inset is already handled by the padding above; this keeps the last
        // row of content off the gesture bar.
        contentWindowInsets = { WindowInsets.navigationBars },
        content = content,
    )
}
