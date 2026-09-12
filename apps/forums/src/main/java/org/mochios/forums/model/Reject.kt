// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import androidx.annotation.StringRes
import org.mochios.forums.R

/**
 * The message for the `reason` a `post/reject` or `comment/reject` event
 * carries, mirroring web's `rejectMessage`. A rejection arrives for either a
 * post or a comment and the wording has to match, so a refused comment does
 * not tell the author their "post" was turned down.
 */
@StringRes
fun rejectMessage(reason: String?, comment: Boolean): Int = when (reason) {
    "access_denied" ->
        if (comment) R.string.forums_reject_comment_access
        else R.string.forums_reject_post_access

    "restricted" ->
        if (comment) R.string.forums_reject_comment_restricted
        else R.string.forums_reject_post_restricted

    "rate_limited" ->
        if (comment) R.string.forums_reject_comment_rate
        else R.string.forums_reject_post_rate

    "invalid" ->
        if (comment) R.string.forums_reject_comment_invalid
        else R.string.forums_reject_post_invalid

    "duplicate" ->
        if (comment) R.string.forums_reject_comment_duplicate
        else R.string.forums_reject_post_duplicate

    "forum_not_found" -> R.string.forums_reject_forum_missing

    // "server_error" and anything the owner sends that this version does
    // not know: the submission did not survive the forum's server.
    else ->
        if (comment) R.string.forums_reject_comment_server
        else R.string.forums_reject_post_server
}
