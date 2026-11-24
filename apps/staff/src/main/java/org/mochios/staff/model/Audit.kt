// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Row of the Comptroller `audit` table plus the resolved actor and object.
 * `data` is a per-action JSON string kept raw, its label keys already resolved
 * to the reader's language and its amounts in minor units beside a
 * `currency`; `object` is the id of the row the entry concerns, named by
 * [objectName] (a listing's title, an account's name; "" when it is gone) and,
 * for an account or staff row, [objectFingerprint]; `action` is a dotted key
 * such as `order.shipped`. [actor] is "staff" with a blank [actorName] where
 * the Comptroller hides which staff member acted.
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
    @SerializedName("actor_fingerprint") val actorFingerprint: String = "",
    @SerializedName("object_name") val objectName: String = "",
    @SerializedName("object_fingerprint") val objectFingerprint: String = "",
    val action: String = "",
    val data: String = "",
    val timestamp: Long = 0,
)

/** Result of `audit/list` and `audit/object`; [next] as on [AccountsListResponse]. */
data class AuditListResponse(
    val audit: List<AuditEntry> = emptyList(),
    val total: Long = 0,
    val next: String? = null,
)
