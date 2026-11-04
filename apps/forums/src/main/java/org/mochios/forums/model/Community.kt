// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import java.net.URI

// A community on another network is named by its title, which two communities
// can share ("test" on lemmy.ml and on sh.itjust.works), so where it is named
// its server goes beside it, and its screen's title gives its handle in full.

/** The server a community lives on: its handle's host (name@host), else its address's; "" for a forum of ours. */
fun communityServer(handle: String, address: String = ""): String {
    val at = handle.lastIndexOf('@')
    if (at > 0 && at < handle.length - 1) return handle.substring(at + 1)
    if (!address.startsWith("https://") && !address.startsWith("http://")) return ""
    return runCatching { URI(address).host }.getOrNull().orEmpty()
}

/** A community's handle as its network writes one, !name@host; "" for none. */
fun communityHandle(handle: String): String = if (handle.isEmpty()) "" else "!$handle"

/**
 * The server to show beside a forum's name: a community's, whose id is its
 * address; never a forum of ours, whose id is an entity's, even one published
 * as a group with an address of its own.
 */
fun forumServer(forum: Forum): String = communityServer(forum.handle, forum.id)
