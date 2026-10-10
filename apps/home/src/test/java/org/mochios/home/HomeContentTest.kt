// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.mochios.home.repository.Grid
import org.mochios.home.repository.Tile
import org.mochios.home.ui.HomeContent
import org.mochios.home.ui.HomeUiState
import org.mochios.home.ui.words
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home grid's body: a tap opens the app, a long press offers its icon for
 * the home screen where the launcher allows it, and the screen says when it
 * is loading, failed, or has nothing to show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val grid = Grid(listOf(Tile("chat", "Chat"), Tile("feeds", "Feeds")))

    private fun show(
        state: HomeUiState,
        pinnable: Boolean = true,
        opened: MutableList<Tile> = mutableListOf(),
        pinned: MutableList<Tile> = mutableListOf(),
        retried: MutableList<Unit> = mutableListOf(),
    ) {
        // An error's message is read through the app's context, which the
        // Application installs at startup.
        AppContext.set(RuntimeEnvironment.getApplication())
        rule.setContent {
            HomeContent(
                state = state,
                pinnable = pinnable,
                onOpen = { tile -> opened += tile },
                onPin = { tile -> pinned += tile },
                onRetry = { retried += Unit },
            )
        }
    }

    @Test
    fun `every app shows by its name`() {
        show(HomeUiState(grid = grid, loading = false))
        rule.onNodeWithText("Chat").assertExists()
        rule.onNodeWithText("Feeds").assertExists()
    }

    @Test
    fun `a tap opens that app`() {
        val opened = mutableListOf<Tile>()
        show(HomeUiState(grid = grid, loading = false), opened = opened)
        rule.onNodeWithText("Feeds").performClick()
        assertEquals(listOf(Tile("feeds", "Feeds")), opened)
    }

    @Test
    fun `a long press offers the app's icon for the home screen`() {
        val pinned = mutableListOf<Tile>()
        show(HomeUiState(grid = grid, loading = false), pinned = pinned)
        rule.onNodeWithText("Chat").performTouchInput { longClick() }
        rule.onNodeWithText("Add to home screen").performClick()
        assertEquals(listOf(Tile("chat", "Chat")), pinned)
    }

    @Test
    fun `where the launcher takes no shortcuts a long press offers nothing`() {
        show(HomeUiState(grid = grid, loading = false), pinnable = false)
        rule.onNodeWithText("Chat").performTouchInput { longClick() }
        rule.onNodeWithText("Add to home screen").assertDoesNotExist()
    }

    @Test
    fun `a theme's tiles still show every app by name`() {
        show(HomeUiState(grid = grid.copy(mask = "squircle", background = "#3366cc"), loading = false))
        rule.onNodeWithText("Chat").assertExists()
        rule.onNodeWithText("Feeds").assertExists()
    }

    @Test
    fun `a name too long for its tile wraps rather than being cut short`() {
        // The longest app name in any language, Maltese for Publisher.
        val name = "Pubblikatur tal-Applikazzjonijiet"
        show(HomeUiState(grid = Grid(listOf(Tile("chat", name), Tile("feeds", "Feeds"))), loading = false))
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(name).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()
        assertTrue("wrapped onto ${layout.lineCount} line(s)", layout.lineCount in 2..3)
        assertTrue("cut short", !layout.hasVisualOverflow)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a tile grows to hold a word too wide for a quarter of the row`() {
        // German for Settings: wider than a quarter of a 411dp row, narrower
        // than a third, so it takes three tiles a row rather than split.
        val word = "Einstellungen"
        val tiles = listOf(word, "Chat", "Feeds", "Go", "Wikis", "Words", "Chess", "Market")
            .mapIndexed { index, label -> Tile("app$index", label) }
        show(HomeUiState(grid = Grid(tiles), loading = false))
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(word).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals(1, layouts.first().lineCount)
    }

    @Test
    fun `a label breaks into words between words and after hyphens`() {
        assertEquals(listOf("Pubblikatur", "tal-", "Applikazzjonijiet"), words("Pubblikatur tal-Applikazzjonijiet"))
    }

    @Test
    fun `no apps says so`() {
        show(HomeUiState(grid = Grid(emptyList()), loading = false))
        rule.onNodeWithText("No apps found").assertExists()
    }

    @Test
    fun `a failure with nothing kept offers a retry`() {
        val retried = mutableListOf<Unit>()
        show(HomeUiState(grid = null, loading = false, error = MochiError.NetworkError()), retried = retried)
        rule.onNodeWithText("Retry", substring = true, ignoreCase = true).performClick()
        assertTrue(retried.isNotEmpty())
    }
}
