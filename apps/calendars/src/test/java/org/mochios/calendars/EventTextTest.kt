// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.calendars.ui.editor.EventText
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor's title, description, location and link, in a phone's width:
 * fields drawn on the page with no box, each named by what it holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventTextTest {

    @get:Rule
    val rule = createComposeRule()

    private var description by mutableStateOf("")
    private var location by mutableStateOf("Room 4")
    private var title by mutableStateOf("Standup")
    private var asked by mutableStateOf(0)
    private var saves = 0
    private val context = RuntimeEnvironment.getApplication()

    private fun show(select: Boolean = false) {
        rule.setContent {
            Box(Modifier.width(352.dp)) {
                EventText(
                    title = title,
                    description = description,
                    location = location,
                    url = "https://example.org",
                    untitled = false,
                    enabled = true,
                    onTitle = {},
                    onDescription = { description = it },
                    onLocation = {},
                    onUrl = {},
                    onSave = { saves++ },
                    select = select,
                    asked = asked,
                )
            }
        }
        rule.waitForIdle()
    }

    /** The title's, location's, link's and description's fields, top to bottom. */
    private fun fields() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        .map { it.boundsInRoot }

    @Test
    fun `each field shows no label, filled or not, and is named for screen readers`() {
        show()
        val names = fields().indices.map { index ->
            rule.onAllNodes(hasSetTextAction())[index].fetchSemanticsNode()
                .config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.single()
        }
        val expected = listOf(
            R.string.calendars_event_title,
            R.string.calendars_event_location,
            R.string.calendars_event_url,
            R.string.calendars_event_description,
        ).map(context::getString)
        assertEquals(expected, names)
        // The empty description shows its name in its place; the filled title does not.
        assertEquals(1, rule.onAllNodesWithText(expected[3]).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(expected[0]).fetchSemanticsNodes().size)
    }

    @Test
    fun `the location and the link sit below the title, then the description below the link`() {
        show()
        val boxes = fields().also { assertEquals(4, it.size) }
        for (index in 1 until boxes.size) {
            assertTrue("field $index below the one before", boxes[index].top >= boxes[index - 1].bottom)
        }
    }

    @Test
    fun `a value wider than its field opens at its start, loaded or replaced`() {
        location = "Conference room on the fourth floor of the east building, past the lifts"
        title = "A standup whose title runs well past the width of a phone"
        show()
        val nodes = rule.onAllNodes(hasSetTextAction())
        // The title, then the location: each shows where it begins.
        for (index in 0..1) {
            assertEquals(
                TextRange(0),
                nodes[index].fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
            )
        }
        // Replaced from outside, as a sync or a reload does: at its start again.
        location = "Boardroom beside the reception desk on the ground floor of the west wing"
        title = "Another long title, read again from the server after a sync"
        rule.waitForIdle()
        for (index in 0..1) {
            assertEquals(
                TextRange(0),
                nodes[index].fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
            )
        }
    }

    @Test
    fun `an empty description is two lines high, taller than the one-line title`() {
        show()
        val boxes = fields()
        val (title, text) = boxes[0] to boxes[3]
        assertTrue("taller: ${text.height} against ${title.height}", text.height > title.height)
    }

    @Test
    fun `a copy's title takes the focus with its text selected`() {
        show(select = true)
        val title = rule.onAllNodes(hasSetTextAction())[0]
        title.assertIsFocused()
        assertEquals(
            TextRange(0, "Standup".length),
            title.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )
    }

    @Test
    fun `a save tried without a title takes the cursor to the title, each time`() {
        show()
        val title = rule.onAllNodes(hasSetTextAction())[0]
        title.assertIsNotFocused()
        asked = 1
        rule.waitForIdle()
        title.assertIsFocused()
        // Moved away and tried again: back to the title.
        rule.onAllNodes(hasSetTextAction())[1].performClick()
        rule.waitForIdle()
        title.assertIsNotFocused()
        asked = 2
        rule.waitForIdle()
        title.assertIsFocused()
    }

    @Test
    fun `the keyboard's action in the title saves`() {
        show()
        rule.onAllNodes(hasSetTextAction())[0].performImeAction()
        rule.waitForIdle()
        assertEquals(1, saves)
    }

    @Test
    fun `the empty description's name sits on its first line, where the cursor is, and goes once anything is typed`() {
        show()
        val name = context.getString(R.string.calendars_event_description)
        val field = rule.onAllNodes(hasSetTextAction())[1]
        // The field's bounds hold its padding, then two lines; the name is
        // its own node only in the unmerged tree.
        val box = field.fetchSemanticsNode().boundsInRoot
        val shown = rule.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val padding = 12 * context.resources.displayMetrics.density
        assertEquals(box.top + padding, shown.top, 1f)
        field.performTextInput("D")
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithText(name).fetchSemanticsNodes().size)
    }

    @Test
    fun `the description grows with what is typed`() {
        show()
        val empty = fields()[1].height
        description = (1..6).joinToString("\n") { number -> "line $number" }
        rule.waitForIdle()
        val grown = fields()[1].height
        assertTrue("grown: $grown against $empty", grown > empty)
    }
}
