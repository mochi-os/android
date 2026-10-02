// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui

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
import org.mochios.forums.ui.moderation.pendingCommentsHeading
import org.mochios.forums.ui.moderation.pendingPostsHeading
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

    private val european = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    private fun assertWritten(text: String, vararg numbers: String) {
        numbers.forEach { number -> assertTrue(text, number in text) }
        assertTrue(text, text.none { it in '٠'..'٩' })
    }

    @Test
    fun `the moderation queue's pending counts`() {
        var texts: List<String> = emptyList()
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides european) {
                texts = listOf(pendingPostsHeading(12345), pendingCommentsHeading(1000))
            }
        }
        rule.waitForIdle()
        assertWritten(texts[0], "12.345")
        assertWritten(texts[1], "1.000")
    }
}
