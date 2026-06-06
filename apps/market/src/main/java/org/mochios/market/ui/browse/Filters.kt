package org.mochios.market.ui.browse

import org.mochios.android.api.MochiError
import org.mochios.market.model.Category
import org.mochios.market.model.Listing

/**
 * Pickable filter axis on the browse screen. Each enum value backs one filter
 * pill in the header row and one section in the [FilterSheet]. The string
 * values stored in [HomeUiState.filters] are the wire values the search API
 * expects (lowercase, server-side enums in [org.mochios.market.model.Enums]).
 *
 * Note: [PRICE_MIN] and [PRICE_MAX] hold whole-currency amounts as typed by
 * the user (e.g. "5.00"). The search API does the minor-units conversion.
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
 * UI state for the market home / browse screen. Mirrors the web side's
 * `BrowseState` in `apps/market/web/src/routes/_authenticated/index.tsx`,
 * trimmed to fields the Android grid needs.
 *
 *  - [query] is the debounced search string sent to `listings/search`.
 *  - [filters] is a sparse map keyed by [Filter]; missing keys mean "all" for
 *    chip groups, "default sort" for [Filter.SORT], and "no bound" for the
 *    price range fields.
 *  - [listings] is the running page-by-page accumulator; [hasMore] tracks
 *    whether `offset + limit < total` after the most recent response.
 *  - [recentListings] is the resolved cache for the recently-viewed strip;
 *    the IDs themselves live in [org.mochios.market.lib.RecentlyViewedStore].
 *  - [filterSheetOpen] is the bottom sheet visibility flag; [focusedFilter]
 *    is the filter that should appear initially expanded.
 */
data class HomeUiState(
    val query: String = "",
    val filters: Map<Filter, String> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val listings: List<Listing> = emptyList(),
    val recentListings: List<Listing> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val error: MochiError? = null,
    val filterSheetOpen: Boolean = false,
    val focusedFilter: Filter? = null,
    /**
     * `true` once the caller's market account exists and isn't `inactive`.
     * Drives the welcome onboarding card on the home screen — when this is
     * `false` we render an "Activate your account" card above the search
     * row. The user can also dismiss the card without activating, which
     * flips this flag locally for the rest of the session so we don't
     * pester returning users every visit.
     */
    val accountActive: Boolean = true,
    /** `true` while the activate-account request is in flight. */
    val activatingAccount: Boolean = false,
    /**
     * `true` once the caller's loaded account has `seller == 1`. Gates the
     * Selling sidebar section and flips the seller-settings sidebar row label
     * between "Become a seller" and "Seller settings". Defaults `false` until
     * the account load completes.
     */
    val isSeller: Boolean = false,
)
