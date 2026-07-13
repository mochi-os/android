// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * One row from the staff appeals queue (`appeals/list`).
 *
 * Mirrors `Appeal` in `apps/staff/web/src/types/appeals.ts`. Appeals are
 * derived from the `moderation` log: a row with `action == "appealed"` and
 * no later `upheld` / `denied` decision is treated as pending. `actor` is
 * the seller who filed the appeal; `reason` is their justification; `score`
 * is the moderation score at the time of the original rejection.
 *
 * [listingModeration] mirrors the parent listing's `moderation` column at
 * the time the row was read, used to detect rapid state changes between
 * list/decide.
 */
data class Appeal(
    val id: String = "",
    val listing: String = "",
    val action: String = "",
    val score: Double = 0.0,
    val actor: String = "",
    val reason: String = "",
    val created: Long = 0,
    val title: String = "",
    val seller: String = "",
    @SerializedName("listing_moderation") val listingModeration: String = "",
    @SerializedName("seller_name") val sellerName: String = "",
)

/**
 * Result of `appeals/list`. Mirrors `AppealsResponse` in
 * `apps/staff/web/src/types/appeals.ts`. Named `AppealsListResponse` here
 * to match the existing api file imports.
 */
data class AppealsListResponse(
    val appeals: List<Appeal> = emptyList(),
    val total: Long = 0,
)

/**
 * Decision a moderator submits on an appeal. The Comptroller validates
 * against this set (see `event_staff_appeals_decide` in
 * `apps/comptroller/starlark/moderation.star`):
 *
 * - [UPHELD] re-activates the listing.
 * - [DENIED] keeps the listing rejected and surfaces the staff notes.
 */
enum class AppealDecision {
    @SerializedName("upheld") UPHELD,
    @SerializedName("denied") DENIED,
}
