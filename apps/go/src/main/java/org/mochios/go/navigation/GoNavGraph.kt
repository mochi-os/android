// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.go.ui.detail.GoGameDetailScreen
import org.mochios.go.ui.list.GoGameListScreen

/**
 * Routes for the Go app module. The list page is the landing screen reached
 * from the launcher / Mochi menu, and each game expands to a detail screen
 * (board + chat + move log) keyed by game id.
 */
object GoApp {
    /** Landing screen — the active + completed games list. */
    const val HOME = "go"
    const val GAME = "go/{gameId}"

    fun gameDetail(gameId: String): String = "go/$gameId"
}

/**
 * Wire the Go app's screens into a parent [NavGraphBuilder]. Pattern matches
 * the People / Wikis / Chess nav graphs.
 *
 * - [onLogout] — handler the host (MainActivity) plugs in to clear the
 *   session and return to the login flow when a screen surfaces that intent.
 * - [onOpenNotifications] — opens the cross-feature notifications screen
 *   (lives in the Settings module). Each top-level screen renders the
 *   notification bell in its top bar.
 * - [onOpenLink] — generic in-app deep-link handler. The Go module doesn't
 *   currently emit any (chat/new etc.) but the parameter mirrors People for
 *   consistency and future-proofs message-from-game links.
 */
fun NavGraphBuilder.goNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    composable(GoApp.HOME) {
        GoGameListScreen(
            navController = navController,
            onLogout = onLogout,
            onOpenNotifications = onOpenNotifications,
            onOpenLink = onOpenLink,
        )
    }

    composable(
        route = GoApp.GAME,
        arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
    ) {
        GoGameDetailScreen(
            navController = navController,
            onOpenNotifications = onOpenNotifications,
        )
    }
}
