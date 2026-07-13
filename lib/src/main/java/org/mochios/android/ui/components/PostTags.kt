// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.R

/**
 * A post tag as the shared tag UI needs it, independent of each module's own
 * wire model.
 *
 * @property qid      Entity id behind the tag; non-null tags can be tuned in the
 *                    user's interest model. Free-text tags render as plain labels.
 * @property interest -100..100 interest weight, colouring the label. Null when
 *                    the tag has no interest signal.
 */
data class TagItem(
    val id: String,
    val label: String,
    val qid: String? = null,
    val interest: Float? = null,
)

/**
 * Tag icon + count that opens a popup listing the post's tags. Entity-backed
 * (`qid`) tags expose interest tuning, [onRemoveTag] adds a per-tag delete, and
 * [onAddTag] puts a text field at the foot of the popup so a tag can be typed
 * without a second dialog. Mirrors web's PostTagsTooltip; shared by feeds and
 * forums.
 *
 * Pass null for a callback to hide the affordance the viewer lacks permission
 * for.
 */
@Composable
fun PostTagsButton(
    tags: List<TagItem>,
    modifier: Modifier = Modifier,
    onAddTag: ((String) -> Unit)? = null,
    onRemoveTag: ((String) -> Unit)? = null,
    onAdjustInterest: ((qid: String, direction: String) -> Unit)? = null,
    horizontalPadding: Dp = 6.dp,
    iconSize: Dp = 18.dp,
    countStyle: TextStyle = MaterialTheme.typography.labelMedium,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var open by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { open = true }
            .padding(horizontal = horizontalPadding, vertical = 4.dp)
    ) {
        val hasTags = tags.isNotEmpty()
        Icon(
            if (hasTags) Icons.Filled.LocalOffer else Icons.Outlined.LocalOffer,
            contentDescription = stringResource(R.string.tags_label),
            modifier = Modifier.size(iconSize),
            tint = tint
        )
        if (hasTags) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${tags.size}", style = countStyle, color = tint)
        }
    }

    DropdownMenu(
        expanded = open,
        onDismissRequest = { open = false; newLabel = "" },
    ) {
        if (tags.isEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tags_none)) },
                enabled = false,
                onClick = {},
            )
        }
        tags.forEach { tag ->
            TagMenuRow(
                tag = tag,
                // Close after a tap so the action visibly registers.
                onAdjustInterest = onAdjustInterest?.let { adjust ->
                    { qid, direction -> adjust(qid, direction); open = false }
                },
                onRemove = onRemoveTag?.let { remove ->
                    { id -> remove(id); open = false }
                },
            )
        }
        if (onAddTag != null) {
            if (tags.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            val submit = {
                val label = newLabel.trim()
                if (label.isNotEmpty()) {
                    onAddTag(label)
                    newLabel = ""
                    open = false
                }
            }
            OutlinedTextField(
                value = newLabel,
                onValueChange = { value -> newLabel = value },
                label = { Text(stringResource(R.string.tags_add)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    IconButton(onClick = submit, enabled = newLabel.isNotBlank()) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.tags_add),
                        )
                    }
                },
                modifier = Modifier
                    .width(260.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TagMenuRow(
    tag: TagItem,
    onAdjustInterest: ((qid: String, direction: String) -> Unit)?,
    onRemove: ((id: String) -> Unit)?,
) {
    // Interest tuning only applies to entity-backed (qid) tags — these feed the
    // user-global interest model. Free-text tags render as plain labels.
    val qid = tag.qid
    val tunable = !qid.isNullOrEmpty() && onAdjustInterest != null
    val labelColor = tag.interest?.let { interestColor(it) } ?: MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(260.dp)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Text(
            text = "#${tag.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (tunable && qid != null && onAdjustInterest != null) {
            IconButton(onClick = { onAdjustInterest(qid, "up") }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.ThumbUp,
                    contentDescription = stringResource(R.string.tags_interest_up),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { onAdjustInterest(qid, "down") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ThumbDown,
                    contentDescription = stringResource(R.string.tags_interest_down),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { onAdjustInterest(qid, "remove") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.tags_interest_clear),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (onRemove != null) {
            IconButton(onClick = { onRemove(tag.id) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.tags_delete),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Continuous interest scale: red (-100) → blue (0) → green (+100). Mirrors the
 * web PostTags hue ramp so the same interest reads as the same colour on both.
 */
private fun interestColor(interest: Float): Color {
    val hue = (240.0 - (interest / 100.0) * 120.0).toFloat().coerceIn(0f, 360f)
    return Color.hsl(hue, 0.8f, 0.45f)
}
