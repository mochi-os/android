// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The list a reload nobody asked for leaves. Everything up to and including
 * [reached], the furthest row the reader has landed on, stays exactly as it
 * is: an unread-only view's [fresh] list no longer holds the rows read this
 * session, and replacing the list with it would take the row on screen with
 * them and leave the pager on whatever now has its page number. What follows
 * comes from [fresh], less anything already kept, each row named by [key].
 *
 * With no [reached] row in the list the reader has no place to lose, and
 * [fresh] replaces it. Null when [fresh] adds nothing past [reached] - the
 * reader is further in than a first page goes - and the list should stand,
 * its paging cursor with it.
 */
fun <T> mergeReload(current: List<T>, fresh: List<T>, reached: String?, key: (T) -> String): List<T>? {
    val index = current.indexOfFirst { row -> key(row) == reached }
    if (index < 0) return fresh
    val kept = current.subList(0, index + 1)
    val merged = appendDistinct(kept, fresh, key)
    return if (merged.size == kept.size) null else merged
}

/**
 * How long a screen coming back waits for a socket of its that is opening
 * again to say so, before reading by itself: core may refuse the socket, at
 * the user's cap or with the server down, and then no word comes.
 */
val REOPEN_GRACE: Duration = 3.seconds
