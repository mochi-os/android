package org.mochios.feeds.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.feeds.ui.feed.FeedScreen
import org.mochios.feeds.ui.feedlist.FeedListScreen
import org.mochios.feeds.ui.find.FindFeedsScreen
import org.mochios.feeds.ui.post.CreatePostScreen
import org.mochios.feeds.ui.post.PostDetailScreen
import org.mochios.feeds.ui.post.PostSourceScreen
import org.mochios.feeds.ui.settings.FeedSettingsScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object FeedsApp {
    const val HOME = "feeds/list"
    const val FEED_LIST = "feeds/list"
    const val FEED = "feeds/feed/{feedId}"
    const val POST = "feeds/post/{feedId}/{postId}"
    const val POST_SOURCE = "feeds/postSource/{feedId}/{postId}?url={url}"
    const val CREATE_POST = "feeds/createPost?feedId={feedId}&postId={postId}"
    const val FIND_FEEDS = "feeds/findFeeds"
    const val FEED_SETTINGS = "feeds/feedSettings/{feedId}"

    fun feed(feedId: String) = "feeds/feed/$feedId"
    fun post(feedId: String, postId: String) = "feeds/post/$feedId/$postId"
    fun postSource(feedId: String, postId: String, url: String): String {
        val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
        return "feeds/postSource/$feedId/$postId?url=$encoded"
    }
    fun createPost(feedId: String? = null, postId: String? = null): String {
        val params = listOfNotNull(
            feedId?.let { "feedId=$it" },
            postId?.let { "postId=$it" }
        )
        return if (params.isEmpty()) "feeds/createPost" else "feeds/createPost?${params.joinToString("&")}"
    }
    fun feedSettings(feedId: String) = "feeds/feedSettings/$feedId"
}

fun NavGraphBuilder.feedsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
) {
    composable(FeedsApp.FEED_LIST) {
        FeedListScreen(
            onNavigateToFeed = { feedId -> navController.navigate(FeedsApp.feed(feedId)) },
            onNavigateToCreatePost = { navController.navigate(FeedsApp.createPost()) },
            onNavigateToFindFeeds = { navController.navigate(FeedsApp.FIND_FEEDS) },
            onLogout = onLogout,
        )
    }

    composable(
        route = FeedsApp.FEED,
        arguments = listOf(navArgument("feedId") { type = NavType.StringType })
    ) {
        FeedScreen(
            onNavigateToPost = { feedId, postId, sourceUrl ->
                if (sourceUrl != null) {
                    navController.navigate(FeedsApp.postSource(feedId, postId, sourceUrl))
                } else {
                    navController.navigate(FeedsApp.post(feedId, postId))
                }
            },
            onNavigateToCreatePost = { feedId ->
                navController.navigate(FeedsApp.createPost(feedId))
            },
            onNavigateToEditPost = { feedId, postId ->
                navController.navigate(FeedsApp.createPost(feedId = feedId, postId = postId))
            },
            onNavigateToSettings = { feedId ->
                navController.navigate(FeedsApp.feedSettings(feedId))
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(
        route = FeedsApp.POST,
        arguments = listOf(
            navArgument("feedId") { type = NavType.StringType },
            navArgument("postId") { type = NavType.StringType }
        )
    ) {
        PostDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditPost = { feedId, postId ->
                navController.navigate(FeedsApp.createPost(feedId = feedId, postId = postId))
            },
        )
    }

    composable(
        route = FeedsApp.POST_SOURCE,
        arguments = listOf(
            navArgument("feedId") { type = NavType.StringType },
            navArgument("postId") { type = NavType.StringType },
            navArgument("url") {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        val encodedUrl = backStackEntry.arguments?.getString("url").orEmpty()
        val sourceUrl = runCatching {
            java.net.URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name())
        }.getOrDefault(encodedUrl)
        PostSourceScreen(
            sourceUrl = sourceUrl,
            onNavigateBack = { navController.popBackStack() },
            onEditPost = { feedId, postId ->
                navController.navigate(FeedsApp.createPost(feedId = feedId, postId = postId))
            },
        )
    }

    composable(
        route = FeedsApp.CREATE_POST,
        arguments = listOf(
            navArgument("feedId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("postId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        CreatePostScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(FeedsApp.FIND_FEEDS) {
        FindFeedsScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToFeed = { feedId ->
                navController.navigate(FeedsApp.feed(feedId)) {
                    popUpTo(FeedsApp.FIND_FEEDS) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = FeedsApp.FEED_SETTINGS,
        arguments = listOf(navArgument("feedId") { type = NavType.StringType })
    ) {
        FeedSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onFeedDeleted = {
                navController.popBackStack(FeedsApp.FEED_LIST, inclusive = false)
            },
        )
    }
}
