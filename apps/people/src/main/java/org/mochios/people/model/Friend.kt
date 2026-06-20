// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * A confirmed friend. The `identity` field is the local user's identity id —
 * the people app stores friendships keyed by (identity, friend id) so the
 * same friend can be added from different identities on the same server.
 */
data class Friend(
    val `class`: String = "person",
    val id: String = "",
    val identity: String = "",
    val name: String = "",
    // Friendship creation time (unix seconds), from the server's `created`
    // column. Drives the "Recently added" sort; 0 when absent.
    val created: Long = 0
)
