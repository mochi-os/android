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

/** The user's hosted fediverse accounts: a comment goes out from one, and a boost is made from the first. */
internal fun hostedFediverse(accounts: List<PublishAccount>): List<PublishAccount> =
    accounts.filter { it.hosted && it.type == "fediverse" }

/** Whether a comment on the post may also go out from one of the user's hosted fediverse accounts. */
internal fun repliesElsewhere(post: Post): Boolean =
    post.copies.any { it.network == "fediverse" } || post.elsewhere

/** Whether the post is boosted from [account]: it holds a copy there under the account's address. */
internal fun boostedFrom(post: Post, account: PublishAccount?): Boolean =
    account != null && post.copies.any { it.network == "fediverse" && it.address == account.identifier }

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
