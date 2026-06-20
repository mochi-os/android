// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import org.mochios.android.R as MochiR
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Currency
import org.mochios.market.model.Interval
import org.mochios.market.model.PricingModel

/**
 * Stripe per-currency minimum charges in minor units. Mirrors the
 * `STRIPE_MINIMUMS` table in `apps/market/web/src/lib/format.ts`. Used to
 * display an inline validation warning when the seller enters a price below
 * what Stripe will accept (50 cents / 50 pence / 50 yen).
 */
val STRIPE_MINIMUMS: Map<Currency, Long> = mapOf(
    Currency.GBP to 50L,
    Currency.USD to 50L,
    Currency.EUR to 50L,
    Currency.JPY to 50L,
)

/**
 * Pricing-model picker and the conditional fields each model surfaces. Mirrors
 * `pricing-model-selector.tsx` on web — a 4-way radio choice at the top
 * (Fixed / PWYW / Subscription / Auction) plus the model-specific fields
 * (currency, price, billing interval, reserve / instant-buy, duration).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingModelSelector(
    pricing: PricingModel,
    currency: Currency,
    priceText: String,
    interval: Interval,
    durationDays: Int,
    reserveText: String,
    instantText: String,
    opensAt: Long?,
    onPricingChange: (PricingModel) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onPriceChange: (String) -> Unit,
    onIntervalChange: (Interval) -> Unit,
    onDurationChange: (Int) -> Unit,
    onReserveChange: (String) -> Unit,
    onInstantChange: (String) -> Unit,
    onOpensChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Pricing model radio row.
        PricingModelRow(
            label = stringResource(R.string.market_editor_pricing_fixed),
            selected = pricing == PricingModel.FIXED,
            onSelect = { onPricingChange(PricingModel.FIXED) },
        )
        PricingModelRow(
            label = stringResource(R.string.market_editor_pricing_pwyw),
            selected = pricing == PricingModel.PWYW,
            onSelect = { onPricingChange(PricingModel.PWYW) },
        )
        PricingModelRow(
            label = stringResource(R.string.market_editor_pricing_subscription),
            selected = pricing == PricingModel.SUBSCRIPTION,
            onSelect = { onPricingChange(PricingModel.SUBSCRIPTION) },
        )
        PricingModelRow(
            label = stringResource(R.string.market_editor_pricing_auction),
            selected = pricing == PricingModel.AUCTION,
            onSelect = { onPricingChange(PricingModel.AUCTION) },
        )

        Spacer(Modifier.height(4.dp))

        when (pricing) {
            PricingModel.FIXED, PricingModel.PWYW -> {
                CurrencyDropdown(currency = currency, onChange = onCurrencyChange)
                PriceField(
                    value = priceText,
                    onChange = onPriceChange,
                    currency = currency,
                    label = stringResource(R.string.market_editor_price),
                )
            }
            PricingModel.SUBSCRIPTION -> {
                CurrencyDropdown(currency = currency, onChange = onCurrencyChange)
                PriceField(
                    value = priceText,
                    onChange = onPriceChange,
                    currency = currency,
                    label = stringResource(R.string.market_editor_price),
                )
                IntervalDropdown(interval = interval, onChange = onIntervalChange)
            }
            PricingModel.AUCTION -> {
                CurrencyDropdown(currency = currency, onChange = onCurrencyChange)
                PriceField(
                    value = reserveText,
                    onChange = onReserveChange,
                    currency = currency,
                    label = stringResource(R.string.market_editor_reserve),
                )
                PriceField(
                    value = instantText,
                    onChange = onInstantChange,
                    currency = currency,
                    label = stringResource(R.string.market_editor_instant),
                    helperText = stringResource(R.string.market_editor_instant_help),
                )
                DurationDropdown(days = durationDays, onChange = onDurationChange)
                StartTimeField(opensAt = opensAt, onChange = onOpensChange)
            }
        }
    }
}

@Composable
private fun PricingModelRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.padding(start = 8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
    currency: Currency,
    onChange: (Currency) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = currency.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.market_editor_currency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Currency.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name) },
                    onClick = {
                        onChange(c)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalDropdown(
    interval: Interval,
    onChange: (Interval) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (interval) {
        Interval.MONTHLY -> stringResource(R.string.market_editor_interval_monthly)
        Interval.YEARLY -> stringResource(R.string.market_editor_interval_yearly)
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.market_editor_interval)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.market_editor_interval_monthly)) },
                onClick = { onChange(Interval.MONTHLY); expanded = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.market_editor_interval_yearly)) },
                onClick = { onChange(Interval.YEARLY); expanded = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationDropdown(
    days: Int,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = stringResource(R.string.market_editor_days, days),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.market_editor_duration)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AUCTION_DURATIONS.forEach { d ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.market_editor_days, d)) },
                    onClick = { onChange(d); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun PriceField(
    value: String,
    onChange: (String) -> Unit,
    currency: Currency,
    label: String,
    helperText: String? = null,
) {
    val minor = remember(value, currency) { toMinorUnits(value, currency) }
    val minimum = STRIPE_MINIMUMS[currency] ?: 0L
    val belowMin = minor in 1L until minimum
    val warning = if (belowMin) {
        stringResource(
            R.string.market_editor_price_below_min,
            formatPrice(minimum, currency),
        )
    } else null
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            isError = belowMin,
            supportingText = (warning ?: helperText)?.let { msg ->
                {
                    Text(
                        text = msg,
                        color = if (belowMin) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Optional future auction start time. Tapping the calendar icon picks a date
 * then a time (combined in the device's local zone, matching web's
 * datetime-local). Clearing reverts to "start on publish" (opensAt = null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeField(
    opensAt: Long?,
    onChange: (Long?) -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }

    // Empty when no start time (the field then reads as "start on publish",
    // explained by the help text below — matching web's empty datetime input).
    val display = opensAt?.let {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(it * 1000L))
    } ?: ""

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.market_editor_start_time)) },
            trailingIcon = {
                Row {
                    if (opensAt != null) {
                        IconButton(onClick = { onChange(null) }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.market_editor_start_time_clear),
                            )
                        }
                    }
                    IconButton(onClick = { showDate = true }) {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = stringResource(R.string.market_editor_start_time),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.market_editor_start_time_help),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }

    if (showDate) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = opensAt?.let { it * 1000L } ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDateMillis = dateState.selectedDateMillis
                        showDate = false
                        if (pickedDateMillis != null) showTime = true
                    },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.market_editor_start_time_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        ) { DatePicker(state = dateState) }
    }

    if (showTime) {
        val initial = java.util.Calendar.getInstance().apply { opensAt?.let { timeInMillis = it * 1000L } }
        val timeState = rememberTimePickerState(
            initialHour = initial.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = initial.get(java.util.Calendar.MINUTE),
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.market_editor_start_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val dm = pickedDateMillis
                    if (dm != null) {
                        // DatePicker returns UTC-midnight millis; read the
                        // calendar date in UTC, then rebuild in the local zone
                        // with the picked time so the epoch matches the user's
                        // local datetime (as web's datetime-local does).
                        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                            .apply { timeInMillis = dm }
                        val local = java.util.Calendar.getInstance().apply {
                            set(
                                utc.get(java.util.Calendar.YEAR),
                                utc.get(java.util.Calendar.MONTH),
                                utc.get(java.util.Calendar.DAY_OF_MONTH),
                                timeState.hour,
                                timeState.minute,
                                0,
                            )
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        onChange(local.timeInMillis / 1000L)
                    }
                    showTime = false
                }) { Text(stringResource(MochiR.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        )
    }
}
