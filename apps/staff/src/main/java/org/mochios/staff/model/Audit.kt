// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * One row from the audit log. Mirrors the schema declared in
 * `apps/comptroller/starlark/comptroller.star` (table `audit`) plus the
 * `actor_name` field that `audit_enrich` adds at read time.
 *
 * `data` is a free-form JSON payload that varies per `action` (e.g.
 * `{"reason": "...", "notes": "..."}` for `report.actioned`). The
 * Comptroller writes it as a JSON-encoded string; the Android client keeps
 * it as the raw string because the per-action shape is open and the audit
 * UI only renders a small subset.
 *
 * `kind` is one of the audit timeline kinds (`order`, `listing`,
 * `subscription`, `dispute`, `account`, `report`, `review`, `audit`...);
 * `object` is the id of the row the entry concerns (numeric ids as decimal
 * strings, account ids as fingerprints); `action` is a dotted key such as
 * `order.shipped`, `listing.warning`, `report.actioned`.
 *
 * Property name workaround: `object` is a Kotlin soft keyword; backticks
 * keep it usable as an identifier.
 */
data class AuditEntry(
    val id: String = "",
    val event: String = "",
    val app: String = "",
    val kind: String = "",
    val `object`: String = "",
    val role: String = "",
    val actor: String = "",
    @SerializedName("actor_name") val actorName: String = "",
    val action: String = "",
    val data: String = "",
    val timestamp: Long = 0,
)

/**
 * Result of `audit/list` and `audit/object`. Mirrors the response shape of
 * `event_staff_audit_list` (`{audit, total}`) in
 * `apps/comptroller/starlark/comptroller.star`.
 */
data class AuditListResponse(
    val audit: List<AuditEntry> = emptyList(),
    val total: Long = 0,
)
