// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import org.mochios.android.util.appendDistinct
import org.mochios.feeds.model.Post

/**
 * The list a reload nobody asked for leaves. Everything up to and including
 * [reached], the furthest post the reader has landed on, stays exactly as it
 * is: an unread-only view's [fresh] list no longer holds the posts read this
 * session, and replacing the list with it would take the post on screen with
 * them and leave the pager on whatever now has its page number. What follows
 * comes from [fresh], less anything already kept.
 *
 * With no [reached] post in the list the reader has no place to lose, and
 * [fresh] replaces it. Null when [fresh] adds nothing past [reached] - the
 * reader is further in than a first page goes - and the list should stand,
 * its paging cursor with it.
 */
internal fun mergeReload(current: List<Post>, fresh: List<Post>, reached: String?): List<Post>? {
    val index = current.indexOfFirst { post -> post.id == reached }
    if (index < 0) return fresh
    val kept = current.subList(0, index + 1)
    val merged = appendDistinct(kept, fresh) { post -> post.id }
    return if (merged.size == kept.size) null else merged
}
