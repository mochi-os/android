// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

/** Ack wrapper for endpoints that just return `{ ok: true }`. */
data class OkResponse(val ok: Boolean = true)

/** Response from `POST -/create`. */
data class CreateWikiResponse(
    val id: String = "",
    val fingerprint: String = "",
    val home: String = "home",
)

/** Response from `POST -/subscribe`. */
data class JoinWikiResponse(
    val id: String = "",
    val fingerprint: String = "",
    val home: String = "home",
    val source: String? = null,
    val message: String? = null,
)

/** Per-renamed-page entry in `PageRenameResponse.renamed[]`. */
data class RenamedEntry(
    val old: String = "",
    val new: String = "",
)

/** Response from `POST {wiki}/-/:page/rename`. */
data class PageRenameResponse(
    val renamed: List<RenamedEntry> = emptyList(),
    @com.google.gson.annotations.SerializedName("updated_links")
    val updatedLinks: Int = 0,
)

/** Response from `GET {wiki}/-/replicas`. */
data class ReplicasResponse(val replicas: List<Replica> = emptyList())

/** Response from `GET {wiki}/-/users/search`. */
data class UsersSearchResponse(val results: List<User> = emptyList())

/** Response from `GET {wiki}/-/groups`. */
typealias WikiGroupsResponse = GroupsResponse
