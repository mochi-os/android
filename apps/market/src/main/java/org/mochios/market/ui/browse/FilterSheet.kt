package org.mochios.market.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.market.R

/**
 * Bottom sheet that lets the user adjust every filter axis on the browse
 * screen. Mirrors the web side's `FilterDrawer` in
 * `apps/market/web/src/components/browse/filter-drawer.tsx`.
 *
 *  - Category is a dropdown sourced from [HomeUiState.categories].
 *  - Type / Condition / Pricing / Delivery are chip groups with an "All"
 *    sentinel that maps to clearing the filter.
 *  - Price range is two number fields; the wire payload still uses whole
 *    currency strings, which the search endpoint converts to minor units.
 *  - Sort is a dropdown.
 *  - Apply just dismisses (filters are applied live as the user toggles
 *    them); Clear wipes every filter back to the empty state.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    state: HomeUiState,
    onUpdate: (Filter, String?) -> Unit,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
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
            Text(
                text = stringResource(R.string.market_filter_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            CategoryDropdown(state = state, onUpdate = onUpdate)

            SectionLabel(stringResource(R.string.market_filter_type))
            ChipRow(
                value = state.filters[Filter.TYPE],
                options = listOf(
                    null to stringResource(R.string.market_filter_all),
                    "physical" to stringResource(R.string.market_filter_type_physical),
                    "digital" to stringResource(R.string.market_filter_type_digital),
                ),
                onSelect = { onUpdate(Filter.TYPE, it) },
            )

            SectionLabel(stringResource(R.string.market_filter_condition))
            ChipRow(
                value = state.filters[Filter.CONDITION],
                options = listOf(
                    null to stringResource(R.string.market_filter_all),
                    "new" to stringResource(R.string.market_filter_condition_new),
                    "used" to stringResource(R.string.market_filter_condition_used),
                    "refurbished" to stringResource(R.string.market_filter_condition_refurbished),
                ),
                onSelect = { onUpdate(Filter.CONDITION, it) },
            )

            SectionLabel(stringResource(R.string.market_filter_pricing))
            ChipRow(
                value = state.filters[Filter.PRICING],
                options = listOf(
                    null to stringResource(R.string.market_filter_all),
                    "fixed" to stringResource(R.string.market_filter_pricing_fixed),
                    "pwyw" to stringResource(R.string.market_filter_pricing_pwyw),
                    "subscription" to stringResource(R.string.market_filter_pricing_subscription),
                    "auction" to stringResource(R.string.market_filter_pricing_auction),
                ),
                onSelect = { onUpdate(Filter.PRICING, it) },
            )

            SectionLabel(stringResource(R.string.market_filter_delivery))
            ChipRow(
                value = state.filters[Filter.DELIVERY],
                options = listOf(
                    null to stringResource(R.string.market_filter_all),
                    "shipping" to stringResource(R.string.market_filter_delivery_shipping),
                    "pickup" to stringResource(R.string.market_filter_delivery_pickup),
                    "download" to stringResource(R.string.market_filter_delivery_download),
                ),
                onSelect = { onUpdate(Filter.DELIVERY, it) },
            )

            SectionLabel(stringResource(R.string.market_filter_price_range))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.filters[Filter.PRICE_MIN].orEmpty(),
                    onValueChange = { onUpdate(Filter.PRICE_MIN, it.ifBlank { null }) },
                    label = { Text(stringResource(R.string.market_filter_price_min)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.filters[Filter.PRICE_MAX].orEmpty(),
                    onValueChange = { onUpdate(Filter.PRICE_MAX, it.ifBlank { null }) },
                    label = { Text(stringResource(R.string.market_filter_price_max)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.market_filter_price_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel(stringResource(R.string.market_filter_sort))
            SortDropdown(state = state, onUpdate = onUpdate)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.market_filter_clear))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.market_filter_apply))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    value: String?,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((optionValue, label) in options) {
            FilterChip(
                selected = value == optionValue,
                onClick = { onSelect(optionValue) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    state: HomeUiState,
    onUpdate: (Filter, String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.filters[Filter.CATEGORY]
    val selectedLabel = if (current == null) {
        stringResource(R.string.market_filter_all)
    } else {
        state.categories.firstOrNull { it.id.toString() == current }?.name
            ?: stringResource(R.string.market_filter_all)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(stringResource(R.string.market_filter_category))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.market_filter_all)) },
                    onClick = {
                        onUpdate(Filter.CATEGORY, null)
                        expanded = false
                    },
                )
                for (category in state.categories) {
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onUpdate(Filter.CATEGORY, category.id.toString())
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(
    state: HomeUiState,
    onUpdate: (Filter, String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sortOptions = listOf(
        null to stringResource(R.string.market_filter_sort_default),
        "recent" to stringResource(R.string.market_filter_sort_recent),
        "price_low" to stringResource(R.string.market_filter_sort_price_low),
        "price_high" to stringResource(R.string.market_filter_sort_price_high),
        "rating" to stringResource(R.string.market_filter_sort_rating),
    )
    val current = state.filters[Filter.SORT]
    val selectedLabel = sortOptions.firstOrNull { it.first == current }?.second
        ?: stringResource(R.string.market_filter_sort_default)
    Box {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                for ((value, label) in sortOptions) {
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onUpdate(Filter.SORT, value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
