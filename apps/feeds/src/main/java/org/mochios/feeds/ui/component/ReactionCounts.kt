// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import org.mochios.android.model.Reaction
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType

/**
 * Map a feeds post/comment's reaction records into the [ReactionCount] list the
 * shared ReactionBar renders.
 *
 * The feeds API returns `reactions` WITHOUT the viewer's own reaction — that's
 * tracked separately in `my_reaction` (unlike chat, whose counts already
 * include the viewer). We fold the viewer's reaction back in so the owner's own
 * reaction shows as a pill and is included in the count. Shared by the feed
 * list card and the post-detail screen (posts and comments) so the count rule
 * stays in one place.
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
 * The viewer's current reaction as a [ReactionType], or null when they haven't
 * reacted (or the stored key is unknown). Pass to ReactionBar's `currentReaction`
 * so the add button shows the viewer's own reaction, like the chat bubble.
 */
fun currentReactionType(myReaction: String): ReactionType? =
    myReaction.takeIf { value -> value.isNotEmpty() }
        ?.let { value -> ReactionType.fromString(value) }
