// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/**
 * How far a list read a page at a time has got. A server that pages by cursor
 * answers each page with `next`, the cursor to ask for what follows it, and ""
 * after the last page; the next page is then asked for with [cursor], which
 * cannot shift when rows are added ahead of it. A server that predates cursors
 * answers no `next` and pages by [page], and the list ends when a page is
 * empty or the rows read reach its `total`.
 */
data class Paging(
    /** The page to ask for next, for a server that pages by number. */
    val page: Int = 1,
    /** The cursor the last page answered: null before the first page, or from a server that sends none. */
    val next: String? = null,
    /** How many rows the pages read so far answered. */
    val read: Long = 0,
    /** Whether there is another page to ask for. */
    val more: Boolean = true,
) {
    /** The `after` to send for the next page, or null to page by [page]. */
    val cursor: String? get() = next?.takeIf { it.isNotEmpty() }

    /**
     * Where the list stands once a page of [count] rows has answered [total]
     * and [next]. A page with no rows ends the list whatever it claims, so a
     * stale total or cursor cannot keep it asking.
     */
    fun advance(count: Int, total: Long, next: String?): Paging {
        val read = read + count
        val more = count > 0 && if (next != null) next.isNotEmpty() else read < total
        return Paging(page = page + 1, next = next, read = read, more = more)
    }
}

/**
 * [page] added after these rows, leaving out a row whose [key] is already
 * listed. A row that moved between two page reads would otherwise be listed
 * twice, and a lazy list keyed by it refuses a repeated key.
 */
fun <T> List<T>.append(page: List<T>, key: (T) -> Any): List<T> {
    val seen = mapTo(HashSet(size + page.size)) { row -> key(row) }
    return this + page.filter { row -> seen.add(key(row)) }
}
