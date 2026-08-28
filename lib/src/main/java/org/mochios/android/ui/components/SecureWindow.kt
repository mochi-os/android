// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Mark the surrounding window secure for as long as this composable is in the
 * tree, so the system refuses to screenshot it, record it, or keep it in the
 * recent-apps thumbnail.
 *
 * Call it from anything that puts a shared secret on screen - a TOTP secret,
 * a set of recovery codes. Both the hosting activity's window and, when the
 * caller is inside a dialog, the dialog's own window are flagged: a dialog
 * gets a separate window, and flagging only one of the two leaves the other
 * capturable.
 */
@Composable
fun SecureWindow() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view) {
        val windows = listOfNotNull(
            context.activity()?.window,
            (view.parent as? DialogWindowProvider)?.window,
        )
        for (window in windows) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        onDispose {
            for (window in windows) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private fun Context.activity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
