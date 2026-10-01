// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemsettings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.settings.api.SystemTheme

class ThemePickerTest {

    private val themes = listOf(
        SystemTheme(id = "app1:zinc", label = "zinc"),
        SystemTheme(id = "app1:blue", label = "Blue"),
        SystemTheme(id = "app1:amber", label = "Amber"),
    )

    @Test
    fun `a theme is named by its label, never its entity id`() {
        assertEquals("Blue", themeLabel(themes, "app1:blue"))
    }

    @Test
    fun `an id no installed theme has names nothing rather than itself`() {
        assertNull(themeLabel(themes, "app9:gone"))
    }

    @Test
    fun `the picker lists themes by label, ignoring case`() {
        assertEquals(
            listOf("Amber", "Blue", "zinc"),
            themeOrder(themes).map { it.label },
        )
    }
}
