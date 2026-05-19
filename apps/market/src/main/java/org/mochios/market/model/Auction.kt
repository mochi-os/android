package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * An auction attached to a listing.
 *
 * Mirrors `Auction` in `apps/market/web/src/types/auctions.ts`. `bid` is the
 * current high bid in minor units, `bidder` is the high bidder entity id (or
 * null if no bids yet). `reserve` / `instant` are 0 when unset. `has_reserve`
 * / `reserve_met` / `mine` are computed flags only present on some response
 * shapes — keep nullable.
 */
data class Auction(
    val id: Long = 0,
    val listing: Long = 0,
    val reserve: Long = 0,
    val instant: Long = 0,
    val opens: Long = 0,
    val closes: Long = 0,
    val bid: Long = 0,
    val bidder: String? = null,
    val bids: Long = 0,
    val extend: Long = 0,
    val extension: Long = 0,
    val status: AuctionStatus? = null,
    @SerializedName("has_reserve") val hasReserve: Boolean? = null,
    @SerializedName("reserve_met") val reserveMet: Boolean? = null,
    val mine: Boolean? = null,
)

/**
 * A bid placed against an auction.
 *
 * Mirrors `Bid` in `apps/market/web/src/types/auctions.ts`. `amount` /
 * `ceiling` are minor units (`ceiling` is the bidder's proxy max). Many
 * fields are denormalised onto the row when surfaced via `bids/mine` so the
 * UI can render a card without an extra listing fetch.
 */
data class Bid(
    val id: Long = 0,
    val auction: Long? = null,
    val listing: Long? = null,
    val bidder: String? = null,
    val amount: Long = 0,
    val ceiling: Long? = null,
    val status: BidStatus? = null,
    val created: Long = 0,
    val mine: Boolean? = null,
    val title: String? = null,
    @SerializedName("start_price") val startPrice: Long? = null,
    val currency: Currency? = null,
    @SerializedName("current_bid") val currentBid: Long? = null,
    val closes: Long? = null,
    @SerializedName("auction_status") val auctionStatus: AuctionStatus? = null,
)

/**
 * Shape of `bids/place` response.
 *
 * Mirrors `BidResponse` in `apps/market/web/src/types/auctions.ts`. `outbid`
 * means another active proxy bid topped this one; `instant` means the bid
 * matched the buy-it-now price and the auction closed in this bidder's
 * favour.
 */
data class BidResponse(
    val bid: Bid = Bid(),
    val outbid: Boolean? = null,
    val instant: Boolean? = null,
    @SerializedName("current_bid") val currentBid: Long? = null,
)

/**
 * Bid lifecycle status. Source: `BidStatus` in
 * `apps/market/web/src/types/common.ts`.
 */
enum class BidStatus {
    @SerializedName("active") ACTIVE,
    @SerializedName("outbid") OUTBID,
    @SerializedName("won") WON,
    @SerializedName("lost") LOST,
    @SerializedName("purchased") PURCHASED,
    @SerializedName("expired") EXPIRED,
}

/**
 * Auction lifecycle status. Source: `AuctionStatus` in
 * `apps/market/web/src/types/common.ts`.
 */
enum class AuctionStatus {
    @SerializedName("scheduled") SCHEDULED,
    @SerializedName("active") ACTIVE,
    @SerializedName("ended_sold") ENDED_SOLD,
    @SerializedName("ended_unsold") ENDED_UNSOLD,
    @SerializedName("payment_overdue") PAYMENT_OVERDUE,
    @SerializedName("cancelled") CANCELLED,
}
