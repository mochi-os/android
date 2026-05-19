package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * One row from the moderation log (`moderation/log`).
 *
 * Mirrors `ModerationEntry` in `apps/staff/web/src/types/moderation.ts`.
 * `action` is one of the lowercase strings written by the Comptroller into
 * the `moderation` table (`warning`, `removed`, `appealed`, `upheld`,
 * `denied`, ...). `actor_name` is resolved server-side.
 */
data class ModerationEntry(
    val id: Long = 0,
    val listing: Long = 0,
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
 * Score thresholds returned by `moderation/thresholds` and accepted by
 * `moderation/set_thresholds`. Mirrors `Thresholds` in
 * `apps/staff/web/src/types/moderation.ts`. Listings scoring at or below
 * [low] are auto-approved; listings scoring at or above [high] are held for
 * manual review. The Comptroller's scoring is monotone (low = safe, high =
 * risky).
 */
data class Thresholds(
    val low: Int = 0,
    val high: Int = 0,
)

/**
 * Convenience alias for [Thresholds] when the call site wants the historical
 * "auto-approve / hold" wording. The wire field names remain `low` / `high`
 * — both UI labels point at the same numbers.
 */
typealias ModerationThresholds = Thresholds
