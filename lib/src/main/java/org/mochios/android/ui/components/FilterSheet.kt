// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The frame every filter sheet shares: a fully expanded bottom sheet whose
 * [content] scrolls under a [MochiSheetHeader] showing [title]. The screen
 * supplies its own sections, so each sheet keeps its own look.
 *
 * @param headerAction Shown in the header before the close button, such as a
 * Clear all button.
 * @param sectionSpacing Gap between the header and each section.
 * @param bottomSpacing Space kept under the last section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    headerAction: (@Composable RowScope.() -> Unit)? = null,
    sectionSpacing: Dp = 16.dp,
    bottomSpacing: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MochiBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        MochiSheetHeader(
            title = title,
            actions = { headerAction?.invoke(this) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            content()
            Spacer(modifier = Modifier.height(bottomSpacing))
        }
    }
}
