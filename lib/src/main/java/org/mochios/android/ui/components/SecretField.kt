// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.mochios.android.R

/** Placeholder standing in for a stored secret. Never the value itself. */
private const val MASK = "••••••••"

/**
 * Masked, write-only credential input for a secret the server never echoes
 * back. [configured] only says whether one is stored, so the bullet placeholder
 * is the one signal that it is. The typed value is cleared on submit, not kept
 * for retry.
 */
@Composable
fun SecretField(
    configured: Boolean,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    saving: Boolean = false,
) {
    var value by remember { mutableStateOf("") }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MochiTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            enabled = enabled && !saving,
            // Masked, and marked as a password so the IME neither learns it nor
            // offers it back as a suggestion.
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            placeholder = if (configured) {
                { Text(MASK) }
            } else {
                null
            },
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            MochiButton(
                onClick = {
                    onSave(value)
                    value = ""
                },
                enabled = enabled && !saving,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(stringResource(R.string.common_save))
            }
        }
    }
}
