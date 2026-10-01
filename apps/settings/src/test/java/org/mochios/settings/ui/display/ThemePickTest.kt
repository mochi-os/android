// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemePickTest {

    @Test
    fun `tapping the chosen theme clears the choice`() {
        assertNull(themePick(picked = "app1:blue", chosen = "app1:blue"))
    }

    @Test
    fun `tapping another theme chooses it`() {
        assertEquals("app1:amber", themePick(picked = "app1:amber", chosen = "app1:blue"))
    }

    /**
     * With no theme chosen the server sends "" rather than its default, so
     * the default's card is an ordinary choice, not one to clear.
     */
    @Test
    fun `with no theme chosen every card chooses`() {
        assertEquals("app1:blue", themePick(picked = "app1:blue", chosen = ""))
    }
}
