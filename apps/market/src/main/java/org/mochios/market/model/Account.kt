package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Per-identity market account (profile + seller state).
 *
 * Mirrors `Account` in `apps/market/web/src/types/accounts.ts`. `business` /
 * `seller` / `onboarded` / `verified` are 0/1 ints (server convention).
 * `stripe` is the connected-account id; `stripe_testmode` indicates the
 * account is wired to Stripe's test environment.
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
 * Compact account projection used by the public seller card on listing
 * detail pages.
 *
 * Mirrors `AccountSummary` in `apps/market/web/src/types/accounts.ts`. Many
 * fields are nullable because the server omits them depending on caller
 * privileges (e.g. internal staff endpoints expose `status`, public ones
 * don't).
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
 * Stripe Connect status for the current account.
 *
 * Mirrors the inline response shape in `accountsApi.stripeStatus` in
 * `apps/market/web/src/api/accounts.ts`. `charges_enabled` indicates the
 * account can accept payments; `payouts_enabled` indicates Stripe will
 * forward funds to the bank account.
 */
data class StripeStatus(
    @SerializedName("charges_enabled") val chargesEnabled: Boolean = false,
    @SerializedName("payouts_enabled") val payoutsEnabled: Boolean = false,
)

/**
 * Platform-fee disclosure surfaced by `accounts/fees`.
 *
 * Mirrors `Fees` in `apps/market/web/src/types/accounts.ts`. `platform` is
 * the Mochi platform fee percentage (e.g. `5.0` for 5%); per-currency
 * Stripe minimums and chargeback fees are intentionally not embedded here
 * (web links users to the Stripe dashboard for those — see
 * `feedback_dont_quote_third_party_rates`).
 */
data class AccountFees(
    val platform: Double = 0.0,
)

/**
 * Itemised breakdown of how an order's `total` decomposes into item, postage,
 * platform fee, and seller payout. Used by the buying and selling detail
 * pages to render the receipt. Money values in minor units.
 */
data class FeeBreakdown(
    val item: Long = 0,
    val postage: Long = 0,
    val total: Long = 0,
    val fee: Long = 0,
    val payout: Long = 0,
    val currency: Currency? = null,
)
