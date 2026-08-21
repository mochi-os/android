// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Response shapes mirroring the inline types in `apps/market/web/src/api/`,
 * below the `{ data: ... }` envelope the request layer unwraps.
 */

// ---- Generic helpers ----------------------------------------------------

/** Acknowledgement-only payload for endpoints that just return `{ ok: true }`. */
data class OkResponse(val ok: Boolean = true)

/**
 * The `{ data: T }` envelope, for callers that unwrap by hand.
 */
data class DataEnvelope<T>(val data: T? = null)

// ---- Listings -----------------------------------------------------------

/**
 * Result of `listings/search`. Mirrors `SearchResponse` in
 * `apps/market/web/src/api/listings.ts`.
 */
data class ListingsSearchResponse(
    val listings: List<Listing> = emptyList(),
    val total: Long = 0,
    val limit: Long = 0,
    val offset: Long = 0,
)

/** Alias name used by [MarketApi.listMyListings]. */
typealias ListingsListResponse = ListingsMineResponse

/**
 * `listings/get` result; mirrors `ListingDetailResponse` in web
 * `api/listings.ts`. `my_order` / `my_reservation` are the caller's existing
 * position.
 */
data class ListingDetailResponse(
    val listing: Listing = Listing(),
    val shipping: List<ShippingOption> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val seller: AccountSummary = AccountSummary(),
    val auction: Auction? = null,
    val bids: List<Bid> = emptyList(),
    val threads: Long = 0,
    @SerializedName("my_order") val myOrder: MyOrder? = null,
    @SerializedName("my_reservation") val myReservation: MyReservation? = null,
    @SerializedName("appeal_pending") val appealPending: Boolean = false,
    val warnings: List<Warning> = emptyList(),
)

/** Inline `my_order` projection on [ListingDetailResponse]. */
data class MyOrder(
    val id: String = "",
    val status: String = "",
)

/** Inline `my_reservation` projection on [ListingDetailResponse]. */
data class MyReservation(
    val id: String = "",
    val type: String = "",
    val created: Long = 0,
)

/** Result of `listings/mine`. */
data class ListingsMineResponse(
    val listings: List<Listing> = emptyList(),
    val total: Long = 0,
)

/**
 * `listings/relist` result: the new draft plus the source auction's settings
 * for pre-filling the publish form.
 */
data class RelistResponse(
    val listing: Listing = Listing(),
    val auction: RelistAuction? = null,
)

/**
 * Auction settings echoed back by `listings/relist` for pre-populating the
 * publish form. Mirrors `RelistAuction` in `api/listings.ts`.
 */
data class RelistAuction(
    val reserve: Long = 0,
    val instant: Long = 0,
    val opens: Long = 0,
    val closes: Long = 0,
    val extend: Long = 0,
    val extension: Long = 0,
)

/**
 * `listings/removal_check` result, for tailoring the removal confirmation;
 * mirrors `RemovalCheck` in web `api/listings.ts`.
 */
data class RemovalCheck(
    @SerializedName("has_active_auction") val hasActiveAuction: Boolean = false,
    @SerializedName("active_bidders") val activeBidders: Long = 0,
    @SerializedName("active_subscribers") val activeSubscribers: Long = 0,
    @SerializedName("has_active_orders") val hasActiveOrders: Boolean = false,
)

// ---- Saved --------------------------------------------------------------
/**
 * `saved/list` result; rows are full Listing snapshots, so no per-id refetch is
 * needed.
 */
data class SavedListResponse(
    val saved: List<Listing> = emptyList(),
    val total: Long = 0,
)

/** Result of `saved/add`, `saved/remove`, `saved/clear`. */
data class SavedToggleResponse(
    val saved: Boolean = false,
)

// ---- Orders -------------------------------------------------------------

/** Result of `orders/purchases` and `orders/sales`. */
data class OrdersListResponse(
    val orders: List<Order> = emptyList(),
    val total: Long = 0,
)

/**
 * `orders/get` result; mirrors `ordersApi.get` in web `api/orders.ts`.
 * `can_review` is the server's verdict on leaving a review now.
 */
data class OrderDetailResponse(
    val order: Order = Order(),
    val listing: Listing = Listing(),
    val assets: List<Asset> = emptyList(),
    val dispute: Dispute? = null,
    val refunds: List<Refund> = emptyList(),
    val evidence: List<DisputeEvidence> = emptyList(),
    val review: Review? = null,
    @SerializedName("peer_review") val peerReview: Review? = null,
    @SerializedName("can_review") val canReview: Boolean = false,
)

