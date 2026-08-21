// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import org.mochios.android.model.Reaction
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType

/**
 * [ReactionCount]s for the shared ReactionBar. The feeds API's `reactions` omit
 * the viewer's own reaction (it arrives as `my_reaction`, unlike chat), so it
 * is folded back in.
 */
fun toReactionCounts(reactions: List<Reaction>, myReaction: String): List<ReactionCount> {
    val counts = LinkedHashMap<ReactionType, Int>()
    for (item in reactions) {
        val type = ReactionType.fromString(item.reaction) ?: continue
        counts[type] = (counts[type] ?: 0) + 1
    }
    val mine = myReaction.takeIf { value -> value.isNotEmpty() }
        ?.let { value -> ReactionType.fromString(value) }
    if (mine != null) {
        counts[mine] = (counts[mine] ?: 0) + 1
    }
    return counts.map { (type, count) ->
        ReactionCount(type, count, isMine = type == mine)
    }
}

/**
 * The viewer's reaction as a [ReactionType] for ReactionBar's
 * `currentReaction`; null when none or unknown.
 */
fun currentReactionType(myReaction: String): ReactionType? =
    myReaction.takeIf { value -> value.isNotEmpty() }
        ?.let { value -> ReactionType.fromString(value) }
