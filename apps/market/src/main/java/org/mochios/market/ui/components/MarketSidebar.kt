// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.market.R
import org.mochios.market.navigation.MarketApp

/**
 * The market destinations as drawer rows, grouped by
 * [DrawerItem.section].
 *
 * Sections (top → bottom):
 *   - Browse  : Home
 *   - Buying  : Saved (badge slot), Purchases, Bids, Subscriptions
 *   - Selling : Listings, Sales, Subscribers
 *               (whole section omitted unless [isSeller])
 *   - Messages: Inbox (unread-badge slot), Reviews
 *   - Settings: Seller settings / Become a seller, Account
 *
 * [org.mochios.market.ui.browse.HomeScreen] wraps its body in
 * [org.mochios.android.ui.components.MochiListDrawer] and passes this list,
 * so market's drawer matches every other app's. "About" is not here — it
 * opens a dialog rather than navigating, so it belongs in the drawer's
 * bottom actions slot.
 *
 * Item ids are route strings, so the host passes its own route as
 * `selectedId` and navigates straight to `item.id`.
 *
 * Badge slots:
 *   - [savedBadge] for saved listings with price drops or ending-soon
 *     auctions (zero hides the badge).
 *   - [inboxUnreadBadge] for unread message threads.
 */
@Composable
fun marketDrawerItems(
    isSeller: Boolean = true,
    savedBadge: Int = 0,
    inboxUnreadBadge: Int = 0,
): List<DrawerItem> {
    val browse = stringResource(R.string.market_sidebar_browse)
    val buying = stringResource(R.string.market_sidebar_buying)
    val selling = stringResource(R.string.market_sidebar_selling)
    val messages = stringResource(R.string.market_sidebar_messages)
    val settings = stringResource(R.string.market_sidebar_settings)
    return buildList {
        add(
            DrawerItem(
                id = MarketApp.HOME,
                title = stringResource(R.string.market_sidebar_home),
                icon = Icons.Default.Home,
                section = browse,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.SAVED,
                title = stringResource(R.string.market_sidebar_saved),
                icon = Icons.Default.Bookmark,
                unread = savedBadge,
                section = buying,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.PURCHASES,
                title = stringResource(R.string.market_sidebar_purchases),
                icon = Icons.Default.Receipt,
                section = buying,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.BIDS,
                title = stringResource(R.string.market_sidebar_bids),
                icon = Icons.Default.Gavel,
                section = buying,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.SUBSCRIPTIONS,
                title = stringResource(R.string.market_sidebar_subscriptions),
                icon = Icons.Default.Repeat,
                section = buying,
            )
        )
        if (isSeller) {
            add(
                DrawerItem(
                    id = MarketApp.LISTINGS,
                    title = stringResource(R.string.market_sidebar_listings),
                    icon = Icons.Default.Inventory,
                    section = selling,
                )
            )
            add(
                DrawerItem(
                    id = MarketApp.SALES,
                    title = stringResource(R.string.market_sidebar_sales),
                    icon = Icons.Default.PointOfSale,
                    section = selling,
                )
            )
            add(
                DrawerItem(
                    id = MarketApp.SUBSCRIBERS,
                    title = stringResource(R.string.market_sidebar_subscribers),
                    icon = Icons.Default.Group,
                    section = selling,
                )
            )
        }
        add(
            DrawerItem(
                id = MarketApp.MESSAGES,
                title = stringResource(R.string.market_sidebar_inbox),
                icon = Icons.Default.Email,
                unread = inboxUnreadBadge,
                section = messages,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.REVIEWS,
                title = stringResource(R.string.market_sidebar_reviews),
                icon = Icons.Default.Star,
                section = messages,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.SELLER_SETTINGS,
                title = if (isSeller) {
                    stringResource(R.string.market_sidebar_seller_settings)
                } else {
                    stringResource(R.string.market_sidebar_become_seller)
                },
                icon = Icons.Default.Storefront,
                section = settings,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.ACCOUNT,
                title = stringResource(R.string.market_sidebar_account),
                icon = Icons.Default.AccountCircle,
                section = settings,
            )
        )
    }
}
