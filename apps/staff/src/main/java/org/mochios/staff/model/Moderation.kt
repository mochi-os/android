// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Row of the Comptroller `moderation` table; `action` is `warning`, `removed`,
 * `appealed`, `upheld`, `denied`, ...
 */
data class ModerationEntry(
    val id: String = "",
    val listing: String = "",
    @SerializedName("listing_title") val listingTitle: String = "",
    val action: String = "",
    val score: Double = 0.0,
    val actor: String = "",
    @SerializedName("actor_name") val actorName: String = "",
    val reason: String = "",
    val created: Long = 0,
)

/**
 * Result of `moderation/log`. Mirrors `ModerationLogResponse` in
 * `apps/staff/web/src/types/moderation.ts`.
 */
data class ModerationLogResponse(
    val log: List<ModerationEntry> = emptyList(),
    val total: Long = 0,
)

/**
 * Moderation score thresholds: scores below [low] auto-approve, [high] and
 * above are held; higher score = riskier.
 */
data class Thresholds(
    val low: Int = 0,
    val high: Int = 0,
)

typealias ModerationThresholds = Thresholds
