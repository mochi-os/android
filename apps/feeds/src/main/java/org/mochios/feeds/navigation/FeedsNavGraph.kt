// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.android.util.leaveRouter
import org.mochios.android.util.navigateFromDrawer
import org.mochios.feeds.ui.feed.FeedScreen
import org.mochios.feeds.ui.feedlist.CreateFeedScreen
import org.mochios.feeds.ui.find.FindFeedsScreen
import org.mochios.feeds.ui.post.CreatePostScreen
import org.mochios.feeds.ui.post.PostDetailScreen
import org.mochios.feeds.ui.post.PostSourceScreen
import org.mochios.feeds.ui.router.FeedsRouter
import org.mochios.feeds.ui.saved.SavedScreen
import org.mochios.feeds.ui.settings.FeedSettingsScreen
import org.mochios.feeds.ui.settings.SourcesScreen

object FeedsApp {
    /**
     * The router, by two names for one route: HOME, where the app opens the
     * feeds, and ROUTER, where this graph declares it. It resolves the
     * last-viewed feed (or "__all__"), navigates to FEED and pops itself off
     * the back stack.
     */
    const val HOME = "feeds/router"
    const val ROUTER = "feeds/router"
    const val FEED = "feeds/feed/{feedId}"
    // [comment] opens the post on one comment and its replies, as a shared
    // link to that comment does.
    const val POST = "feeds/post/{feedId}/{postId}?comment={comment}"
    const val POST_SOURCE = "feeds/postSource/{feedId}/{postId}?url={url}&expand={expand}"
    const val CREATE_POST = "feeds/createPost?feedId={feedId}&postId={postId}"
    // [link] is an optional mochi:// share link to resolve on arrival, which is
    // how a notification about a feed the user does not hold reaches it.
    const val FIND_FEEDS = "feeds/findFeeds?link={link}"
    const val CREATE_FEED = "feeds/createFeed"
    const val FEED_SETTINGS = "feeds/feedSettings/{feedId}"
    const val FEED_SOURCES = "feeds/feedSources/{feedId}?source={source}"
    const val SAVED = "feeds/saved"

    // Every value a builder puts in a route is escaped with Uri.encode, which
    // Navigation undoes once as it fills the destination's arguments: an id
    // holding `/`, `?`, `#`, `&` or `%` then still names its own screen.
    fun feed(feedId: String) = "feeds/feed/${Uri.encode(feedId)}"
    fun findFeeds(link: String? = null) =
        if (link.isNullOrEmpty()) "feeds/findFeeds"
        else "feeds/findFeeds?link=${Uri.encode(link)}"

    /**
     * The screen a `/feeds/...` notification link opens: discovery for
     * `/feeds/find?link=<share link>` (a feed the user does not hold yet), the
     * post for `/feeds/<feed>/<post>`, opened on one comment when the link
     * names it with `?comment=`, else the feed. Null for the bare app.
     */
    fun linkRoute(feedId: String?, postId: String?, link: String?, comment: String? = null): String? = when {
        feedId == "find" -> findFeeds(link)
        feedId.isNullOrEmpty() -> null
        !postId.isNullOrEmpty() -> post(feedId, postId, comment)
        else -> feed(feedId)
    }
    fun post(feedId: String, postId: String, comment: String? = null): String {
        val base = "feeds/post/${Uri.encode(feedId)}/${Uri.encode(postId)}"
        return if (comment.isNullOrEmpty()) base else "$base?comment=${Uri.encode(comment)}"
    }
    fun postSource(feedId: String, postId: String, url: String, expand: Boolean) =
        "feeds/postSource/${Uri.encode(feedId)}/${Uri.encode(postId)}?url=${Uri.encode(url)}&expand=$expand"
    fun createPost(feedId: String? = null, postId: String? = null): String {
        val params = listOfNotNull(
            feedId?.let { "feedId=${Uri.encode(it)}" },
            postId?.let { "postId=${Uri.encode(it)}" }
        )
        return if (params.isEmpty()) "feeds/createPost" else "feeds/createPost?${params.joinToString("&")}"
    }
    fun feedSettings(feedId: String) = "feeds/feedSettings/${Uri.encode(feedId)}"
    fun feedSources(feedId: String, source: String? = null): String {
        val base = "feeds/feedSources/${Uri.encode(feedId)}"
        return source?.takeIf { it.isNotEmpty() }?.let { "$base?source=${Uri.encode(it)}" } ?: base
    }
}

