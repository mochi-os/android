// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.router

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.mochios.android.ui.components.LastViewedStore

/**
 * Start destination: routes to the game last opened, or to the no-selection
 * sentinel when none is recorded; the spinner masks the one-frame handoff.
 */
@Composable
fun GoRouter(onResolve: (gameId: String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val target = LastViewedStore.get(context, GO_FEATURE) ?: LastViewedStore.ALL
        onResolve(target)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** [LastViewedStore] key for the go module. */
const val GO_FEATURE = "go"
