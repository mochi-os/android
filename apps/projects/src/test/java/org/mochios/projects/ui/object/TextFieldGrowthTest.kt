// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.projects.model.ProjectField
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A text field of the object form, in a phone's width, as its value grows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextFieldGrowthTest {

    @get:Rule
    val rule = createComposeRule()

    private var value by mutableStateOf("")

    private fun show(rows: Int, initial: String) {
        value = initial
        val field = ProjectField(id = "notes", name = "Notes", fieldtype = "text", rows = rows)
        rule.setContent {
            Box(Modifier.width(352.dp).testTag("field")) {
                FieldEditor(
                    field = field,
                    value = value,
                    options = emptyList(),
                    canWrite = true,
                    people = emptyList(),
                    showLabel = false,
                    onValueChange = { value = it },
                    onSearchUsers = { emptyList() },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun height(): Float = rule.onNodeWithTag("field").fetchSemanticsNode().size.height.toFloat()

    private fun lines(count: Int): String = (1..count).joinToString("\n") { number -> "line $number" }

    @Test
    fun `a one-row field wraps a value too long for one line`() {
        show(rows = 1, initial = "Short")
        val line = height()
        value = "A value a good deal longer than a single line of a phone's width has any room to show"
        rule.waitForIdle()
        assertTrue("taller than one line: ${height()} against $line", height() > line)
    }

    @Test
    fun `a line break typed into a one-row field becomes a space`() {
        show(rows = 1, initial = "")
        rule.onNode(hasSetTextAction()).performTextInput("one\ntwo")
        rule.waitForIdle()
        assertEquals("one two", value)
    }

    @Test
    fun `a many-row field keeps its line breaks`() {
        show(rows = 3, initial = "")
        rule.onNode(hasSetTextAction()).performTextInput("one\ntwo")
        rule.waitForIdle()
        assertEquals("one\ntwo", value)
    }

    @Test
    fun `a many-row field grows past its rows to show every line`() {
        show(rows = 3, initial = lines(3))
        val three = height()
        value = lines(12)
        rule.waitForIdle()
        assertTrue("twelve lines stand taller than three: ${height()} against $three", height() > three * 2)
    }

    @Test
    fun `a many-row field starts at its rows, three at most`() {
        show(rows = 3, initial = "")
        val three = height()
        value = lines(3)
        rule.waitForIdle()
        assertEquals(three, height(), 1f)
    }
}
