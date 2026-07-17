// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.forums.ui.find.FindForumsScreen
import org.mochios.forums.ui.forum.ForumScreen
import org.mochios.forums.ui.moderation.ForumModerationScreen
import org.mochios.forums.ui.moderation.ForumModerationSettingsScreen
import org.mochios.forums.ui.newpost.NewPostScreen
import org.mochios.forums.ui.post.PostScreen
import org.mochios.forums.ui.router.ForumsRouter
import org.mochios.forums.ui.saved.SavedScreen
import org.mochios.forums.ui.settings.ForumSettingsScreen

object ForumsApp {
    const val HOME = "forums/router"
    const val ROUTER = "forums/router"
    // Detail routes use a `forum/` discriminator after the feature prefix so
    // they can't shadow the literal HOME / FIND_FORUMS routes — `forums/list`
    // would otherwise match `forums/{forumId}` with forumId='list' and route
    // to the detail screen rendering NotFoundState.
    const val FORUM = "forums/forum/{forumId}"
    const val POST = "forums/forum/{forumId}/post/{postId}"
    // A single route composes and edits: an edit carries the target post's id as
    // an optional query arg, a new post omits it.
    const val NEW_POST = "forums/forum/{forumId}/new?postId={postId}"
    const val FIND_FORUMS = "forums/discover"
    const val FORUM_SETTINGS = "forums/forum/{forumId}/settings"
    const val MODERATION = "forums/forum/{forumId}/moderation"
    const val MODERATION_SETTINGS = "forums/forum/{forumId}/moderation/settings"
    const val SAVED = "forums/saved"

    fun forum(forumId: String) = "forums/forum/$forumId"
    fun post(forumId: String, postId: String) = "forums/forum/$forumId/post/$postId"
    fun newPost(forumId: String, postId: String? = null) =
        if (postId.isNullOrEmpty()) "forums/forum/$forumId/new"
        else "forums/forum/$forumId/new?postId=$postId"
    fun forumSettings(forumId: String) = "forums/forum/$forumId/settings"
    fun moderation(forumId: String) = "forums/forum/$forumId/moderation"
    fun moderationSettings(forumId: String) = "forums/forum/$forumId/moderation/settings"
}

fun NavGraphBuilder.forumsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
) {
    composable(ForumsApp.ROUTER) {
        ForumsRouter(onResolve = { forumId ->
            navController.navigate(ForumsApp.forum(forumId)) {
                popUpTo(ForumsApp.ROUTER) { inclusive = true }
            }
        })
    }

    composable(
        route = ForumsApp.FORUM,
        arguments = listOf(navArgument("forumId") {
            type = NavType.StringType
            defaultValue = ""
            nullable = false
        }),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://{host}/forums/{forumId}" }
        )
    ) { backStackEntry ->
        val forumId = backStackEntry.arguments?.getString("forumId").orEmpty()
        ForumScreen(
            forumId = forumId,
            onSelectForum = { id ->
                navController.navigate(ForumsApp.forum(id)) {
                    popUpTo(ForumsApp.FORUM) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onPostClick = { fId, pId -> navController.navigate(ForumsApp.post(fId, pId)) },
            onNewPost = { fId -> navController.navigate(ForumsApp.newPost(fId)) },
            onFindForums = { navController.navigate(ForumsApp.FIND_FORUMS) },
            onSettings = { fId -> navController.navigate(ForumsApp.forumSettings(fId)) },
            onModeration = { fId -> navController.navigate(ForumsApp.moderation(fId)) },
            onNavigateToSaved = { navController.navigate(ForumsApp.SAVED) },
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
        )
    }

    composable(ForumsApp.SAVED) {
        SavedScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenPost = { forumId, postId ->
                navController.navigate(ForumsApp.post(forumId, postId))
            },
        )
    }

    composable(
        route = ForumsApp.POST,
        arguments = listOf(
            navArgument("forumId") { type = NavType.StringType },
            navArgument("postId") { type = NavType.StringType }
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://{host}/forums/{forumId}/-/{postId}" }
        )
    ) {
        PostScreen(
            onBack = { navController.popBackStack() },
            onEditPost = { fId, pId -> navController.navigate(ForumsApp.newPost(fId, pId)) },
        )
    }

    composable(
        route = ForumsApp.NEW_POST,
        arguments = listOf(
            navArgument("forumId") { type = NavType.StringType },
            navArgument("postId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        )
    ) {
        NewPostScreen(
            onBack = { navController.popBackStack() },
            onPostCreated = { _, _ ->
                // Return to where the compose began — the forum for a new post, or
                // the post itself for an edit. A new post's list pulls it in via
                // ForumsRepository.postCreated; an edited post refreshes over its
                // live subscription.
                navController.popBackStack()
            },
        )
    }

    composable(ForumsApp.FIND_FORUMS) {
        FindForumsScreen(
            onBack = { navController.popBackStack() },
            // Drop discovery from the back stack and open the forum just joined,
            // so Back returns to the forum the user came from, not to the search.
            onForumSubscribed = { forumId ->
                navController.navigate(ForumsApp.forum(forumId)) {
                    popUpTo(ForumsApp.FIND_FORUMS) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = ForumsApp.FORUM_SETTINGS,
        arguments = listOf(navArgument("forumId") { type = NavType.StringType })
    ) {
        ForumSettingsScreen(
            onBack = { navController.popBackStack() },
            onForumDeleted = { navController.popBackStack(ForumsApp.ROUTER, inclusive = false) },
            // Leaving the forum leaves its screens too — the user is no longer a
            // subscriber, so the forum behind settings is no longer theirs to
            // return to.
            onUnsubscribed = { navController.popBackStack(ForumsApp.ROUTER, inclusive = false) },
        )
    }

    composable(
        route = ForumsApp.MODERATION,
        arguments = listOf(navArgument("forumId") { type = NavType.StringType })
    ) { backStackEntry ->
        val forumId = backStackEntry.arguments?.getString("forumId").orEmpty()
        ForumModerationScreen(
            onBack = { navController.popBackStack() },
            onOpenSettings = { navController.navigate(ForumsApp.moderationSettings(forumId)) },
        )
    }

    composable(
        route = ForumsApp.MODERATION_SETTINGS,
        arguments = listOf(navArgument("forumId") { type = NavType.StringType })
    ) {
        ForumModerationSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
