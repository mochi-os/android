// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.staff.R
import org.mochios.staff.navigation.StaffApp

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
                icon = Icons.Outlined.SpaceDashboard,
                section = overview,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.LISTINGS,
                title = stringResource(R.string.staff_sidebar_listings),
                icon = Icons.Outlined.Inventory,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.APPEALS,
                title = stringResource(R.string.staff_sidebar_appeals),
                icon = Icons.Outlined.Gavel,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.REPORTS,
                title = stringResource(R.string.staff_sidebar_reports),
                icon = Icons.Outlined.Flag,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.DISPUTES,
                title = stringResource(R.string.staff_sidebar_disputes),
                icon = Icons.Outlined.Report,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.REVIEWS,
                title = stringResource(R.string.staff_sidebar_reviews),
                icon = Icons.Outlined.Star,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.MODERATION,
                title = stringResource(R.string.staff_sidebar_moderation),
                icon = Icons.Outlined.History,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.CATEGORIES,
                title = stringResource(R.string.staff_sidebar_categories),
                icon = Icons.Outlined.Category,
                section = market,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.ACCOUNTS,
                title = stringResource(R.string.staff_sidebar_accounts),
                icon = Icons.Outlined.People,
                section = management,
            )
        )
        add(
            DrawerItem(
                id = StaffApp.TEAM,
                title = stringResource(R.string.staff_sidebar_team),
                icon = Icons.Outlined.Group,
                section = management,
            )
        )
        if (userRole == "admin") {
            add(
                DrawerItem(
                    id = StaffApp.CONFIG,
                    title = stringResource(R.string.staff_sidebar_config),
                    icon = Icons.Outlined.Settings,
                    section = settings,
                )
            )
        }
    }
}
