package org.mochios.android.util

import org.mochios.android.model.Reaction
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType

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
