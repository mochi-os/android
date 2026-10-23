// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.model

import com.google.gson.annotations.SerializedName
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.android.model.PlaceData
import org.mochios.android.model.Reaction

data class Post(
    val id: String = "",
    val feed: String = "",
    @SerializedName("feed_fingerprint") val feedFingerprint: String = "",
    @SerializedName("feed_name") val feedName: String = "",
    val body: String = "",
    @SerializedName("body_markdown") val bodyMarkdown: String = "",
    val data: PostData? = null,
    val created: Long = 0,
    @SerializedName("created_string") val createdString: String = "",
    val updated: Long = 0,
    val attachments: List<Attachment> = emptyList(),
    @SerializedName("my_reaction") val myReaction: String = "",
    val reactions: List<Reaction> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val up: Int = 0,
    val down: Int = 0,
    val read: Long = 0,
    val source: PostSource? = null,
    val score: Double? = null,
    /** Whether the reader follows the post; only the single-post view says. */
    val following: Boolean? = null,
    /** Where else the post is: its copies on other networks. */
    val copies: List<PostCopy> = emptyList(),
    /** How many times the post was boosted on other networks. */
    val boosts: Int = 0,
    /**
     * Who may see the post (public, unlisted or followers), its content
     * warning and sensitive flag, when it was pinned (0 for not) and its
     * language.
     */
    val visibility: String = "",
    val warning: String = "",
    val sensitive: Int = 0,
    val pinned: Long = 0,
    val language: String = "",
    /** For a post from another network: its author's id and its id there. */
    val author: String = "",
    val remote: String = "",
    /** The options the reader voted for in the post's poll. */
    val voted: List<Int> = emptyList(),
) {
    /** A post from another network, whose author a boost names. */
    val elsewhere: Boolean get() = remote.isNotEmpty() && author.isNotEmpty()
}

data class PostData(
    val checkin: PlaceData? = null,
    val travelling: TravellingData? = null,
    val memory: MemoryData? = null,
    val rss: RssData? = null,
    /** The post on another network the post's first link quotes. */
    val quote: PostQuote? = null,
    val poll: PostPoll? = null,
    /** For a post from another network: its author and, for a boost, who boosted it. */
    val person: PostPerson? = null,
    val boost: PostPerson? = null,
    val media: List<PostMedia>? = null,
    val emoji: List<PostEmoji>? = null,
)

data class RssData(
    val html: String = "",
    val image: String = "",
    val link: String = "",
    val source: String = "",
    val title: String = ""
)

data class TravellingData(
    val origin: PlaceData? = null,
    val destination: PlaceData? = null
)

data class MemoryData(
    @SerializedName("years_ago") val yearsAgo: Int = 0
)

data class PostSource(
    val name: String = "",
    val url: String = ""
)
