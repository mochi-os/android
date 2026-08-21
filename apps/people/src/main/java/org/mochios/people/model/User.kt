// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

import com.google.gson.annotations.SerializedName

data class User(
    val `class`: String = "person",
    val created: Long = 0,
    val data: String = "",
    val fingerprint: String = "",
    @SerializedName("fingerprint_hyphens")
    val fingerprintHyphens: String = "",
    val id: String = "",
    val location: String = "",
    val name: String = "",
    val updated: Long = 0,
    val relationshipStatus: RelationshipStatus = RelationshipStatus.NONE
)

/**
 * Relationship from the current user's identity to a person, as decorated by
 * the friends-search endpoint. Wire format is lowercase strings.
 */
enum class RelationshipStatus {
    @SerializedName("friend") FRIEND,
    @SerializedName("invited") INVITED,
    @SerializedName("pending") PENDING,
    @SerializedName("self") SELF,
    @SerializedName("none") NONE,
}
