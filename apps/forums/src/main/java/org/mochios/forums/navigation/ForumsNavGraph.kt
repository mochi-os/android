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
    const val NEW_POST = "forums/forum/{forumId}/new"
    const val FIND_FORUMS = "forums/discover"
    const val FORUM_SETTINGS = "forums/forum/{forumId}/settings"
    const val MODERATION = "forums/forum/{forumId}/moderation"
    const val MODERATION_SETTINGS = "forums/forum/{forumId}/moderation/settings"

    fun forum(forumId: String) = "forums/forum/$forumId"
    fun post(forumId: String, postId: String) = "forums/forum/$forumId/post/$postId"
    fun newPost(forumId: String) = "forums/forum/$forumId/new"
    fun forumSettings(forumId: String) = "forums/forum/$forumId/settings"
    fun moderation(forumId: String) = "forums/forum/$forumId/moderation"
    fun moderationSettings(forumId: String) = "forums/forum/$forumId/moderation/settings"
}

fun NavGraphBuilder.forumsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
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
            onLogout = onLogout,
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
        )
    }

    composable(
        route = ForumsApp.NEW_POST,
        arguments = listOf(navArgument("forumId") { type = NavType.StringType })
    ) {
        NewPostScreen(
            onBack = { navController.popBackStack() },
            onPostCreated = { fId, pId ->
                navController.popBackStack()
                navController.navigate(ForumsApp.post(fId, pId))
            },
        )
    }

    composable(ForumsApp.FIND_FORUMS) {
        FindForumsScreen(
            onBack = { navController.popBackStack() },
            onForumSubscribed = { navController.popBackStack() },
        )
    }

    composable(
        route = ForumsApp.FORUM_SETTINGS,
        arguments = listOf(navArgument("forumId") { type = NavType.StringType })
    ) { backStackEntry ->
        val forumId = backStackEntry.arguments?.getString("forumId").orEmpty()
        ForumSettingsScreen(
            onBack = { navController.popBackStack() },
            onForumDeleted = { navController.popBackStack(ForumsApp.ROUTER, inclusive = false) },
            onModeration = { navController.navigate(ForumsApp.moderation(forumId)) },
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
