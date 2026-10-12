// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

// Most apps start at a router: a screen with no content of its own that reads
// the item last viewed and opens it in its place. A deep link arriving as the
// app starts navigates on the router's first frame, before the router has
// resolved, so the two have to keep out of each other's way.

/**
 * Leave a router for the destination it resolved, in its place.
 *
 * Only while the router is still the screen on top. Once a deep link has
 * opened a screen over it, the router's own navigate would pop that screen
 * along with itself and open the last-viewed item instead of the linked one.
 *
 * @param router the router's own back stack entry.
 * @param route the destination it resolved.
 */
fun NavController.leaveRouter(router: NavBackStackEntry, route: String) {
    if (currentBackStackEntry?.id != router.id) return
    navigate(route) { popUpTo(router.destination.id) { inclusive = true } }
}

/**
 * Open a deep link's [route] in an app that starts at [router], in place of
 * the router or of the app's open [screen], as a drawer pick would.
 *
 * Left beneath the linked screen, the router would resolve again on Back and
 * reopen the item just viewed, which is the linked one. A second copy of
 * [screen] would keep the first one's ViewModel and websocket subscription
 * running.
 *
 * @param router the app's router route.
 * @param screen the route pattern of the screen [route] fills.
 * @param route the destination the link names.
 */
fun NavController.openInPlace(router: String, screen: String, route: String) {
    if (isAt(route)) return
    val replaced = when {
        holds(router) -> router
        holds(screen) -> screen
        else -> null
    }
    navigate(route) {
        replaced?.let { popUpTo(it) { inclusive = true } }
        launchSingleTop = true
    }
}

/**
 * Whether [route] already has an entry on the back stack.
 *
 * @param route a destination's route pattern.
 * @return true when the stack holds one.
 */
fun NavController.holds(route: String): Boolean = topmost(route) != null

/**
 * Whether [route] is the screen already on top, arguments and all. Only the
 * arguments its destination declares with navArgument are compared; an
 * undeclared one matches any value.
 *
 * @param route a filled route.
 * @return true when navigating there would re-open the current screen.
 */
fun NavController.isAt(route: String): Boolean {
    val entry = currentBackStackEntry ?: return false
    return topmost(route) === entry
}

/**
 * The entry nearest the top of the back stack that [route] matches, by the
 * same destination-and-arguments test popBackStack(route) uses. The public
 * way to ask: the back stack itself and that test are internal to the
 * navigation library.
 *
 * @param route a route pattern or a filled route.
 * @return the entry, or null when none on the stack matches.
 */
private fun NavController.topmost(route: String): NavBackStackEntry? =
    try {
        getBackStackEntry(route)
    } catch (_: IllegalArgumentException) {
        null
    }
