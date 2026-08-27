// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.go.R

enum class GoSidebarFilter { ACTIVE, COMPLETED }

@Composable
fun goDrawerItems(): List<DrawerItem> = listOf(
    DrawerItem(
        id = GoSidebarFilter.ACTIVE.name,
        title = stringResource(R.string.go_sidebar_active),
        icon = Icons.Outlined.PlayArrow,
    ),
    DrawerItem(
        id = GoSidebarFilter.COMPLETED.name,
        title = stringResource(R.string.go_sidebar_completed),
        icon = Icons.Outlined.CheckCircle,
    ),
)

/** Resolves a [goDrawerItems] row id back to its filter. */
fun goDrawerFilter(itemId: String): GoSidebarFilter = GoSidebarFilter.valueOf(itemId)
