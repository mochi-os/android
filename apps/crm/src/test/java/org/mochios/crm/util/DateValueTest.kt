// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateValueTest {
    @Test
    fun `an epoch passes through`() {
        assertEquals(1_700_000_000L, dateSeconds("1700000000"))
        assertEquals(1_700_000_000L, dateSeconds("1700000000.0"))
    }

    @Test
    fun `an ISO date is midnight UTC of that day`() {
        assertEquals(1_704_067_200L, dateSeconds("2024-01-01"))
    }

    @Test
    fun `an ISO date-time keeps its offset`() {
        assertEquals(1_704_067_200L, dateSeconds("2024-01-01T01:00:00+01:00"))
        assertEquals(1_704_067_200L, dateSeconds("2024-01-01T00:00:00Z"))
    }

    @Test
    fun `anything else is not a date`() {
        assertNull(dateSeconds(""))
        assertNull(dateSeconds("next week"))
    }
}
