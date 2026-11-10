// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import org.mochios.android.model.Comment
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

/**
 * What the server stores for a reaction request. `post/react` and
 * `comment/react` are not toggles: any valid reaction replaces the viewer's,
 * and "none" (or an empty value) removes it. Mirroring that here keeps the
 * optimistic update honest.
 */
fun applied(reaction: String): String = if (reaction == "none") "" else reaction

/**
 * The reader's own reaction on the comment [id] anywhere in [comments], or
 * null when the comment is not there.
 */
fun reactionOf(comments: List<Comment>, id: String): String? {
    for (comment in comments) {
        if (comment.id == id) return comment.myReaction
        reactionOf(comment.children, id)?.let { return it }
    }
    return null
}

/**
 * [comments] with the reader's own reaction on the comment [id] set to
 * [reaction], wherever it sits. Other readers' reactions are held apart from
 * the reader's own, so they stay as they are.
 */
fun withReaction(comments: List<Comment>, id: String, reaction: String): List<Comment> =
    comments.map { comment ->
        val updated = if (comment.id == id) comment.copy(myReaction = reaction) else comment
        if (updated.children.isEmpty()) updated
        else updated.copy(children = withReaction(updated.children, id, reaction))
    }
