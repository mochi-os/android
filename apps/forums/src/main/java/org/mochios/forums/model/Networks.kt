// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/** A post elsewhere in forums carrying the same link as the one viewed. */
data class Crosspost(
    val post: String = "",
    val title: String = "",
    val forum: String = "",
    @SerializedName("forum_name") val forumName: String = "",
    val fingerprint: String = "",
)

/** The words of a link's card, as the post keeps them. */
data class PostEmbed(
    val title: String = "",
    val description: String = "",
    val site: String = "",
    val page: String = "",
)

/** A link post's card, decoded from its embed; empty for a post with none or a malformed one. */
fun postEmbed(embed: String): PostEmbed {
    if (embed.isBlank()) return PostEmbed()
    return runCatching { Gson().fromJson(embed, PostEmbed::class.java) }.getOrNull() ?: PostEmbed()
}

/**
 * One of the user's accounts on other networks: a hosted fediverse account a
 * community is joined as, or a linked Lemmy account whose communities and
 * settings are read in.
 */
data class NetworkAccount(
    val id: String = "",
    val type: String = "",
    val identifier: String = "",
    val label: String = "",
    val name: String = "",
    val hosted: Boolean = false,
)

/** A community on another server joined as a forum. */
data class CommunityJoined(
    val id: String = "",
    val fingerprint: String = "",
    val name: String = "",
    @SerializedName("already_subscribed") val alreadySubscribed: Boolean = false,
)

/** What reading a Lemmy account's communities brought in. */
data class CommunitiesAdded(
    val added: Int = 0,
    val communities: Int = 0,
    val posts: Int = 0,
)

/** What importing a Lemmy account's settings export brought in so far, and whether more follow. */
data class CommunitiesImported(
    val communities: Int = 0,
    val blocks: Int = 0,
    val saved: Int = 0,
    val more: Boolean = false,
)

/** A name the owner may put a community under: one of their handles on a domain. */
data class HandleName(
    val name: String = "",
    val domain: String = "",
)

/**
 * The forum as a community on the fediverse: whether it is published, its
 * permanent id and its address, the domains a name may be taken on and the
 * owner's handles a name may sit under.
 */
data class CommunityState(
    val published: Boolean = false,
    val address: String = "",
    val identifier: String = "",
    val domains: List<String> = emptyList(),
    val handles: List<HandleName> = emptyList(),
)

data class SensitiveState(val sensitive: Int = 0)
