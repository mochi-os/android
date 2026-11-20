// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.market.model.ShippingOption
import org.mochios.market.model.ShippingRegions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Shipping regions are codes: a country's code or a group's, named in the
 * reader's language, a region written before codes shown as it was; and an
 * option is offered to a buyer whose country its region covers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShippingTest {

    private val resources = RuntimeEnvironment.getApplication().resources

    private val regions = ShippingRegions(
        countries = listOf("FR", "GB", "DE", "US"),
        groups = mapOf("world" to emptyList(), "europe" to listOf("FR", "GB", "DE"), "eu" to listOf("FR", "DE")),
    )

    @Test
    fun `a region is named in the reader's language`() {
        assertEquals("United Kingdom", regionName("GB", resources, Locale.ENGLISH))
        assertEquals("Royaume-Uni", regionName("GB", resources, Locale.FRENCH))
        assertEquals("Europe", regionName("europe", resources, Locale.ENGLISH))
        assertEquals("Worldwide", regionName("world", resources, Locale.ENGLISH))
        assertEquals("Mainland only", regionName("Mainland only", resources, Locale.ENGLISH))
    }

    @Test
    fun `an editor offers the groups first, then the countries by name`() {
        val choices = regionChoices(regions, Locale.ENGLISH)
        assertEquals(listOf("world", "europe", "eu"), choices.take(3))
        assertEquals(listOf("FR", "DE", "GB", "US"), choices.drop(3))
    }

    @Test
    fun `countries come from the platform while the server's list is not to hand`() {
        assertTrue(countryChoices(null, Locale.ENGLISH).containsAll(listOf("GB", "JP", "BR")))
    }

    @Test
    fun `a region covers a country by code, a group by its members, the world all`() {
        assertEquals(true, covers("GB", "GB", regions))
        assertEquals(false, covers("GB", "FR", regions))
        assertEquals(true, covers("eu", "DE", regions))
        assertEquals(false, covers("eu", "GB", regions))
        assertEquals(true, covers("world", "US", regions))
        assertNull("a region written before codes is not judged", covers("Worldwide", "US", regions))
        assertNull("a group's members unknown", covers("eu", "DE", null))
        assertNull("no country chosen", covers("GB", "", regions))
    }

    @Test
    fun `the cheapest covering option is picked, and options not covering are left out`() {
        val options = listOf(
            ShippingOption(id = "s1", region = "GB", price = 300),
            ShippingOption(id = "s2", region = "europe", price = 900),
            ShippingOption(id = "s3", region = "Islands", price = 100),
        )
        assertEquals("s2", cheapestFor("FR", options, regions)?.id)
        assertEquals(listOf("s2", "s3"), optionsFor("FR", options, regions).map { it.id })
        assertEquals(options, optionsFor("", options, regions))
    }

    @Test
    fun `typing narrows by name without accents, or by code`() {
        assertTrue(regionMatches("CI", "Côte d’Ivoire", "cote"))
        assertTrue(regionMatches("GB", "United Kingdom", "gb"))
        assertTrue(!regionMatches("GB", "United Kingdom", "france"))
    }
}
