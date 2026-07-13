// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
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
import org.mochios.market.R
import org.mochios.market.navigation.MarketApp

/**
 * Drawer body for the market app's left rail. Mirrors the wikis sidebar
 * pattern — top-level market screens wrap their body in [androidx.compose
 * .material3.ModalNavigationDrawer] with this composable as the drawer
 * content so the hamburger opens the same navigation choices everywhere.
 *
 * Sections (top → bottom):
 *   - Browse  : Home
 *   - Buying  : Saved (badge slot), Purchases, Bids, Subscriptions
 *   - Selling : Listings, Sales*, Subscribers*
 *               (* = seller-gated; only rendered when [isSeller] is true)
 *   - Messages: Inbox (unread-badge slot), Reviews
 *   - Settings: Account
 *
 * Active row is highlighted by matching the render route against
 * [currentRoute]. Navigation requests are emitted via [onNavigate] which
 * is wired to a `NavController.navigate(route)` call by the host screen —
 * the [navController] parameter is kept on the signature for callers that
 * prefer to route directly through it in later passes.
 *
 * Badge slots:
 *   - [savedBadge] for the count of saved listings with price drops or
 *     ending-soon auctions (zero hides the badge).
 *   - [inboxUnreadBadge] for unread message threads.
 */
@Composable
fun MarketSidebar(
    currentRoute: String?,
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    onNavigate: (String) -> Unit,
    isSeller: Boolean = true,
    savedBadge: Int = 0,
    inboxUnreadBadge: Int = 0,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // Browse
            SectionHeader(R.string.market_sidebar_browse)
            SidebarRow(
                route = MarketApp.HOME,
                currentRoute = currentRoute,
                icon = Icons.Default.Home,
                labelRes = R.string.market_sidebar_home,
                onClick = { onNavigate(MarketApp.HOME) },
            )

            // Buying
            HorizontalDivider()
            SectionHeader(R.string.market_sidebar_buying)
            SidebarRow(
                route = MarketApp.SAVED,
                currentRoute = currentRoute,
                icon = Icons.Default.Bookmark,
                labelRes = R.string.market_sidebar_saved,
                badge = savedBadge,
                onClick = { onNavigate(MarketApp.SAVED) },
            )
            SidebarRow(
                route = MarketApp.PURCHASES,
                currentRoute = currentRoute,
                icon = Icons.Default.Receipt,
                labelRes = R.string.market_sidebar_purchases,
                onClick = { onNavigate(MarketApp.PURCHASES) },
            )
            SidebarRow(
                route = MarketApp.BIDS,
                currentRoute = currentRoute,
                icon = Icons.Default.Gavel,
                labelRes = R.string.market_sidebar_bids,
                onClick = { onNavigate(MarketApp.BIDS) },
            )
            SidebarRow(
                route = MarketApp.SUBSCRIPTIONS,
                currentRoute = currentRoute,
                icon = Icons.Default.Repeat,
                labelRes = R.string.market_sidebar_subscriptions,
                onClick = { onNavigate(MarketApp.SUBSCRIPTIONS) },
            )

            // Selling (entire section gated behind seller status)
            if (isSeller) {
                HorizontalDivider()
                SectionHeader(R.string.market_sidebar_selling)
                SidebarRow(
                    route = MarketApp.LISTINGS,
                    currentRoute = currentRoute,
                    icon = Icons.Default.Inventory,
                    labelRes = R.string.market_sidebar_listings,
                    onClick = { onNavigate(MarketApp.LISTINGS) },
                )
                SidebarRow(
                    route = MarketApp.SALES,
                    currentRoute = currentRoute,
                    icon = Icons.Default.PointOfSale,
                    labelRes = R.string.market_sidebar_sales,
                    onClick = { onNavigate(MarketApp.SALES) },
                )
                SidebarRow(
                    route = MarketApp.SUBSCRIBERS,
                    currentRoute = currentRoute,
                    icon = Icons.Default.Group,
                    labelRes = R.string.market_sidebar_subscribers,
                    onClick = { onNavigate(MarketApp.SUBSCRIBERS) },
                )
            }

            // Messages
            HorizontalDivider()
            SectionHeader(R.string.market_sidebar_messages)
            SidebarRow(
                route = MarketApp.MESSAGES,
                currentRoute = currentRoute,
                icon = Icons.Default.Email,
                labelRes = R.string.market_sidebar_inbox,
                badge = inboxUnreadBadge,
                onClick = { onNavigate(MarketApp.MESSAGES) },
            )
            SidebarRow(
                route = MarketApp.REVIEWS,
                currentRoute = currentRoute,
                icon = Icons.Default.Star,
                labelRes = R.string.market_sidebar_reviews,
                onClick = { onNavigate(MarketApp.REVIEWS) },
            )

            // Settings
            HorizontalDivider()
            SectionHeader(R.string.market_sidebar_settings)
            SidebarRow(
                route = MarketApp.SELLER_SETTINGS,
                currentRoute = currentRoute,
                icon = Icons.Default.Storefront,
                labelRes = if (isSeller) {
                    R.string.market_sidebar_seller_settings
                } else {
                    R.string.market_sidebar_become_seller
                },
                onClick = { onNavigate(MarketApp.SELLER_SETTINGS) },
            )
            SidebarRow(
                route = MarketApp.ACCOUNT,
                currentRoute = currentRoute,
                icon = Icons.Default.AccountCircle,
                labelRes = R.string.market_sidebar_account,
                onClick = { onNavigate(MarketApp.ACCOUNT) },
            )
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
    badge: Int = 0,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
        badge = if (badge > 0) {
            { Badge { Text(badge.toString()) } }
        } else {
            null
        },
        selected = route != null && route == currentRoute,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
