// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The filter button's badge counts the filters that have a pill: a price range
 * once whatever its bounds, the sort order, and never the currency.
 */
class ActiveFilterCountTest {

    @Test
    fun `no filters count nothing`() {
        assertEquals(0, activeFilterCount(emptyMap()))
    }

    @Test
    fun `each attribute counts once`() {
        val filters = mapOf(
            Filter.CATEGORY to "3",
            Filter.TYPE to "physical",
            Filter.CONDITION to "used",
            Filter.PRICING to "fixed",
            Filter.DELIVERY to "pickup",
        )
        assertEquals(5, activeFilterCount(filters))
    }

    @Test
    fun `a price range counts once with either or both bounds`() {
        assertEquals(1, activeFilterCount(mapOf(Filter.PRICE_MIN to "5")))
        assertEquals(1, activeFilterCount(mapOf(Filter.PRICE_MAX to "50")))
        assertEquals(1, activeFilterCount(mapOf(Filter.PRICE_MIN to "5", Filter.PRICE_MAX to "50")))
    }

    @Test
    fun `a sort order counts`() {
        assertEquals(1, activeFilterCount(mapOf(Filter.SORT to "price_low")))
    }

    @Test
    fun `currency is not a filter`() {
        assertEquals(0, activeFilterCount(mapOf(Filter.CURRENCY to "eur")))
    }
}
