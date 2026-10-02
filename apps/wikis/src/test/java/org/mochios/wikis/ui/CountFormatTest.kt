// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui

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
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.ui.attachments.AttachmentsTopBar
import org.mochios.wikis.ui.history.RevertDialog
import org.mochios.wikis.ui.tags.CountPill
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
    fun `the attachments' totals`() {
        show {
            AttachmentsTopBar(
                attachments = List(1234) { index -> Attachment(id = "i$index", name = "i$index.png", type = "image/png") } +
                    List(1000) { index -> Attachment(id = "d$index", name = "d$index.pdf", type = "application/pdf") },
                onBack = {},
            )
        }
        rule.onNodeWithText("2.234", substring = true).assertExists()
        rule.onNodeWithText("1.234", substring = true).assertExists()
        rule.onNodeWithText("1.000", substring = true).assertExists()
    }

    @Test
    fun `a tag's page count`() {
        show { CountPill(count = 12345) }
        rule.onNodeWithText("12.345").assertExists()
        rule.onNodeWithContentDescription("12.345", substring = true).assertExists()
    }

    /** A version is an identifier: plain digits, never grouped. */
    @Test
    fun `the version a revert goes back to`() {
        show { RevertDialog(slug = "home", version = 1234, isReverting = false, onConfirm = {}, onDismiss = {}) }
        rule.onNodeWithText("أنت على وشك استرجاع home إلى الإصدار 1234", substring = true).assertExists()
        rule.onNodeWithText("تم الاسترجاع إلى الإصدار 1234").assertExists()
    }
}
