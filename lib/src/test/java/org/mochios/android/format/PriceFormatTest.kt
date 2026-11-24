// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.format

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A price drawn on a screen is written in the user's number format, which the
 * composition holds: no caller can leave it out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rGB")
class PriceFormatTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a price on screen takes the user's number format`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))) {
                Text(formatPrice(123_456, "gbp"))
            }
        }
        rule.onNodeWithText("1.234,56", substring = true).assertExists()
    }
}
