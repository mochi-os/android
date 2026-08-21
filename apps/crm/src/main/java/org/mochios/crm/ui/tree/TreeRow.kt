// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.DragState
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.draggableItem
import org.mochios.android.ui.components.dnd.dropTarget
import org.mochios.android.ui.components.dnd.isDragging
import org.mochios.android.ui.components.personAvatarPath
import org.mochios.crm.R
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.Person
import org.mochios.crm.ui.board.parseColor
import org.mochios.crm.ui.crm.CrmViewModel
import org.mochios.android.R as MochiR

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreeRow(
    node: TreeNode,
    viewModel: CrmViewModel,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onReparent: ((newParentId: String) -> Unit)? = null,
    dragState: DragState? = null,
    onDragDrop: ((sourceId: String, edge: DragEdge) -> Unit)? = null,
    // Passed in rather than read off viewModel.uiState here. A direct .value
    // read is a snapshot Compose does not subscribe to, so a row whose own
    // parameters had not changed kept rendering stale class definitions and
    // people. Parameters also keep the subscription in the screen, which
    // already collects: a row that collected for itself would recompose on
    // every state change, including whole-list ones, for each row on screen.
    crmDetails: CrmDetails?,
    people: List<Person>,
    // The UNFILTERED list: the reparent dialog must offer parents that the
    // current view filters out.
    allObjects: List<CrmObject>,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showReparentDialog by remember { mutableStateOf(false) }
    val indent = (node.depth * 24).dp
    val obj = node.obj
    val cardFields = viewModel.getCardFields(obj.objectClass)

    val isBeingDragged = dragState != null && dragState.isDragging(obj.id)
    val isDropTarget = dragState != null &&
        dragState.targetItemId == obj.id &&
        dragState.draggingItemId != null &&
        dragState.draggingItemId != obj.id
    val targetEdge = dragState?.targetEdge

    // Validate the drop target up-front so the modifier can reject self/descendant
    // drops without showing a misleading hover affordance.
    val acceptDrop: (String, DragEdge) -> Boolean = { sourceId, _ ->
        sourceId != obj.id && obj.id !in viewModel.collectDescendants(sourceId)
    }

    val dragHintLabel = if (dragState != null && onDragDrop != null) {
        stringResource(R.string.crm_drag_row)
    } else {
        ""
    }
    val dragModifier = if (dragState != null && onDragDrop != null) {
        Modifier
            .semantics { contentDescription = dragHintLabel }
            .draggableItem(state = dragState, itemId = obj.id)
            .dropTarget(
                state = dragState,
                itemId = obj.id,
                orientation = DropOrientation.Vertical,
                acceptedEdges = setOf(DragEdge.Top, DragEdge.Bottom, DragEdge.On),
                accept = acceptDrop,
                onDrop = { sourceId, edge -> onDragDrop(sourceId, edge) }
            )
    } else Modifier

    val edgeBorderModifier = if (isDropTarget) {
        when (targetEdge) {
            DragEdge.Top, DragEdge.Bottom -> Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            )
            DragEdge.On -> Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            ).background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
            else -> Modifier
        }
    } else Modifier

    val visualModifier = if (isBeingDragged) {
        Modifier
            .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.small)
            .scale(1.05f)
            .alpha(0.9f)
    } else Modifier

    // One card per object: class line, title, optional description, then a meta
    // row of field values. Fields land in slots by type — enumerated renders as
    // a colour dot plus label, a user field resolves to a member avatar, a date
    // formats and turns red once it is past, and the first multi-line text
    // field becomes the description.
    // The class names the field its objects are titled by, so leaving it in the
    // card fields would print the title a second time at the end of the meta row.
    val cls = crmDetails?.classes?.find { klass -> klass.id == obj.objectClass }
    val titleFieldId = cls?.title.orEmpty()
    val untitled = stringResource(R.string.crm_untitled)
    val title = obj.stringValue(titleFieldId).ifBlank { untitled }
    val bodyFields = cardFields.filter { field -> field.id != titleFieldId }
    // Prefer a multi-line text field for the description, but a single-line one
    // is the description in most schemas too — only the title is special.
    val descriptionField = bodyFields.firstOrNull { field ->
        field.fieldtype == "text" && field.rows > 1
    } ?: bodyFields.firstOrNull { field -> field.fieldtype == "text" }
    val description = descriptionField
        ?.let { field -> obj.stringValue(field.id).takeIf { value -> value.isNotBlank() } }
    // Ordered by type rather than by the class's field order, so the status and
    // priority always open the meta row directly under the description instead
    // of landing on a wrapped second line behind an owner or a date.
    val metaOrder = listOf("enumerated", "user", "date")
    val metaFields = bodyFields
        .filter { field ->
            field.id != descriptionField?.id && obj.stringValue(field.id).isNotBlank()
        }
        .sortedBy { field ->
            metaOrder.indexOf(field.fieldtype).takeIf { rank -> rank >= 0 } ?: metaOrder.size
        }

    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 3.dp, bottom = 3.dp)
            .then(dragModifier)
            .then(visualModifier)
            .then(edgeBorderModifier)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Class line: expander ahead of the class name, overflow menu on the
            // trailing edge. Only rows with children draw the expander — nothing
            // else shifts, so the title, description and meta stay on the card's
            // 12dp inset.
            // Title, description and meta line up under the class name: on a row
            // with children the expander pushes it right, so the text below
            // shifts by the same amount rather than sitting under the button.
            val bodyStart = if (node.hasChildren) 36.dp else 0.dp

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.hasChildren) {
                    MochiIconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (node.isExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = if (node.isExpanded) {
                                stringResource(MochiR.string.common_collapse)
                            } else {
                                stringResource(MochiR.string.common_expand)
                            },
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = cls?.name.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.weight(1f))

                Box {
                    MochiIconButton(
                        onClick = { showContextMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = stringResource(MochiR.string.common_more_options),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MochiDropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false }
                    ) {
                        if (onReparent != null) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.crm_tree_move)) },
                                onClick = {
                                    showContextMenu = false
                                    showReparentDialog = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.DriveFileMove, contentDescription = null) },
                            )
                        }
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(MochiR.string.common_delete)) },
                            onClick = {
                                showContextMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            destructive = true,
                        )
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = bodyStart)
            )

            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = bodyStart, top = 4.dp)
                )
            }

            if (metaFields.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = bodyStart, top = 6.dp)
                ) {
                    metaFields.forEach { field ->
                        MetaValue(
                            field = field,
                            objectClass = obj.objectClass,
                            value = obj.stringValue(field.id),
                            viewModel = viewModel,
                            people = people,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
            }
        }
    }

    if (showReparentDialog && onReparent != null) {
        val possibleParents = allObjects.filter { candidate -> candidate.id != obj.id }
        MochiAlertDialog(
            onDismissRequest = { showReparentDialog = false },
            title = stringResource(R.string.crm_tree_move_to_parent),
            content = {
                LazyColumn {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onReparent("")
                                    showReparentDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.crm_tree_root_level),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        HorizontalDivider()
                    }
                    items(possibleParents) { parent ->
                        val parentTitleField = crmDetails?.classes
                            ?.find { klass -> klass.id == parent.objectClass }
                            ?.title?.takeIf { fieldId -> fieldId.isNotBlank() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onReparent(parent.id)
                                    showReparentDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = parentTitleField
                                    ?.let { fieldId -> parent.stringValue(fieldId) }
                                    .orEmpty()
                                    .ifBlank { untitled },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        HorizontalDivider()
                    }
                }
            },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/**
 * One field value in a card's meta row, styled by field type: an enumerated
 * option shows its colour dot and name, a user field shows the member avatar
 * and name, a date is formatted and turns red once it is past, and anything
 * else falls back to plain muted text.
 */
@Composable
private fun MetaValue(
    field: CrmField,
    /** The owning object's class, which is what scopes an option id. */
    objectClass: String,
    value: String,
    viewModel: CrmViewModel,
    people: List<Person>,
    modifier: Modifier = Modifier,
) {
    // Each item aligns to its text baseline in the FlowRow (via [modifier]).
    val rowModifier = modifier
    when (field.fieldtype) {
        "enumerated" -> {
            val option = viewModel.getOptionsForObject(objectClass, field.id)
                .find { candidate -> candidate.id == value }
            Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                if (option != null && option.colour.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(parseColor(option.colour), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                MetaText(option?.name ?: value)
            }
        }

        "user" -> {
            val person = people.find { candidate -> candidate.id == value }
            val name = person?.name?.takeIf { personName -> personName.isNotBlank() } ?: value
            Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                // The field value is the person's entity id, which is what the
                // people app serves an avatar under — without src the avatar
                // could only ever be seeded initials, never the real photo.
                EntityAvatar(
                    name = name,
                    src = personAvatarPath(value),
                    seed = value,
                    size = 22.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                MetaText(name)
            }
        }

        "date" -> {
            val format = LocalFormat.current
            val seconds = value.toLongOrNull() ?: value.toDoubleOrNull()?.toLong()
            val label = seconds
                ?.let { epoch -> format.formatDate(epoch) }
                ?.takeIf { formatted -> formatted.isNotBlank() } ?: value
            val overdue = seconds != null &&
                seconds > 0 &&
                seconds < System.currentTimeMillis() / 1000
            Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (overdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        else -> {
            Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                MetaText(value)
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
