// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.chess.ui.detail.ChessGameDetailScreen
import org.mochios.chess.ui.list.ChessGameListScreen

/**
 * Route constants and helpers for the chess Android module.
 *
 * Two top-level surfaces:
 *
 *  - [HOME] (`chess`) — class-level game list. Lands here when the chess
 *    launcher alias is tapped.
 *  - [GAME] (`chess/{gameId}`) — entity-context game detail. The detail
 *    screen pattern matches every other entity-context route in the
 *    Android client (e.g. `wikis/{wikiId}`).
 *
 * The detail screen itself is a stub in this wave — a separate agent owns
 * the board, engine integration, chat panel, and WebSocket wiring. The
 * stub gives the NavGraph something concrete to register so the rest of
 * the module builds cleanly.
 */
object ChessApp {
    /** Launcher entry point — the game list. */
    const val HOME = "chess"

    /** Entity-context route pattern for the per-game detail screen. */
    const val GAME = "chess/{gameId}"

    /** Build the per-game detail route for a specific game UID / fingerprint. */
    fun gameDetail(gameId: String) = "chess/$gameId"
}

/**
 * Register the chess routes on the supplied [NavGraphBuilder]. Called from
 * `MainActivity.setContent`'s `NavHost`, alongside every other app's
 * `*NavGraph` extension. Callback contract mirrors the other apps so the
 * host can wire the same `onLogout` / `onOpenNotifications` / `onOpenLink`
 * handlers it already uses.
 *
 *  - [onLogout] reserved for future use (the list screen overflow currently
 *    leaves logout to the menu shell, matching the pattern of wikis and
 *    feeds). Accepted now to keep the call-site uniform.
 *  - [onOpenNotifications] is a one-arg navigator into the lib's
 *    notifications inbox surface.
 *  - [onOpenLink] handles cross-app deep links — e.g. the new-game dialog's
 *    "Add friends" empty-state button jumps to the People app.
 */
fun NavGraphBuilder.chessNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    composable(ChessApp.HOME) {
        ChessGameListScreen(
            navController = navController,
            onLogout = onLogout,
            onOpenNotifications = onOpenNotifications,
            onOpenLink = onOpenLink,
        )
    }

    composable(
        route = ChessApp.GAME,
        arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
    ) {
        ChessGameDetailScreen(navController = navController)
    }
}
