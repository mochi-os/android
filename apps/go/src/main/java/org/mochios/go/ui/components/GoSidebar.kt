// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.go.R

enum class GoSidebarFilter { ACTIVE, COMPLETED }

@Composable
fun GoSidebar(
    currentFilter: GoSidebarFilter,
    onSelectFilter: (GoSidebarFilter) -> Unit,
    onNewGame: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
        Column {
            // Header matches the launcher icon label.
            Text(
                text = stringResource(R.string.go_app_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp),
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            FilterRow(
                filter = GoSidebarFilter.ACTIVE,
                current = currentFilter,
                icon = Icons.Default.PlayArrow,
                labelRes = R.string.go_sidebar_active,
                onSelect = onSelectFilter,
            )
            FilterRow(
                filter = GoSidebarFilter.COMPLETED,
                current = currentFilter,
                icon = Icons.Default.CheckCircle,
                labelRes = R.string.go_sidebar_completed,
                onSelect = onSelectFilter,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                label = { Text(stringResource(R.string.go_sidebar_new_game)) },
                selected = false,
                onClick = onNewGame,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

@Composable
private fun FilterRow(
    filter: GoSidebarFilter,
    current: GoSidebarFilter,
    icon: ImageVector,
    labelRes: Int,
    onSelect: (GoSidebarFilter) -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
        selected = current == filter,
        onClick = { onSelect(filter) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
