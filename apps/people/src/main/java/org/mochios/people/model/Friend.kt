// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * A confirmed friend. [identity] is the local identity the friendship belongs
 * to, not the friend's id.
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
