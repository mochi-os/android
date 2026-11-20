// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.browse

import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.market.model.Category
import org.mochios.market.model.Listing

/**
 * Filter axes on the browse screen. Values in [HomeUiState.filters] are the
 * search API's wire values; [PRICE_MIN] / [PRICE_MAX] hold major-unit amounts
 * as typed ("5.00") and are converted to minor units of [CURRENCY] (lowercase
 * ISO code, `usd` when unset) when the search is sent, since the server
 * compares them against the minor-unit price column of that currency alone.
 */
enum class Filter {
    CATEGORY,
    TYPE,
    CONDITION,
    PRICING,
    DELIVERY,
    PRICE_MIN,
    PRICE_MAX,
    CURRENCY,
    SORT,
}

/**
 * How many of the filters with a pill [filters] sets; any at all puts a dot on
 * the filter button. A price range counts once whichever bounds it has, and a
 * sort order counts since it gets a pill and a chip too; the currency the range
 * is read in is not a filter of its own.
 */
fun activeFilterCount(filters: Map<Filter, String>): Int {
    val attributes = listOf(
        Filter.CATEGORY,
        Filter.TYPE,
        Filter.CONDITION,
        Filter.PRICING,
        Filter.DELIVERY,
        Filter.SORT,
    ).count { filter -> filters.containsKey(filter) }
    val priceRange = filters.containsKey(Filter.PRICE_MIN) || filters.containsKey(Filter.PRICE_MAX)
    return attributes + if (priceRange) 1 else 0
}

/**
 * Browse screen state. [filters] is sparse: a missing key means "all", default
 * sort, or no price bound.
 */
data class HomeUiState(
    val query: String = "",
    val filters: Map<Filter, String> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val listings: List<Listing> = emptyList(),
    val recentListings: List<Listing> = emptyList(),
    /** String IDs of listings the user has saved locally; drives the card's bookmark fill. */
    val savedIds: Set<String> = emptySet(),
    /** The search's first page while nothing is listed, else its next page below the grid. */
    val load: LoadState = LoadState.Idle,
    val paging: Paging = Paging(),
    val filterSheetOpen: Boolean = false,
    val focusedFilter: Filter? = null,
    /**
     * Drives the activation card, shown only for `false`: an account read that
     * said inactive. Null until the account is read, and when the read fails.
     * Dismissing the card also sets it for the session, so `true` does not
     * prove the account is active.
     */
    val accountActive: Boolean? = null,
    /** `true` while the activate-account request is in flight. */
    val activatingAccount: Boolean = false,
)
