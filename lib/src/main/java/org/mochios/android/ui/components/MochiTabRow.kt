// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One tab in a [MochiTabRow].
 *
 * @property label The tab's title.
 * @property icon Drawn above the label. Null leaves the tab text-only.
 */
data class MochiTab(
    val label: String,
    val icon: ImageVector? = null,
)

/**
 * The tab row every app uses: labels in neutral colours, the primary colour
 * spent on the selected tab's divider alone, one line per label.
 *
 * Fixed rather than scrollable, so the tabs divide the width evenly. A label
 * too long for its share is truncated, not wrapped.
 *
 * @param tabs The tabs, in the order they are shown.
 * @param selectedIndex Index into [tabs] of the active tab.
 * @param onSelect Called with the index of the tab that was tapped, including
 *   the one already active.
 * @param modifier Modifier for the row.
 * @param containerColor The row's background. Transparent inside a sheet that
 *   paints its own.
 */
@Composable
fun MochiTabRow(
    tabs: List<MochiTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    if (tabs.isEmpty()) {
        return
    }
    // A selection outside the list would put the indicator off the end of
    // tabPositions, so it is clamped rather than left to throw.
    val active = selectedIndex.coerceIn(0, tabs.lastIndex)

    TabRow(
        selectedTabIndex = active,
        modifier = modifier.fillMaxWidth(),
        containerColor = containerColor,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[active]),
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == active,
                onClick = { onSelect(index) },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = tab.icon?.let { icon ->
                    { Icon(icon, contentDescription = null) }
                },
                text = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}
