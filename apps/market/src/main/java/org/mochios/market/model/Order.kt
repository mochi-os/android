package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * A buyer-side or seller-side order row.
 *
 * Mirrors `Order` in `apps/market/web/src/types/orders.ts`. Money fields
 * (`item`, `postage`, `total`, `fee`, `payout`) are in minor units. The
 * timestamp fields default to `0` when the relevant lifecycle step has not
 * happened yet — never -1, never null.
 *
 * `title` / `listing_type` / `seller_name` / `buyer_name` are denormalised
 * helpers attached to list responses; they may be absent on a single-order
 * get from older server versions, so they stay nullable.
 */
data class Order(
    val id: Long = 0,
    val listing: Long = 0,
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
    val option: Long = 0,
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
 * Shape of `orders/create` / `orders/auction` responses.
 *
 * Mirrors `OrderCreateResponse` in `apps/market/web/src/types/orders.ts`.
 * `checkout_url` is the Stripe Checkout URL the SPA should navigate to (or
 * the success URL if no payment is required).
 */
data class OrderCreateResponse(
    val order: Order? = null,
    @SerializedName("checkout_url") val checkoutUrl: String = "",
)

/**
 * Shipping / tracking information attached to an order once the seller marks
 * it as shipped. Convenience projection of the `carrier` / `tracking` / `url`
 * fields on `Order` — used by the UI when only the tracking details are
 * needed without the whole order row.
 */
data class Tracking(
    val carrier: String = "",
    val tracking: String = "",
    val url: String = "",
)

/**
 * Refund record. Refunds in market are surfaced via the `refunded` flag on
 * `Order` plus the seller-issued `orders/refund` response; this struct exists
 * to model the staff-side refund row when surfaced separately (Stripe refund
 * id, partial amount, reason).
 *
 * `kind` is `"partial"` or `"full"` (server-classified). `description` is
 * the seller-supplied free-text note shown in the prior-refunds list.
 */
data class Refund(
    val id: Long = 0,
    val order: Long = 0,
    val amount: Long = 0,
    val currency: Currency? = null,
    val reason: String = "",
    val description: String = "",
    val kind: String = "",
    val stripe: String = "",
    val created: Long = 0,
)
