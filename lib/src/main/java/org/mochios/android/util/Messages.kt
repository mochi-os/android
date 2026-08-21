// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/**
 * Merge [incoming] into [existing] by [key], ordered by [created].
 *
 * [key] must be a content key, not an id: a WebSocket frame carries no id, so a
 * synthetic one never matches the REST row and collides between two messages
 * from one sender in the same second.
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
 * Append [incoming] to [existing], dropping duplicate [key]s and preserving
 * arrival order: feeds and forums page over a server-side score, so a local
 * sort would discard it, and both return overlapping pages, which Compose
 * rejects as duplicate keys.
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
 * Fold a freshly fetched newest page into what is loaded, keeping scrollback.
 * [incoming] wins for an id in both, carrying edits, reactions and deletes.
 * Disjoint sets replace instead: stitching would hide a gap in the list.
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
