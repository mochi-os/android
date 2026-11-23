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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.page.openRenamed
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A renamed page opens under its new name in place of the old one, so Back
 * does not return to a screen that still acts on the name that is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RenamedPageTest {

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
        // Entering a wiki lands on its home page straight over the list: the
        // wiki-home resolver pops itself, so it is never on the stack.
        rule.runOnIdle { navigation.navigate(WikisApp.pageView("w1", "home")) }
        rule.runOnIdle { navigation.navigate(WikisApp.pageView("w1", "draft")) }
    }

    private fun pages(): List<String?> = navigation.currentBackStack.value
        .filter { entry -> entry.destination.route == WikisApp.PAGE_VIEW }
        .map { entry -> entry.arguments?.getString("page") }

    @Test
    fun `the renamed page replaces the old one, and Back returns to the page before it`() {
        rule.runOnIdle { navigation.openRenamed("w1", "draft", "final") }
        rule.runOnIdle {
            assertEquals("THE POINT: no entry is left at the old name", listOf("home", "final"), pages())
            navigation.popBackStack()
        }
        rule.runOnIdle { assertEquals("home", navigation.currentBackStackEntry?.arguments?.getString("page")) }
    }
}
