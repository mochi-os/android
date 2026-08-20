// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.go.R

/**
 * Filter values surfaced in the Go sidebar. Both filters live inside the
 * list page (they don't push a new route) — they just toggle which set of
 * games the [org.mochios.go.ui.list.GoGameListScreen] renders.
 */
enum class GoSidebarFilter { ACTIVE, COMPLETED }

/**
 * The two game filters as drawer rows.
 *
 * [org.mochios.go.ui.list.GoGameListScreen] wraps its body in
 * [org.mochios.android.ui.components.MochiListDrawer] and passes this list,
 * so Go's drawer matches the one the list apps (chat, feeds, forums, ...)
 * draw. "New game" is not here — it opens a dialog rather than selecting a
 * filter, so it belongs in the drawer's bottom actions slot.
 *
 * Item ids are [GoSidebarFilter] names; [goDrawerFilter] maps a clicked row
 * back to its filter.
 */
@Composable
fun goDrawerItems(): List<DrawerItem> = listOf(
    DrawerItem(
        id = GoSidebarFilter.ACTIVE.name,
        title = stringResource(R.string.go_sidebar_active),
        icon = Icons.Default.PlayArrow,
    ),
    DrawerItem(
        id = GoSidebarFilter.COMPLETED.name,
        title = stringResource(R.string.go_sidebar_completed),
        icon = Icons.Default.CheckCircle,
    ),
)

/** Resolves a [goDrawerItems] row id back to its filter. */
fun goDrawerFilter(itemId: String): GoSidebarFilter = GoSidebarFilter.valueOf(itemId)
