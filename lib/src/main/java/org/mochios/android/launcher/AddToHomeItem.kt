// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HomeMax
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.mochios.android.R
import org.mochios.android.ui.components.MochiDropdownMenuItem

/**
 * "Add to home screen" in an item's menu, which pins a shortcut to the item
 * at [path] in [app], named [label] - see [Shortcuts.pinItem]. [onClick]
 * closes the menu. Absent where the launcher cannot pin.
 */
@Composable
fun AddToHomeItem(app: String, path: String, label: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val pinnable = remember(context) { Shortcuts.supported(context) }
    if (!pinnable) return
    MochiDropdownMenuItem(
        text = { Text(stringResource(R.string.launcher_add_to_home)) },
        onClick = {
            onClick()
            Shortcuts.pinItem(context, app, path, label)
        },
        leadingIcon = { Icon(Icons.Outlined.HomeMax, contentDescription = null) },
    )
}
