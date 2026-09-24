// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** The city a zone reads as beside a time, the same as the web shows it. */
class ZonesTest {

    @Test
    fun `the city is the part after the last slash, with spaces for underscores`() {
        assertEquals("New York", zoneCity("America/New_York"))
        assertEquals("London", zoneCity("Europe/London"))
    }

    @Test
    fun `a nested region keeps only its last part`() {
        assertEquals("Buenos Aires", zoneCity("America/Argentina/Buenos_Aires"))
    }

    @Test
    fun `a sea zone reads as its offset from UTC, the sign the right way round`() {
        assertEquals("UTC+8", zoneCity("Etc/GMT-8"))
        assertEquals("UTC-10", zoneCity("Etc/GMT+10"))
        assertEquals("UTC", zoneCity("Etc/GMT"))
        assertEquals("UTC+1", zoneCity("Etc/GMT-1"))
        assertEquals("UTC", zoneCity("Etc/GMT+0"))
    }

    @Test
    fun `a zone with no slash is its own label`() {
        assertEquals("UTC", zoneCity("UTC"))
    }
}
