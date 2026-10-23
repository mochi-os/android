// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.navigation

import org.junit.Assert.assertEquals
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
    fun `a link naming nothing opens the market home alone`() {
        assertEquals(emptyList<String>(), routes(""))
        assertEquals(emptyList<String>(), routes("listings"))
        assertEquals(emptyList<String>(), routes("account/a1"))
        assertEquals(emptyList<String>(), routes("elsewhere/x"))
    }
}
