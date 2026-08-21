// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Auction` in `apps/market/web/src/types/auctions.ts`. Money in minor
 * units; `reserve` / `instant` are 0 when unset; the nullable flags appear only
 * on some responses.
 */
data class Auction(
    val id: String = "",
    val listing: String = "",
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
 * Mirrors `Bid` in web `types/auctions.ts`. `ceiling` is the bidder's proxy
 * maximum; the listing fields are denormalised onto `bids/mine` rows.
 */
data class Bid(
    val id: String = "",
    val auction: String? = null,
    val listing: String? = null,
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
 * `bids/place` response; mirrors `BidResponse` in web `types/auctions.ts`.
 * `outbid`: another proxy bid topped this one; `instant`: the bid met the
 * buy-now price and closed the auction.
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
