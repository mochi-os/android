// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Setting up an authenticator, a code typed on a keyboard that writes another
 * script's digits is sent as the ASCII digits the server compares against.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TotpCodeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a setup code typed in Arabic-Indic digits is verified as ASCII`() {
        var verified = ""
        rule.setContent {
            TotpSetupDialog(
                secret = "SECRET",
                url = "otpauth://totp/a?secret=SECRET",
                onCancel = {},
                onVerify = { code -> verified = code },
            )
        }
        rule.onNode(hasSetTextAction()).performTextInput("١٢٣٤٥٦")
        rule.onNodeWithText("Verify").performClick()
        rule.waitForIdle()
        assertEquals("123456", verified)
    }
}
