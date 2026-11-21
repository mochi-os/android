// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.people.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * The profile editor's words: a profile that will not load says why, where it
 * said "Failed to save"; a failed change gives the server's reason or the
 * network's before that fallback; and the length counter writes its counts
 * as the user writes numbers, digits 0 to 9, where String.format used the
 * language's own digits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun begin() = AppContext.set(context)

    @Test
    fun `a profile that will not load says why and offers Retry, not that it failed to save`() {
        var retried = 0
        rule.setContent { ErrorBlock(PaddingValues(), MochiError.NetworkError()) { retried++ } }
        rule.onNodeWithText(context.getString(MochiR.string.error_network)).assertExists()
        assertEquals(0, rule.onAllNodesWithText(context.getString(R.string.people_profile_save_failed)).fetchSemanticsNodes().size)
        rule.onNodeWithText(context.getString(MochiR.string.common_retry)).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `a failed change gives the server's reason, the network's, and the fallback only when neither says more`() {
        val resources = context.resources
        assertEquals("Profile is too long", profileFailure(MochiError.Unknown("Profile is too long"), resources))
        assertEquals(context.getString(MochiR.string.error_network), profileFailure(MochiError.NetworkError(), resources))
        assertEquals("Failed to save", profileFailure(MochiError.ServerError(500), resources))
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the profile's length is written in the user's number format, in digits 0 to 9`() {
        var text = ""
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))) {
                text = profileLength(12345, LocalFormat.current)
            }
        }
        rule.waitForIdle()
        assertEquals("12.345 / 102.400", text)
        assertTrue(text, text.none { it in '٠'..'٩' })
    }
}
