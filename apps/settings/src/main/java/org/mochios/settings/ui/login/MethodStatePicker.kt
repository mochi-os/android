// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.settings.R

/** The states a login method can be in, in the order the pickers show them. */
internal val METHOD_STATE_SLOTS = listOf("disabled", "allowed", "required")

/** A login method state's name. */
@Composable
internal fun methodStateLabel(slot: String): String = when (slot) {
    "disabled" -> stringResource(R.string.system_settings_state_disabled)
    "allowed" -> stringResource(R.string.system_settings_state_allowed)
    "required" -> stringResource(R.string.system_settings_state_required)
    else -> slot
}

/**
 * Disabled / Allowed / Required for one login method, as the user's Login
 * screen and the server's System settings both pick it: the states in [slots]
 * that are among [METHOD_STATE_SLOTS], the current one filled, those in
 * [unavailable] and every one while [busy] not pickable.
 */
@Composable
internal fun MethodStatePicker(
    value: String,
    slots: Collection<String>,
    busy: Boolean,
    onPick: (String) -> Unit,
    unavailable: Set<String> = emptySet(),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        METHOD_STATE_SLOTS.filter { it in slots }.forEach { slot ->
            if (value == slot) {
                MochiButton(onClick = {}, enabled = false, modifier = Modifier.padding(end = 4.dp)) {
                    Text(methodStateLabel(slot))
                }
            } else {
                MochiOutlinedButton(
                    onClick = { onPick(slot) },
                    enabled = !busy && slot !in unavailable,
                    modifier = Modifier.padding(end = 4.dp),
                ) { Text(methodStateLabel(slot)) }
            }
        }
    }
}
