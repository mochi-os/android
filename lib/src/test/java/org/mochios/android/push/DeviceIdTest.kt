// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device id is minted here and validated by the server; the two must agree
 * on its shape or every registration is refused.
 */
class DeviceIdTest {

    @Test
    fun `a minted id is within the server's alphabet and bounds`() {
        repeat(20) {
            val id = mintDeviceId()
            assertTrue(id, deviceIdValid(id))
        }
    }

    @Test
    fun `ids the server would refuse are refused here`() {
        assertFalse(deviceIdValid(""))
        assertFalse(deviceIdValid("short"))
        assertFalse(deviceIdValid("has a space"))
        assertFalse(deviceIdValid("under_score-0001"))
        assertFalse(deviceIdValid("x".repeat(65)))
        assertTrue(deviceIdValid("x".repeat(64)))
        assertTrue(deviceIdValid("phone-0001"))
    }
}
