// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Each server theme picks its own icon colour, and a theme the app does not
 * know picks the nearest one on the colour wheel.
 */
class LauncherTintTest {

    @Test
    fun `each server theme picks its own colour`() {
        assertEquals(IconTint.BLUE, LauncherTint.tintFor(250f, 0.135f))
        assertEquals(IconTint.ROSE, LauncherTint.tintFor(350f, 0.135f))
        assertEquals(IconTint.STEEL, LauncherTint.tintFor(250f, 0.055f))
        assertEquals(IconTint.TEAL, LauncherTint.tintFor(185f, 0.09f))
        assertEquals(IconTint.TERRACOTTA, LauncherTint.tintFor(40f, 0.15f))
    }

    @Test
    fun `a muted blue is steel and a vivid one is blue`() {
        assertEquals(IconTint.STEEL, LauncherTint.tintFor(255f, 0.06f))
        assertEquals(IconTint.BLUE, LauncherTint.tintFor(245f, 0.12f))
    }

    @Test
    fun `an unknown theme gets the nearest colour, measured across 0`() {
        assertEquals(IconTint.ROSE, LauncherTint.tintFor(5f, 0.14f))
        assertEquals(IconTint.TERRACOTTA, LauncherTint.tintFor(60f, 0.15f))
        assertEquals(IconTint.TEAL, LauncherTint.tintFor(160f, 0.12f))
    }

    @Test
    fun `a grey theme gets the most muted colour`() {
        assertEquals(IconTint.STEEL, LauncherTint.tintFor(0f, 0f))
    }
}
