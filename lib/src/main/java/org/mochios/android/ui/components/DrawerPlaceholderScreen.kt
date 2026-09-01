// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * What a drawer-backed feature shows before anything is selected: the title
 * bar with its hamburger, and a centred line telling the viewer to pick
 * something from the drawer.
 *
 * @param title Screen title, reused as the hamburger's content description.
 * @param message Centred prompt shown in place of content.
 * @param onOpenDrawer Called when the hamburger is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerPlaceholderScreen(
    title: String,
    message: String,
    onOpenDrawer: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    MochiIconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = title)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
