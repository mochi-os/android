package org.mochios.android.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.R

data class Reaction(
    val feed: String = "",
    val post: String = "",
    val comment: String = "",
    val subscriber: String = "",
    val name: String = "",
    val reaction: String = ""
)

enum class ReactionType(val emoji: String) {
    LIKE("👍"),
    DISLIKE("👎"),
    LAUGH("😂"),
    AMAZED("😮"),
    LOVE("❤️"),
    SAD("😢"),
    ANGRY("😡"),
    AGREE("✅"),
    DISAGREE("❌");

    companion object {
        fun fromString(value: String): ReactionType? {
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}

@Composable
fun ReactionType.label(): String = stringResource(
    when (this) {
        ReactionType.LIKE -> R.string.reaction_like
        ReactionType.DISLIKE -> R.string.reaction_dislike
        ReactionType.LAUGH -> R.string.reaction_laugh
        ReactionType.AMAZED -> R.string.reaction_amazed
        ReactionType.LOVE -> R.string.reaction_love
        ReactionType.SAD -> R.string.reaction_sad
        ReactionType.ANGRY -> R.string.reaction_angry
        ReactionType.AGREE -> R.string.reaction_agree
        ReactionType.DISAGREE -> R.string.reaction_disagree
    }
)

data class ReactionCount(
    val type: ReactionType,
    val count: Int,
    val isMine: Boolean
)
