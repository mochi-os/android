// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.android.share.SharedImages
import org.mochios.android.share.shareMessage
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.util.leaveRouter
import org.mochios.android.util.navigateFromDrawer
import org.mochios.android.util.openInPlace
import org.mochios.chat.ui.chat.ChatScreen
import org.mochios.chat.ui.newchat.NewChatScreen
import org.mochios.chat.ui.share.ChatShareScreen
import org.mochios.chat.ui.router.CHAT_FEATURE
import org.mochios.chat.ui.router.ChatRouter
import org.mochios.chat.ui.settings.ChatSettingsScreen

object ChatApp {
    /**
     * HOME is the router: it resolves the last-viewed chat, navigates to CHAT
     * and pops itself.
     */
    const val HOME = "chat/router"
    const val ROUTER = "chat/router"
    // Detail routes use a `chat/` discriminator after the feature prefix so
    // they can't shadow the literal HOME / NEW_CHAT routes — `chat/list`
    // would otherwise match `chat/{chatId}` with chatId='list' and route to
    // the detail screen rendering NotFoundState.
    // [message] and [images] fill the chat's message box with what another
    // app shares, from a `chat/share` link; nothing is sent until the user
    // sends it. A new chat started from that choice carries them on.
    const val CHAT = "chat/chat/{chatId}?message={message}&images={images}"
    const val NEW_CHAT = "chat/new?friend={friend}&account={account}&address={address}&name={name}&message={message}&images={images}"
    const val CHAT_SETTINGS = "chat/chat/{chatId}/settings"
    const val SHARE = "chat/share?text={text}&url={url}&images={images}"

    // Every value a builder puts in a route is escaped with Uri.encode, which
    // Navigation undoes once as it fills the destination's arguments, so a
    // value holding `&`, `#` or `%` cannot add arguments of its own.
    fun chat(chatId: String) = "chat/chat/${Uri.encode(chatId)}"

    /**
     * The chat, its message box filled with [message] and the images
     * [SharedImages] holds under [images].
     */
    fun chat(chatId: String, message: String, images: String): String =
        "chat/chat/${Uri.encode(chatId)}" + query("message" to message, "images" to images)
    fun newChat(friendId: String = "") = if (friendId.isEmpty()) "chat/new" else "chat/new?friend=${Uri.encode(friendId)}"

    /** A new chat, whose message box opens filled with [message] and [images], as [chat]'s does. */
    fun newChat(message: String, images: String): String = "chat/new" + query("message" to message, "images" to images)

    /**
     * The choice of chat for what another app shares: [text], then [url] on
     * a line of its own, and the images [SharedImages] holds under [images].
     */
    fun share(text: String, url: String, images: String): String =
        "chat/share" + query("text" to text, "url" to url, "images" to images)

    private fun query(vararg values: Pair<String, String>): String {
        val filled = values.filter { (_, value) -> value.isNotEmpty() }
        return if (filled.isEmpty()) "" else "?" + filled.joinToString("&") { (key, value) -> "$key=${Uri.encode(value)}" }
    }

    /** New chat on [account] with the person at [address] there, as people hands a messaging address over. */
    fun newChat(account: String, address: String, name: String) =
        "chat/new?account=${Uri.encode(account)}&address=${Uri.encode(address)}&name=${Uri.encode(name)}"
    fun chatSettings(chatId: String) = "chat/chat/${Uri.encode(chatId)}/settings"
}

fun NavGraphBuilder.chatNavGraph(
    navController: NavController,
    onOpenNotifications: () -> Unit,
    onOpenLink: (String) -> Unit = {},
) {
    composable(ChatApp.ROUTER) { entry ->
        ChatRouter(onResolve = { chatId ->
            navController.leaveRouter(entry, ChatApp.chat(chatId))
        })
    }

    composable(
        route = ChatApp.CHAT,
        arguments = listOf("chatId", "message", "images").map { name ->
            navArgument(name) {
                type = NavType.StringType
                defaultValue = ""
                nullable = false
            }
        },
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://{host}/chat/{chatId}" }
        )
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
        ChatScreen(
            chatId = chatId,
            onSelectChat = { id ->
                navController.navigateFromDrawer(ChatApp.chat(id))
            },
            onNewChat = { navController.navigate(ChatApp.newChat()) },
            onSettings = { id -> navController.navigate(ChatApp.chatSettings(id)) },
            onChatDeleted = {
                // The chat no longer exists locally, and is forgotten as last
                // viewed: the router resolves to another, or to none.
                navController.navigate(ChatApp.ROUTER) {
                    popUpTo(ChatApp.CHAT) { inclusive = true }
                }
            },
            onOpenNotifications = onOpenNotifications,
        )
    }

    // A friend to chat with, or a contact's messaging address on another
    // network with the account to reach it through, as people hands it over.
    composable(
        route = ChatApp.NEW_CHAT,
        arguments = listOf("friend", "account", "address", "name", "message", "images").map { name ->
            navArgument(name) {
                type = NavType.StringType
                defaultValue = ""
                nullable = false
            }
        },
    ) { entry ->
        val message = entry.arguments?.getString("message").orEmpty()
        val images = entry.arguments?.getString("images").orEmpty()
        NewChatScreen(
            onBack = { navController.popBackStack() },
            onChatCreated = { chatId ->
                if (message.isNotEmpty() || images.isNotEmpty()) {
                    // Started from a share: the chat opens with it in its message box.
                    navController.openShared(chatId, message, images)
                } else {
                    navController.popBackStack()
                    navController.navigate(ChatApp.chat(chatId)) {
                        popUpTo(ChatApp.CHAT) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            },
            onOpenLink = onOpenLink,
        )
    }

    // What another app shares, for the user to choose the chat whose message
    // box it fills, or to start one.
    composable(
        route = ChatApp.SHARE,
        arguments = listOf("text", "url", "images").map { name ->
            navArgument(name) {
                type = NavType.StringType
                defaultValue = ""
                nullable = false
            }
        },
    ) { entry ->
        val message = shareMessage(entry.arguments?.getString("text").orEmpty(), entry.arguments?.getString("url").orEmpty())
        val images = entry.arguments?.getString("images").orEmpty()
        ChatShareScreen(
            onBack = { navController.popBackStack() },
            onChoose = { chatId -> navController.openShared(chatId, message, images) },
            onNewChat = { navController.navigate(ChatApp.newChat(message, images)) },
        )
    }

    composable(
        route = ChatApp.CHAT_SETTINGS,
        arguments = listOf(navArgument("chatId") { type = NavType.StringType })
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
        ChatSettingsScreen(
            onBack = { navController.popBackStack() },
            onChatLeft = {
                // Deleted here: forgotten as last viewed, or the router would
                // open it again. Navigate to the router rather than pop back to
                // it: it removes itself from the stack once it resolves, so
                // there is no entry to pop to.
                LastViewedStore.drop(navController.context, CHAT_FEATURE, chatId)
                navController.navigate(ChatApp.ROUTER) {
                    popUpTo(ChatApp.CHAT) { inclusive = true }
                }
            },
        )
    }
}

/**
 * Open [chatId] with [message] and [images] in its message box, in place of
 * the choice of chat, and of a new chat started from it: Back leaves the
 * chat as a drawer pick's does. Nothing is sent until the user sends it.
 */
internal fun NavController.openShared(chatId: String, message: String, images: String) {
    popBackStack(ChatApp.SHARE, inclusive = true)
    openInPlace(ChatApp.ROUTER, ChatApp.CHAT, ChatApp.chat(chatId, message, images))
}
