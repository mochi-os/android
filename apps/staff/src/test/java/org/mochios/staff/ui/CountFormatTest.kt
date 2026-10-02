// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui

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
import org.mochios.staff.model.Account
import org.mochios.staff.ui.accounts.AccountRow
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

    /** The sales are counted; the verification level is a label. */
    @Test
    fun `an account's sales and verification level`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))) {
                AccountRow(account = Account(id = "a", name = "Shop", sales = 12345, verified = 2), onHistory = {}, onAction = {})
            }
        }
        rule.onNodeWithText("12.345", substring = true).assertExists()
        rule.onNodeWithText("L2").assertExists()
    }
}
