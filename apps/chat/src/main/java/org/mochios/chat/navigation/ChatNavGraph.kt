package org.mochios.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.chat.ui.chat.ChatScreen
import org.mochios.chat.ui.newchat.NewChatScreen
import org.mochios.chat.ui.router.ChatRouter
import org.mochios.chat.ui.settings.ChatSettingsScreen

object ChatApp {
    /**
     * HOME points at the router (resolves last-viewed chat); the router
     * navigates onward to CHAT with the resolved id (or empty for first
     * launch with no history) and pops itself off the back stack.
     */
    const val HOME = "chat/router"
    const val ROUTER = "chat/router"
    // Detail routes use a `chat/` discriminator after the feature prefix so
    // they can't shadow the literal HOME / NEW_CHAT routes — `chat/list`
    // would otherwise match `chat/{chatId}` with chatId='list' and route to
    // the detail screen rendering NotFoundState.
    const val CHAT = "chat/chat/{chatId}"
    const val NEW_CHAT = "chat/new"
    const val CHAT_SETTINGS = "chat/chat/{chatId}/settings"

    fun chat(chatId: String) = "chat/chat/$chatId"
    fun chatSettings(chatId: String) = "chat/chat/$chatId/settings"
}

fun NavGraphBuilder.chatNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
) {
    composable(ChatApp.ROUTER) {
        ChatRouter(onResolve = { chatId ->
            navController.navigate(ChatApp.chat(chatId)) {
                popUpTo(ChatApp.ROUTER) { inclusive = true }
            }
        })
    }

    composable(
        route = ChatApp.CHAT,
        arguments = listOf(navArgument("chatId") {
            type = NavType.StringType
            defaultValue = ""
            nullable = false
        }),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://{host}/chat/{chatId}" }
        )
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
        ChatScreen(
            chatId = chatId,
            onSelectChat = { id ->
                navController.navigate(ChatApp.chat(id)) {
                    popUpTo(ChatApp.CHAT) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onNewChat = { navController.navigate(ChatApp.NEW_CHAT) },
            onSettings = { id -> navController.navigate(ChatApp.chatSettings(id)) },
            onLogout = onLogout,
        )
    }

    composable(ChatApp.NEW_CHAT) {
        NewChatScreen(
            onBack = { navController.popBackStack() },
            onChatCreated = { chatId ->
                navController.popBackStack()
                navController.navigate(ChatApp.chat(chatId)) {
                    popUpTo(ChatApp.CHAT) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }

    composable(
        route = ChatApp.CHAT_SETTINGS,
        arguments = listOf(navArgument("chatId") { type = NavType.StringType })
    ) {
        ChatSettingsScreen(
            onBack = { navController.popBackStack() },
            onChatLeft = {
                // Drop back to the router so last-viewed re-resolves (the
                // left chat shouldn't reappear as the next destination).
                navController.popBackStack(ChatApp.ROUTER, inclusive = false)
            },
        )
    }
}
