// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Link button: a markdown link whose stand-ins are in the writer's language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarkdownToolbarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `with nothing selected the link's text is left selected`() {
        val linked = TextFieldValue("See ", TextRange(4)).linked("Text", "Adresse")
        assertEquals("See [Text](Adresse)", linked.text)
        assertEquals(TextRange(5, 9), linked.selection)
    }

    @Test
    fun `with text selected the address is left selected`() {
        val linked = TextFieldValue("See home now", TextRange(4, 8)).linked("Text", "Adresse")
        assertEquals("See [home](Adresse) now", linked.text)
        assertEquals(TextRange(11, 18), linked.selection)
    }

    private fun linkedBody(): String {
        var body by mutableStateOf(TextFieldValue(""))
        var link = ""
        rule.setContent {
            link = androidx.compose.ui.res.stringResource(org.mochios.android.R.string.format_link)
            MarkdownToolbar(body = body, onBodyChange = { body = it })
        }
        rule.onNodeWithContentDescription(link).performClick()
        rule.waitForIdle()
        return body.text
    }

    @Test
    fun `the button writes the stand-ins from the catalogue`() {
        assertEquals("[text](url)", linkedBody())
    }

    @Test
    @Config(qualifiers = "de")
    fun `the stand-ins are in the writer's language`() {
        assertEquals("[Text](URL)", linkedBody())
    }
}
