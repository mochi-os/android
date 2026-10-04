// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/**
 * A month and its year as the language writes them together, through the
 * platform's own patterns rather than a name and a number put side by side.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MonthYearTest {

    private val saved = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(saved)

    private fun said(tag: String): String {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return Format(UserPreferences()).formatMonthYear(LocalDate.of(2026, 10, 3))
    }

    @Test
    fun `each language writes the month and year its own way`() {
        assertEquals("October 2026", said("en-GB"))
        // The nominative month, not the genitive a date takes ("октября").
        assertEquals("октябрь 2026\u202fг.", said("ru"))
        assertEquals("2026年10月", said("ja"))
        assertEquals("2026. október", said("hu"))
    }
}
