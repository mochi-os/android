package org.mochios.crm.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.sp
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.DragState
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.draggableItem
import org.mochios.android.ui.components.dnd.dropTarget
import org.mochios.android.ui.components.dnd.isDragging
import org.mochios.crm.R
import org.mochios.crm.model.CrmObject
import org.mochios.crm.ui.crm.CrmViewModel
import org.mochios.android.R as MochiR

private const val MAX_NESTING_DEPTH = 3

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BoardCard(
    obj: CrmObject,
    viewModel: CrmViewModel,
    borderFieldId: String?,
    childrenByParent: Map<String, List<CrmObject>>,
    columnFieldId: String = "",
    rowFieldId: String? = null,
    depth: Int = 0,
    cardDragState: DragState? = null,
    cardIndexInColumn: Int = -1,
    columnObjectsForDrop: List<CrmObject> = emptyList(),
    targetColumnId: String = "",
    onClick: () -> Unit
) {
    var showMoveSheet by rememberSaveable(obj.id) { mutableStateOf(false) }
    var showOverflow by remember(obj.id) { mutableStateOf(false) }
    var collapsed by rememberSaveable(obj.id) { mutableStateOf(false) }

    val children = childrenByParent[obj.id] ?: emptyList()
    val hasChildren = children.isNotEmpty()
    val isNested = depth > 0

    val borderColor = if (borderFieldId != null) {
        val borderValue = obj.stringValue(borderFieldId)
        if (borderValue.isNotBlank()) {
            val options = viewModel.getAllOptionsForField(borderFieldId)
            val option = options.find { it.id == borderValue }
            if (option != null && option.colour.isNotBlank()) {
                parseColor(option.colour)
            } else null
        } else null
    } else null

    val crmDetails = viewModel.uiState.value.crmDetails
    val prefix = crmDetails?.crm?.prefix ?: ""
    val cls = crmDetails?.classes?.find { it.id == obj.objectClass }
    val titleFieldId = cls?.title?.takeIf { it.isNotBlank() }
    val title = if (titleFieldId != null) {
        obj.stringValue(titleFieldId).ifBlank { "$prefix-${obj.number}" }
    } else {
        obj.readable.ifBlank { "$prefix-${obj.number}" }
    }
    val cardFields = viewModel.getCardFields(obj.objectClass)

    // Drag-drop wiring — only top-level cards participate. Nested children stay
    // visual-only to avoid confusing dual-target situations (the parent and
    // the child both being valid drop targets at the same point).
    val isDragSource = cardDragState != null && !isNested && cardIndexInColumn >= 0
    val isBeingDragged = isDragSource && cardDragState!!.isDragging(obj.id)
    val isDropTarget = isDragSource && cardDragState!!.targetItemId == obj.id &&
        cardDragState.draggingItemId != null && cardDragState.draggingItemId != obj.id
    val targetEdge = cardDragState?.targetEdge

    val dragHintLabel = if (isDragSource) stringResource(R.string.crm_drag_card) else ""
    val dragModifier = if (isDragSource) {
        Modifier
            .semantics { contentDescription = dragHintLabel }
            .draggableItem(state = cardDragState!!, itemId = obj.id)
            .dropTarget(
                state = cardDragState,
                itemId = obj.id,
                orientation = DropOrientation.Vertical,
                acceptedEdges = setOf(DragEdge.Top, DragEdge.Bottom),
                onDrop = { sourceId, edge ->
                    // Compute new rank (1-based) within the target column.
                    // Index in the rendered list; if source is in the same
                    // column we exclude it from the count so the rank lines
                    // up with what the server sees post-removal.
                    val sameColumn = columnObjectsForDrop.any { it.id == sourceId }
                    val effectiveIndex = if (sameColumn) {
                        // Count target's position excluding source.
                        var seen = 0
                        var pos = 0
                        for (o in columnObjectsForDrop) {
                            if (o.id == sourceId) continue
                            if (o.id == obj.id) { pos = seen; break }
                            seen++
                        }
                        pos
                    } else {
                        cardIndexInColumn
                    }
                    val rank = when (edge) {
                        DragEdge.Top -> effectiveIndex + 1
                        DragEdge.Bottom -> effectiveIndex + 2
                        else -> effectiveIndex + 1
                    }
                    viewModel.moveObject(sourceId, columnFieldId, targetColumnId, rank)
                }
            )
    } else Modifier

    val edgeBorderModifier = if (isDropTarget) {
        when (targetEdge) {
            DragEdge.Top -> Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            )
            DragEdge.Bottom -> Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier)
            .then(visualModifier)
            .then(edgeBorderModifier)
            // Every card carries a visible outline (matching web, where each
            // Card has a default border); the border-field colour overrides the
            // default subtle outline when one is set.
            .border(
                1.dp,
                borderColor ?: MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (!isNested) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(if (!isNested) 12.dp else 8.dp)) {
                // Header row: [chevron] [title] [child count] [overflow]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (hasChildren) {
                        Icon(
                            imageVector = if (collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                            contentDescription = if (collapsed) stringResource(MochiR.string.common_expand) else stringResource(MochiR.string.common_collapse),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { collapsed = !collapsed }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = title,
                        style = if (!isNested) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasChildren && collapsed) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${children.size}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Overflow menu — accessible fallback for the
                    // MoveObjectSheet now that long-press is reserved for
                    // drag-start.
                    if (!isNested) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    contentDescription = stringResource(MochiR.string.common_more_options),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.crm_move_to_column)) },
                                    onClick = {
                                        showOverflow = false
                                        showMoveSheet = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Body fields (top-level only, matching web's !isNested check)
                if (!isNested) {
                    val bodyFields = cardFields.filter {
                        it.id != columnFieldId && it.id != rowFieldId && it.id != titleFieldId
                    }
                    val fieldsWithValues = bodyFields.filter { obj.stringValue(it.id).isNotBlank() }
                    if (fieldsWithValues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            fieldsWithValues.forEach { field ->
                                val value = obj.stringValue(field.id)
                                when (field.fieldtype) {
                                    "enumerated" -> {
                                        val options = viewModel.getAllOptionsForField(field.id)
                                        val opt = options.find { it.id == value }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (opt != null && opt.colour.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(
                                                            parseColor(opt.colour),
                                                            MaterialTheme.shapes.extraSmall
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = opt?.name ?: value,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    else -> {
                                        Text(
                                            text = value.take(80),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Nested children
                if (hasChildren && !collapsed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    if (depth < MAX_NESTING_DEPTH) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            children.sortedBy { it.rank }.forEach { child ->
                                BoardCard(
                                    obj = child,
                                    viewModel = viewModel,
                                    borderFieldId = borderFieldId,
                                    childrenByParent = childrenByParent,
                                    columnFieldId = columnFieldId,
                                    rowFieldId = rowFieldId,
                                    depth = depth + 1,
                                    onClick = onClick
                                )
                            }
                        }
                    } else {
                        val deepCount = countDeepChildren(obj.id, childrenByParent)
                        Text(
                            text = stringResource(R.string.crm_board_nested_count, deepCount),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
        }
    }

    if (showMoveSheet) {
        MoveObjectSheet(
            obj = obj,
            viewModel = viewModel,
            onDismiss = { showMoveSheet = false }
        )
    }
}

private fun countDeepChildren(parentId: String, childrenByParent: Map<String, List<CrmObject>>): Int {
    val direct = childrenByParent[parentId] ?: return 0
    return direct.size + direct.sumOf { countDeepChildren(it.id, childrenByParent) }
}
