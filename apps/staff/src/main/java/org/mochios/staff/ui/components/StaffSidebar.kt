// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.staff.R
import org.mochios.staff.navigation.StaffApp

/**
 * The staff-console destinations as drawer rows, grouped by
 * [DrawerItem.section].
 *
 * Sections (top → bottom):
 *   - Overview   : Dashboard
 *   - Market     : Listings, Appeals, Reports, Disputes, Reviews,
 *                  Moderation log, Categories
 *   - Management : Accounts, Team
 *   - Settings   : Configuration
 *
 * The Settings section is omitted when [userRole] is anything other than
 * `"admin"`; moderators and support staff never see the configuration entry.
 *
 * [StaffLayout] wraps its body in
 * [org.mochios.android.ui.components.MochiListDrawer] and passes this list,
 * so staff's drawer matches every other app's. "About" is not here — it
 * opens a dialog rather than navigating, so it belongs in the drawer's
 * bottom actions slot.
 *
 * Item ids are route strings, so the host passes its own route as
 * `selectedId` and navigates straight to `item.id`.
 */
@Composable
fun staffDrawerItems(userRole: String?): List<DrawerItem> {
    val overview = stringResource(R.string.staff_sidebar_overview)
    val market = stringResource(R.string.staff_sidebar_market)
    val management = stringResource(R.string.staff_sidebar_management)
    val settings = stringResource(R.string.staff_sidebar_settings)
    return buildList {
        add(
            DrawerItem(
                id = StaffApp.HOME,
                title = stringResource(R.string.staff_sidebar_dashboard),
                icon = Icons.Default.SpaceDashboard,
                section = overview,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.LISTINGS,
                title = stringResource(R.string.staff_sidebar_listings),
                icon = Icons.Default.Inventory,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.APPEALS,
                title = stringResource(R.string.staff_sidebar_appeals),
                icon = Icons.Default.Gavel,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.REPORTS,
                title = stringResource(R.string.staff_sidebar_reports),
                icon = Icons.Default.Flag,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.DISPUTES,
                title = stringResource(R.string.staff_sidebar_disputes),
                icon = Icons.Default.Report,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.REVIEWS,
                title = stringResource(R.string.staff_sidebar_reviews),
                icon = Icons.Default.Star,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.MODERATION,
                title = stringResource(R.string.staff_sidebar_moderation),
                icon = Icons.Default.History,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.CATEGORIES,
                title = stringResource(R.string.staff_sidebar_categories),
                icon = Icons.Default.Category,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.ACCOUNTS,
                title = stringResource(R.string.staff_sidebar_accounts),
                icon = Icons.Default.People,
                section = management,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.TEAM,
                title = stringResource(R.string.staff_sidebar_team),
                icon = Icons.Default.Group,
                section = management,
            )
        )
        if (userRole == "admin") {
            add(
                DrawerItem(
                    id = StaffApp.CONFIG,
                    title = stringResource(R.string.staff_sidebar_config),
                    icon = Icons.Default.Settings,
                    section = settings,
                )
            )
        }
    }
}
