// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.model

/**
 * A copy of a post on another network, published through one of the owner's
 * accounts: the account's address, the network, the copy's id there and its
 * page.
 */
data class PostCopy(
    val address: String = "",
    val network: String = "",
    val remote: String = "",
    val url: String = "",
)

/**
 * A person on another network: the author of a post from there, who boosted
 * it, or the author of a quoted post. A post's people name their page; a
 * quote's author names it as [url].
 */
data class PostPerson(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val page: String = "",
    val avatar: String = "",
    val url: String = "",
) {
    /** What the person is called: their name, else their address. */
    val label: String get() = name.ifEmpty { address }
}

/**
 * A post on another network that the post's first link quotes: who wrote it,
 * its text and its first picture, kept with the post as the writer saw it.
 */
data class PostQuote(
    val network: String = "",
    val url: String = "",
    val id: String = "",
    val text: String = "",
    val image: String = "",
    val created: Long = 0,
    val author: PostPerson? = null,
)

/** A post's poll: its options with their counts, when it closes (0 for never) and how many voted. */
data class PostPoll(
    val options: List<PollOption> = emptyList(),
    val multiple: Boolean = false,
    val expires: Long = 0,
    val voters: Int = 0,
)

data class PollOption(
    val name: String = "",
    val votes: Int = 0,
)

/** One of the media of a post from another network, by its address there. */
data class PostMedia(
    val url: String = "",
    val type: String = "",
    val name: String = "",
    val blurhash: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/** A custom emoji a post's text names by `:name:`. */
data class PostEmoji(
    val name: String = "",
    val url: String = "",
)

/**
 * One of the owner's accounts on another network a feed may publish to or read
 * from: linked on another server, or hosted here.
 */
data class PublishAccount(
    val id: String = "",
    val type: String = "",
    val identifier: String = "",
    val name: String = "",
    val server: String = "",
    val hosted: Boolean = false,
    val url: String = "",
)

/**
 * What a public feed publishes to: the owner's accounts, the ids chosen, how
 * many copies the feed holds on each, and the Bluesky custom feed it is
 * published as from each hosted Bluesky account.
 */
data class PublishState(
    val accounts: List<PublishAccount> = emptyList(),
    val publish: List<String> = emptyList(),
    val copies: Map<String, Int> = emptyMap(),
    val generators: Map<String, String> = emptyMap(),
)

/** The list as saved, and the copies kept on the accounts that left it, by account. */
data class PublishSaved(
    val publish: List<String> = emptyList(),
    val kept: Map<String, Int> = emptyMap(),
)

data class CopiesDeleted(val deleted: Int = 0)

data class Generators(val generators: Map<String, String> = emptyMap())

/** How many an import brought in so far, and whether more follow in the background. */
data class Imported(
    val imported: Int = 0,
    val more: Boolean = false,
    val total: Int = 0,
)

data class Backups(val files: List<String> = emptyList())

/** A keyword filter on a feed: posts naming the phrase are hidden from the reader who set it. */
data class Filter(
    val id: String = "",
    val phrase: String = "",
    val created: Long = 0,
)

data class Filters(val filters: List<Filter> = emptyList())

/**
 * Someone the user knows, from their contacts, with a handle on another
 * network: offered as a person to follow.
 */
data class Person(
    val contact: String = "",
    val name: String = "",
    val handle: String = "",
    val network: String = "",
    val id: String = "",
    val avatar: String = "",
    val page: String = "",
    val address: String = "",
    val person: String = "",
    val followed: Boolean = false,
)

data class People(val people: List<Person> = emptyList())

/**
 * What a handle or link looked up from search names: a person or community on
 * another network, with what a feed needs to follow them, or a post there.
 */
data class Lookup(
    val kind: String = "",
    val network: String = "",
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val avatar: String = "",
    val url: String = "",
    val quote: PostQuote? = null,
)

data class Pinned(val post: String = "", val pinned: Long = 0)

data class Voted(
    val post: String = "",
    val poll: PostPoll? = null,
    val voted: List<Int> = emptyList(),
)

data class Copies(val copies: List<PostCopy> = emptyList())

/**
 * What a post is made or edited with beyond its text: who may see it, its
 * content warning, the sensitive flag and its language. Each goes to the
 * server only when set, so an edit that says nothing leaves the post's own.
 */
data class PostFeatures(
    val visibility: String? = null,
    val warning: String? = null,
    val sensitive: Boolean? = null,
    val language: String? = null,
)

/** The poll a post is made with: its options, whether several may be chosen, and how long it runs in seconds. */
data class PollInput(
    val options: List<String>,
    val multiple: Boolean,
    val duration: Int,
)
