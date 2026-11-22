// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A one-time code typed on a keyboard that writes another script's digits
 * reaches the server as the ASCII digits it compares against.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CodeDigitsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `digits of any script become ASCII`() {
        assertEquals("123456", codeDigits("١٢٣٤٥٦", 6))
        assertEquals("123456", codeDigits("۱۲۳۴۵۶", 6))
        assertEquals("123456", codeDigits("१२३४५६", 6))
        assertEquals("123456", codeDigits("１２３４５６", 6))
        assertEquals("19", codeDigits("𝟙𝟡", 6))
    }

    @Test
    fun `anything else is dropped and the code is cut to its length`() {
        assertEquals("123456", codeDigits("12 3-4a5b6789", 6))
        assertEquals("", codeDigits("abc", 6))
    }

    @Test
    fun `the code boxes hold ASCII digits for digits typed in another script`() {
        var value by mutableStateOf("")
        var filled = ""
        rule.setContent {
            CodeInputBoxes(value = value, onValueChange = { value = it }, onFilled = { filled = it })
        }
        rule.onNode(hasSetTextAction()).performTextInput("١٢٣٤٥٦")
        rule.waitForIdle()
        assertEquals("123456", value)
        assertEquals("123456", filled)
    }
}
