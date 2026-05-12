package org.mochios.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.chat.ui.chat.ChatScreen
import org.mochios.chat.ui.chatlist.ChatListScreen
import org.mochios.chat.ui.newchat.NewChatScreen
import org.mochios.chat.ui.settings.ChatSettingsScreen

object ChatApp {
    const val HOME = "chat/list"
    const val CHAT_LIST = "chat/list"
    const val CHAT = "chat/{chatId}"
    const val NEW_CHAT = "chat/new"
    const val CHAT_SETTINGS = "chat/{chatId}/settings"

    fun chat(chatId: String) = "chat/$chatId"
    fun chatSettings(chatId: String) = "chat/$chatId/settings"
}

fun NavGraphBuilder.chatNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
) {
    composable(ChatApp.CHAT_LIST) {
        ChatListScreen(
            onChatClick = { chatId -> navController.navigate(ChatApp.chat(chatId)) },
            onNewChat = { navController.navigate(ChatApp.NEW_CHAT) },
            onLogout = onLogout,
        )
    }

    composable(
        route = ChatApp.CHAT,
        arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://{host}/chat/{chatId}" }
        )
    ) {
        ChatScreen(
            onBack = { navController.popBackStack() },
            onSettings = { chatId -> navController.navigate(ChatApp.chatSettings(chatId)) },
        )
    }

    composable(ChatApp.NEW_CHAT) {
        NewChatScreen(
            onBack = { navController.popBackStack() },
            onChatCreated = { chatId ->
                navController.popBackStack()
                navController.navigate(ChatApp.chat(chatId))
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
                navController.popBackStack(ChatApp.CHAT_LIST, inclusive = false)
            },
        )
    }
}
