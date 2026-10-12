// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An app that starts at a router, given a deep link as it starts, as the shell
 * activity hands one over: the link is consumed by an effect beside the
 * NavHost on the frame the router first composes, and the router resolves the
 * last-viewed item in an effect of its own on that same frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RouterNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var navigation: NavHostController

    private fun start(link: (NavController.() -> Unit)? = null) {
        rule.setContent {
            val controller = rememberNavController()
            navigation = controller
            LaunchedEffect(Unit) { link?.invoke(controller) }
            NavHost(navController = controller, startDestination = ROUTER) {
                composable(ROUTER) { entry ->
                    LaunchedEffect(Unit) { controller.leaveRouter(entry, item(LAST)) }
                    Text("router")
                }
                // The id is declared, as every app's is: the back stack matches a
                // filled route's arguments only against those it declares.
                composable(ITEM, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    Text("item ${entry.arguments?.getString("id")}")
                }
                composable(OTHER) { Text("other") }
            }
        }
        rule.waitForIdle()
    }

    private fun act(step: NavController.() -> Unit) {
        rule.runOnIdle { navigation.step() }
        rule.waitForIdle()
    }

    private fun showing(): String? = navigation.currentBackStackEntry?.let { entry ->
        entry.destination.route?.replace("{id}", entry.arguments?.getString("id").orEmpty())
    }

    @Test
    fun `with no link the router opens the last-viewed item in its place`() {
        start()
        assertEquals(item(LAST), showing())
        assertNull(navigation.previousBackStackEntry)
    }

    @Test
    fun `a link as the app starts opens the linked item, not the last-viewed one`() {
        start { openInPlace(ROUTER, ITEM, item(LINKED)) }
        assertEquals(item(LINKED), showing())
        rule.onNodeWithText("item $LINKED").assertExists()
        rule.onNodeWithText("item $LAST").assertDoesNotExist()
    }

    @Test
    fun `the linked item replaces the router, so Back cannot reopen it through the router`() {
        start { openInPlace(ROUTER, ITEM, item(LINKED)) }
        assertNull(navigation.previousBackStackEntry)
    }

    @Test
    fun `a link while an item is open replaces it and what it opened, rather than stacking a second copy`() {
        start()
        act { navigate(OTHER) }
        act { openInPlace(ROUTER, ITEM, item(LINKED)) }
        assertEquals(item(LINKED), showing())
        assertNull(navigation.previousBackStackEntry)
    }

    @Test
    fun `a link to the item already open leaves it as it is`() {
        start()
        val open = navigation.currentBackStackEntry?.id
        act { openInPlace(ROUTER, ITEM, item(LAST)) }
        assertEquals(open, navigation.currentBackStackEntry?.id)
    }

    @Test
    fun `a router covered as the app starts stays beneath, and resolves once Back uncovers it`() {
        start { navigate(OTHER) }
        assertEquals(OTHER, showing())
        assertEquals(ROUTER, navigation.previousBackStackEntry?.destination?.route)
        act { popBackStack() }
        assertEquals(item(LAST), showing())
        assertNull(navigation.previousBackStackEntry)
    }

    private companion object {
        const val ROUTER = "app/router"
        const val ITEM = "app/item/{id}"
        const val OTHER = "app/other"
        const val LAST = "last"
        const val LINKED = "linked"

        fun item(id: String) = "app/item/$id"
    }
}
