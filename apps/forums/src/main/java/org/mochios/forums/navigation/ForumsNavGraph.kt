package org.mochios.forums.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.forums.ui.find.FindForumsScreen
import org.mochios.forums.ui.forum.ForumScreen
import org.mochios.forums.ui.forumlist.ForumListScreen
import org.mochios.forums.ui.newpost.NewPostScreen
import org.mochios.forums.ui.post.PostScreen
import org.mochios.forums.ui.settings.ForumSettingsScreen

object ForumsApp {
    const val HOME = "forums/list"
    const val FORUM_LIST = "forums/list"
    const val FORUM = "forums/{forumId}"
    const val POST = "forums/{forumId}/post/{postId}"
    const val NEW_POST = "forums/{forumId}/new"
    const val FIND_FORUMS = "forums/discover"
    const val FORUM_SETTINGS = "forums/{forumId}/settings"

    fun forum(forumId: String) = "forums/$forumId"
    fun post(forumId: String, postId: String) = "forums/$forumId/post/$postId"
    fun newPost(forumId: String) = "forums/$forumId/new"
    fun forumSettings(forumId: String) = "forums/$forumId/settings"
}

fun NavGraphBuilder.forumsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
) {
    composable(ForumsApp.FORUM_LIST) {
        ForumListScreen(
            onForumClick = { id -> navController.navigate(ForumsApp.forum(id)) },
            onFindForums = { navController.navigate(ForumsApp.FIND_FORUMS) },
            onLogout = onLogout,
        )
    }

    composable(
        route = ForumsApp.FORUM,
        arguments = listOf(navArgument("forumId") { type = NavType.StringType }),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://{host}/forums/{forumId}" }
        )
    ) {
        ForumScreen(
            onBack = { navController.popBackStack() },
            onPostClick = { fId, pId -> navController.navigate(ForumsApp.post(fId, pId)) },
            onNewPost = { fId -> navController.navigate(ForumsApp.newPost(fId)) },
            onSettings = { fId -> navController.navigate(ForumsApp.forumSettings(fId)) },
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
    ) {
        ForumSettingsScreen(
            onBack = { navController.popBackStack() },
            onForumDeleted = { navController.popBackStack(ForumsApp.FORUM_LIST, inclusive = false) },
        )
    }
}
