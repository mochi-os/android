// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.router

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
 * Start destination for the feeds nav graph. Reads the last-viewed feed
 * from [LastViewedStore] and immediately navigates the caller's
 * `onResolve` callback to the corresponding [FeedsApp.FEED] route, or to
 * the [LastViewedStore.ALL] aggregate view when no prior visit is
 * recorded (cold install, data clear, fresh login).
 *
 * Stays on screen for one frame only — a tiny [CircularProgressIndicator]
 * masks the navigation handoff so the user never sees a blank surface.
 */
@Composable
fun FeedsRouter(onResolve: (feedId: String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val target = LastViewedStore.get(context, FEEDS_FEATURE) ?: LastViewedStore.ALL
        onResolve(target)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

const val FEEDS_FEATURE = "feeds"
