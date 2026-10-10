// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `background` preference as the server sends it: "theme", its default, or "off". */
class BackgroundTest {
    @Test
    fun offTurnsTheGlowOff() {
        assertFalse(backgroundOf("off"))
    }

    @Test
    fun theThemeAndAnUnsetPreferenceKeepIt() {
        assertTrue(backgroundOf("theme"))
        assertTrue(backgroundOf(null))
        assertTrue(backgroundOf(""))
    }
}
