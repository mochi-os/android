// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.go.ui.list.GoGameListScreen
import org.mochios.go.ui.newgame.NewGoGameScreen

object GoApp {
    /** Landing screen — the active + completed games list. */
    const val HOME = "go"
    const val GAME = "go/{gameId}"

    /** Start-a-new-game screen (opponent, board size, komi). */
    const val NEW_GAME = "go/new"

    fun gameDetail(gameId: String): String = "go/$gameId"
}

/**
 * Wire the Go screens into the parent graph; [onOpenLink] is unused by Go and
 * mirrors People.
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
            gameId = "",
            onLogout = onLogout,
            onOpenNotifications = onOpenNotifications,
            onOpenLink = onOpenLink,
        )
    }

    composable(GoApp.NEW_GAME) {
        NewGoGameScreen(
            onBack = { navController.popBackStack() },
            // Drop the new-game screen and open the game just started, so Back
            // from the board returns to the list rather than the form.
            onCreated = { gameId ->
                navController.navigate(GoApp.gameDetail(gameId)) {
                    popUpTo(GoApp.NEW_GAME) { inclusive = true }
                }
            },
            onAddFriends = { onOpenLink("people") },
        )
    }

    composable(
        route = GoApp.GAME,
        arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
    ) { backStackEntry ->
        GoGameListScreen(
            navController = navController,
            gameId = backStackEntry.arguments?.getString("gameId").orEmpty(),
            onLogout = onLogout,
            onOpenNotifications = onOpenNotifications,
            onOpenLink = onOpenLink,
        )
    }
}
