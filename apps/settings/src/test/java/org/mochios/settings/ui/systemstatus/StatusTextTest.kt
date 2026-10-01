// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemstatus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class StatusTextTest {

    @get:Rule
    val rule = createComposeRule()

    /** Reachability through a relay used to be joined by hand from two separate labels. */
    @Test
    fun `a relayed reachability reads as one phrase`() {
        var texts: List<String> = emptyList()
        rule.setContent { texts = listOf(reachabilityValue("Public", true), reachabilityValue("Public", false)) }
        rule.waitForIdle()
        assertEquals(listOf("Public · via relay", "Public"), texts)
    }

    /** The totals used to be built from the peer card's labels, so French read "Connecté 3". */
    @Test
    @Config(qualifiers = "fr")
    fun `the peer totals read as one phrase in the language`() {
        var texts: List<String> = emptyList()
        rule.setContent { texts = listOf(peerTotals(5, 3, 2), peerTotals(5, 3, null)) }
        rule.waitForIdle()
        assertEquals(
            listOf("Connus 5 · Connectés 3 · Maillage de diffusion 2", "Connus 5 · Connectés 3"),
            texts,
        )
    }

    private val european = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    /** The counts used to be written raw, ignoring the user's number format. */
    @Test
    fun `a count row writes the number as the user writes numbers`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides european) { CountRow(label = "Users", count = 12345) }
        }
        rule.onNodeWithText("12.345").assertExists()
    }

    @Test
    fun `the peer totals write their numbers as the user writes numbers`() {
        var text = ""
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides european) { text = peerTotals(12345, 1000, 2000) }
        }
        rule.waitForIdle()
        assertEquals("Known 12.345 · Connected 1.000 · Broadcast mesh 2.000", text)
    }

    /**
     * The counts inside the plural phrases used to be written by String.format,
     * which uses the language's own digits (Arabic-Indic in Arabic) and no
     * grouping, beside rows written 0 to 9 with the user's grouping.
     */
    @Test
    @Config(qualifiers = "ar")
    fun `the hole punch and relay counts are written as the user writes numbers`() {
        var texts: List<String> = emptyList()
        rule.setContent { texts = listOf(holepunchValue(12345, 2000), relayingValue(1000, 12345, 3000, 4000)) }
        rule.waitForIdle()
        for (expected in listOf("12,345", "2,000")) assertTrue(texts[0], expected in texts[0])
        for (expected in listOf("1,000", "12,345", "3,000", "4,000")) assertTrue(texts[1], expected in texts[1])
        texts.forEach { text -> assertTrue(text, text.none { it in '\u0660'..'\u0669' }) }
    }
}
