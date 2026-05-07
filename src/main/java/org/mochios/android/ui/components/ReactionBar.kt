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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.mochios.android.R
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType
import org.mochios.android.model.label

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionBar(
    reactions: List<ReactionCount>,
    onReact: (String) -> Unit,
    onRemoveReaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (reaction in reactions) {
            val bgColor = if (reaction.isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val borderColor = if (reaction.isMine) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(shape)
                    .background(bgColor)
                    .border(1.dp, borderColor, shape)
                    .clickable {
                        if (reaction.isMine) onRemoveReaction() else onReact(reaction.type.name.lowercase())
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    imageVector = Icons.Default.Add,
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

@Composable
private fun ReactionPicker(
    onSelect: (ReactionType) -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(8.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(ReactionType.entries.toList()) { type ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(type) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${type.emoji} ${type.label()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
