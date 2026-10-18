// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.page.openCanonical
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A page opened at a name a redirect sends elsewhere moves to the page it
 * shows, so tagging and editing reach that page and Back does not return to
 * the old name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CanonicalPageTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var navigation: NavHostController

    @Before
    fun setUp() {
        rule.setContent {
            navigation = rememberNavController()
            NavHost(navigation, startDestination = WikisApp.HOME) {
                composable(WikisApp.HOME) {}
                composable(
                    route = WikisApp.PAGE_VIEW,
                    arguments = listOf(
                        navArgument("wikiId") { type = NavType.StringType },
                        navArgument("page") { type = NavType.StringType },
                    ),
                ) {}
            }
        }
        rule.runOnIdle { navigation.navigate(WikisApp.pageView("w1", "home1")) }
    }

    private fun page(): String? = navigation.currentBackStackEntry?.arguments?.getString("page")

    @Test
    fun `a page reached through a redirect moves to its own name, in place of the old one`() {
        var opened: String? = null
        rule.runOnIdle {
            opened = navigation.currentBackStackEntry?.id
            navigation.openCanonical("w1", "home1", "sub-page")
        }
        rule.runOnIdle {
            assertEquals("sub-page", page())
            // A fresh entry, so the screen gets a view model of its own: the
            // old one would keep acting on the old name.
            assertNotEquals(opened, navigation.currentBackStackEntry?.id)
            assertEquals("w1", navigation.currentBackStackEntry?.arguments?.getString("wikiId"))
            navigation.popBackStack()
        }
        rule.runOnIdle { assertEquals(WikisApp.HOME, navigation.currentDestination?.route) }
    }

    @Test
    fun `a page at its own name stays where it is`() {
        rule.runOnIdle { navigation.openCanonical("w1", "home1", "home1") }
        rule.runOnIdle { navigation.openCanonical("w1", "home1", null) }
        rule.runOnIdle { navigation.openCanonical("w1", "home1", "") }
        rule.runOnIdle {
            assertEquals("home1", page())
            assertEquals(2, navigation.currentBackStack.value.count { entry -> entry.destination.route != null })
        }
    }
}
