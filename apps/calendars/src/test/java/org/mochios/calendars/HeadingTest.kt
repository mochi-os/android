// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.calendars.ui.calendar.heading
import java.time.LocalDate
import java.util.Locale

/**
 * The week view's column header: the weekday and the day on one line, in
 * whichever order the locale's pattern puts them.
 */
class HeadingTest {

    private val DAY: LocalDate = LocalDate.of(2026, 9, 22)

    @Test
    fun `weekday before the day under a British pattern`() {
        assertEquals("Tue 22", heading(DAY, "EEE d", Locale.UK))
    }

    @Test
    fun `day before the weekday under an American pattern`() {
        assertEquals("22 Tue", heading(DAY, "d EEE", Locale.US))
    }

    @Test
    fun `the weekday name follows the locale`() {
        assertEquals("mar. 22", heading(DAY, "EEE d", Locale.FRANCE))
    }
}
