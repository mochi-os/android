// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.FilterChipRow
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.staff.R

/**
 * One filter in [StaffFilters].
 *
 * @property label Name of the filter, shown before its choices.
 * @property chipLabel Label of the removable chip shown while the filter is set.
 * @property anyLabel The choice that clears the filter, shown first.
 * @property options Wire value and display name of each other choice.
 * @property current The picked wire value, or null for any.
 * @property onSelect Called with the picked wire value, or null to clear it.
 */
data class StaffFilter(
    val label: String,
    val chipLabel: String,
    val anyLabel: String,
    val options: List<Pair<String, String>>,
    val current: String?,
    val onSelect: (String?) -> Unit,
)

/** Where [StaffFilters] puts the choices. */
enum class StaffFilterStyle {
    /** A sideways-scrolling line of choice chips per filter, above the list. */
    Chips,

    /**
     * In a bottom sheet opened from [StaffFilterButton], with a removable chip
     * above the list for each filter that is set. For many or long choices.
     */
    Sheet,
}

/**
 * The filter block every staff list screen puts above its list: its filters in
 * the given [style], then an optional [search] field. The search text gets no
 * chip, since the field already shows it.
 *
 * @param sheetOpen Whether the [StaffFilterStyle.Sheet] sheet is showing.
 * @param onSheetDismiss Called when that sheet is swiped or tapped away.
 */
@Composable
fun StaffFilters(
    filters: List<StaffFilter>,
    modifier: Modifier = Modifier,
    style: StaffFilterStyle = StaffFilterStyle.Chips,
    sheetOpen: Boolean = false,
    onSheetDismiss: () -> Unit = {},
    search: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (style) {
            StaffFilterStyle.Chips -> {
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
            StaffFilterStyle.Sheet -> {
                search?.invoke()
                ActiveChips(filters)
            }
        }
    }
    if (style == StaffFilterStyle.Sheet && sheetOpen) {
        FilterSheet(filters, onSheetDismiss)
    }
}

/**
 * Top bar button that opens a [StaffFilterStyle.Sheet] filter sheet, badged
 * with how many filters are set.
 */
@Composable
fun StaffFilterButton(activeCount: Int, onClick: () -> Unit) {
    MochiIconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (activeCount > 0) {
                    Badge { Text(activeCount.toString()) }
                }
            },
        ) {
            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.staff_filter))
        }
    }
}

/**
 * The choices of [filter] as chips, [StaffFilter.anyLabel] first: on one line
 * that scrolls sideways when [singleLine], wrapping onto more lines otherwise.
 */
@Composable
private fun ChoiceChips(
    filter: StaffFilter,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(filters: List<StaffFilter>, onDismiss: () -> Unit) {
    MochiBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.staff_filter),
                style = MaterialTheme.typography.titleLarge,
            )
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveChips(filters: List<StaffFilter>) {
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
            val current = filter.current.orEmpty()
            val value = filter.options
                .firstOrNull { option -> option.first == current }
                ?.second
                ?: current
            AssistChip(
                onClick = { filter.onSelect(null) },
                label = {
                    Text(
                        text = stringResource(
                            R.string.staff_filter_chip_template,
                            filter.chipLabel,
                            value,
                        ),
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.staff_filter_chip_remove),
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}
