// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mochios.crm.R
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.CrmView

/**
 * Non-interactive preview of how the configured view would lay objects out.
 * Uses synthetic sample objects so the preview shows interesting variety
 * even on a crm with no real data. Mirrors the web's
 * `apps/crm/web/src/features/editor/components/design-preview.tsx`
 * but compressed for phone screens: smaller cards, narrower columns, and
 * a single rendered view at a time.
 *
 * The preview is intentionally rebuilt from raw values (no shared hooks
 * with `BoardView` / `TreeView`), so it has no interactive state and is
 * cheap to recompose on every config edit.
 */
@Composable
fun DesignPreview(
    crm: CrmDetails,
    view: CrmView?,
    classFilter: CrmClass? = null,
    modifier: Modifier = Modifier
) {
    if (view == null) {
        PreviewEmpty(
            modifier = modifier,
            message = stringResource(R.string.crm_design_preview_no_view)
        )
        return
    }

    // Resolve the classes the preview should sample from. If the view has an
    // explicit class filter, use that intersected with classFilter; otherwise
    // fall back to classFilter or all classes.
    val viewClassIds = view.classes.toSet()
    val candidateClasses = when {
        classFilter != null && (viewClassIds.isEmpty() || classFilter.id in viewClassIds) ->
            listOf(classFilter)
        viewClassIds.isNotEmpty() ->
            crm.classes.filter { it.id in viewClassIds }
        else -> crm.classes
    }
    if (candidateClasses.isEmpty()) {
        PreviewEmpty(
            modifier = modifier,
            message = stringResource(R.string.crm_design_preview_no_class)
        )
        return
    }

    val sampleObjects = remember(view, candidateClasses, crm.options, crm.fields) {
        buildSampleObjects(crm, candidateClasses, view)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.crm_design_preview_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = view.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                .padding(6.dp)
        ) {
            if (view.viewtype == "board") {
                BoardPreview(crm = crm, view = view, samples = sampleObjects)
            } else {
                TreePreview(crm = crm, view = view, samples = sampleObjects)
            }
        }
    }
}

/* ------------------------------ Board ------------------------------ */

@Composable
private fun BoardPreview(
    crm: CrmDetails,
    view: CrmView,
    samples: List<CrmObject>
) {
    val columnFieldId = view.columns
    if (columnFieldId.isBlank()) {
        PreviewMessage(stringResource(R.string.crm_design_preview_pick_columns))
        return
    }
    val columnOptions = optionsForField(crm, columnFieldId)
    if (columnOptions.isEmpty()) {
        PreviewMessage(stringResource(R.string.crm_design_preview_no_options))
        return
    }

    val borderFieldId = view.border.takeIf { it.isNotBlank() }
    val borderOptions = borderFieldId?.let { optionsForField(crm, it) } ?: emptyList()

    // Group samples by column option.
    val samplesByColumn = columnOptions.associate { opt ->
        opt.id to samples.filter { it.stringValue(columnFieldId) == opt.id }
    }
    val unassignedLabel = stringResource(R.string.crm_board_unassigned)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        columnOptions.forEach { col ->
            BoardPreviewColumn(
                title = col.name,
                accent = col.colour.let(::parsePreviewColor),
                cards = samplesByColumn[col.id] ?: emptyList(),
                crm = crm,
                view = view,
                borderFieldId = borderFieldId,
                borderOptions = borderOptions
            )
        }
        // Show an Unassigned column only if there are samples without a value
        // for the column field.
        val unassigned = samples.filter { it.stringValue(columnFieldId).isBlank() }
        if (unassigned.isNotEmpty()) {
            BoardPreviewColumn(
                title = unassignedLabel,
                accent = null,
                cards = unassigned,
                crm = crm,
                view = view,
                borderFieldId = borderFieldId,
                borderOptions = borderOptions
            )
        }
    }
}

