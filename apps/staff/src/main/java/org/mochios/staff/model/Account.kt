package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Marketplace account row as exposed by staff endpoints.
 *
 * Mirrors `Account` in `apps/staff/web/src/types/accounts.ts`. `business`,
 * `seller`, `onboarded`, `verified` are 0/1 ints (server convention); `status`
 * is one of the lowercase strings in [AccountStatus] (free-form string on the
 * wire — tolerate unknown values).
 */
data class Account(
    val id: String = "",
    val name: String = "",
    val biography: String = "",
    val business: Int = 0,
    val company: String = "",
    val vat: String = "",
    val seller: Int = 0,
    val stripe: String = "",
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
 * Result of `accounts/list`. Mirrors `AccountsListResponse` in
 * `apps/staff/web/src/types/accounts.ts`.
 */
data class AccountsListResponse(
    val accounts: List<Account> = emptyList(),
    val total: Long = 0,
)

/**
 * Narrow account projection returned by the suspend/unsuspend/ban/unban
 * mutations. The Comptroller responds with the updated account row but the
 * Android client only consumes the moderation-relevant fields; the wider
 * [Account] shape is also assignment-compatible if a future call site needs
 * everything.
 */
data class AccountSummary(
    val id: String = "",
    val name: String = "",
    val status: String = "",
    val reason: String = "",
    @SerializedName("seller") val seller: Int = 0,
    @SerializedName("verified") val verified: Int = 0,
    val rating: Double = 0.0,
    val reviews: Long = 0,
    val sales: Long = 0,
    val created: Long = 0,
    val updated: Long = 0,
)

/**
 * Account moderation status. The server treats `status` as an open string
 * set (see `apps/comptroller/starlark/staff.star`), but these are the values
 * the UI switches on; unknown statuses must be tolerated.
 */
enum class AccountStatus {
    @SerializedName("active") ACTIVE,
    @SerializedName("suspended") SUSPENDED,
    @SerializedName("banned") BANNED,
}
