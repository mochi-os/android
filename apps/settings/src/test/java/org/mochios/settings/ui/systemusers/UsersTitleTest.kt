// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemusers

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsersTitleTest {

    @get:Rule
    val rule = createComposeRule()

    /** The count used to be written raw, ignoring the user's number format. */
    @Test
    fun `the title writes the user count as the user writes numbers`() {
        var titles: List<String> = emptyList()
        rule.setContent {
            CompositionLocalProvider(
                LocalFormat provides Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA)),
            ) {
                titles = listOf(usersTitle(12345), usersTitle(0))
            }
        }
        rule.waitForIdle()
        assertEquals(listOf("Users (12.345)", "Users"), titles)
    }
}
