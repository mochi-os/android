// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.projects.R
import org.mochios.projects.model.Link
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.ProjectObject

private data class DisplayLink(
    val sectionKey: String,
    val targetId: String, // the "other" object id
    val displayTitle: String,
    val readable: String,
    val className: String,
    // Originating direction:
    val isOutgoing: Boolean,
    val linktype: String
)

/**
 * Inline links section, rendered inside PropertiesTab to match the web's
 * object-detail-panel layout (links embedded in Properties, not a separate tab).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksSection(
    obj: ProjectObject,
    projectDetails: ProjectDetails,
    viewModel: ObjectDetailViewModel,
    onNavigateToObject: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val canWrite = uiState.access == "owner" || uiState.access == "design" || uiState.access == "write"
    var showAddSheet by remember { mutableStateOf(false) }

    val sectionRelates = stringResource(R.string.projects_links_section_relates)
    val sectionBlocks = stringResource(R.string.projects_links_section_blocks)
    val sectionBlockedBy = stringResource(R.string.projects_links_section_blocked_by)
    val sectionDuplicates = stringResource(R.string.projects_links_section_duplicates)
    val sectionDuplicatedBy = stringResource(R.string.projects_links_section_duplicated_by)

    // Resolve display titles using siblingObjects + values; fall back to the
    // partial info the API returned with the link itself.
    fun resolveTitle(otherId: String, fallback: Link): Pair<String, String> {
        val sibling = uiState.siblingObjects.find { it.id == otherId }
        val prefix = projectDetails.project.prefix
        val number = if (sibling != null && sibling.number != 0) sibling.number else fallback.number
        val readable = if (prefix.isNotBlank()) "$prefix-$number" else "#$number"
        if (sibling != null) {
            val cls = projectDetails.classes.find { it.id == sibling.objectClass }
            val titleField = cls?.title.orEmpty()
            val titleVal = if (titleField.isNotBlank()) sibling.values[titleField]?.toString().orEmpty() else ""
            val title = if (titleVal.isNotBlank()) titleVal else readable
            return title to readable
        }
        // Fallback to the link's own title field (returned by /links endpoint)
        val title = if (fallback.title.isNotBlank()) fallback.title else readable
        return title to readable
    }

    fun classNameFor(otherId: String, fallback: Link): String {
        val sibling = uiState.siblingObjects.find { it.id == otherId }
        val classId = sibling?.objectClass ?: fallback.objectClass
        return projectDetails.classes.find { it.id == classId }?.name.orEmpty()
    }

    val display = mutableListOf<Pair<String, DisplayLink>>()
    for (l in uiState.outgoingLinks) {
        val (title, readable) = resolveTitle(l.target, l)
        val section = when (l.linktype) {
            "blocks" -> sectionBlocks
            "duplicates" -> sectionDuplicates
            else -> sectionRelates
        }
        display.add(section to DisplayLink(
            sectionKey = l.linktype,
            targetId = l.target,
            displayTitle = title,
            readable = readable,
            className = classNameFor(l.target, l),
            isOutgoing = true,
            linktype = l.linktype
        ))
    }
    for (l in uiState.incomingLinks) {
        val (title, readable) = resolveTitle(l.source, l)
        // From this object's perspective, "blocks" incoming = "blocked by";
        // "duplicates" incoming = "duplicated by"; "relates" stays "relates".
        val section = when (l.linktype) {
            "blocks" -> sectionBlockedBy
            "duplicates" -> sectionDuplicatedBy
            else -> sectionRelates
        }
        display.add(section to DisplayLink(
            sectionKey = "incoming-${l.linktype}",
            targetId = l.source,
            displayTitle = title,
            readable = readable,
            className = classNameFor(l.source, l),
            isOutgoing = false,
            linktype = l.linktype
        ))
    }

    val grouped = display.groupBy({ it.first }, { it.second })
    val sectionOrder = listOf(sectionRelates, sectionBlocks, sectionBlockedBy, sectionDuplicates, sectionDuplicatedBy)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.projects_links_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (canWrite) {
                IconButton(onClick = { showAddSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.projects_links_add))
                }
            }
        }
        if (display.isEmpty()) {
            Text(
                text = stringResource(R.string.projects_links_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (section in sectionOrder) {
                val items = grouped[section] ?: continue
                if (items.isEmpty()) continue
                Text(
                    text = section,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                for (item in items) {
                    LinkRow(
                        link = item,
                        canWrite = canWrite,
                        onClick = { onNavigateToObject(item.targetId) },
                        onDelete = {
                            if (item.isOutgoing) {
                                viewModel.deleteOutgoingLink(item.targetId, item.linktype)
                            } else {
                                viewModel.deleteIncomingLink(item.targetId, item.linktype)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showAddSheet) {
        AddLinkSheet(
            obj = obj,
            projectDetails = projectDetails,
            viewModel = viewModel,
            onDismiss = { showAddSheet = false }
        )
    }
}

@Composable
private fun LinkRow(
    link: DisplayLink,
    canWrite: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = link.readable,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (link.className.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = { /* type chip is decorative */ },
                        label = { Text(link.className, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors()
                    )
                }
            }
        }
        if (canWrite) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.projects_links_remove),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLinkSheet(
    obj: ProjectObject,
    projectDetails: ProjectDetails,
    viewModel: ObjectDetailViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsState()

    val typeRelates = "relates" to stringResource(R.string.projects_links_type_relates)
    val typeBlocks = "blocks" to stringResource(R.string.projects_links_type_blocks)
    val typeBlockedBy = "blocked by" to stringResource(R.string.projects_links_type_blocked_by)
    val typeDuplicates = "duplicates" to stringResource(R.string.projects_links_type_duplicates)
    val linkTypes = listOf(typeRelates, typeBlocks, typeBlockedBy, typeDuplicates)

    var selectedType by remember { mutableStateOf(typeRelates.first) }
    var typeExpanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    // Build set of already-linked object ids to filter out
    val linkedIds = remember(uiState.outgoingLinks, uiState.incomingLinks, obj.id) {
        val s = mutableSetOf<String>()
        s.add(obj.id)
        uiState.outgoingLinks.forEach { it.target.takeIf { id -> id.isNotBlank() }?.let(s::add) }
        uiState.incomingLinks.forEach { it.source.takeIf { id -> id.isNotBlank() }?.let(s::add) }
        s
    }

    val q = search.trim().lowercase()
    val filtered = uiState.siblingObjects
        .filter { it.id !in linkedIds }
        .map { it to objectDisplayLabel(it, projectDetails) }
        .filter { (_, labels) ->
            q.isEmpty() ||
                labels.first.lowercase().contains(q) ||
                labels.second.lowercase().contains(q)
        }
        .take(20)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.projects_links_add),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it }
            ) {
                OutlinedTextField(
                    value = linkTypes.find { it.first == selectedType }?.second ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.projects_links_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    linkTypes.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedType = key
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.projects_links_search_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filtered.isEmpty() && q.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.projects_links_no_matches),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(filtered) { (target, labels) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedType == "blocked by") {
                                        // Reverse direction: target blocks current object
                                        viewModel.createReverseLink(target.id, "blocks")
                                    } else {
                                        viewModel.createLink(target.id, selectedType)
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = labels.first,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = labels.second,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun objectDisplayLabel(obj: ProjectObject, projectDetails: ProjectDetails): Pair<String, String> {
    val cls = projectDetails.classes.find { it.id == obj.objectClass }
    val titleField = cls?.title.orEmpty()
    val titleVal = if (titleField.isNotBlank()) obj.values[titleField]?.toString().orEmpty() else ""
    val prefix = projectDetails.project.prefix
    val readable = if (prefix.isNotBlank()) "$prefix-${obj.number}" else "#${obj.number}"
    val title = if (titleVal.isNotBlank()) titleVal else readable
    return title to readable
}
