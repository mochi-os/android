// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

import com.google.gson.annotations.SerializedName

/**
 * A person entity returned by friend / user search. Matches the web
 * `User` type in `apps/people/web/src/api/types/friends.ts`.
 *
 * The relationship status is server-decorated on `friends/search` so the UI
 * can render the right action button without a second round-trip:
 *  - friend   — already friends, show Remove / View
 *  - invited  — outgoing invite pending, show Cancel
 *  - pending  — incoming invite waiting, show Accept / Ignore
 *  - self     — the searcher themselves, hide actions
 *  - none     — no existing relationship, show Add
 */
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
