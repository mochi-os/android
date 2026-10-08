// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A passkey card says when it was created and last used, "Never" for one not
 * used yet, and a linked account card when it was last used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageLinesTest {

    @get:Rule
    val rule = createComposeRule()

    private fun count(text: String) =
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun `a passkey used before says when it was created and last used`() {
        rule.setContent { UsageLines(created = 1781168284, used = 1791368862) }
        assertEquals(1, count("Created "))
        assertEquals(1, count("Last used "))
    }

    @Test
    fun `a passkey never used says so`() {
        rule.setContent { UsageLines(created = 1781168284, used = 0, never = true) }
        assertEquals(1, count("Created "))
        assertEquals(1, count("Last used Never"))
    }

    @Test
    fun `a time left at 0 without never has no line`() {
        rule.setContent { UsageLines(created = 1781168284, used = 0) }
        assertEquals(0, count("Last used"))
    }

    @Test
    fun `a linked account says only when it was last used`() {
        rule.setContent { UsageLines(used = 1791368862) }
        assertEquals(0, count("Created"))
        assertEquals(1, count("Last used "))
    }
}
