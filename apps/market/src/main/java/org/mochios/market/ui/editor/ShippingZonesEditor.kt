// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Currency
import org.mochios.market.model.ShippingOption

/**
 * Region option offered in the shipping-zone dialog. Mirrors the value/label
 * pairs used on web. The wire format on the server treats region codes
 * opaquely — ISO 3166 two-letter codes for countries, plus the group codes
 * `EU` and `WW` for the European Union and Worldwide buckets.
 */
private data class RegionChoice(val wireCode: String, val labelRes: Int)

private val REGION_CHOICES: List<RegionChoice> = listOf(
    RegionChoice("WW", R.string.market_editor_zone_worldwide),
    RegionChoice("EU", R.string.market_editor_zone_european_union),
    RegionChoice("GB", R.string.market_editor_zone_country_gb),
    RegionChoice("US", R.string.market_editor_zone_country_us),
    RegionChoice("CA", R.string.market_editor_zone_country_ca),
    RegionChoice("AU", R.string.market_editor_zone_country_au),
    RegionChoice("DE", R.string.market_editor_zone_country_de),
    RegionChoice("FR", R.string.market_editor_zone_country_fr),
    RegionChoice("IT", R.string.market_editor_zone_country_it),
    RegionChoice("ES", R.string.market_editor_zone_country_es),
    RegionChoice("NL", R.string.market_editor_zone_country_nl),
    RegionChoice("JP", R.string.market_editor_zone_country_jp),
)

/**
 * Listing-level shipping-zones editor. Mirrors web's `shipping-zones-editor.tsx`:
 * a list of zones plus "Add zone" / "Edit" controls that pop a dialog with
 * region, price, days, and notes fields. Per-zone delete inline.
 */
@Composable
fun ShippingZonesEditor(
    zones: List<ShippingOption>,
    currency: Currency,
    onChange: (List<ShippingOption>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<ShippingOption?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (zones.isEmpty()) {
            Text(
                text = stringResource(R.string.market_editor_zones_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            zones.forEachIndexed { index, zone ->
                ZoneRow(
                    zone = zone,
                    onEdit = { editing = zone },
                    onDelete = {
                        onChange(zones.filterIndexed { i, _ -> i != index })
                    },
                )
                if (index < zones.size - 1) HorizontalDivider()
            }
        }

        Spacer(Modifier.padding(top = 8.dp))
        OutlinedButton(
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.market_editor_zone_add))
        }
    }

    if (creating) {
        ZoneDialog(
            initial = null,
            currency = currency,
            onDismiss = { creating = false },
            onSave = { zone ->
                onChange(zones + zone)
                creating = false
            },
        )
    }
    val editingZone = editing
    if (editingZone != null) {
        ZoneDialog(
            initial = editingZone,
            currency = currency,
            onDismiss = { editing = null },
            onSave = { updated ->
                val replaced = zones.map { if (it === editingZone) updated else it }
                onChange(replaced)
                editing = null
            },
        )
    }
}

@Composable
private fun ZoneRow(
    zone: ShippingOption,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val regionLabel = REGION_CHOICES.firstOrNull { it.wireCode.equals(zone.region, ignoreCase = true) }
        ?.let { stringResource(it.labelRes) }
        ?: zone.region
    val currency = Currency.entries.firstOrNull { it.name.equals(zone.currency, ignoreCase = true) }
    val priceLabel = if (currency != null) formatPrice(zone.price, currency) else zone.price.toString()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = regionLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val subtitle = buildString {
                append(priceLabel)
                if (zone.days.isNotEmpty()) {
                    append(" · ")
                    append(zone.days)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (zone.notes.isNotEmpty()) {
                Text(
                    text = zone.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.market_editor_zone_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.market_editor_zone_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneDialog(
    initial: ShippingOption?,
    currency: Currency,
    onDismiss: () -> Unit,
    onSave: (ShippingOption) -> Unit,
) {
    var region by remember {
        mutableStateOf(initial?.region?.ifEmpty { REGION_CHOICES.first().wireCode }
            ?: REGION_CHOICES.first().wireCode)
    }
    val initialPriceText = initial?.let {
        val major = it.price.toDouble() / pow10(currencyDecimals(currency))
        if (major == 0.0) "" else
            if (currencyDecimals(currency) == 0) major.toLong().toString()
            else String.format("%.${currencyDecimals(currency)}f", major)
    } ?: ""
    var priceText by remember { mutableStateOf(initialPriceText) }
    var daysText by remember { mutableStateOf(initial?.days.orEmpty()) }
    var notesText by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var regionExpanded by remember { mutableStateOf(false) }
    val regionLabel = REGION_CHOICES.firstOrNull { it.wireCode == region }
        ?.let { stringResource(it.labelRes) } ?: region

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) stringResource(R.string.market_editor_zone_add)
                else stringResource(R.string.market_editor_zone_edit),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = regionExpanded,
                    onExpandedChange = { regionExpanded = it },
                ) {
                    OutlinedTextField(
                        value = regionLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.market_editor_zone_region)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    DropdownMenu(
                        expanded = regionExpanded,
                        onDismissRequest = { regionExpanded = false },
                    ) {
                        REGION_CHOICES.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(stringResource(choice.labelRes)) },
                                onClick = {
                                    region = choice.wireCode
                                    regionExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.market_editor_zone_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text(stringResource(R.string.market_editor_zone_days)) },
                    placeholder = { Text(stringResource(R.string.market_editor_zone_days_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(stringResource(R.string.market_editor_zone_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minor = toMinorUnits(priceText, currency)
                    onSave(
                        ShippingOption(
                            id = initial?.id ?: "",
                            listing = initial?.listing ?: "",
                            region = region,
                            price = minor,
                            currency = currency.name.lowercase(),
                            days = daysText.trim(),
                            notes = notesText.trim(),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.market_editor_zone_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.market_editor_zone_cancel))
            }
        },
    )
}

private fun currencyDecimals(c: Currency): Int = when (c) {
    Currency.JPY -> 0
    Currency.GBP, Currency.USD, Currency.EUR -> 2
}

private fun pow10(n: Int): Long {
    var v = 1L
    repeat(n) { v *= 10L }
    return v
}
