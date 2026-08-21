// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
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
import org.mochios.wikis.R
import org.mochios.wikis.navigation.WikisApp

/**
 * Drawer body for the wikis app: the class-level All / Find / Create entries.
 */
@Composable
fun WikisSidebar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onCreateWiki: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
        Column {
            // Header — matches the web sidebar's "Wikis" group title.
            Text(
                text = stringResource(R.string.wikis_sidebar_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp),
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SidebarRow(
                route = WikisApp.HOME,
                currentRoute = currentRoute,
                icon = Icons.Default.Book,
                labelRes = R.string.wikis_sidebar_all,
                onClick = { onNavigate(WikisApp.HOME) },
            )
            SidebarRow(
                route = WikisApp.FIND,
                currentRoute = currentRoute,
                icon = Icons.Default.Search,
                labelRes = R.string.wikis_sidebar_find,
                onClick = { onNavigate(WikisApp.FIND) },
            )
            // "Create wiki" opens a dialog inside WikiListScreen rather than a
            // route, so it never matches currentRoute — `selected` stays false.
            SidebarRow(
                route = null,
                currentRoute = currentRoute,
                icon = Icons.Default.Add,
                labelRes = R.string.wikis_sidebar_create,
                onClick = onCreateWiki,
            )
        }
    }
}

@Composable
private fun SidebarRow(
    route: String?,
    currentRoute: String,
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
        selected = route != null && route == currentRoute,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
