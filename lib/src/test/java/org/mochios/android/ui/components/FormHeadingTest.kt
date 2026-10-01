// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A title as the heading of an object's form, in a phone's width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormHeadingTest {

    @get:Rule
    val rule = createComposeRule()

    private var value by mutableStateOf("")
    private var readOnly by mutableStateOf(false)

    private fun show(initial: String) {
        value = initial
        rule.setContent {
            Box(Modifier.width(352.dp)) {
                FormHeading(
                    value = value,
                    onValueChange = { value = it },
                    name = "Title",
                    readOnly = readOnly,
                    modifier = Modifier.testTag("heading"),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun height(): Float = rule.onNodeWithTag("heading").fetchSemanticsNode().size.height.toFloat()

    @Test
    fun `a title too long for one line wraps instead of running off the side`() {
        show("Short")
        val line = height()
        value = "[Project Mobile] Ticket fields are different between Tickets view and List view"
        rule.waitForIdle()
        assertTrue("three lines or more: ${height()} against one of $line", height() >= line * 3)
    }

    @Test
    fun `a line break typed into the title becomes a space`() {
        show("")
        rule.onNode(hasSetTextAction()).performTextInput("one\ntwo")
        rule.waitForIdle()
        assertEquals("one two", value)
    }

    @Test
    fun `an empty title shows the field's name in its place`() {
        show("")
        rule.onNodeWithText("Title").assertIsDisplayed()
    }

    @Test
    fun `a title that cannot be edited is plain text`() {
        readOnly = true
        show("Fixed in place")
        rule.onNodeWithText("Fixed in place").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
    }
}
