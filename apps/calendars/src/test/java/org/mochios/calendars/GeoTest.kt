// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.calendars.ui.calendar.geo

/** The event sheet's location tap: a `geo:` search the phone's map app answers. */
class GeoTest {

    @Test
    fun `a location becomes a geo search`() {
        assertEquals("geo:0,0?q=Meeting%20room", geo("Meeting room"))
    }

    @Test
    fun `the location is trimmed and its punctuation encoded`() {
        assertEquals(
            "geo:0,0?q=12%20Example%20St%20%26%20Co%2C%20Springfield",
            geo("  12 Example St & Co, Springfield "),
        )
    }
}
