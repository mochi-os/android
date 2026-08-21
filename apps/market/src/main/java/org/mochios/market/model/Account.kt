// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Market account; mirrors `Account` in `apps/market/web/src/types/accounts.ts`.
 * `stripe` is the connected-account id.
 */
data class Account(
    val id: String = "",
    val name: String = "",
    val biography: String = "",
    val business: Int = 0,
    val company: String = "",
    val vat: String = "",
    @SerializedName("address_name") val addressName: String = "",
    @SerializedName("address_line1") val addressLine1: String = "",
    @SerializedName("address_line2") val addressLine2: String = "",
    @SerializedName("address_city") val addressCity: String = "",
    @SerializedName("address_region") val addressRegion: String = "",
    @SerializedName("address_postcode") val addressPostcode: String = "",
    @SerializedName("address_country") val addressCountry: String = "",
    val location: String = "",
    val seller: Int = 0,
    val stripe: String = "",
    @SerializedName("stripe_testmode") val stripeTestmode: Boolean = false,
    val onboarded: Int = 0,
    val verified: Int = 0,
    val status: String = "",
    val reason: String = "",
    val rating: Double = 0.0,
    val reviews: Long = 0,
    val sales: Long = 0,
    val created: Long = 0,
    val updated: Long = 0,
)

/**
 * Public seller-card projection; mirrors `AccountSummary` in web
 * `types/accounts.ts`. Nullable fields are omitted by the server depending on
 * caller privileges.
 */
data class AccountSummary(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val status: String? = null,
    val verified: Int? = null,
    val onboarded: Int? = null,
    val rating: Double = 0.0,
    val reviews: Long = 0,
    val sales: Long = 0,
    val created: Long = 0,
)

/**
 * Free-form account moderation status; the server treats this as an open
 * string set, but the UI switches on these known values.
 */
enum class AccountStatus {
    @SerializedName("active") ACTIVE,
    @SerializedName("warned") WARNED,
    @SerializedName("suspended") SUSPENDED,
    @SerializedName("banned") BANNED,
}

/**
 * Stripe Connect status; mirrors `accountsApi.stripeStatus` in
 * `apps/market/web/src/api/accounts.ts`.
 */
data class StripeStatus(
    @SerializedName("charges_enabled") val chargesEnabled: Boolean = false,
    @SerializedName("payouts_enabled") val payoutsEnabled: Boolean = false,
)

/**
 * Platform-fee disclosure from `accounts/fees`; `platform` is a percentage.
 * Stripe's own fees are deliberately not embedded - the UI links to the Stripe
 * dashboard instead.
 */
data class AccountFees(
    val platform: Double = 0.0,
)

/**
 * How an order's `total` decomposes into item, postage, fee and payout; minor
 * units.
 */
data class FeeBreakdown(
    val item: Long = 0,
    val postage: Long = 0,
    val total: Long = 0,
    val fee: Long = 0,
    val payout: Long = 0,
    val currency: Currency? = null,
)
