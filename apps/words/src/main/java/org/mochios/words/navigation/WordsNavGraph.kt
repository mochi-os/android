package org.mochios.words.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.words.ui.detail.WordsGameDetailScreen
import org.mochios.words.ui.list.WordsGameListScreen

/**
 * Route constants and helpers for the Words Android module.
 *
 * - [HOME] is the games-list landing page (`/words` in the web URL scheme).
 * - [GAME] is the detail screen for a single game (`/words/<gameId>`),
 *   where `gameId` is either the game's `id` or `fingerprint`.
 *
 * Deep link `https://<host>/words/{gameId}` is registered so notification
 * payloads carrying a "/words/<id>" link route directly to the game
 * detail.
 */
object WordsApp {
    const val HOME = "words"
    const val GAME = "words/{gameId}"

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
            onLogout = onLogout,
            onOpenNotifications = onOpenNotifications,
            onOpenLink = onOpenLink,
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
