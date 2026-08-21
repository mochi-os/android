// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

/**
 * Row from `audit/object`; mirrors `AuditEntry` in
 * `apps/market/web/src/api/audit.ts`. `action` is a dotted key
 * (`order.shipped`), `data` an action-specific JSON blob.
 */
data class AuditEvent(
    val id: String = "",
    val event: String = "",
    val app: String = "",
    val kind: String = "",
    val `object`: String = "",
    val role: String = "",
    val actor: String = "",
    @com.google.gson.annotations.SerializedName("actor_name") val actorName: String = "",
    val action: String = "",
    val data: String = "",
    val timestamp: Long = 0,
)

/**
 * Staff warning on a listing; mirrors the `warnings` entries on
 * `ListingDetailResponse` in web `api/listings.ts`.
 */
data class Warning(
    val reason: String = "",
    val created: Long = 0,
)
