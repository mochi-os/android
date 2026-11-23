// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.router

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.navigation.openWikis
import org.mochios.wikis.navigation.reopen
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The app starting at its router: the page last viewed reopens over the wiki
 * list, as the web reopens it; the list, once shown, is what opens next; and
 * a link as the app starts is what opens instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikisRouterTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var navigation: NavHostController

    @Before
    fun begin() {
        LastViewedStore.forget(context)
    }

    private fun start(link: (NavController.() -> Unit)? = null) {
        rule.setContent {
            val controller = rememberNavController()
            navigation = controller
            LaunchedEffect(Unit) { link?.invoke(controller) }
            NavHost(controller, startDestination = WikisApp.ROUTER) {
                composable(WikisApp.ROUTER) { entry ->
                    WikisRouter(onResolve = { page -> controller.reopen(entry, page) })
                }
                composable(WikisApp.HOME) {
                    RememberWikiList()
                    Text("list")
                }
                composable(
                    route = WikisApp.WIKI_HOME,
                    arguments = listOf(navArgument("wikiId") { type = NavType.StringType }),
                ) {
                    Text("resolver")
                }
                composable(
                    route = WikisApp.PAGE_VIEW,
                    arguments = listOf(
                        navArgument("wikiId") { type = NavType.StringType },
                        navArgument("page") { type = NavType.StringType },
                    ),
                ) { entry ->
                    RememberWikiPage(entry.arguments?.getString("wikiId").orEmpty(), entry.arguments?.getString("page").orEmpty())
                    Text("page")
                }
            }
        }
        rule.waitForIdle()
    }

    /** The back stack's screens, as "route" or "wiki/page". */
    private fun stack(): List<String> = navigation.currentBackStack.value
        .mapNotNull { entry ->
            val route = entry.destination.route ?: return@mapNotNull null
            val wiki = entry.arguments?.getString("wikiId")
            val page = entry.arguments?.getString("page")
            when {
                page != null -> "$wiki/$page"
                wiki != null -> "$wiki"
                else -> route
            }
        }

    @Test
    fun `with nothing kept the app opens on the wiki list`() {
        start()
        assertEquals(listOf(WikisApp.HOME), stack())
    }

    /** The page view wrote these at every visit, and nothing read them. */
    @Test
    fun `the page last viewed reopens over the wiki list`() {
        LastViewedStore.set(context, WIKIS_FEATURE, "w1")
        LastViewedStore.set(context, wikiPageFeature("w1"), "notes")
        start()
        assertEquals(listOf(WikisApp.HOME, "w1/notes"), stack())
        assertEquals("passing the list on the way keeps the page", "w1", LastViewedStore.get(context, WIKIS_FEATURE))
    }

    @Test
    fun `a wiki kept without a page reopens at its home`() {
        LastViewedStore.set(context, WIKIS_FEATURE, "w1")
        start()
        assertEquals(listOf(WikisApp.HOME, "w1"), stack())
    }

    @Test
    fun `going back to the wiki list makes it what the next launch opens`() {
        LastViewedStore.set(context, WIKIS_FEATURE, "w1")
        LastViewedStore.set(context, wikiPageFeature("w1"), "notes")
        start()
        rule.runOnIdle { navigation.popBackStack() }
        rule.waitForIdle()
        assertEquals(listOf(WikisApp.HOME), stack())
        assertNull(LastViewedStore.get(context, WIKIS_FEATURE))
        assertNull(lastWikiPage(context))
    }

    @Test
    fun `a link as the app starts opens the linked page over the list, in place of the router`() {
        LastViewedStore.set(context, WIKIS_FEATURE, "w1")
        LastViewedStore.set(context, wikiPageFeature("w1"), "notes")
        start {
            openWikis()
            navigate(WikisApp.pageView("w2", "plans"))
        }
        assertEquals(listOf(WikisApp.HOME, "w2/plans"), stack())
        assertEquals("w2", LastViewedStore.get(context, WIKIS_FEATURE))
    }

    @Test
    fun `a link while a page is open returns to the list beneath it`() {
        start()
        rule.runOnIdle { navigation.navigate(WikisApp.pageView("w1", "notes")) }
        rule.waitForIdle()
        rule.runOnIdle {
            navigation.openWikis()
            navigation.navigate(WikisApp.pageView("w2", "plans"))
        }
        rule.waitForIdle()
        assertEquals(listOf(WikisApp.HOME, "w2/plans"), stack())
    }
}
