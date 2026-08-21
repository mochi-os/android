// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.browse

import org.mochios.android.api.MochiError
import org.mochios.market.model.Category
import org.mochios.market.model.Listing

/**
 * Filter axes on the browse screen. Values in [HomeUiState.filters] are the
 * search API's wire values; [PRICE_MIN] / [PRICE_MAX] hold whole-currency
 * amounts as typed ("5.00") - the server converts to minor units.
 */
enum class Filter {
    CATEGORY,
    TYPE,
    CONDITION,
    PRICING,
    DELIVERY,
    PRICE_MIN,
    PRICE_MAX,
    SORT,
    TAG,
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
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val error: MochiError? = null,
    val filterSheetOpen: Boolean = false,
    val focusedFilter: Filter? = null,
    /**
     * Drives the activation card. Dismissing the card also flips this for the
     * session, so `true` does not prove the account is active.
     */
    val accountActive: Boolean = true,
    /** `true` while the activate-account request is in flight. */
    val activatingAccount: Boolean = false,
    val isSeller: Boolean = false,
)
