// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A row of filter chips, one per option, the app's one way to lay out a set of
 * choices as chips. Wraps onto more lines, or stays on one line that scrolls
 * sideways when [singleLine].
 *
 * @param options Value and label of each chip, in order.
 * @param isSelected Whether the chip for a value reads as picked.
 * @param onSelect Called with the value of a tapped chip.
 * @param singleLine Keep the chips on one line that scrolls sideways.
 * @param contentPadding Padding around the chips; inside the scroll when
 * [singleLine], so the chips scroll to the edge.
 * @param horizontalSpacing Gap between chips on a line.
 * @param verticalSpacing Gap between wrapped lines.
 * @param leading Shown before the first chip, such as the filter's name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FilterChipRow(
    options: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    val chips: @Composable () -> Unit = {
        leading?.invoke()
        options.forEach { (value, label) ->
            FilterChip(
                selected = isSelected(value),
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
    if (singleLine) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips()
        }
    } else {
        FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            chips()
        }
    }
}
