// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.words.ui.detail.WordsGameDetailScreen
import org.mochios.words.ui.list.WordsGameListScreen
import org.mochios.words.ui.newgame.NewWordsGameScreen

/**
 * Route constants and helpers for the Words Android module.
 *
 * - [HOME] is the games-list landing page (`/words` in the web URL scheme).
 * - [GAME] is the detail screen for a single game (`/words/<gameId>`),
 *   where `gameId` is the game's `id`.
 *
 * Deep link `https://<host>/words/{gameId}` is registered so notification
 * payloads carrying a "/words/<id>" link route directly to the game
 * detail.
 */
object WordsApp {
    const val HOME = "words"
    const val GAME = "words/{gameId}"

    /** Start-a-new-game screen (opponent picker + language). */
    const val NEW_GAME = "words/new"

    fun gameDetail(gameId: String) = "words/$gameId"
}

fun NavGraphBuilder.wordsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    composable(WordsApp.HOME) {
        WordsGameListScreen(
            onGameClick = { gameId ->
                navController.navigate(WordsApp.gameDetail(gameId))
            },
            onNewGame = { navController.navigate(WordsApp.NEW_GAME) },
            onLogout = onLogout,
            onOpenNotifications = onOpenNotifications,
            onOpenLink = onOpenLink,
        )
    }

    composable(WordsApp.NEW_GAME) {
        NewWordsGameScreen(
            onBack = { navController.popBackStack() },
            // Drop the new-game screen and open the game just started, so Back
            // from the board returns to the list rather than the picker.
            onCreated = { gameId ->
                navController.navigate(WordsApp.gameDetail(gameId)) {
                    popUpTo(WordsApp.NEW_GAME) { inclusive = true }
                }
            },
            onAddFriends = { onOpenLink("people") },
        )
    }

    composable(
        route = WordsApp.GAME,
        arguments = listOf(navArgument("gameId") {
            type = NavType.StringType
            defaultValue = ""
            nullable = false
        }),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://{host}/words/{gameId}" },
        ),
    ) { backStackEntry ->
        val gameId = backStackEntry.arguments?.getString("gameId").orEmpty()
        WordsGameDetailScreen(
            gameId = gameId,
            onBack = { navController.popBackStack() },
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
        )
    }
}
