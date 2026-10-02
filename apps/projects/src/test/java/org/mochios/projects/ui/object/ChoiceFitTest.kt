// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.choiceFits
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.ProjectField
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rule that lets a choice field share its row, held against the dropdown
 * it speaks for: laid out at every width a shared cell can have, the dropdown
 * must show its value on one line wherever the rule says it fits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w420dp-h6000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChoiceFitTest {

    @get:Rule
    val rule = createComposeRule()

    private val widths = (120..240 step 2).toList()
    private var text: Dp = 0.dp

    private fun show(name: String, colour: String) {
        val field = ProjectField(id = "status", name = "Status", fieldtype = "enumerated")
        val option = FieldOption(id = "chosen", name = name, colour = colour)
        rule.setContent {
            val measurer = rememberTextMeasurer()
            val style = LocalTextStyle.current
            text = with(LocalDensity.current) { measurer.measure(name, style, maxLines = 1).size.width.toDp() }
            Column {
                widths.forEach { width ->
                    Box(Modifier.width(width.dp).testTag("cell$width")) {
                        FieldEditor(
                            field = field,
                            value = option.id,
                            options = listOf(option),
                            canWrite = true,
                            people = emptyList(),
                            showLabel = false,
                            onValueChange = {},
                            onSearchUsers = { emptyList() },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun height(width: Int): Float = with(rule.density) {
        rule.onNodeWithTag("cell$width").fetchSemanticsNode().size.height.toDp().value
    }

    /** A field showing its value on one line stands at the text field's own height. */
    private val line: Float get() = height(widths.last())

    private fun check(name: String, colour: String) {
        show(name, colour)
        val swatch = colour.isNotBlank()
        for (width in widths) {
            if (choiceFits(text, swatch, width.dp)) {
                assertEquals("$name wraps in a ${width}dp cell the rule shares", line, height(width), 0.5f)
            }
        }
        // Not so cautious that it refuses cells the value fits with room to spare.
        val accepted = widths.first { width -> choiceFits(text, swatch, width.dp) }
        val fitting = widths.first { width -> height(width) <= line + 0.5f }
        assertTrue("the rule first shares at ${accepted}dp, the value fits from ${fitting}dp", accepted - fitting <= 4)
    }

    @Test
    fun `a choice with a colour dot shares its row only where its name stays on one line`() {
        check("In progress", "#3b82f6")
    }

    @Test
    fun `a choice without a colour shares its row only where its name stays on one line`() {
        check("In progress", "")
    }

    @Test
    fun `In progress with its colour dot fits half the form on a 384dp phone`() {
        show("In progress", "#3b82f6")
        // (384 - 16 - 16 - 12) / 2
        assertTrue(choiceFits(text, swatch = true, cell = 170.dp))
        assertEquals(line, height(170), 0.5f)
    }
}
