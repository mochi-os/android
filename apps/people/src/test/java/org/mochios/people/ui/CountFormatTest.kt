// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui

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
import org.mochios.people.model.Book
import org.mochios.people.ui.contacts.deleteBookMessage
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Counts inside sentences used to be written by String.format, which uses the
 * language's own digits (Arabic-Indic in Arabic) and no grouping. They are
 * written as the user writes numbers, digits 0 to 9, as the web writes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar")
class CountFormatTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the contacts a book's deletion takes with it`() {
        var text = ""
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))) {
                text = deleteBookMessage(Book(name = "Work", count = 12345))
            }
        }
        rule.waitForIdle()
        assertTrue(text, "Work" in text && "12.345" in text)
        assertTrue(text, text.none { it in '٠'..'٩' })
    }
}
