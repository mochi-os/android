// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownField
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextField
import org.mochios.settings.R
import org.mochios.settings.api.Availability
import org.mochios.android.R as MochiR

/** How long the field waits after the last keystroke before asking the server. */
private const val AVAILABILITY_DELAY = 400L

/**
 * The check a name field runs as the user types, and its answer: the answer
 * for the name as it stands, and whether one is still coming.
 */
@Composable
internal fun rememberAvailability(
    name: String,
    domain: String,
    check: suspend (String, String) -> Availability?,
): Pair<Availability?, Boolean> {
    var result by remember { mutableStateOf<Availability?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(name, domain) {
        result = null
        if (name.isEmpty()) {
            checking = false
            return@LaunchedEffect
        }
        checking = true
        delay(AVAILABILITY_DELAY)
        result = check(name, domain)
        checking = false
    }
    return result to checking
}

/** Under a name field: checking, available, or why not. */
@Composable
internal fun AvailabilityNote(result: Availability?, checking: Boolean) {
    val (text, color) = when {
        checking -> stringResource(R.string.networks_checking) to MaterialTheme.colorScheme.onSurfaceVariant
        result == null -> return
        result.available -> stringResource(R.string.networks_available) to MaterialTheme.colorScheme.primary
        else -> availabilityReason(result.reason) to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

/**
 * Choosing a handle: the field with its live check, a suggestion from the
 * person's name, and a confirmation, since a handle is permanent. The owner's
 * Handles screen uses it too, to take a name.
 */
@Composable
internal fun HandleChooser(
    domains: List<String>,
    suggestion: String,
    title: String,
    reserving: Boolean,
    check: suspend (String, String) -> Availability?,
    onReserve: (name: String, domain: String, onReserved: () -> Unit) -> Unit,
) {
    var name by remember(suggestion) { mutableStateOf(suggestion) }
    var domain by remember(domains) { mutableStateOf(domains.firstOrNull().orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val (result, checking) = rememberAvailability(name, domain, check)
    val address = if (domain.isNotEmpty()) "$name@$domain" else name

    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            MochiTextField(
                value = name,
                onValueChange = { name = it.trim().lowercase() },
                label = { Text(stringResource(R.string.networks_handle)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (domains.size > 1) {
                MochiDropdownField(
                    value = domain,
                    expanded = picking,
                    onExpandedChange = { picking = it },
                    label = stringResource(R.string.networks_domain),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (option in domains) {
                        MochiDropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                picking = false
                                domain = option
                            },
                            selected = option == domain,
                        )
                    }
                }
            }
            Column(modifier = Modifier.heightIn(min = 20.dp)) {
                AvailabilityNote(result = result, checking = checking && name.isNotEmpty())
            }
            MochiButton(
                onClick = { confirming = true },
                enabled = result?.available == true && !checking && !reserving,
            ) {
                if (reserving) {
                    CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.networks_reserve))
            }
        }
    }

    if (confirming) {
        MochiAlertDialog(
            onDismissRequest = { confirming = false },
            title = stringResource(R.string.networks_reserve_title),
            text = stringResource(R.string.networks_reserve_message, address),
            confirmText = stringResource(R.string.networks_reserve),
            onConfirm = {
                confirming = false
                onReserve(name, domain) { name = "" }
            },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}