/**
 * The address a post-source screen opens, as its route carried it: Navigation
 * has already undone the builder's escaping, so decoding again would turn an
 * escape inside the address itself (`%2F`, `%26`, `+`) into the character.
 */
internal fun sourceArgument(arguments: Bundle?): String = arguments?.getString("url").orEmpty()

/** The source the sources screen scrolls to, as its route carried it; see [sourceArgument]. */
internal fun highlightArgument(arguments: Bundle?): String = arguments?.getString("source").orEmpty()

fun NavGraphBuilder.feedsNavGraph(
    navController: NavController,
    onOpenNotifications: () -> Unit,
    onOpenLink: (String) -> Unit = {},
) {
    composable(FeedsApp.ROUTER) { entry ->
        FeedsRouter(onResolve = { feedId ->
            navController.leaveRouter(entry, FeedsApp.feed(feedId))
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
            onNavigateToSaved = { navController.navigate(FeedsApp.SAVED) },
            onSelectFeed = { feedId ->
                // Swap the current feed in-place rather than stacking — back
                // from a feed goes to the host (not a chain of every feed
                // the user clicked in the drawer).
                navController.navigateFromDrawer(FeedsApp.feed(feedId))
            },
            onNavigateToFindFeeds = { navController.navigate(FeedsApp.findFeeds()) },
            onNavigateToCreateFeed = { navController.navigate(FeedsApp.CREATE_FEED) },
            onOpenNotifications = onOpenNotifications,
        )
    }

    composable(FeedsApp.CREATE_FEED) {
        CreateFeedScreen(
            onBack = { navController.popBackStack() },
            // Drop the create screen and open the new feed, so it lands on its
            // empty "create the first post" state and Back returns to the feed
            // the user came from.
            onCreated = { feedId ->
                navController.navigate(FeedsApp.feed(feedId)) {
                    popUpTo(FeedsApp.CREATE_FEED) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = FeedsApp.POST,
        arguments = listOf(
            navArgument("feedId") { type = NavType.StringType },
            navArgument("postId") { type = NavType.StringType },
            navArgument("comment") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        )
    ) {
        PostDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditPost = { feedId, postId ->
                navController.navigate(FeedsApp.createPost(feedId = feedId, postId = postId))
            },
            onNavigateToSources = { feedId, sourceUrl ->
                navController.navigate(FeedsApp.feedSources(feedId, sourceUrl))
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
        PostSourceScreen(
            sourceUrl = sourceArgument(backStackEntry.arguments),
            initiallyExpanded = backStackEntry.arguments?.getBoolean("expand") == true,
            onNavigateBack = { navController.popBackStack() },
            onEditPost = { feedId, postId ->
                navController.navigate(FeedsApp.createPost(feedId = feedId, postId = postId))
            },
            onNavigateToSources = { feedId, ingestionUrl ->
                navController.navigate(FeedsApp.feedSources(feedId, ingestionUrl))
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

    composable(FeedsApp.SAVED) {
        SavedScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenPost = { feedId, postId ->
                navController.navigate(FeedsApp.post(feedId, postId))
            },
        )
    }

    composable(
        route = FeedsApp.FIND_FEEDS,
        arguments = listOf(navArgument("link") {
            type = NavType.StringType
            defaultValue = ""
        }),
    ) {
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
                // Back to the router so the last-viewed lookup re-runs.
                // Navigate, not pop: the router removes itself from the stack
                // once resolved.
                navController.navigate(FeedsApp.ROUTER) {
                    popUpTo(FeedsApp.FEED) { inclusive = true }
                }
            },
            onUnsubscribed = {
                // Same as delete: the user no longer follows this feed, so
                // bounce to the router rather than back to the now-empty feed.
                navController.navigate(FeedsApp.ROUTER) {
                    popUpTo(FeedsApp.FEED) { inclusive = true }
                }
            },
            onOpenLink = onOpenLink,
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
        val highlightSource = highlightArgument(backStackEntry.arguments)
        SourcesScreen(
            highlightSource = highlightSource.takeIf { it.isNotEmpty() },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
