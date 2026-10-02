// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.android.ui.components.MediaGrid
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    @Test
    fun `the images a grid has no room for`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides european) {
                MediaGrid(urls = List(1004) { index -> "https://example.org/$index.png" }, onClick = {})
            }
        }
        rule.onNodeWithText("+1.000").assertExists()
    }

    @Test
    fun `the lightbox's position among its images`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides european) {
                LightboxScreen(images = List(1234) { index -> "https://example.org/$index.png" }, initialIndex = 999, onDismiss = {})
            }
        }
        rule.onNodeWithText("1.000 / 1.234").assertExists()
    }

    @Test
    fun `a relative time`() {
        val now = System.currentTimeMillis() / 1000
        var texts: List<String> = emptyList()
        rule.setContent {
            texts = listOf(european.formatTimestamp(now - 5 * 60), european.formatRelativeTime(now - 3 * 3600))
        }
        rule.waitForIdle()
        assertWritten(texts[0], "5")
        assertWritten(texts[1], "3")
    }

    /** A status code is an identifier: plain digits, never grouped. */
    @Test
    fun `a server error's status code`() {
        AppContext.set(RuntimeEnvironment.getApplication())
        assertWritten(MochiError.ServerError(503).userMessage(), "503")
    }
}
