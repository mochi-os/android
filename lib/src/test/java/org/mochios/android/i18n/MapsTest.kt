// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

/** The `maps` preference as the server sends it: a known service, or the default. */
class MapsTest {
    @Test
    fun aKnownServiceIsRead() {
        assertEquals(Maps.GOOGLE, Maps.fromString("google"))
        assertEquals(Maps.OPENSTREETMAP, Maps.fromString("openstreetmap"))
    }

    @Test
    fun anythingElseIsTheDefault() {
        assertEquals(Maps.OPENSTREETMAP, Maps.fromString(null))
        assertEquals(Maps.OPENSTREETMAP, Maps.fromString(""))
        assertEquals(Maps.OPENSTREETMAP, Maps.fromString("bing"))
    }
}
