// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * A friendship invitation waiting on one side or the other. [id] is the other
 * person's entity id and [identity] the local identity the invite belongs to;
 * [direction] is `from` for one received and `to` for one sent, which the list
 * it arrives in also says.
 */
data class FriendInvite(
    val identity: String = "",
    val id: String = "",
    val direction: String = "",
    val name: String = "",
    val updated: Long = 0,
)
