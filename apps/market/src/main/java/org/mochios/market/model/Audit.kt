// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

/**
 * One row from the per-object audit timeline (`audit/object` endpoint).
 *
 * Mirrors `AuditEntry` in `apps/market/web/src/api/audit.ts`. The
 * server-emitted `action` is a dotted key (e.g. `order.shipped`,
 * `listing.warning`, `dispute.opened`) that the UI translates via
 * `components/shared/audit-labels.ts`. `data` is a JSON blob with
 * action-specific payload.
 *
 * Named `AuditEvent` per the task spec; web calls it `AuditEntry`. Both
 * names refer to the same wire shape.
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
 * Staff-issued warning attached to a listing.
 *
 * Mirrors the inline `Array<{ reason: string; created: number }>` shape on
 * `ListingDetailResponse.warnings` in `apps/market/web/src/api/listings.ts`.
 * Warnings are softer than removal — the listing stays active but a notice
 * is shown to the seller and (depending on visibility) to buyers.
 */
data class Warning(
    val reason: String = "",
    val created: Long = 0,
)
