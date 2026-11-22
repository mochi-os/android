// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A number field takes a minus sign, a leading point and a decimal comma on
 * the way to a number, and sends only what the server stores
 * (`^-?\d+(\.\d+)?$`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class NumberFieldTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the start of a number can be typed`() {
        listOf("", "-", ".", "-.", "5.", ".5", "-.5", "5,", "5,25", "-12.0").forEach { text ->
            assertTrue(text, isPartialNumber(text))
        }
    }

    @Test
    fun `what can never be a number cannot be typed`() {
        listOf("5-", "--5", "1e3", "NaN", "Infinity", "+5", "5d", "0x1p3", "1.2.3", "1,2.3", " 5").forEach { text ->
            assertFalse(text, isPartialNumber(text))
        }
    }

    @Test
    fun `a typed number is sent as the server stores one`() {
        assertEquals("5", completeNumber("5."))
        assertEquals("0.5", completeNumber(".5"))
        assertEquals("-0.5", completeNumber("-.5"))
        assertEquals("5.25", completeNumber("5,25"))
        assertEquals("-12.0", completeNumber("-12.0"))
        assertEquals("", completeNumber(""))
    }

    @Test
    fun `no number yet is not sent`() {
        listOf("-", ".", "-.", "1e3", "NaN").forEach { text -> assertNull(text, completeNumber(text)) }
    }

    @Test
    fun `typing sends each complete number and keeps the text as typed`() {
        val sent = mutableListOf<String>()
        var stored by mutableStateOf("")
        rule.setContent {
            NumberField(value = stored, onValueChange = { number -> sent += number; stored = number })
        }
        val field = rule.onNode(hasSetTextAction())
        field.performTextInput("-")
        rule.waitForIdle()
        field.assert(hasText("-"))
        assertEquals(emptyList<String>(), sent)

        field.performTextInput(".5")
        rule.waitForIdle()
        // The server's form is sent; what was typed stays in the field.
        assertEquals(listOf("-0.5"), sent)
        field.assert(hasText("-.5"))

        field.performTextInput("x")
        rule.waitForIdle()
        field.assert(hasText("-.5"))
        assertEquals(listOf("-0.5"), sent)
    }

    @Test
    fun `a trailing point standing for the stored number sends nothing`() {
        val sent = mutableListOf<String>()
        rule.setContent { NumberField(value = "5", onValueChange = { number -> sent += number }) }
        val field = rule.onNode(hasSetTextAction())
        field.performTextReplacement("5.")
        rule.waitForIdle()
        field.assert(hasText("5."))
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `a new stored value replaces the text`() {
        var stored by mutableStateOf("5")
        rule.setContent { NumberField(value = stored, onValueChange = {}) }
        stored = "7.25"
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assert(hasText("7.25"))
    }

    @Test
    fun `a stored value that is no number can still be emptied`() {
        val sent = mutableListOf<String>()
        rule.setContent { NumberField(value = "abc", onValueChange = { number -> sent += number }) }
        val field = rule.onNode(hasSetTextAction())
        field.performTextReplacement("ab")
        rule.waitForIdle()
        field.assert(hasText("ab"))
        field.performTextReplacement("")
        rule.waitForIdle()
        assertEquals(listOf(""), sent)
    }
}
