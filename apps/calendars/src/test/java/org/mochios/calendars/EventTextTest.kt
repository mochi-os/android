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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.calendars.ui.editor.EventText
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The editor's title and description, in a phone's width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventTextTest {

    @get:Rule
    val rule = createComposeRule()

    private var description by mutableStateOf("")
    private var saves = 0

    private fun show(select: Boolean = false) {
        rule.setContent {
            Box(Modifier.width(352.dp)) {
                EventText(
                    title = "Standup",
                    description = description,
                    location = "Room 4",
                    url = "https://example.org",
                    untitled = false,
                    enabled = true,
                    onTitle = {},
                    onDescription = { description = it },
                    onLocation = {},
                    onUrl = {},
                    onSave = { saves++ },
                    select = select,
                )
            }
        }
        rule.waitForIdle()
    }

    /** The title's, description's, location's and link's fields, top to bottom. */
    private fun fields() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        .map { it.boundsInRoot }

    @Test
    fun `the description sits straight below the title, then the location and the link`() {
        show()
        val boxes = fields().also { assertEquals(4, it.size) }
        for (index in 1 until boxes.size) {
            assertTrue("field $index below the one before", boxes[index].top >= boxes[index - 1].bottom)
        }
    }

    @Test
    fun `an empty description is two lines high, taller than the one-line title`() {
        show()
        val (title, text) = fields()
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
    fun `the keyboard's action in the title saves`() {
        show()
        rule.onAllNodes(hasSetTextAction())[0].performImeAction()
        rule.waitForIdle()
        assertEquals(1, saves)
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
