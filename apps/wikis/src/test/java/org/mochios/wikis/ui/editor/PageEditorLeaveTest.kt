// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.editor

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.TestWiki
import org.mochios.wikis.ui.ok
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * Leaving the page editor by the system back or the top bar's arrow while it
 * holds edits that were never saved: it asks first, and leaves at once when
 * there is nothing to lose.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class PageEditorLeaveTest {

    @get:Rule
    val rule = createComposeRule()

    private val wiki = TestWiki { method, path ->
        when ("$method $path") {
            "GET /wikis/w1/-/info" -> ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki", "home": "home"}, "permissions": {"view": true, "edit": true}}""")
            "GET /wikis/w1/-/pages/home" -> ok("""{"page": {"id": "p1", "slug": "home", "title": "Home", "content": "Text"}}""")
            else -> null
        }
    }

    private lateinit var navigation: NavHostController
    private lateinit var dispatcher: OnBackPressedDispatcher

    @Before
    fun begin() {
        wiki.start()
    }

    @After
    fun end() = wiki.stop()

    private fun text(id: Int): String = wiki.context.getString(id)

    /** The editor open over the wiki list, on page "home", or on a new page. */
    private fun open(isNew: Boolean = false) {
        val arguments = if (isNew) mapOf("wikiId" to "w1") else mapOf("wikiId" to "w1", "page" to "home")
        val model = PageEditorViewModel(SavedStateHandle(arguments), wiki.repository, wiki.session)
        rule.setContent {
            navigation = rememberNavController()
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NavHost(navigation, startDestination = WikisApp.HOME) {
                composable(WikisApp.HOME) {}
                composable(
                    route = WikisApp.PAGE_VIEW,
                    arguments = listOf(
                        navArgument("wikiId") { type = NavType.StringType },
                        navArgument("page") { type = NavType.StringType },
                    ),
                ) {}
                composable(
                    route = if (isNew) WikisApp.NEW_PAGE else WikisApp.PAGE_EDIT,
                    arguments = if (isNew) {
                        listOf(
                            navArgument("wikiId") { type = NavType.StringType },
                            navArgument("slug") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        )
                    } else {
                        listOf(
                            navArgument("wikiId") { type = NavType.StringType },
                            navArgument("page") { type = NavType.StringType },
                        )
                    },
                ) {
                    PageEditorScreen(navigation, viewModel = model)
                }
            }
        }
        rule.runOnIdle { navigation.navigate(if (isNew) WikisApp.newPage("w1") else WikisApp.pageEdit("w1", "home")) }
        // Reading the nodes is what lets the main thread run the answers.
        rule.waitUntil(5_000) {
            rule.onAllNodes(isRoot()).fetchSemanticsNodes()
            model.uiState.value.wiki != null && (isNew || model.uiState.value.title == "Home")
        }
        rule.waitForIdle()
    }

    private fun showing(): String? = navigation.currentBackStackEntry?.destination?.route

    private fun edit(from: String, to: String) {
        rule.onNode(hasSetTextAction() and hasText(from)).performTextReplacement(to)
        rule.waitForIdle()
    }

    private fun systemBack() {
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun arrow() {
        rule.onNodeWithContentDescription(text(MochiR.string.common_back)).performClick()
        rule.waitForIdle()
    }

    private fun asking() = rule.onNodeWithText(text(MochiR.string.common_discard_title))

    /** The system back used to pop an existing page's editor at once and drop the edit. */
    @Test
    fun `the system back with unsaved edits asks first, and leaves once they are discarded`() {
        open()
        edit("Text", "Text, more")
        systemBack()
        asking().assertExists()
        assertEquals(WikisApp.PAGE_EDIT, showing())

        rule.onNodeWithText(text(MochiR.string.common_discard)).performClick()
        rule.waitForIdle()
        asking().assertDoesNotExist()
        assertEquals(WikisApp.HOME, showing())
    }

    /** The arrow used to leave at once and drop the edit. */
    @Test
    fun `the back arrow asks only while there are unsaved edits`() {
        open()
        edit("Text", "Text, more")
        arrow()
        asking().assertExists()
        rule.onNodeWithText(text(MochiR.string.common_cancel)).performClick()
        rule.waitForIdle()
        asking().assertDoesNotExist()
        assertEquals(WikisApp.PAGE_EDIT, showing())
        rule.onNode(hasSetTextAction() and hasText("Text, more")).assertExists()

        edit("Text, more", "Text")
        arrow()
        asking().assertDoesNotExist()
        assertEquals(WikisApp.HOME, showing())
    }

    @Test
    fun `the system back with nothing changed leaves at once`() {
        open()
        systemBack()
        asking().assertDoesNotExist()
        assertEquals(WikisApp.HOME, showing())
    }

    /** A new page's back used to leave for the wiki's home and drop what was typed. */
    @Test
    fun `a new page with a title typed asks before it is dropped`() {
        open(isNew = true)
        rule.onNodeWithText(text(org.mochios.wikis.R.string.wikis_editor_title_label)).performTextReplacement("Plans")
        rule.waitForIdle()
        systemBack()
        asking().assertExists()
        assertEquals(WikisApp.NEW_PAGE, showing())

        rule.onNodeWithText(text(MochiR.string.common_discard)).performClick()
        rule.waitForIdle()
        assertEquals("the wiki's home, as leaving a blank page does", WikisApp.PAGE_VIEW, showing())
    }
}
