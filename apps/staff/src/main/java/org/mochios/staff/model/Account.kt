// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Account` in `apps/staff/web/src/types/accounts.ts`; `business`,
 * `seller`, `onboarded`, `verified` are 0/1 ints and `status` is free-form on
 * the wire.
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
 * Subset of [Account] returned by the suspend/unsuspend/ban/unban mutations.
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
 * Known `status` values; the wire string is free-form, so tolerate others.
 */
enum class AccountStatus {
    @SerializedName("active") ACTIVE,
    @SerializedName("suspended") SUSPENDED,
    @SerializedName("banned") BANNED,
}
