// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.projects.R
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.ProjectField

/**
 * Bottom sheet holding every sort and filter axis for the object list. Follows
 * the market module's `FilterSheet`: changes apply live as the user toggles
 * them, so the sheet has no Apply button — only Clear all and a dismiss.
 *
 * @param fieldSortOptions Sortable project fields, paired with their labels.
 * @param builtInSortOptions Sort keys every project has, paired with their labels.
 * @param activeSort Sort key the user (or the view) chose, or null when the list
 *   is on its implicit fallback and no chip should read as selected.
 * @param activeDirection "asc" or "desc".
 * @param filterFields Filterable fields paired with their selectable options.
 * @param activeFieldFilters Selected option ids per field id.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SortFilterSheet(
    fieldSortOptions: List<Pair<String, String>>,
    builtInSortOptions: List<Pair<String, String>>,
    activeSort: String?,
    activeDirection: String,
    filterFields: List<Pair<ProjectField, List<FieldOption>>>,
    activeFieldFilters: Map<String, Set<String>>,
    watchedOnly: Boolean,
    onSortChange: (String) -> Unit,
    onToggleDirection: () -> Unit,
    onToggleFieldValue: (fieldId: String, optionId: String) -> Unit,
    onClearFieldFilter: (String) -> Unit,
    onToggleWatched: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MochiBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.projects_sort_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                MochiTextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.projects_filter_clear_all))
                }
            }

            // Direction sits on the "Sort by" heading itself: it's a property of
            // the sort, and a labelled button there is a bigger, closer target
            // than a separate control below the chips.
            val descending = activeDirection == "desc"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(
                    text = stringResource(R.string.projects_sort_field),
                    modifier = Modifier.weight(1f),
                )
                MochiTextButton(onClick = onToggleDirection) {
                    Icon(
                        imageVector = if (descending) {
                            Icons.Default.ArrowDownward
                        } else {
                            Icons.Default.ArrowUpward
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (descending) R.string.projects_sort_direction_desc
                            else R.string.projects_sort_direction_asc
                        )
                    )
                }
            }
            // The project's own sortable fields keep their own row, above the
            // sort keys every project has — the split the old dropdown drew as
            // a divider.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fieldSortOptions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fieldSortOptions.forEach { (id, label) ->
                            FilterChip(
                                selected = id == activeSort,
                                onClick = { onSortChange(id) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    builtInSortOptions.forEach { (id, label) ->
                        FilterChip(
                            selected = id == activeSort,
                            onClick = { onSortChange(id) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            filterFields.forEach { (field, options) ->
                val selected = activeFieldFilters[field.id].orEmpty()
                SectionLabel(field.name)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selected.isEmpty(),
                        onClick = { onClearFieldFilter(field.id) },
                        label = { Text(stringResource(R.string.projects_filter_all)) },
                    )
                    options.forEach { option ->
                        FilterChip(
                            selected = option.id in selected,
                            onClick = { onToggleFieldValue(field.id, option.id) },
                            label = { Text(option.name) },
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.projects_filter_other))
            FilterChip(
                selected = watchedOnly,
                onClick = onToggleWatched,
                label = { Text(stringResource(R.string.projects_watched)) },
                leadingIcon = if (watchedOnly) {
                    {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
