// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/**
 * Merge [incoming] game messages into [existing], dropping any whose [key] is
 * already present, and return the result ordered by [created].
 *
 * Exists because a game's chat arrives by two routes that cannot be reconciled
 * by id. The REST rows carry a server `mochi.uid()`; the WebSocket payload
 * carries no id at all, so each client synthesises one. Deduplicating on that
 * synthetic id fails both ways: it never matches the REST row, so an echo
 * arriving after a refetch renders twice, and it *does* collide between two
 * messages from one sender in the same second, silently dropping the second.
 *
 * So [key] should be a content key — created, body, name and type — which is
 * what the web client keys on, and for the same reason.
 *
 * Merging rather than replacing also preserves paged-in scrollback: a refresh
 * that assigns the newest page outright throws away everything the user
 * scrolled back to load.
 */
fun <T> mergeMessages(
    existing: List<T>,
    incoming: List<T>,
    key: (T) -> String,
    created: (T) -> Long,
): List<T> {
    if (incoming.isEmpty()) return existing
    val seen = existing.mapTo(HashSet()) { key(it) }
    val added = incoming.filter { seen.add(key(it)) }
    if (added.isEmpty()) return existing
    return (existing + added).sortedBy(created)
}

/**
 * Append [incoming] to [existing], dropping anything whose [key] is already
 * present, and preserving the order both arrived in.
 *
 * The order-preserving sibling of [mergeMessages]. Paginated lists cannot sort
 * client-side: feeds orders by a server-side relevance score and forums by
 * pinned-then-score, so imposing any local order would discard the ordering the
 * request asked for.
 *
 * Needed because both servers return overlapping pages by construction. Feeds
 * pages by offset over a time-decaying score and schedules a rescore of that
 * very column when page 1 is fetched; forums pages on a `created` cursor while
 * ordering by score, so any earlier post below the cursor repeats — as does
 * every pinned post, on every page. Appended bare, those duplicates become
 * duplicate LazyColumn keys, and Compose throws rather than rendering.
 */
fun <T> appendDistinct(
    existing: List<T>,
    incoming: List<T>,
    key: (T) -> String,
): List<T> {
    if (incoming.isEmpty()) return existing
    val seen = existing.mapTo(HashSet()) { key(it) }
    val added = incoming.filter { seen.add(key(it)) }
    return if (added.isEmpty()) existing else existing + added
}

/**
 * Fold a freshly fetched newest page into what is already loaded, so a refresh
 * keeps the scrollback the user paged in.
 *
 * A chat refetches its newest page on every inbound message, reaction and
 * delete. Assigning that page outright throws away everything the reader
 * scrolled back to load — so one message arriving wipes minutes of scrolling,
 * and the list then jumps to the bottom.
 *
 * [incoming] wins for any id in both, which is what carries an edit, a reaction
 * change or a delete tombstone onto a row already on screen.
 *
 * The one case where replacing IS right: when the two sets do not overlap at
 * all, the client has been away long enough that more than a page arrived, and
 * stitching them would present a contiguous list with an invisible hole in it.
 * Losing the scrollback is the better failure, so that case replaces.
 */
fun <T> mergeNewest(
    existing: List<T>,
    incoming: List<T>,
    id: (T) -> String,
    created: (T) -> Long,
): List<T> {
    if (incoming.isEmpty()) return existing
    if (existing.isEmpty()) return incoming
    val known = existing.mapTo(HashSet()) { id(it) }
    val overlaps = incoming.any { known.contains(id(it)) } ||
        incoming.minOf(created) <= existing.maxOf(created)
    if (!overlaps) return incoming
    val byId = LinkedHashMap<String, T>(existing.size + incoming.size)
    for (item in existing) byId[id(item)] = item
    for (item in incoming) byId[id(item)] = item
    return byId.values.sortedWith(compareBy(created, id))
}

/** Single-message form of [mergeMessages], for a WebSocket frame. */
fun <T> mergeMessage(
    existing: List<T>,
    incoming: T,
    key: (T) -> String,
    created: (T) -> Long,
): List<T> = mergeMessages(existing, listOf(incoming), key, created)
