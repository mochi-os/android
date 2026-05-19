package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Envelope types for endpoints that return a list + total alongside other
 * top-level fields, plus the assorted small wrappers each endpoint defines
 * inline on the web side.
 *
 * All shapes here mirror the inline anonymous types declared in
 * `apps/market/web/src/api/`. They cover the outer object below the
 * standard `{ data: ... }` wrapper that Retrofit / the request module
 * already unpacks.
 */

// ---- Generic helpers ----------------------------------------------------

/** Acknowledgement-only payload for endpoints that just return `{ ok: true }`. */
data class OkResponse(val ok: Boolean = true)

/**
 * Standard `{ data: T }` envelope used by every market endpoint. Retrofit
 * usually unwraps this — exposed here for the cases where the caller wants
 * to inspect the envelope directly (rare).
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
 * Result of `listings/get`. Mirrors `ListingDetailResponse` in
 * `apps/market/web/src/api/listings.ts`. `my_order` / `my_reservation`
 * encode any active position the caller already holds (for hiding buy
 * controls / showing tracking link).
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
    val id: Long = 0,
    val status: String = "",
)

/** Inline `my_reservation` projection on [ListingDetailResponse]. */
data class MyReservation(
    val id: Long = 0,
    val type: String = "",
    val created: Long = 0,
)

/** Result of `listings/mine`. */
data class ListingsMineResponse(
    val listings: List<Listing> = emptyList(),
    val total: Long = 0,
)

/**
 * Result of `listings/relist`. The original listing is duplicated as a new
 * draft; if the source was an auction, the auction settings are returned so
 * the UI can pre-populate the publish form.
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
 * Result of `listings/removal_check`. Used by the SPA to tailor the removal
 * confirmation dialog (e.g. "this will end an active auction with 3
 * bidders"). Mirrors `RemovalCheck` in `api/listings.ts`.
 */
data class RemovalCheck(
    @SerializedName("has_active_auction") val hasActiveAuction: Boolean = false,
    @SerializedName("active_bidders") val activeBidders: Long = 0,
    @SerializedName("active_subscribers") val activeSubscribers: Long = 0,
    @SerializedName("has_active_orders") val hasActiveOrders: Boolean = false,
)

// ---- Orders -------------------------------------------------------------

/** Result of `orders/purchases` and `orders/sales`. */
data class OrdersListResponse(
    val orders: List<Order> = emptyList(),
    val total: Long = 0,
)

/**
 * Result of `orders/get`. Mirrors the inline anonymous shape in
 * `ordersApi.get` (`apps/market/web/src/api/orders.ts`). `can_review`
 * indicates whether the caller is allowed to leave a review on this order
 * right now (lifecycle + role + dedup).
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
 * Result of `photos/list`. Web returns a bare `Photo[]` (no envelope); the
 * Android API treats the array as the response so [MarketRepository.listPhotos]
 * returns `List<Photo>` directly.
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
    val id: Long = 0,
    val title: String = "",
    val price: Long = 0,
    val currency: Currency? = null,
    val pricing: PricingModel? = null,
    val status: ListingStatus? = null,
)

// ---- Reviews ------------------------------------------------------------

/**
 * Result of `reviews/account`, `reviews/inbox`, `reviews/sent`. All three
 * endpoints return the same shape; the `Review` rows carry different
 * denormalised fields depending on the caller's perspective.
 */
data class ReviewsListResponse(
    val reviews: List<Review> = emptyList(),
    val total: Long = 0,
)

/**
 * Result of `reviews/inbox`. Same shape as [ReviewsListResponse] but kept as a
 * separate alias for API clarity — the rows carry `listing_title` /
 * `reviewer_name` denormalised on inbox responses (see `Review` model).
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
 * Result of `assets/download` when the asset is hosted externally. The
 * server returns the metadata payload (including the external reference
 * URL) instead of streaming bytes. Mochi-hosted downloads stream raw bytes
 * and never produce a JSON body — callers branch on the response
 * content-type.
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

/** JSON envelope used by [MarketApi.downloadAssetInfo]. */
typealias AssetDownloadResponse = AssetDownloadMetadata
