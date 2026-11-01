// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import org.mochios.android.model.Comment
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PublishAccount

/** The poll limits the server holds a post to. */
internal const val POLL_OPTIONS_MINIMUM = 2
internal const val POLL_OPTIONS_MAXIMUM = 8
internal const val POLL_OPTION_MAXIMUM = 100
internal const val WARNING_MAXIMUM = 500

/** How long a poll runs, in seconds, as the composer offers it. */
internal val POLL_DURATIONS = listOf(300, 1800, 3600, 21600, 86400, 259200, 604800)
internal const val POLL_DURATION_DEFAULT = 86400

/** The visibilities a post may be made with, public first. */
internal val VISIBILITIES = listOf("public", "unlisted", "followers")

/** The user's hosted fediverse accounts, which a person there is followed through. */
internal fun hostedFediverse(accounts: List<PublishAccount>): List<PublishAccount> =
    accounts.filter { it.hosted && it.type == "fediverse" }

/** The user's hosted accounts a post from another network is boosted from: the fediverse's and Bluesky's. */
internal fun boostAccounts(accounts: List<PublishAccount>): List<PublishAccount> =
    accounts.filter { it.hosted && (it.type == "fediverse" || it.type == "bluesky") }

/**
 * The account a post from another network is boosted from: the first of [accounts] on the
 * post's own network, Bluesky for one whose id there is an at:// URI, else the fediverse.
 */
internal fun boostAccount(post: Post, accounts: List<PublishAccount>): PublishAccount? {
    val network = if (post.remote.startsWith("at://")) "bluesky" else "fediverse"
    return accounts.firstOrNull { it.type == network }
}

/** Whether the post is on the fediverse: a copy of it there, or it came from there. */
internal fun onFediverse(post: Post): Boolean =
    post.copies.any { it.network == "fediverse" } || post.elsewhere

/** Whether the post is boosted from [account]: it holds a copy on the account's network under its address. */
internal fun boostedFrom(post: Post, account: PublishAccount?): Boolean =
    account != null && post.copies.any { it.network == account.type && it.address == account.identifier }

/**
 * Whether a comment is by a person on another network, whom the owner may
 * block, mute or report: their id there holds a colon, which a Mochi identity
 * never does.
 */
internal fun outsider(comment: Comment): Boolean = comment.authorId.contains(':')

/** A poll as composed in the editor. */
data class PollDraft(
    val options: List<String> = listOf("", ""),
    val multiple: Boolean = false,
    val duration: Int = POLL_DURATION_DEFAULT,
)

/** Whether a poll as composed may be sent: every option written, no twins. */
internal fun pollValid(poll: PollDraft?): Boolean {
    if (poll == null) return true
    val options = poll.options.map { it.trim() }
    if (options.size < POLL_OPTIONS_MINIMUM || options.size > POLL_OPTIONS_MAXIMUM) return false
    if (options.any { it.isEmpty() }) return false
    return options.map { it.lowercase() }.toSet().size == options.size
}
