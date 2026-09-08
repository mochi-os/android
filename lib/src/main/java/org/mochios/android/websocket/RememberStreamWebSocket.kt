// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point so [rememberStreamWebSocket] can resolve the singleton
 * socket without a ViewModel.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MochiWebSocketEntryPoint {

    /** @return the app-wide socket. */
    fun mochiWebSocket(): MochiWebSocket
}

/**
 * Open a screen-scoped stream for as long as this composable is in the
 * composition. A null or blank [streamKey] returns null and opens nothing;
 * changing it closes the previous stream and opens a new one.
 *
 * @param streamKey the key the server multiplexes the stream by: a game
 *   record's key, "staff-events", "market-thread-<id>".
 * @param app the app the key belongs to ("chess", "go", "words", "staff",
 *   "market"), which names the token the connection authorises with.
 * @return the stream, or null when there is no key to open one on.
 */
@Composable
fun rememberStreamWebSocket(streamKey: String?, app: String): MochiWebSocket.Stream? {
    if (streamKey.isNullOrBlank()) return null
    val context = LocalContext.current
    val socket = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MochiWebSocketEntryPoint::class.java,
        ).mochiWebSocket()
    }
    val stream = remember(socket, streamKey, app) { socket.openStream(streamKey, app) }
    DisposableEffect(stream) {
        onDispose { stream.close() }
    }
    return stream
}
