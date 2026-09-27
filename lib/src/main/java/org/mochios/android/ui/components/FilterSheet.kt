// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The frame every filter sheet shares: a fully expanded bottom sheet whose
 * [content] scrolls under a [title] row. The screen supplies its own sections,
 * so each sheet keeps its own look.
 *
 * @param titleWeight Weight of the title, or null for the style's own.
 * @param headerAction Shown at the end of the title row, such as a Clear all button.
 * @param sectionSpacing Gap between the title and each section.
 * @param bottomSpacing Space kept under the last section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    titleWeight: FontWeight? = FontWeight.SemiBold,
    headerAction: (@Composable RowScope.() -> Unit)? = null,
    sectionSpacing: Dp = 16.dp,
    bottomSpacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MochiBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = titleWeight,
                    modifier = Modifier.weight(1f),
                )
                headerAction?.invoke(this)
            }
            content()
            Spacer(modifier = Modifier.height(bottomSpacing))
        }
    }
}
