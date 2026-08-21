// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Row of the Comptroller `audit` table plus `actor_name`. `data` is a
 * per-action JSON string kept raw; `object` is the id of the row the entry
 * concerns (account ids as fingerprints); `action` is a dotted key such as
 * `order.shipped`.
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

data class AuditListResponse(
    val audit: List<AuditEntry> = emptyList(),
    val total: Long = 0,
)
