// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dialog

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class RefundInputTest {
    private lateinit var saved: Locale

    @Before
    fun rememberLocale() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(saved)
    }

    @Test
    fun blankMeansTheFullRefund() {
        assertEquals(RefundInput.Full, parseRefundInput("", "gbp"))
        assertEquals(RefundInput.Full, parseRefundInput("   ", "gbp"))
    }

    @Test
    fun aPositiveAmountIsPartialInMinorUnits() {
        assertEquals(RefundInput.Partial(1234), parseRefundInput("12.34", "gbp"))
        assertEquals(RefundInput.Partial(1500), parseRefundInput("1500", "jpy"))
        // A thousands separator parses through the locale, not as a second
        // decimal point; this is the input that used to become a full refund.
        assertEquals(RefundInput.Partial(123456), parseRefundInput("1,234.56", "usd"))
    }

    // Anything that does not parse to a positive amount must block the
    // submit: a null amount tells the server "refund everything".
    @Test
    fun anUnparseableOrZeroAmountIsInvalidNotFull() {
        assertEquals(RefundInput.Invalid, parseRefundInput("abc", "gbp"))
        assertEquals(RefundInput.Invalid, parseRefundInput("0", "gbp"))
        assertEquals(RefundInput.Invalid, parseRefundInput("-5", "gbp"))
    }
}