/**
 * Result of `orders/dispute`. The endpoint returns the updated order; the
 * dispute row is fetched separately via `disputes/get`.
 */
data class OrderDisputeResponse(val order: Order = Order())

/** Result of `orders/refund` — updated order plus the dispute if any. */
data class OrderRefundResponse(
    val order: Order = Order(),
    val dispute: Dispute? = null,
)

// ---- Bids ---------------------------------------------------------------

/** Result of `bids/mine`. */
data class BidsMineResponse(
    val bids: List<Bid> = emptyList(),
    val total: Long = 0,
)

/** Alias name used by [MarketApi.listMyBids]. */
typealias BidsListResponse = BidsMineResponse

// ---- Photos -------------------------------------------------------------

/**
 * `photos/list` returns a bare array.
 */
typealias PhotosListResponse = List<Photo>

// ---- Assets -------------------------------------------------------------

/**
 * Result of `assets/external`. Web returns a bare `Asset[]` (the full asset
 * list after the new external row is added).
 */
typealias AssetListResponse = List<Asset>

// ---- Subscriptions ------------------------------------------------------

/**
 * Result of `subscriptions/create` — Stripe Checkout URL plus the pending
 * subscription row.
 */
data class SubscriptionCreateResponse(
    val subscription: Subscription = Subscription(),
    @SerializedName("checkout_url") val checkoutUrl: String = "",
)

/** Result of `subscriptions/mine` and `subscriptions/subscribers`. */
data class SubscriptionsListResponse(
    val subscriptions: List<Subscription> = emptyList(),
    val total: Long = 0,
)

/** Alias name used by [MarketApi]. */
typealias SubscriptionListResponse = SubscriptionsListResponse

// ---- Threads ------------------------------------------------------------

/** Result of `threads/mine`. */
data class ThreadsListResponse(
    val threads: List<MarketThread> = emptyList(),
    val total: Long = 0,
)

/**
 * Result of `threads/get`. The listing projection is intentionally narrow
 * — just enough for the conversation header card.
 */
data class ThreadDetailResponse(
    val thread: MarketThread = MarketThread(),
    val messages: List<Message> = emptyList(),
    val listing: ThreadListingPreview = ThreadListingPreview(),
)

/** Narrow listing projection on [ThreadDetailResponse]. */
data class ThreadListingPreview(
    val id: String = "",
    val title: String = "",
    val price: Long = 0,
    val currency: Currency? = null,
    val pricing: PricingModel? = null,
    val status: ListingStatus? = null,
)

// ---- Reviews ------------------------------------------------------------

/**
 * Result of `reviews/account`, `reviews/inbox` and `reviews/sent`; the rows'
 * denormalised fields differ by perspective.
 */
data class ReviewsListResponse(
    val reviews: List<Review> = emptyList(),
    val total: Long = 0,
)

/**
 * Result of `reviews/inbox`. Same shape as [ReviewsListResponse].
 */
typealias InboxReviewListResponse = ReviewsListResponse

/** Result of `reviews/sent`. Same shape as [ReviewsListResponse]. */
typealias SentReviewListResponse = ReviewsListResponse

// ---- Audit --------------------------------------------------------------

/** Result of `audit/object`. */
data class AuditListResponse(
    val audit: List<AuditEvent> = emptyList(),
    val total: Long = 0,
)

// ---- Accounts -----------------------------------------------------------

/**
 * Result of `accounts/stripe/onboarding`. The SPA must navigate the top
 * window (not the sandboxed iframe) to this URL.
 */
data class StripeOnboardingResponse(val url: String = "")

// ---- Assets -------------------------------------------------------------

/**
 * `assets/download` body when the asset is hosted externally; Mochi-hosted
 * downloads stream bytes with no JSON body.
 */
data class AssetDownloadMetadata(
    val hosting: String = "",
    val asset: AssetDownloadInfo? = null,
)

/** Asset reference returned inside [AssetDownloadMetadata]. */
data class AssetDownloadInfo(
    val filename: String = "",
    val mime: String = "",
    val reference: String = "",
)


/** Envelope shape for an externally-hosted asset: `{"data": {...}}`. */
data class AssetDownloadEnvelope(
    val data: AssetDownloadMetadata? = null,
)

/**
 * Result of an asset download: an external reference or the bytes, decided by
 * the response.
 */
sealed interface AssetDownload {

    /** Hosted elsewhere: open [url] rather than saving anything. */
    data class External(val url: String) : AssetDownload

    /** Mochi-hosted: the file itself, to be saved and opened. */
    data class Bytes(
        val fileName: String,
        val mime: String,
        val body: okhttp3.ResponseBody,
    ) : AssetDownload
}
