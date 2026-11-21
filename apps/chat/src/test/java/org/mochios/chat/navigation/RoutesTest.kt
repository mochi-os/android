// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The route builders against the module's real graph: each route must match
 * the screen it names and hand that screen back every value as it was given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutesTest {

    private val navigation = NavHostController(RuntimeEnvironment.getApplication()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = ChatApp.ROUTER) { chatNavGraph(this@apply, onOpenNotifications = {}) }
    }

    /** Opens [route] and answers the destination it matched and the arguments it filled. */
    private fun open(route: String): Pair<String?, Bundle> {
        navigation.navigate(route)
        val entry = navigation.currentBackStackEntry!!
        return entry.destination.route to (entry.arguments ?: Bundle())
    }

    @Test
    fun `every builder hands its screen an awkward id unchanged`() {
        open(ChatApp.chat(AWKWARD)).let { (route, arguments) ->
            assertEquals(ChatApp.CHAT, route)
            assertEquals(AWKWARD, arguments.getString("chatId"))
        }
        open(ChatApp.chatSettings(AWKWARD)).let { (route, arguments) ->
            assertEquals(ChatApp.CHAT_SETTINGS, route)
            assertEquals(AWKWARD, arguments.getString("chatId"))
        }
        open(ChatApp.newChat(AWKWARD, AWKWARD, AWKWARD)).let { (route, arguments) ->
            assertEquals(ChatApp.NEW_CHAT, route)
            assertEquals(AWKWARD, arguments.getString("account"))
            assertEquals(AWKWARD, arguments.getString("address"))
            assertEquals(AWKWARD, arguments.getString("name"))
        }
    }

    @Test
    fun `a friend's id cannot add an account and an address of its own`() {
        val friend = "x&account=A&address=B"
        val (route, arguments) = open(ChatApp.newChat(friend))
        assertEquals(ChatApp.NEW_CHAT, route)
        assertEquals(friend, arguments.getString("friend"))
        assertEquals("", arguments.getString("account"))
        assertEquals("", arguments.getString("address"))
        open(ChatApp.newChat(AWKWARD)).let { (_, awkward) ->
            assertEquals(AWKWARD, awkward.getString("friend"))
        }
    }

    @Test
    fun `no link from outside the app opens the new chat screen, which would start a chat unasked`() {
        assertTrue("the graph's links are searched", navigation.graph.hasDeepLink(Uri.parse("https://example.org/chat/c1")))
        assertFalse(navigation.graph.hasDeepLink(Uri.parse("mochi://chat/with?friend=f&account=a&address=b&name=n")))
    }

    private companion object {
        /** Every character that splits or ends a route, and a space. */
        const val AWKWARD = "a/b?c#d&e=f%g h+i"
    }
}
