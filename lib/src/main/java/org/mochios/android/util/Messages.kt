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

/** Single-message form of [mergeMessages], for a WebSocket frame. */
fun <T> mergeMessage(
    existing: List<T>,
    incoming: T,
    key: (T) -> String,
    created: (T) -> Long,
): List<T> = mergeMessages(existing, listOf(incoming), key, created)
