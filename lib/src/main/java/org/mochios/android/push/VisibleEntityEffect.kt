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
 * Marks the calling screen as the one on display for [objectId], for as long
 * as it is resumed: an arriving push about the same entity is dropped rather
 * than posted, and rows that landed while the user was away are cleared from
 * the tray on every return, not only when the screen is first opened.
 *
 * Inside a `NavHost` the lifecycle is the destination's own, so this follows
 * navigation as well as the activity going to the background.
 *
 * @param app the app slug the tray tags carry, e.g. "feeds".
 * @param objectId the entity on screen; a blank id registers nothing.
 */
@Composable
fun VisibleEntityEffect(app: String, objectId: String) {
    if (app.isEmpty() || objectId.isEmpty()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, app, objectId) {
        val token = Any()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    VisibleEntity.show(token, app, objectId)
                    SystemNotifications.cancelFor(context, app, objectId)
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
