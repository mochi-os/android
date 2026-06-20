// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.router

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
 * Start destination for the forums nav graph. Reads the last-viewed
 * forum and navigates onward; empty string is the "no forum selected"
 * sentinel that [ForumScreen] handles by auto-opening the drawer.
 */
@Composable
fun ForumsRouter(onResolve: (forumId: String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onResolve(LastViewedStore.get(context, FORUMS_FEATURE).orEmpty())
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

const val FORUMS_FEATURE = "forums"
