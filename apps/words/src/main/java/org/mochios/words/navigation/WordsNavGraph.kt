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
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.words.ui.list.WordsGameListScreen
import org.mochios.words.ui.newgame.NewWordsGameScreen
import org.mochios.words.ui.router.WordsRouter

object WordsApp {
    /** Launcher entry point — resolves the last-viewed game and steps aside. */
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
        WordsRouter(onResolve = { gameId ->
            navController.navigate(WordsApp.gameDetail(gameId)) {
                popUpTo(WordsApp.HOME) { inclusive = true }
            }
        })
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
        WordsGameListScreen(
            gameId = backStackEntry.arguments?.getString("gameId").orEmpty(),
            onGameClick = { gameId ->
                navController.navigate(WordsApp.gameDetail(gameId)) {
                    popUpTo(WordsApp.GAME) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onGameClosed = {
                navController.navigate(WordsApp.gameDetail(LastViewedStore.ALL)) {
                    popUpTo(WordsApp.GAME) { inclusive = true }
                }
            },
            onNewGame = { navController.navigate(WordsApp.NEW_GAME) },
            onLogout = onLogout,
            onOpenNotifications = onOpenNotifications,
            onOpenLink = onOpenLink,
        )
    }
}
