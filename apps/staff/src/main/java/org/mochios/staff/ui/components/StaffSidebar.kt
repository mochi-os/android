// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.navigation.NavController
import org.mochios.staff.R
import org.mochios.staff.navigation.StaffApp

/**
 * Drawer body for the staff app's left rail. Mirrors the market sidebar
 * pattern — top-level staff screens wrap their body in
 * [androidx.compose.material3.ModalNavigationDrawer] with this composable as
 * the drawer content so the hamburger opens the same navigation choices
 * everywhere.
 *
 * Sections (top → bottom):
 *   - Overview    : Dashboard
 *   - Market      : Listings, Appeals, Reports, Disputes, Reviews,
 *                   Moderation log, Categories
 *   - Management  : Accounts, Team
 *   - Settings    : Configuration (admin only)
 *
 * The Settings section is hidden when [userRole] is anything other than
 * `"admin"`; moderators and support staff never see the configuration entry.
 *
 * Active row is highlighted by matching the rendered route against
 * [currentRoute]. Navigation requests are emitted via [onNavigate] which the
 * host screen wires to a `NavController.navigate(route)` call; the
 * [navController] parameter is kept on the signature for callers that prefer
 * to route directly through it in later passes.
 */
@Composable
fun StaffSidebar(
    currentRoute: String?,
    userRole: String?,
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    onNavigate: (String) -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // Overview
            SectionHeader(R.string.staff_sidebar_overview)
            SidebarRow(
                route = StaffApp.HOME,
                currentRoute = currentRoute,
                icon = Icons.Default.SpaceDashboard,
                labelRes = R.string.staff_sidebar_dashboard,
                onClick = { onNavigate(StaffApp.HOME) },
            )

            // Market
            HorizontalDivider()
            SectionHeader(R.string.staff_sidebar_market)
            SidebarRow(
                route = StaffApp.LISTINGS,
                currentRoute = currentRoute,
                icon = Icons.Default.Inventory,
                labelRes = R.string.staff_sidebar_listings,
                onClick = { onNavigate(StaffApp.LISTINGS) },
            )
            SidebarRow(
                route = StaffApp.APPEALS,
                currentRoute = currentRoute,
                icon = Icons.Default.Gavel,
                labelRes = R.string.staff_sidebar_appeals,
                onClick = { onNavigate(StaffApp.APPEALS) },
            )
            SidebarRow(
                route = StaffApp.REPORTS,
                currentRoute = currentRoute,
                icon = Icons.Default.Flag,
                labelRes = R.string.staff_sidebar_reports,
                onClick = { onNavigate(StaffApp.REPORTS) },
            )
            SidebarRow(
                route = StaffApp.DISPUTES,
                currentRoute = currentRoute,
                icon = Icons.Default.Report,
                labelRes = R.string.staff_sidebar_disputes,
                onClick = { onNavigate(StaffApp.DISPUTES) },
            )
            SidebarRow(
                route = StaffApp.REVIEWS,
                currentRoute = currentRoute,
                icon = Icons.Default.Star,
                labelRes = R.string.staff_sidebar_reviews,
                onClick = { onNavigate(StaffApp.REVIEWS) },
            )
            SidebarRow(
                route = StaffApp.MODERATION,
                currentRoute = currentRoute,
                icon = Icons.Default.History,
                labelRes = R.string.staff_sidebar_moderation,
                onClick = { onNavigate(StaffApp.MODERATION) },
            )
            SidebarRow(
                route = StaffApp.CATEGORIES,
                currentRoute = currentRoute,
                icon = Icons.Default.Category,
                labelRes = R.string.staff_sidebar_categories,
                onClick = { onNavigate(StaffApp.CATEGORIES) },
            )

            // Management
            HorizontalDivider()
            SectionHeader(R.string.staff_sidebar_management)
            SidebarRow(
                route = StaffApp.ACCOUNTS,
                currentRoute = currentRoute,
                icon = Icons.Default.People,
                labelRes = R.string.staff_sidebar_accounts,
                onClick = { onNavigate(StaffApp.ACCOUNTS) },
            )
            SidebarRow(
                route = StaffApp.TEAM,
                currentRoute = currentRoute,
                icon = Icons.Default.Group,
                labelRes = R.string.staff_sidebar_team,
                onClick = { onNavigate(StaffApp.TEAM) },
            )

            // Settings — admin only.
            if (userRole == "admin") {
                HorizontalDivider()
                SectionHeader(R.string.staff_sidebar_settings)
                SidebarRow(
                    route = StaffApp.CONFIG,
                    currentRoute = currentRoute,
                    icon = Icons.Default.Settings,
                    labelRes = R.string.staff_sidebar_config,
                    onClick = { onNavigate(StaffApp.CONFIG) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionHeader(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SidebarRow(
    route: String?,
    currentRoute: String?,
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
