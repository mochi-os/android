package org.mochios.feeds.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.feeds.ui.feed.FeedScreen
import org.mochios.feeds.ui.find.FindFeedsScreen
import org.mochios.feeds.ui.post.CreatePostScreen
import org.mochios.feeds.ui.post.PostDetailScreen
import org.mochios.feeds.ui.post.PostSourceScreen
import org.mochios.feeds.ui.router.FeedsRouter
import org.mochios.feeds.ui.settings.FeedSettingsScreen
import org.mochios.feeds.ui.settings.SourcesScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object FeedsApp {
    /**
     * HOME points at the router (resolves last-viewed feed or "__all__").
     * The router immediately navigates to FEED with the resolved id and
     * pops itself off the back stack so the user never sees the spinner
     * for more than a frame.
     */
    const val HOME = "feeds/router"
    const val ROUTER = "feeds/router"
    const val FEED = "feeds/feed/{feedId}"
    const val POST = "feeds/post/{feedId}/{postId}"
    const val POST_SOURCE = "feeds/postSource/{feedId}/{postId}?url={url}&expand={expand}"
    const val CREATE_POST = "feeds/createPost?feedId={feedId}&postId={postId}"
    const val FIND_FEEDS = "feeds/findFeeds"
    const val FEED_SETTINGS = "feeds/feedSettings/{feedId}"
    const val FEED_SOURCES = "feeds/feedSources/{feedId}?source={source}"

    fun feed(feedId: String) = "feeds/feed/$feedId"
    fun post(feedId: String, postId: String) = "feeds/post/$feedId/$postId"
    fun postSource(feedId: String, postId: String, url: String, expand: Boolean): String {
        val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
        return "feeds/postSource/$feedId/$postId?url=$encoded&expand=$expand"
    }
    fun createPost(feedId: String? = null, postId: String? = null): String {
        val params = listOfNotNull(
            feedId?.let { "feedId=$it" },
            postId?.let { "postId=$it" }
        )
        return if (params.isEmpty()) "feeds/createPost" else "feeds/createPost?${params.joinToString("&")}"
    }
    fun feedSettings(feedId: String) = "feeds/feedSettings/$feedId"
    fun feedSources(feedId: String, source: String? = null): String {
        val base = "feeds/feedSources/$feedId"
        return source?.takeIf { it.isNotEmpty() }?.let {
            val encoded = URLEncoder.encode(it, StandardCharsets.UTF_8.name())
            "$base?source=$encoded"
        } ?: base
    }
}

fun NavGraphBuilder.feedsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
) {
    composable(FeedsApp.ROUTER) {
        FeedsRouter(onResolve = { feedId ->
            navController.navigate(FeedsApp.feed(feedId)) {
                popUpTo(FeedsApp.ROUTER) { inclusive = true }
            }
        })
    }

    composable(
        route = FeedsApp.FEED,
        arguments = listOf(navArgument("feedId") { type = NavType.StringType })
    ) {
        FeedScreen(
            onNavigateToPost = { feedId, postId, sourceUrl, expandComments ->
                if (sourceUrl != null) {
                    navController.navigate(FeedsApp.postSource(feedId, postId, sourceUrl, expandComments))
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
            onNavigateToSources = { feedId, sourceUrl ->
                navController.navigate(FeedsApp.feedSources(feedId, sourceUrl))
            },
            onSelectFeed = { feedId ->
                // Swap the current feed in-place rather than stacking — back
                // from a feed goes to the host (not a chain of every feed
                // the user clicked in the drawer).
                navController.navigate(FeedsApp.feed(feedId)) {
                    popUpTo(FeedsApp.FEED) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onNavigateToFindFeeds = { navController.navigate(FeedsApp.FIND_FEEDS) },
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
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
            },
            navArgument("expand") {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) { backStackEntry ->
        val encodedUrl = backStackEntry.arguments?.getString("url").orEmpty()
        val sourceUrl = runCatching {
            java.net.URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name())
        }.getOrDefault(encodedUrl)
        PostSourceScreen(
            sourceUrl = sourceUrl,
            initiallyExpanded = backStackEntry.arguments?.getBoolean("expand") == true,
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
                // Feed deletion drops the user back to the router so the
                // last-viewed lookup re-runs (the deleted feed shouldn't
                // come back as the destination).
                navController.popBackStack(FeedsApp.ROUTER, inclusive = false)
            },
        )
    }

    composable(
        route = FeedsApp.FEED_SOURCES,
        arguments = listOf(
            navArgument("feedId") { type = NavType.StringType },
            navArgument("source") {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        val encodedSource = backStackEntry.arguments?.getString("source").orEmpty()
        val highlightSource = runCatching {
            java.net.URLDecoder.decode(encodedSource, StandardCharsets.UTF_8.name())
        }.getOrDefault(encodedSource)
        SourcesScreen(
            highlightSource = highlightSource.takeIf { it.isNotEmpty() },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
