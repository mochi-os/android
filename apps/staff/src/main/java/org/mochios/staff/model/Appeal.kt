// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Appeal` in `apps/staff/web/src/types/appeals.ts`: a `moderation` row
 * with `action == "appealed"` and no later `upheld`/`denied` decision. `score`
 * is the moderation score at the original rejection; `listingModeration` is the
 * listing's state when read.
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

data class AppealsListResponse(
    val appeals: List<Appeal> = emptyList(),
    val total: Long = 0,
)

/**
 * `upheld` re-activates the listing; `denied` keeps it rejected. Validated by
 * `event_staff_appeals_decide`.
 */
enum class AppealDecision {
    @SerializedName("upheld") UPHELD,
    @SerializedName("denied") DENIED,
}
