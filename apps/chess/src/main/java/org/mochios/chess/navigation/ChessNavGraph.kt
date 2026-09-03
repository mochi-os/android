// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.chess.ui.list.ChessGameListScreen
import org.mochios.chess.ui.newgame.NewChessGameScreen
import org.mochios.chess.ui.router.ChessRouter

object ChessApp {
    /** Launcher entry point — resolves the last-viewed game and steps aside. */
    const val HOME = "chess"

    /** Entity-context route pattern for the per-game detail screen. */
    const val GAME = "chess/{gameId}"

    /** Start-a-new-game screen (opponent picker). */
    const val NEW_GAME = "chess/new"

    /** Build the per-game detail route for a specific game UID / fingerprint. */
    fun gameDetail(gameId: String) = "chess/$gameId"
}

fun NavGraphBuilder.chessNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    composable(ChessApp.HOME) {
        ChessRouter(onResolve = { gameId ->
            navController.navigate(ChessApp.gameDetail(gameId)) {
                popUpTo(ChessApp.HOME) { inclusive = true }
            }
        })
    }

    composable(ChessApp.NEW_GAME) {
        NewChessGameScreen(
            onBack = { navController.popBackStack() },
            // Drop the new-game screen and open the game just started, so Back
            // from the board returns to the list rather than the picker.
            onCreated = { gameId ->
                navController.navigate(ChessApp.gameDetail(gameId)) {
                    popUpTo(ChessApp.NEW_GAME) { inclusive = true }
                }
            },
            onAddFriends = { onOpenLink("people?action=add") },
        )
    }

    composable(
        route = ChessApp.GAME,
        arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
    ) { backStackEntry ->
        ChessGameListScreen(
            navController = navController,
            gameId = backStackEntry.arguments?.getString("gameId").orEmpty(),
            onLogout = onLogout,
            onOpenNotifications = onOpenNotifications,
            onOpenLink = onOpenLink,
        )
    }
}
