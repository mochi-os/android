// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.format

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MoneyFormatTest {
    private lateinit var saved: Locale

    @Before
    fun rememberLocale() {
        saved = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(saved)
    }

    // The amount masks admit "," as a decimal mark in every locale, so the
    // parser must read it as one even where the locale treats "," as grouping.
    @Test
    fun commaIsTheDecimalMarkInAPeriodDecimalLocale() {
        Locale.setDefault(Locale.US)
        assertEquals(1234L, toMinorUnits("12,34", "gbp"))
        assertEquals(1234L, toMinorUnits("12.34", "gbp"))
        assertEquals(50L, toMinorUnits(",5", "usd"))
    }

    @Test
    fun periodIsTheDecimalMarkInACommaDecimalLocale() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals(1234L, toMinorUnits("12.34", "eur"))
        assertEquals(1234L, toMinorUnits("12,34", "eur"))
    }

    @Test
    fun zeroDecimalCurrencyTakesWholeUnits() {
        Locale.setDefault(Locale.US)
        assertEquals(1500L, toMinorUnits("1500", "jpy"))
    }

    @Test
    fun groupedInputStillUsesTheLocaleParser() {
        Locale.setDefault(Locale.US)
        assertEquals(123456L, toMinorUnits("1,234.56", "usd"))
    }

    @Test
    fun blankAndInvalidInputAreZero() {
        Locale.setDefault(Locale.US)
        assertEquals(0L, toMinorUnits("", "usd"))
        assertEquals(0L, toMinorUnits("   ", "usd"))
        assertEquals(0L, toMinorUnits("abc", "usd"))
    }

    @Test
    fun majorTextRoundTripsInEveryLocale() {
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.FRANCE, Locale.JAPAN)) {
            Locale.setDefault(locale)
            assertEquals("12.34", minorToMajorText(1234L, "gbp"))
            assertEquals("0.05", minorToMajorText(5L, "usd"))
            assertEquals("1500", minorToMajorText(1500L, "jpy"))
            assertEquals("", minorToMajorText(0L, "eur"))
            assertEquals("-12.34", minorToMajorText(-1234L, "eur"))
            assertEquals(1234L, toMinorUnits(minorToMajorText(1234L, "eur"), "eur"))
        }
    }
}
