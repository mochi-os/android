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
