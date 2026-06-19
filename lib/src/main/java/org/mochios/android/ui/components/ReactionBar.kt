package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import org.mochios.android.R
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionBar(
    reactions: List<ReactionCount>,
    onReact: (String) -> Unit,
    onRemoveReaction: () -> Unit,
    modifier: Modifier = Modifier,
    showAddButton: Boolean = true,
    maxVisible: Int? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val visibleReactions = maxVisible?.let { max -> reactions.take(max) } ?: reactions
    val hiddenCount = maxVisible
        ?.let { max -> reactions.drop(max).sumOf { reaction -> reaction.count } }
        ?: 0

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (reaction in visibleReactions) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
                    .clickable {
                        if (reaction.isMine) onRemoveReaction() else onReact(reaction.type.name.lowercase())
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text(
                    text = reaction.type.emoji,
                    style = MaterialTheme.typography.bodySmall
                )
                if (reaction.count > 1) {
                    Text(
                        text = " ${reaction.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (hiddenCount > 0) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "+$hiddenCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showAddButton) {
            Box {
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showPicker = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEmotions,
                        contentDescription = stringResource(R.string.reaction_add),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showPicker) {
                    Popup(
                        onDismissRequest = { showPicker = false },
                        alignment = Alignment.TopStart
                    ) {
                        ReactionPicker(
                            onSelect = { type ->
                                showPicker = false
                                onReact(type.name.lowercase())
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standalone smiley button that opens the [ReactionPicker]. Use where the
 * add-reaction affordance lives apart from the reaction pills — e.g. floating on
 * a chat bubble's corner, with the pills shown separately via
 * [ReactionBar] (`showAddButton = false`).
 *
 * When [currentReaction] is set, the button shows that reaction's emoji instead
 * of the generic smiley, reflecting the viewer's own reaction.
 *
 * @param onReact invoked with the chosen reaction key (lowercase [ReactionType] name).
 * @param currentReaction the viewer's existing reaction, shown on the button if any.
 */
@Composable
fun ReactionAddButton(
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentReaction: ReactionType? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val positionProvider = rememberBelowEndPositionProvider()

    Box(modifier) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { showPicker = true },
            contentAlignment = Alignment.Center
        ) {
            if (currentReaction != null) {
                Text(
                    text = currentReaction.emoji,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.EmojiEmotions,
                    contentDescription = stringResource(R.string.reaction_add),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showPicker) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { showPicker = false }
            ) {
                ReactionPicker(
                    onSelect = { type ->
                        showPicker = false
                        onReact(type.name.lowercase())
                    }
                )
            }
        }
    }
}

/**
 * A [PopupPositionProvider] that places the popup just below the anchor, aligned
 * to the anchor's trailing edge, and flips above when there's no room below.
 */
@Composable
private fun rememberBelowEndPositionProvider(gap: Dp = 4.dp): PopupPositionProvider {
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    return remember(gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (anchorBounds.right - popupContentSize.width)
                    .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val below = anchorBounds.bottom + gapPx
                val y = if (below + popupContentSize.height <= windowSize.height) {
                    below
                } else {
                    (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
                }
                return IntOffset(x, y)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionPicker(
    onSelect: (ReactionType) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)

    FlowRow(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .widthIn(max = 248.dp)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (type in ReactionType.entries) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelect(type) }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.emoji,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
