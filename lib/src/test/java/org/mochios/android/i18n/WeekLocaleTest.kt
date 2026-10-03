// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/** The date picker's locale starts the week where the user does and keeps their language. */
class WeekLocaleTest {

    private val german = Locale.GERMANY

    @Test
    fun `the week starts where the user's does`() {
        assertEquals(DayOfWeek.SUNDAY, WeekFields.of(weekLocale(0, german)).firstDayOfWeek)
        assertEquals(DayOfWeek.MONDAY, WeekFields.of(weekLocale(1, Locale.US)).firstDayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, WeekFields.of(weekLocale(6, german)).firstDayOfWeek)
    }

    @Test
    fun `the user's language stays, so the months and weekdays read in it`() {
        for (day in listOf(0, 1, 6)) {
            assertEquals("de", weekLocale(day, german).language)
        }
        assertEquals("ja", weekLocale(1, Locale.JAPAN).language)
        assertEquals("Hant", weekLocale(0, Locale.forLanguageTag("zh-Hant-TW")).script)
    }

    @Test
    fun `a week start no region gives keeps the user's locale`() {
        assertEquals(german, weekLocale(3, german))
    }
}
