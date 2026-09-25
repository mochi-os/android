// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** The boot request names the zone this device keeps time in. */
class ShellRequestTest {

    @Test
    fun `the boot request carries the device zone`() {
        val before = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals("Asia/Tokyo", shellRequest().timezone)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals("America/New_York", shellRequest().timezone)
        } finally {
            TimeZone.setDefault(before)
        }
    }
}
