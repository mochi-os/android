// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.api.DestinationFeed
import org.mochios.settings.api.DestinationRow
import org.mochios.settings.api.DestinationsAvailable
import org.mochios.settings.api.NotifCategory
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A category card's collapsed destinations line, as the web's category row reads it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class DestinationSummaryTest {

    @get:Rule
    val rule = createComposeRule()

    private val available = DestinationsAvailable(
        feeds = listOf(
            DestinationFeed(id = "sport", name = "Sport"),
            DestinationFeed(id = "news", name = "News"),
            DestinationFeed(id = "weather", name = "Weather"),
        ),
    )

    private fun card(destinations: List<DestinationRow>) {
        rule.setContent {
            CategoryCard(
                category = NotifCategory(id = "a", label = "Wichtig", destinations = destinations),
                available = available,
                onEdit = {},
                onDelete = {},
                onTest = {},
            )
        }
    }

    /** The line used to read "Ziele · 3/4", a hand-built pattern around raw counts. */
    @Test
    fun `the chosen destinations are named, in natural order, joined as the language joins a list`() {
        card(
            listOf(
                DestinationRow(type = "web"),
                DestinationRow(type = "rss", target = "sport"),
                DestinationRow(type = "rss", target = "news"),
            ),
        )
        rule.onNodeWithText("RSS: News, RSS: Sport und Webbrowser").assertExists()
        val texts = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode().let { root ->
            buildList {
                fun walk(node: SemanticsNode) {
                    node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
                    node.children.forEach(::walk)
                }
                walk(root)
            }
        }
        assertFalse(rule.onRoot().printToString(), texts.any { Regex("""\d+\s*/\s*\d+""").containsMatchIn(it) })
        assertFalse(texts.toString(), texts.any { "Weather" in it })
    }

    @Test
    fun `tapping the line lists each destination on its own`() {
        card(listOf(DestinationRow(type = "web"), DestinationRow(type = "rss", target = "news")))
        rule.onNodeWithText("RSS: News und Webbrowser").performClick()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText("RSS: News").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("Webbrowser").fetchSemanticsNodes().size)
    }

    @Test
    fun `a category with no destinations says so`() {
        card(emptyList())
        rule.onNodeWithText("Keine Ziele").assertExists()
    }
}
