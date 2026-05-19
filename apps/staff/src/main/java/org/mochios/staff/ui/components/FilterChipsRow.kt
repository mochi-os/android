package org.mochios.staff.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.staff.R

/**
 * One active filter the list screens surface as a removable chip. `label` is
 * the filter dimension ("Status", "Type") and `value` is the currently
 * selected option's user-facing string ("pending", "Listing", the raw
 * search query). [onRemove] resets the underlying filter to its default
 * (null for dropdowns, "" for the search query).
 */
data class FilterChipSpec(
    val label: String,
    val value: String,
    val onRemove: () -> Unit,
)

/**
 * Horizontal row of removable filter chips. Renders nothing when [chips] is
 * empty so the list screens don't show an empty spacer band between the
 * dropdowns and the result list.
 *
 * Mirrors the web staff console's active-filter chip pattern in
 * `apps/staff/web/src/features/`: each chip combines the filter dimension
 * and its current value behind a trailing close icon that clears that one
 * filter without touching the others.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterChipsRow(
    chips: List<FilterChipSpec>,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chips.forEach { chip ->
            AssistChip(
                onClick = chip.onRemove,
                label = {
                    Text(
                        text = stringResource(
                            R.string.staff_filter_chip_template,
                            chip.label,
                            chip.value,
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
