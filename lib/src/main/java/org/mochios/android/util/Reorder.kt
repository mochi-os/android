// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/**
 * This list rearranged to match [order], with every entry's rank rewritten to
 * its new index.
 *
 * Applied to a screen's own state the moment a reorder is sent, so a second
 * reorder issued before the server's reload lands composes on the new order
 * instead of the one the row captured. Entries missing from [order] keep their
 * relative position at the end.
 *
 * @param order The ids in the order to show them.
 * @param id Reads an entry's id.
 * @param withRank Returns a copy of an entry carrying the given rank.
 * @return The entries in their new order, renumbered from zero.
 */
fun <T> List<T>.reorderedTo(
    order: List<String>,
    id: (T) -> String,
    withRank: (T, Int) -> T,
): List<T> {
    val wanted = order.withIndex().associate { (index, value) -> value to index }
    return sortedBy { entry -> wanted[id(entry)] ?: Int.MAX_VALUE }
        .mapIndexed { index, entry -> withRank(entry, index) }
}
