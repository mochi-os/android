// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.mochios.android.R as MochiR
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton

/**
 * One entry in a [StaffCardMenu].
 *
 * @property label Text shown for the entry.
 * @property icon Leading icon shown beside the label.
 * @property destructive Whether the entry is drawn in the error colour.
 * @property onClick Called after the menu closes.
 */
data class StaffCardAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The overflow button a staff list card carries its actions behind, so the
 * card does not spend a row on buttons. Draws nothing when [actions] is empty.
 */
@Composable
fun StaffCardMenu(actions: List<StaffCardAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) {
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        MochiIconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(MochiR.string.common_more_options),
            )
        }
        MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                MochiDropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    destructive = action.destructive,
                )
            }
        }
    }
}
