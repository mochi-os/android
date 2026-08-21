// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Order` in `apps/market/web/src/types/orders.ts`. Money in minor
 * units; lifecycle timestamps are 0 until the step happens; the denormalised
 * name fields appear only on list responses.
 */
data class Order(
    val id: String = "",
    val listing: String = "",
    val buyer: String = "",
    val seller: String = "",
    val type: String = "",
    val item: Long = 0,
    val postage: Long = 0,
    val total: Long = 0,
    val currency: Currency? = null,
    val fee: Long = 0,
    val payout: Long = 0,
    val stripe: String = "",
    val delivery: DeliveryMethod? = null,
    @SerializedName("address_name") val addressName: String = "",
    @SerializedName("address_line1") val addressLine1: String = "",
    @SerializedName("address_line2") val addressLine2: String = "",
    @SerializedName("address_city") val addressCity: String = "",
    @SerializedName("address_region") val addressRegion: String = "",
    @SerializedName("address_postcode") val addressPostcode: String = "",
    @SerializedName("address_country") val addressCountry: String = "",
    val option: String = "",
    val carrier: String = "",
    val tracking: String = "",
    val url: String = "",
    val downloads: Long = 0,
    val status: OrderStatus? = null,
    val created: Long = 0,
    val updated: Long = 0,
    val shipped: Long = 0,
    val delivered: Long = 0,
    val completed: Long = 0,
    val refunded: Long = 0,
    val title: String? = null,
    @SerializedName("listing_type") val listingType: String? = null,
    @SerializedName("seller_name") val sellerName: String? = null,
    @SerializedName("buyer_name") val buyerName: String? = null,
)

/**
 * Order lifecycle status. Source: `OrderStatus` in
 * `apps/market/web/src/types/common.ts`.
 */
enum class OrderStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("paid") PAID,
    @SerializedName("shipped") SHIPPED,
    @SerializedName("delivered") DELIVERED,
    @SerializedName("completed") COMPLETED,
    @SerializedName("disputed") DISPUTED,
    @SerializedName("refunded") REFUNDED,
    @SerializedName("cancelled") CANCELLED,
}

/**
 * `orders/create` / `orders/auction` response; `checkout_url` is the Stripe
 * Checkout URL, or the success URL when no payment is due.
 */
data class OrderCreateResponse(
    val order: Order? = null,
    @SerializedName("checkout_url") val checkoutUrl: String = "",
)

data class Tracking(
    val carrier: String = "",
    val tracking: String = "",
    val url: String = "",
)

/**
 * Staff-side refund row. `kind` is `"partial"` or `"full"`; `description` is
 * the seller's note.
 */
data class Refund(
    val id: String = "",
    val order: String = "",
    val amount: Long = 0,
    val currency: Currency? = null,
    val reason: String = "",
    val description: String = "",
    val kind: String = "",
    val stripe: String = "",
    val created: Long = 0,
)
