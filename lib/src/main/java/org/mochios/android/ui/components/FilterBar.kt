// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.R

/**
 * One single-choice filter in a [FilterBar]. A [current] value missing from
 * [options] still gets its removable chip, named by [chipLabel] alone, since
 * the raw wire value means nothing to the reader.
 *
 * @property label Name of the filter, shown before its choices.
 * @property chipLabel Label of the removable chip shown while the filter is set.
 * @property anyLabel The choice that clears the filter, shown first.
 * @property options Wire value and display name of each other choice.
 * @property current The picked wire value, or null for any.
 * @property onSelect Called with the picked wire value, or null to clear it.
 */
data class ChoiceFilter(
    val label: String,
    val chipLabel: String,
    val anyLabel: String,
    val options: List<Pair<String, String>>,
    val current: String?,
    val onSelect: (String?) -> Unit,
)

/** Where [FilterBar] puts the choices. */
enum class FilterBarStyle {
    /** A sideways-scrolling line of choice chips per filter, above the list. */
    Chips,

    /**
     * In a bottom sheet opened from [FilterButton], with a removable chip
     * above the list for each filter that is set. For many or long choices.
     */
    Sheet,
}

/**
 * The filter block a list screen puts above its list: its filters in
 * the given [style], then an optional [search] field. The search text gets no
 * chip, since the field already shows it.
 *
 * @param sheetOpen Whether the [FilterBarStyle.Sheet] sheet is showing.
 * @param onSheetDismiss Called when that sheet is swiped or tapped away.
 */
@Composable
fun FilterBar(
    filters: List<ChoiceFilter>,
    modifier: Modifier = Modifier,
    style: FilterBarStyle = FilterBarStyle.Chips,
    sheetOpen: Boolean = false,
    onSheetDismiss: () -> Unit = {},
    search: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (style) {
            FilterBarStyle.Chips -> {
                val labelled = filters.size > 1
                filters.forEach { filter ->
                    ChoiceChips(
                        filter = filter,
                        labelled = labelled,
                        singleLine = true,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                search?.invoke()
            }
            FilterBarStyle.Sheet -> {
                search?.invoke()
                ActiveChips(filters)
            }
        }
    }
    if (style == FilterBarStyle.Sheet && sheetOpen) {
        ChoiceSheet(filters, onSheetDismiss)
    }
}

/**
 * Top bar button that opens a filter sheet, with a dot while [active], that is
 * while some filter is set. A dot rather than a count: the filters themselves
 * show on screen, and a number beside the notification bell would read as
 * unread items.
 */
@Composable
fun FilterButton(
    active: Boolean,
    onClick: () -> Unit,
    contentDescription: String = stringResource(R.string.common_filter),
) {
    MochiIconButton(
        onClick = onClick,
        modifier = Modifier.semantics { selected = active },
    ) {
        BadgedBox(
            badge = {
                if (active) {
                    Badge(modifier = Modifier.size(6.dp))
                }
            },
        ) {
            Icon(Icons.Default.FilterList, contentDescription = contentDescription)
        }
    }
}

/**
 * The choices of [filter] as chips, [ChoiceFilter.anyLabel] first: on one line
 * that scrolls sideways when [singleLine], wrapping onto more lines otherwise.
 */
@Composable
private fun ChoiceChips(
    filter: ChoiceFilter,
    labelled: Boolean,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    FilterChipRow(
        options = listOf<Pair<String?, String>>(null to filter.anyLabel) + filter.options,
        isSelected = { value -> filter.current == value },
        onSelect = filter.onSelect,
        modifier = modifier,
        singleLine = singleLine,
        contentPadding = if (singleLine) PaddingValues(horizontal = 16.dp) else PaddingValues(0.dp),
        verticalSpacing = 4.dp,
        leading = if (labelled) {
            {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun ChoiceSheet(filters: List<ChoiceFilter>, onDismiss: () -> Unit) {
    FilterSheet(
        title = stringResource(R.string.common_filter),
        onDismiss = onDismiss,
        titleWeight = null,
        sectionSpacing = 8.dp,
    ) {
        filters.forEach { filter ->
            Text(
                text = filter.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
            ChoiceChips(filter = filter, labelled = false, singleLine = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveChips(filters: List<ChoiceFilter>) {
    val set = filters.filter { filter -> !filter.current.isNullOrBlank() }
    if (set.isEmpty()) {
        return
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        set.forEach { filter ->
            val value = filter.options
                .firstOrNull { option -> option.first == filter.current }
                ?.second
            AssistChip(
                onClick = { filter.onSelect(null) },
                label = {
                    Text(
                        text = if (value != null) {
                            stringResource(R.string.common_filter_chip, filter.chipLabel, value)
                        } else {
                            filter.chipLabel
                        },
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_filter_remove),
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}
