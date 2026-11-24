// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A seller's public profile carries their latest listings as card rows, each
 * with its first photo's id; the profile draws them with the listing card.
 */
class ProfileListingTest {

    private val profile = """
        {"id": "s1", "name": "Seller", "listings": [
          {"id": "l1", "title": "Teapot", "price": 1250, "currency": "gbp", "pricing": "fixed", "type": "physical", "photo": "p1"},
          {"id": "l2", "title": "Album", "price": 800, "currency": "eur", "pricing": "subscription", "type": "digital", "photo": ""}
        ]}
    """.trimIndent()

    @Test
    fun `the profile's listings are read as cards`() {
        val account = Gson().fromJson(profile, Account::class.java)
        assertEquals(listOf("l1", "l2"), account.listings.map { it.id })
        val teapot = account.listings[0].toListing()
        assertEquals("Teapot", teapot.title)
        assertEquals(1250L, teapot.price)
        assertEquals(Currency.GBP, teapot.currency)
        assertEquals(PricingModel.FIXED, teapot.pricing)
        assertEquals(ListingType.PHYSICAL, teapot.type)
        assertEquals("p1", teapot.photo?.id)
    }

    @Test
    fun `a listing with no photo has none to draw`() {
        val account = Gson().fromJson(profile, Account::class.java)
        assertNull(account.listings[1].toListing().photo)
    }

    // A subscription row does not say its period, so the card shows none.
    @Test
    fun `a row carries no period it was not sent`() {
        val account = Gson().fromJson(profile, Account::class.java)
        assertNull(account.listings[1].toListing().interval)
    }

    @Test
    fun `a profile without listings has none`() {
        assertEquals(emptyList<ProfileListing>(), Gson().fromJson("""{"id": "b1"}""", Account::class.java).listings)
    }
}
