// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextField
import org.mochios.market.lib.countryChoices
import org.mochios.market.lib.regionChoices
import org.mochios.market.lib.regionMatches
import org.mochios.market.lib.regionName
import org.mochios.market.model.ShippingRegions

/**
 * A shipping region or a country, picked from [choices] (codes, in the order
 * offered) by name in the reader's language; typing narrows the list. [value]
 * is the chosen code, or a region written before codes, shown as it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionField(
    value: String,
    choices: List<String>,
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf<String?>(null) }
    val names = remember(choices, locale) { choices.associateWith { code -> regionName(code, resources, locale) } }
    val shown = if (value.isEmpty()) "" else names[value] ?: regionName(value, resources, locale)
    val typed = query
    val offered = if (typed == null) choices else choices.filter { code -> regionMatches(code, names[code].orEmpty(), typed) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open ->
            expanded = open && enabled
            if (!open) query = null
        },
        modifier = modifier,
    ) {
        // Open, the field is for typing: it starts empty, with the current
        // choice behind it, so what is typed narrows the list from scratch.
        MochiTextField(
            value = typed ?: if (expanded) "" else shown,
            onValueChange = { text ->
                query = text
                expanded = true
            },
            label = { Text(label) },
            placeholder = if (expanded && shown.isNotEmpty()) {
                { Text(shown) }
            } else {
                null
            },
            singleLine = true,
            isError = isError,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded && offered.isNotEmpty(),
            onDismissRequest = {
                expanded = false
                query = null
            },
        ) {
            offered.forEach { code ->
                MochiDropdownMenuItem(
                    text = { Text(names[code].orEmpty()) },
                    onClick = {
                        onChange(code)
                        expanded = false
                        query = null
                    },
                    selected = code == value,
                )
            }
        }
    }
}

/** Every country a buyer may name, by name in the reader's language. */
@Composable
fun rememberCountryChoices(regions: ShippingRegions?): List<String> {
    val locale = LocalConfiguration.current.locales[0]
    return remember(regions, locale) { countryChoices(regions, locale) }
}

/** What a seller may ship to: the region groups, then every country by name. */
@Composable
fun rememberRegionChoices(regions: ShippingRegions?): List<String> {
    val locale = LocalConfiguration.current.locales[0]
    return remember(regions, locale) { regionChoices(regions, locale) }
}
