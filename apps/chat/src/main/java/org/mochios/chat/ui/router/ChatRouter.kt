// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.router

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
 * Start destination for the chat nav graph. Reads the last-viewed chat
 * from [LastViewedStore] and immediately navigates to the matching detail
 * route. If no prior chat is recorded (fresh install / data clear), the
 * empty string is passed through so [ChatScreen] can render its
 * drawer-only "pick a chat" view with the drawer auto-opened.
 */
@Composable
fun ChatRouter(onResolve: (chatId: String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onResolve(LastViewedStore.get(context, CHAT_FEATURE).orEmpty())
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

const val CHAT_FEATURE = "chat"
