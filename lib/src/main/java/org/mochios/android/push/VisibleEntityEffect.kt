// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Marks the calling screen as the one on display for [path], for as long
 * as it is resumed: an arriving push about the same entity is dropped rather
 * than posted, and rows that landed while the user was away are cleared from
 * the tray on every return, not only when the screen is first opened.
 *
 * Inside a `NavHost` the lifecycle is the destination's own, so this follows
 * navigation as well as the activity going to the background.
 *
 * @param app the app slug the link starts with, e.g. "feeds".
 * @param path what the screen shows, under the app: usually an entity id, but
 *   as many segments as it takes to identify it - market puts a category where
 *   other apps put the entity, so a message thread names three. A blank path
 *   registers nothing.
 * @param socketKey the key this screen's websocket subscribes with. Suppression
 *   only holds while that socket is up, so a screen with no live socket covers
 *   nothing - which is why this is required rather than guessed from [path].
 * @param marksRead whether being on this screen means the user has read what
 *   arrives for it, so the row is retired on the server too. True only where
 *   the screen shows exactly the entity a notification names - a chat, a game -
 *   never a container like a feed, whose notifications are about posts inside
 *   it rather than the feed itself.
 */
@Composable
fun VisibleEntityEffect(
    app: String,
    path: String,
    socketKey: String,
    marksRead: Boolean = false,
) {
    if (app.isEmpty() || path.isEmpty() || socketKey.isEmpty()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, app, path, socketKey, marksRead) {
        val token = Any()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    VisibleEntity.show(token, app, path, marksRead, socketKey)
                    SystemNotifications.cancelFor(context, app, path.substringBefore('/'))
                }

                Lifecycle.Event.ON_PAUSE -> VisibleEntity.hide(token)

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            VisibleEntity.hide(token)
        }
    }
}
