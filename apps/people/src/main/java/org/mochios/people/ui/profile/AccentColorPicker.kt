// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.ColorPicker
import org.mochios.people.R

/**
 * Inline accent colour picker backing the Profile editor's "Accent" section:
 * the shared [ColorPicker] plus the Clear / Save buttons this form needs.
 *
 * [hex] is the draft the ViewModel holds; edits flow out through [onHexChange]
 * (live, for the avatar preview) and are persisted by [onSave]. An empty hex
 * means "no accent".
 *
 * @param hex Current accent hex ("" when unset).
 * @param isSaving Disables Save and shows a spinner while a save is in flight.
 * @param onHexChange Fired on every change with the new `#rrggbb` hex.
 * @param onClear Clears the accent (the caller persists the empty value).
 * @param onSave Persists the current draft.
 */
@Composable
fun AccentColorPicker(
    hex: String,
    isSaving: Boolean,
    onHexChange: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Save and Clear share the same in-flight flag ([isSaving]); remember which
    // button started the request so only that one shows the spinner. Reset once
    // the request settles.
    var pending by remember { mutableStateOf<AccentAction?>(null) }
    LaunchedEffect(isSaving) {
        if (!isSaving) pending = null
    }

    ColorPicker(
        hex = hex,
        onHexChange = onHexChange,
        modifier = modifier,
        hexPlaceholder = stringResource(R.string.people_profile_accent_none),
    ) {
        val clearing = isSaving && pending == AccentAction.CLEAR
        val saving = isSaving && pending == AccentAction.SAVE
        OutlinedButton(
            onClick = {
                pending = AccentAction.CLEAR
                onClear()
            },
            enabled = !isSaving,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            if (clearing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(stringResource(R.string.people_profile_clear))
        }
        Button(
            onClick = {
                pending = AccentAction.SAVE
                onSave()
            },
            enabled = !isSaving,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.people_common_save))
        }
    }
}

/** Which accent button kicked off the in-flight request, for its spinner. */
private enum class AccentAction { SAVE, CLEAR }
