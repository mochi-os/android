// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.format.personName
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A time left is written in the language's own unit abbreviations, two units
 * at most, with the user's digits; a person is named, or fingerprinted, or
 * Unknown, never by entity id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemainingTest {

    @get:Rule
    val rule = createComposeRule()

    private val european = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    private fun shows(seconds: Long, expected: String) {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides european) {
                Text(LocalFormat.current.formatRemaining(seconds))
            }
        }
        rule.onNodeWithText(expected).assertExists()
    }

    @Test
    fun `days and hours`() = shows(1_234 * 86_400L + 3 * 3_600L + 59, "1.234d 3h")

    @Test
    fun `hours and minutes`() = shows(2 * 3_600L + 5 * 60L + 7, "2h 5m")

    @Test
    fun `minutes and seconds`() = shows(4 * 60L + 9, "4m 9s")

    @Test
    fun `seconds alone`() = shows(40L, "40s")

    @Test
    fun `a person with no name is shown by fingerprint, never by id`() {
        rule.setContent {
            Text(personName(name = " ", fingerprint = "abcdefghijk"))
        }
        rule.onNodeWithText("abc-def-ghi").assertExists()
    }

    @Test
    fun `a person with neither is Unknown`() {
        rule.setContent {
            Text(personName(name = null, fingerprint = null))
        }
        rule.onNodeWithText("Unknown").assertExists()
    }
}
