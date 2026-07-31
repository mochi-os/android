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

/** Single-message form of [mergeMessages], for a WebSocket frame. */
fun <T> mergeMessage(
    existing: List<T>,
    incoming: T,
    key: (T) -> String,
    created: (T) -> Long,
): List<T> = mergeMessages(existing, listOf(incoming), key, created)
