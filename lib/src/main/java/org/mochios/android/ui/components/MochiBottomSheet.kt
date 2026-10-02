// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * App-wide [ModalBottomSheet] wrapper. The app runs edge to edge, so a plain
 * expanded sheet slides its first row under the status bar; this insets it and
 * pads content above the navigation bar. Use it instead of `ModalBottomSheet`.
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

/**
 * A full-height sheet for content the user types in. While the keyboard is up,
 * a swipe down puts the keyboard away instead of closing the sheet: scrolling
 * back through what was typed is the same gesture, and a sheet closed by
 * accident takes the typing with it. With the keyboard down it closes as any
 * sheet does.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MochiEditorSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var typing by remember { mutableStateOf(false) }
    // The sheet is a window of its own: its keyboard answers to the controller
    // its content sees, not to the one of the screen behind it.
    var keyboard by remember { mutableStateOf<SoftwareKeyboardController?>(null) }
    val sheetState = rememberHeldSheetState(held = { typing }, onHeld = { keyboard?.hide() })
    MochiBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        val visible = WindowInsets.isImeVisible
        val controller = LocalSoftwareKeyboardController.current
        SideEffect {
            typing = visible
            keyboard = controller
        }
        content()
    }
}

/**
 * A full-height sheet state that refuses to hide while [held] says so, calling
 * [onHeld] each time it refuses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberHeldSheetState(held: () -> Boolean, onHeld: () -> Unit): SheetState {
    val isHeld by rememberUpdatedState(held)
    val refused by rememberUpdatedState(onHeld)
    // One lambda for the state's whole life: handed a new one, the state is
    // built again and the sheet with it.
    val confirm = remember {
        { value: SheetValue ->
            if (value == SheetValue.Hidden && isHeld()) {
                refused()
                false
            } else {
                true
            }
        }
    }
    return rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirm)
}
