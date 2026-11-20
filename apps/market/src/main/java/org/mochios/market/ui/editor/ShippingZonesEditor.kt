// Copyright © 2026 Mochisoft OÜ
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.market.R
import org.mochios.market.lib.WORLD
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.minorToMajorText
import org.mochios.market.lib.regionName
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Currency
import org.mochios.market.model.ShippingOption
import org.mochios.market.ui.components.RegionField

/**
 * A listing's shipping options, each for a region from [choices]: the region
 * groups, then every country by name ([org.mochios.market.lib.regionChoices]).
 * A region is stored as its code and shown by name.
 */
@Composable
fun ShippingZonesEditor(
    zones: List<ShippingOption>,
    currency: Currency,
    choices: List<String>,
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
        MochiOutlinedButton(
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
            choices = choices,
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
            choices = choices,
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
    val regionLabel = regionName(zone.region, LocalResources.current, LocalConfiguration.current.locales[0])
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
        MochiIconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.market_editor_zone_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        MochiIconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.market_editor_zone_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ZoneDialog(
    initial: ShippingOption?,
    currency: Currency,
    choices: List<String>,
    onDismiss: () -> Unit,
    onSave: (ShippingOption) -> Unit,
) {
    var region by remember { mutableStateOf(initial?.region?.ifEmpty { WORLD } ?: WORLD) }
    val initialPriceText = initial?.let { minorToMajorText(it.price, currency) } ?: ""
    var priceText by remember { mutableStateOf(initialPriceText) }
    var daysText by remember { mutableStateOf(initial?.days.orEmpty()) }
    var notesText by remember { mutableStateOf(initial?.notes.orEmpty()) }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) stringResource(R.string.market_editor_zone_add)
        else stringResource(R.string.market_editor_zone_edit),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RegionField(
                    value = region,
                    choices = choices,
                    label = stringResource(R.string.market_editor_zone_region),
                    onChange = { code -> region = code },
                )
                MochiTextField(
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
                MochiTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text(stringResource(R.string.market_editor_zone_days)) },
                    placeholder = { Text(stringResource(R.string.market_editor_zone_days_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MochiTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(stringResource(R.string.market_editor_zone_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmText = stringResource(R.string.market_editor_zone_save),
        onConfirm = {
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
        dismissText = stringResource(R.string.market_editor_zone_cancel),
    )
}

