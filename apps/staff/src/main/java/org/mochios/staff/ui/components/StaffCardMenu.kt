// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import org.mochios.android.R as MochiR
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton

/**
 * One entry in a [StaffCardMenu].
 *
 * @property label Text shown for the entry.
 * @property icon Leading icon shown beside the label.
 * @property destructive Whether the entry is drawn in the error colour when it
 * sits in the menu. A lone action's button stays neutral, since it only opens
 * the confirmation.
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
 * card does not spend a row on buttons. A lone action is drawn as its own
 * icon-and-label button instead, since a menu of one only adds a tap. Draws
 * nothing when [actions] is empty.
 */
@Composable
fun StaffCardMenu(actions: List<StaffCardAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) {
        return
    }
    val single = actions.singleOrNull()
    if (single != null) {
        MochiOutlinedButton(onClick = single.onClick, modifier = modifier) {
            Icon(single.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(single.label)
        }
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
