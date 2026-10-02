// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.calendars.ui.components.pollFailure
import org.mochios.calendars.ui.dialogs.reminderLabel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Numbers inside sentences used to be written by String.format, which uses the
 * language's own digits (Arabic-Indic in Arabic) and no grouping. Counts are
 * written as the user writes numbers and identifiers as plain digits, 0 to 9,
 * as the web writes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar")
class CountFormatTest {

    @get:Rule
    val rule = createComposeRule()

    private val european = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    private fun assertWritten(text: String, vararg numbers: String) {
        numbers.forEach { number -> assertTrue(text, number in text) }
        assertTrue(text, text.none { it in '٠'..'٩' })
    }

    private fun texts(content: @Composable () -> List<String>): List<String> {
        var texts: List<String> = emptyList()
        rule.setContent { CompositionLocalProvider(LocalFormat provides european) { texts = content() } }
        rule.waitForIdle()
        return texts
    }

    @Test
    fun `a reminder's lead`() {
        val texts = texts { listOf(reminderLabel(1501), reminderLabel(180), reminderLabel(1000 * 1440)) }
        assertWritten(texts[0], "1.501")
        assertWritten(texts[1], "3")
        assertWritten(texts[2], "1.000")
    }

    /** A status code is an identifier: plain digits, never grouped. */
    @Test
    fun `the status a calendar's address answered`() {
        assertWritten(texts { listOfNotNull(pollFailure("status:503")) }.single(), "503")
    }
}