@Composable
private fun BoardPreviewColumn(
    title: String,
    accent: Color?,
    cards: List<CrmObject>,
    crm: CrmDetails,
    view: CrmView,
    borderFieldId: String?,
    borderOptions: List<FieldOption>
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            if (accent != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${cards.size}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            cards.take(4).forEach { obj ->
                MiniCard(
                    obj = obj,
                    crm = crm,
                    view = view,
                    borderFieldId = borderFieldId,
                    borderOptions = borderOptions
                )
            }
        }
    }
}

@Composable
private fun MiniCard(
    obj: CrmObject,
    crm: CrmDetails,
    view: CrmView,
    borderFieldId: String?,
    borderOptions: List<FieldOption>
) {
    val borderColor = if (borderFieldId != null) {
        val v = obj.stringValue(borderFieldId)
        borderOptions.find { it.id == v }?.colour?.let(::parsePreviewColor)
    } else null

    val cls = crm.classes.find { it.id == obj.objectClass }
    val titleFieldId = cls?.title?.takeIf { it.isNotBlank() }
    val title = titleFieldId?.let { obj.stringValue(it) }?.ifBlank { obj.readable } ?: obj.readable

    // Show one extra body field (if present) — pick the first card-eligible
    // field that isn't the title or column/row/border field.
    val classFields = crm.fields[obj.objectClass].orEmpty()
    val excluded = setOfNotNull(
        titleFieldId,
        view.columns.takeIf { it.isNotBlank() },
        view.rows.takeIf { it.isNotBlank() },
        borderFieldId
    )
    val bodyField = classFields.firstOrNull {
        it.id !in excluded && obj.stringValue(it.id).isNotBlank()
    }

    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surface)
    val cardModifier = if (borderColor != null) {
        baseModifier.border(1.dp, borderColor, RoundedCornerShape(4.dp))
    } else {
        baseModifier
    }

    Column(
        modifier = cardModifier.padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (bodyField != null) {
            val raw = obj.stringValue(bodyField.id)
            val display = when (bodyField.fieldtype) {
                "enumerated" -> {
                    val opts = optionsForField(crm, bodyField.id)
                    opts.find { it.id == raw }?.name ?: raw
                }
                else -> raw
            }
            if (display.isNotBlank()) {
                Text(
                    text = display,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ------------------------------ Tree ------------------------------ */

@Composable
private fun TreePreview(
    crm: CrmDetails,
    view: CrmView,
    samples: List<CrmObject>
) {
    if (samples.isEmpty()) {
        PreviewMessage(stringResource(R.string.crm_design_preview_empty))
        return
    }

    val childMap = samples.groupBy { it.parent }
    val roots = samples.filter { obj ->
        obj.parent.isBlank() || samples.none { it.id == obj.parent }
    }
    val flat = remember(samples) {
        flattenPreviewTree(roots, childMap, depth = 0).take(10)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        flat.forEach { node ->
            TreeRowMini(
                crm = crm,
                view = view,
                obj = node.obj,
                depth = node.depth,
                hasChildren = node.hasChildren
            )
        }
    }
}

private data class PreviewTreeNode(
    val obj: CrmObject,
    val depth: Int,
    val hasChildren: Boolean
)

private fun flattenPreviewTree(
    items: List<CrmObject>,
    childMap: Map<String, List<CrmObject>>,
    depth: Int
): List<PreviewTreeNode> {
    val out = mutableListOf<PreviewTreeNode>()
    for (item in items) {
        val children = childMap[item.id].orEmpty()
        out.add(PreviewTreeNode(item, depth, children.isNotEmpty()))
        if (children.isNotEmpty() && depth < 2) {
            out.addAll(flattenPreviewTree(children, childMap, depth + 1))
        }
    }
    return out
}

@Composable
private fun TreeRowMini(
    crm: CrmDetails,
    view: CrmView,
    obj: CrmObject,
    depth: Int,
    hasChildren: Boolean
) {
    val cls = crm.classes.find { it.id == obj.objectClass }
    val titleFieldId = cls?.title?.takeIf { it.isNotBlank() }
    val title = titleFieldId?.let { obj.stringValue(it) }?.ifBlank { obj.readable } ?: obj.readable

    val borderFieldId = view.border.takeIf { it.isNotBlank() }
    val accent = if (borderFieldId != null) {
        val v = obj.stringValue(borderFieldId)
        optionsForField(crm, borderFieldId).find { it.id == v }?.colour?.let(::parsePreviewColor)
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (hasChildren && depth < 2) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        if (accent != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ------------------------------ Helpers ------------------------------ */

@Composable
private fun PreviewEmpty(modifier: Modifier, message: String) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun optionsForField(
    crm: CrmDetails,
    fieldId: String
): List<FieldOption> {
    for ((_, classOptions) in crm.options) {
        val opts = classOptions[fieldId]
        if (!opts.isNullOrEmpty()) return opts.sortedBy { it.rank }
    }
    return emptyList()
}

private fun parsePreviewColor(hex: String): Color? {
    if (hex.isBlank()) return null
    return try {
        val cleaned = hex.removePrefix("#")
        val expanded = if (cleaned.length == 3) cleaned.map { "$it$it" }.joinToString("") else cleaned
        Color(android.graphics.Color.parseColor("#$expanded"))
    } catch (_: Exception) {
        null
    }
}

/**
 * Build a small set of synthetic objects that exercise the configured view.
 * Targets ~5 objects, varied across the column field's options (so the
 * board has cards in multiple columns) and across the border field's
 * options (so border colour shows variety). One sample is given a parent
 * so tree previews show nesting.
 */
private fun buildSampleObjects(
    crm: CrmDetails,
    classes: List<CrmClass>,
    view: CrmView
): List<CrmObject> {
    val baseClass = classes.first()
    val classId = baseClass.id

    val columnOptions = if (view.columns.isNotBlank())
        optionsForField(crm, view.columns)
    else emptyList()
    val rowOptions = if (view.rows.isNotBlank())
        optionsForField(crm, view.rows)
    else emptyList()
    val borderOptions = if (view.border.isNotBlank())
        optionsForField(crm, view.border)
    else emptyList()

    val titleFieldId = baseClass.title.takeIf { it.isNotBlank() }
    val classFields = crm.fields[classId].orEmpty()
    // Pick one extra non-system field to populate to add card detail.
    val detailField = classFields.firstOrNull {
        it.id != titleFieldId &&
            it.id != view.columns &&
            it.id != view.rows &&
            it.id != view.border &&
            it.fieldtype in setOf("text", "number", "enumerated")
    }

    val sampleTitles = listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon")
    val count = sampleTitles.size

    val samples = sampleTitles.mapIndexed { index, name ->
        val values = mutableMapOf<String, Any?>()
        // Always put the title under the title field so MiniCard can find it.
        if (titleFieldId != null) {
            values[titleFieldId] = name
        }
        if (columnOptions.isNotEmpty()) {
            values[view.columns] = columnOptions[index % columnOptions.size].id
        }
        if (rowOptions.isNotEmpty()) {
            values[view.rows] = rowOptions[index % rowOptions.size].id
        }
        if (borderOptions.isNotEmpty()) {
            values[view.border] = borderOptions[index % borderOptions.size].id
        }
        if (detailField != null) {
            val v: Any = when (detailField.fieldtype) {
                "number" -> ((index + 1) * 3).toString()
                "enumerated" -> {
                    val opts = optionsForField(crm, detailField.id)
                    if (opts.isNotEmpty()) opts[index % opts.size].id else ""
                }
                else -> "Sample ${index + 1}"
            }
            if ((v as? String).orEmpty().isNotBlank() || v !is String) {
                values[detailField.id] = v
            }
        }
        CrmObject(
            id = "preview-$index",
            crm = crm.crm.id,
            objectClass = classId,
            number = index + 1,
            // Make sample 2 a child of sample 1, sample 3 a child of sample 2,
            // so tree previews show nesting (2 levels deep).
            parent = when (index) {
                1 -> "preview-0"
                2 -> "preview-1"
                else -> ""
            },
            // Sample objects only — a zero-padded index keeps them in order under
            // the string-keyed rank sort (#53).
            rank = index.toString().padStart(4, '0'),
            readable = name,
            values = values
        )
    }

    return samples.take(count)
}
