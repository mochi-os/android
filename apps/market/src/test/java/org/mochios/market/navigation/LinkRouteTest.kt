// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screens a `/market/...` notification link opens over the market home,
 * in the shapes the server writes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkRouteTest {

    private fun routes(path: String, query: Map<String, String> = emptyMap()) =
        MarketApp.linkRoutes(path.split('/')) { key -> query[key] }

    @Test
    fun `a listing link opens the listing`() {
        assertEquals(listOf("market/listing/l1"), routes("listings/l1"))
    }

    @Test
    fun `the client's own singular listing link still opens the listing`() {
        assertEquals(listOf("market/listing/l1"), routes("listing/l1"))
    }

    @Test
    fun `a message link opens the conversation over its listing`() {
        assertEquals(
            listOf("market/listing/l1", "market/messages/l1/t1"),
            routes("listings/l1/messages/t1"),
        )
    }

    @Test
    fun `a message link without a conversation opens the listing`() {
        assertEquals(listOf("market/listing/l1"), routes("listings/l1/messages"))
    }

    @Test
    fun `a bids link opens the bids`() {
        assertEquals(listOf("market/bids"), routes("bids"))
    }

    @Test
    fun `purchase, subscription and seller links open as before`() {
        assertEquals(listOf("market/purchases/o1"), routes("purchases/o1"))
        assertEquals(listOf(MarketApp.PURCHASES), routes("purchases"))
        assertEquals(listOf(MarketApp.SUBSCRIPTIONS), routes("subscriptions"))
        assertEquals(
            listOf(MarketApp.sellerSettings("c1", "s1", null, null)),
            routes("account/seller", mapOf("code" to "c1", "state" to "s1")),
        )
    }

    @Test
    fun `a sale link opens the sale, and sales alone the sales`() {
        assertEquals(listOf("market/sales/o1"), routes("sales/o1"))
        assertEquals(listOf(MarketApp.SALES), routes("sales"))
    }

    @Test
    fun `an order's message link opens the conversation over the order`() {
        assertEquals(
            listOf("market/purchases/o1", "market/messages/t1"),
            routes("purchases/o1", mapOf("thread" to "t1")),
        )
        assertEquals(
            listOf("market/sales/o1", "market/messages/t1"),
            routes("sales/o1", mapOf("thread" to "t1")),
        )
    }

    @Test
    fun `a thread that is not an id opens the order alone`() {
        assertEquals(listOf("market/sales/o1"), routes("sales/o1", mapOf("thread" to "../x")))
    }

    @Test
    fun `the seller's lists and the account open`() {
        assertEquals(listOf(MarketApp.SUBSCRIBERS), routes("subscribers"))
        assertEquals(listOf(MarketApp.LISTINGS), routes("listings"))
        assertEquals(listOf(MarketApp.ACCOUNT), routes("account"))
        assertEquals(listOf(MarketApp.ACCOUNT), routes("account", mapOf("stripe_error" to "x")))
    }

    @Test
    fun `an account link opens its profile`() {
        assertEquals(listOf("market/account/a1"), routes("account/a1"))
    }

    @Test
    fun `a link naming nothing opens the market home alone`() {
        assertEquals(emptyList<String>(), routes(""))
        assertEquals(emptyList<String>(), routes("elsewhere/x"))
    }

    @Test
    fun `a paid subscription returns to the subscriptions, an order to the purchases`() {
        assertEquals("/market/subscriptions", MarketApp.checkoutReturn("success", "subscription", null))
        assertEquals("/market/purchases", MarketApp.checkoutReturn("success", "order", null))
        assertEquals("/market/purchases", MarketApp.checkoutReturn("success", null, null))
    }

    @Test
    fun `a cancelled checkout returns to its listing, or the market for a listing that is not an id`() {
        assertEquals("/market/listing/l1", MarketApp.checkoutReturn("cancel", null, "l1"))
        assertEquals("/market", MarketApp.checkoutReturn("cancel", null, "l1/../x"))
        assertEquals("/market", MarketApp.checkoutReturn("cancel", null, null))
        assertEquals(null, MarketApp.checkoutReturn("elsewhere", null, null))
    }

    /**
     * Every link shape the Comptroller writes into a notification, as
     * `apps/comptroller/starlark` builds them, opens a screen over the home.
     */
    @Test
    fun `every link the Comptroller writes opens a screen`() {
        val written = listOf(
            "listings/l1" to emptyMap(),
            "listings/l1/messages/t1" to emptyMap(),
            "listings" to emptyMap(),
            "bids" to emptyMap(),
            "purchases/o1" to emptyMap(),
            "purchases/o1" to mapOf("thread" to "t1"),
            "sales/o1" to emptyMap(),
            "sales/o1" to mapOf("thread" to "t1"),
            "subscriptions" to emptyMap(),
            "subscribers" to emptyMap(),
            "account" to emptyMap(),
            "account/a1" to emptyMap(),
        )
        for ((path, query) in written) {
            assertTrue("/market/$path opens nothing", routes(path, query).isNotEmpty())
        }
    }
}
