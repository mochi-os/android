// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.navigation

import android.os.Bundle
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("forums/forum/abc/post/p1", ForumsApp.linkRoute("abc", "p1", null))
    }

    @Test
    fun `a comment link opens the post on the comment`() {
        assertEquals("forums/forum/abc/post/p1?comment=c1", ForumsApp.linkRoute("abc", "p1", null, "c1"))
        assertEquals("forums/forum/abc/post/p1", ForumsApp.linkRoute("abc", "p1", null, ""))
        // The comment comes from a link, so it cannot add a query of its own.
        assertEquals(
            "forums/forum/abc/post/p1?comment=c1%26x%3D1",
            ForumsApp.linkRoute("abc", "p1", null, "c1&x=1"),
        )
    }

    @Test
    fun `a forum link opens the forum`() {
        assertEquals("forums/forum/abc", ForumsApp.linkRoute("abc", null, null))
        assertEquals("forums/forum/abc", ForumsApp.linkRoute("abc", "", ""))
    }

    @Test
    fun `a forum reached through its owner's server opens discovery with the share link`() {
        assertEquals(
            "forums/discover?link=mochi%3A%2F%2F12D3KooWPeer%2Fabc",
            ForumsApp.linkRoute("abc", null, "12D3KooWPeer"),
        )
    }

    @Test
    fun `the bare app link names no screen`() {
        assertNull(ForumsApp.linkRoute(null, null, null))
        assertNull(ForumsApp.linkRoute("", null, "12D3KooWPeer"))
    }

    // The builders against the module's real graph: each route must match the
    // screen it names and hand that screen back every value as it was given.

    private val navigation = NavHostController(RuntimeEnvironment.getApplication()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = ForumsApp.ROUTER) { forumsNavGraph(this@apply, onOpenNotifications = {}) }
    }

    /** Opens [route] and answers the destination it matched and the arguments it filled. */
    private fun open(route: String): Pair<String?, Bundle> {
        navigation.navigate(route)
        val entry = navigation.currentBackStackEntry!!
        return entry.destination.route to (entry.arguments ?: Bundle())
    }

    @Test
    fun `every builder hands its screen an awkward id unchanged`() {
        open(ForumsApp.forum(AWKWARD)).let { (route, arguments) ->
            assertEquals(ForumsApp.FORUM, route)
            assertEquals(AWKWARD, arguments.getString("forumId"))
        }
        open(ForumsApp.post(AWKWARD, AWKWARD, AWKWARD)).let { (route, arguments) ->
            assertEquals(ForumsApp.POST, route)
            assertEquals(AWKWARD, arguments.getString("forumId"))
            assertEquals(AWKWARD, arguments.getString("postId"))
            assertEquals(AWKWARD, arguments.getString("comment"))
        }
        open(ForumsApp.newPost(AWKWARD, AWKWARD)).let { (route, arguments) ->
            assertEquals(ForumsApp.NEW_POST, route)
            assertEquals(AWKWARD, arguments.getString("forumId"))
            assertEquals(AWKWARD, arguments.getString("postId"))
        }
        open(ForumsApp.findForums(AWKWARD)).let { (route, arguments) ->
            assertEquals(ForumsApp.FIND_FORUMS, route)
            assertEquals(AWKWARD, arguments.getString("link"))
        }
        open(ForumsApp.forumSettings(AWKWARD)).let { (route, arguments) ->
            assertEquals(ForumsApp.FORUM_SETTINGS, route)
            assertEquals(AWKWARD, arguments.getString("forumId"))
        }
        open(ForumsApp.moderation(AWKWARD)).let { (route, arguments) ->
            assertEquals(ForumsApp.MODERATION, route)
            assertEquals(AWKWARD, arguments.getString("forumId"))
        }
    }

    @Test
    fun `a crafted id names no other forum's screen`() {
        // Shaped to reach another forum's settings, or the editor on another post.
        open(ForumsApp.forum("x/settings?")).let { (route, arguments) ->
            assertEquals(ForumsApp.FORUM, route)
            assertEquals("x/settings?", arguments.getString("forumId"))
        }
        open(ForumsApp.post("f", "x/new?postId=P&")).let { (route, arguments) ->
            assertEquals(ForumsApp.POST, route)
            assertEquals("x/new?postId=P&", arguments.getString("postId"))
        }
    }

    @Test
    fun `a community's address names its forum`() {
        val (route, arguments) = open(ForumsApp.forum("https://lemmy.ml/c/test"))
        assertEquals(ForumsApp.FORUM, route)
        assertEquals("https://lemmy.ml/c/test", arguments.getString("forumId"))
    }

    private companion object {
        /** Every character that splits or ends a route, and a space. */
        const val AWKWARD = "a/b?c#d&e=f%g h+i"
    }
}
