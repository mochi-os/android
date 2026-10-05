// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phone's own list of zones, which the zone picker offers: the same
 * zones of places ICU lists as canonical locations, read through public
 * calls Android has had since API 24.
 */
@RunWith(RobolectricTestRunner::class)
class ZonesPlatformTest {

    @Test
    @Config(sdk = [35])
    fun `the zones of places are the ones ICU lists as canonical locations`() {
        val canonical = android.icu.util.TimeZone.getAvailableIDs(
            android.icu.util.TimeZone.SystemTimeZoneType.CANONICAL_LOCATION,
            null,
            null,
        )
        assertEquals(canonical.toSortedSet(), Zones.Platform.places().toSortedSet())
    }
}
