// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.market.R
import org.mochios.market.navigation.MarketApp

/**
 * Drawer body shared by the top-level market screens. [savedBadge] counts saved
 * listings with price drops or ending-soon auctions; zero hides it.
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
                icon = Icons.Outlined.Home,
                section = browse,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.SAVED,
                title = stringResource(R.string.market_sidebar_saved),
                icon = Icons.Outlined.Bookmark,
                unread = savedBadge,
                section = buying,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.PURCHASES,
                title = stringResource(R.string.market_sidebar_purchases),
                icon = Icons.Outlined.Receipt,
                section = buying,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.BIDS,
                title = stringResource(R.string.market_sidebar_bids),
                icon = Icons.Outlined.Gavel,
                section = buying,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.SUBSCRIPTIONS,
                title = stringResource(R.string.market_sidebar_subscriptions),
                icon = Icons.Outlined.Repeat,
                section = buying,
            )
        )
        if (isSeller) {
            add(
                DrawerItem(
                    id = MarketApp.LISTINGS,
                    title = stringResource(R.string.market_sidebar_listings),
                    icon = Icons.Outlined.Inventory,
                    section = selling,
                )
            )
            add(
                DrawerItem(
                    id = MarketApp.SALES,
                    title = stringResource(R.string.market_sidebar_sales),
                    icon = Icons.Outlined.PointOfSale,
                    section = selling,
                )
            )
            add(
                DrawerItem(
                    id = MarketApp.SUBSCRIBERS,
                    title = stringResource(R.string.market_sidebar_subscribers),
                    icon = Icons.Outlined.Group,
                    section = selling,
                )
            )
        }
        add(
            DrawerItem(
                id = MarketApp.MESSAGES,
                title = stringResource(R.string.market_sidebar_inbox),
                icon = Icons.Outlined.Email,
                unread = inboxUnreadBadge,
                section = messages,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.REVIEWS,
                title = stringResource(R.string.market_sidebar_reviews),
                icon = Icons.Outlined.Star,
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
                icon = Icons.Outlined.Storefront,
                section = settings,
            )
        )
        add(
            DrawerItem(
                id = MarketApp.ACCOUNT,
                title = stringResource(R.string.market_sidebar_account),
                icon = Icons.Outlined.AccountCircle,
                section = settings,
            )
        )
    }
}
