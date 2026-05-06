package org.mochi.android.util

import org.mochi.android.model.Reaction
import org.mochi.android.model.ReactionCount
import org.mochi.android.model.ReactionType

fun List<Reaction>.toReactionCounts(myReaction: String): List<ReactionCount> {
    val countMap = mutableMapOf<String, Int>()
    for (reaction in this) {
        val key = reaction.reaction.lowercase()
        countMap[key] = (countMap[key] ?: 0) + 1
    }

    val myReactionLower = myReaction.lowercase()

    return countMap.mapNotNull { (key, count) ->
        val type = ReactionType.fromString(key) ?: return@mapNotNull null
        ReactionCount(
            type = type,
            count = count,
            isMine = key == myReactionLower
        )
    }.sortedByDescending { it.count }
}
