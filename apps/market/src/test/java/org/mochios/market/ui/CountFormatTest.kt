// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.market.ui.account.BiographyField
import org.mochios.market.ui.components.PhotoCarousel
import org.mochios.market.ui.components.RatingStars
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

    private fun show(content: @Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalFormat provides european, content = content) }
    }

    @Test
    fun `a rating's review count`() {
        show { RatingStars(rating = 4f, count = 12345) }
        rule.onNodeWithText("(12.345", substring = true).assertExists()
    }

    @Test
    fun `the biography's length against its limit`() {
        show { BiographyField(value = "x".repeat(1234), onChange = {}) }
        rule.onNodeWithText("1.234 / 5.000").assertExists()
    }

    /** The position among the photos is counted; a thumbnail's number is a label. */
    @Test
    fun `a carousel's photo position and thumbnail`() {
        show { PhotoCarousel(photoUrls = List(1234) { index -> "https://example.org/$index.png" }) }
        rule.onNodeWithContentDescription("الصورة 1 من 1.234").assertExists()
        rule.onNodeWithContentDescription("صورة مصغرة 1").assertExists()
    }
}
