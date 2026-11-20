// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.market.R
import org.mochios.market.model.Currency
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The seller's refund amount: blank refunds the whole remainder and sends no
 * amount; an amount that is zero, unreadable, a fraction of a minor unit or
 * above the remainder is refused in the dialog, never sent as a full refund.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IssueRefundDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `blank, or all of the remainder, is the whole remainder`() {
        assertEquals(RefundEntry.Whole, refundEntry("", Currency.GBP, 1000))
        assertEquals(RefundEntry.Whole, refundEntry("  ", Currency.GBP, 1000))
        assertEquals(RefundEntry.Whole, refundEntry("10", Currency.GBP, 1000))
        assertEquals(RefundEntry.Whole, refundEntry("10.00", Currency.GBP, 1000))
    }

    @Test
    fun `part of the remainder is that many minor units`() {
        assertEquals(RefundEntry.Part(500), refundEntry("5", Currency.GBP, 1000))
        assertEquals(RefundEntry.Part(501), refundEntry("5,01", Currency.GBP, 1000))
        assertEquals(RefundEntry.Part(50), refundEntry(".5", Currency.GBP, 1000))
        assertEquals(RefundEntry.Part(5), refundEntry("5", Currency.JPY, 1000))
    }

    @Test
    fun `nothing that reads as zero or cannot be read refunds anything`() {
        for (text in listOf("0", "0.00", ".", ",", "0.004", "£5", "5.5.5", "-5", "1,234.00")) {
            assertEquals("\"$text\"", RefundEntry.Invalid, refundEntry(text, Currency.GBP, 100_000))
        }
        assertEquals(RefundEntry.Invalid, refundEntry("5.5", Currency.JPY, 1000))
    }

    @Test
    fun `more than the remainder is refused`() {
        assertEquals(RefundEntry.Invalid, refundEntry("10.01", Currency.GBP, 1000))
    }

    private fun show(onSubmit: (Long?) -> Unit) {
        AppContext.set(context)
        rule.setContent {
            IssueRefundDialog(
                open = true,
                currency = Currency.GBP,
                remaining = 1000,
                onSubmit = onSubmit,
                onDismiss = {},
            )
        }
        rule.waitForIdle()
    }

    private fun confirm() =
        rule.onNode(hasText(context.getString(R.string.market_refund_dialog_submit)) and hasClickAction())

    @Test
    fun `a zero amount holds the refund back`() {
        val sent = mutableListOf<Long?>()
        show { amount -> sent += amount }
        rule.onNode(hasSetTextAction()).performTextInput("0")
        rule.waitForIdle()
        confirm().assertIsNotEnabled()
        assertEquals(emptyList<Long?>(), sent)
    }

    @Test
    fun `a blank field sends the whole remainder as no amount`() {
        val sent = mutableListOf<Long?>()
        show { amount -> sent += amount }
        confirm().assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(listOf<Long?>(null), sent)
    }

    @Test
    fun `a part sends its minor units`() {
        val sent = mutableListOf<Long?>()
        show { amount -> sent += amount }
        rule.onNode(hasSetTextAction()).performTextInput("2.50")
        rule.waitForIdle()
        confirm().performClick()
        rule.waitForIdle()
        assertEquals(listOf<Long?>(250), sent)
    }
}
