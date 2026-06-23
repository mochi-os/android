// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import androidx.compose.ui.graphics.Shape
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
    maxVisible: Int? = null,
    currentReaction: ReactionType? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    // The viewer's own reaction always sits at the right end of the bar — in the
    // slot the add button would otherwise hold. Others render first, then the
    // overflow chip, then the viewer's reaction (or the add button if they
    // haven't reacted). When the viewer has reacted the add button is hidden;
    // tapping their highlighted pill reopens the picker to change or clear it.
    val mine = reactions.firstOrNull { reaction -> reaction.isMine }
    val others = reactions.filter { reaction -> !reaction.isMine }
    val visibleOthers = maxVisible?.let { max ->
        val slots = if (mine != null) (max - 1).coerceAtLeast(0) else max
        others.take(slots)
    } ?: others
    val hiddenCount = others
        .filter { reaction -> reaction !in visibleOthers }
        .sumOf { reaction -> reaction.count }

    val onSelect: (ReactionType) -> Unit = { type ->
        showPicker = false
        onReact(type.name.lowercase())
    }
    val onClear = {
        showPicker = false
        onRemoveReaction()
    }
    val onDismiss = { showPicker = false }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (reaction in visibleOthers) {
            ReactionPill(
                reaction = reaction,
                shape = shape,
                onClick = { onReact(reaction.type.name.lowercase()) }
            )
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

        if (mine != null) {
            Box {
                ReactionPill(
                    reaction = mine,
                    shape = shape,
                    onClick = { showPicker = true }
                )
                if (showPicker) {
                    ReactionPickerPopup(currentReaction, onSelect, onClear, onDismiss)
                }
            }
        } else if (showAddButton) {
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
                    ReactionPickerPopup(currentReaction, onSelect, onClear, onDismiss)
                }
            }
        }
    }
}

/**
 * A single reaction pill — the emoji plus its count. The viewer's own reaction
 * is highlighted with the primary container colour so the bar marks which
 * reaction is theirs.
 */
@Composable
private fun ReactionPill(
    reaction: ReactionCount,
    shape: Shape,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(
                if (reaction.isMine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
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
                color = if (reaction.isMine) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * The reaction-picker popup, anchored just below the bar's right-most chip (the
 * viewer's reaction or the add button).
 */
@Composable
private fun ReactionPickerPopup(
    currentReaction: ReactionType?,
    onSelect: (ReactionType) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        popupPositionProvider = rememberBelowEndPositionProvider(),
        onDismissRequest = onDismiss
    ) {
        ReactionPicker(
            currentReaction = currentReaction,
            onSelect = onSelect,
            onClear = onClear
        )
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
    onSelect: (ReactionType) -> Unit,
    currentReaction: ReactionType? = null,
    onClear: () -> Unit = {}
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
                    // Highlight the viewer's current reaction in the picker.
                    .then(
                        if (type == currentReaction) {
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            Modifier
                        }
                    )
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
        // Clear-reaction option, only when the viewer has reacted.
        if (currentReaction != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onClear() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.reaction_remove),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
