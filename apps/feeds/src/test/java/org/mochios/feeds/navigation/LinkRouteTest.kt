// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.navigation

import android.os.Bundle
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkRouteTest {

    @Test
    fun `a post link opens the post`() {
        assertEquals("feeds/post/abc/p1", FeedsApp.linkRoute("abc", "p1", null))
    }

    @Test
    fun `a comment link opens the post on the comment`() {
        assertEquals("feeds/post/abc/p1?comment=c1", FeedsApp.linkRoute("abc", "p1", null, "c1"))
        assertEquals("feeds/post/abc/p1", FeedsApp.linkRoute("abc", "p1", null, ""))
        // The comment comes from a link, so it cannot add a query of its own.
        assertEquals("feeds/post/abc/p1?comment=c1%26x%3D1", FeedsApp.linkRoute("abc", "p1", null, "c1&x=1"))
    }

    @Test
    fun `a comment link without a post opens the feed`() {
        assertEquals("feeds/feed/abc", FeedsApp.linkRoute("abc", null, null, "c1"))
    }

    @Test
    fun `a feed link opens the feed`() {
        assertEquals("feeds/feed/abc", FeedsApp.linkRoute("abc", null, null))
        assertEquals("feeds/feed/abc", FeedsApp.linkRoute("abc", "", null))
    }

    @Test
    fun `a discovery link carries its share link`() {
        assertEquals(
            "feeds/findFeeds?link=mochi%3A%2F%2F12D3KooWPeer%2Fabc",
            FeedsApp.linkRoute("find", null, "mochi://12D3KooWPeer/abc"),
        )
        assertEquals("feeds/findFeeds", FeedsApp.linkRoute("find", null, null))
    }

    @Test
    fun `the bare app link names no screen`() {
        assertNull(FeedsApp.linkRoute(null, null, null))
        assertNull(FeedsApp.linkRoute("", null, null))
    }

    // The builders against the module's real graph: each route must match the
    // screen it names and hand that screen back every value as it was given.

    private val navigation = NavHostController(RuntimeEnvironment.getApplication()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = FeedsApp.ROUTER) { feedsNavGraph(this@apply) }
    }

    /** Opens [route] and answers the destination it matched and the arguments it filled. */
    private fun open(route: String): Pair<String?, Bundle> {
        navigation.navigate(route)
        val entry = navigation.currentBackStackEntry!!
        return entry.destination.route to (entry.arguments ?: Bundle())
    }

    @Test
    fun `every builder hands its screen an awkward id unchanged`() {
        open(FeedsApp.feed(AWKWARD)).let { (route, arguments) ->
            assertEquals(FeedsApp.FEED, route)
            assertEquals(AWKWARD, arguments.getString("feedId"))
        }
        open(FeedsApp.post(AWKWARD, AWKWARD, AWKWARD)).let { (route, arguments) ->
            assertEquals(FeedsApp.POST, route)
            assertEquals(AWKWARD, arguments.getString("feedId"))
            assertEquals(AWKWARD, arguments.getString("postId"))
            assertEquals(AWKWARD, arguments.getString("comment"))
        }
        open(FeedsApp.createPost(AWKWARD, AWKWARD)).let { (route, arguments) ->
            assertEquals(FeedsApp.CREATE_POST, route)
            assertEquals(AWKWARD, arguments.getString("feedId"))
            assertEquals(AWKWARD, arguments.getString("postId"))
        }
        open(FeedsApp.feedSettings(AWKWARD)).let { (route, arguments) ->
            assertEquals(FeedsApp.FEED_SETTINGS, route)
            assertEquals(AWKWARD, arguments.getString("feedId"))
        }
        open(FeedsApp.findFeeds(AWKWARD)).let { (route, arguments) ->
            assertEquals(FeedsApp.FIND_FEEDS, route)
            assertEquals(AWKWARD, arguments.getString("link"))
        }
    }

    @Test
    fun `an article address reaches the source screen with its own escapes intact`() {
        val (route, arguments) = open(FeedsApp.postSource(AWKWARD, AWKWARD, ARTICLE, expand = true))
        assertEquals(FeedsApp.POST_SOURCE, route)
        assertEquals(AWKWARD, arguments.getString("feedId"))
        assertEquals(AWKWARD, arguments.getString("postId"))
        assertTrue(arguments.getBoolean("expand"))
        assertEquals(ARTICLE, sourceArgument(arguments))
    }

    @Test
    fun `a source address reaches the sources screen with its own escapes intact`() {
        val (route, arguments) = open(FeedsApp.feedSources(AWKWARD, ARTICLE))
        assertEquals(FeedsApp.FEED_SOURCES, route)
        assertEquals(AWKWARD, arguments.getString("feedId"))
        assertEquals(ARTICLE, highlightArgument(arguments))
    }

    private companion object {
        /** Every character that splits or ends a route, and a space. */
        const val AWKWARD = "a/b?c#d&e=f%g h+i"

        /** An address whose own query holds escapes and a plus. */
        const val ARTICLE = "https://example.org/a%2Fb?q=x%26y%3Dz&r=1+2&s=%25"
    }
}
