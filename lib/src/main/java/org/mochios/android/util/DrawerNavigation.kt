// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import androidx.navigation.NavController

/**
 * Opens a drawer pick in place of the screen it was picked on, so drawer
 * picks never pile up and Back leaves the app from any of them.
 *
 * The screen is matched by its back stack entry rather than its route, so it
 * is replaced whatever arguments it was opened with and however it was
 * reached, a deep link included.
 *
 * @param route the destination the drawer item opens.
 */
fun NavController.navigateFromDrawer(route: String) {
    val current = currentDestination?.id
    navigate(route) {
        if (current != null) {
            popUpTo(current) { inclusive = true }
        }
        launchSingleTop = true
    }
}
