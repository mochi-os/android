// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HierarchyTest {
    @Test
    fun `no parents at all is spelt with the server's sentinel`() {
        assertEquals("_none_", hierarchyParameter(emptyList()))
    }

    @Test
    fun `root only is the empty entry`() {
        assertEquals("", hierarchyParameter(listOf(HIERARCHY_ROOT)))
    }

    @Test
    fun `parents and root are joined`() {
        assertEquals(",contact", hierarchyParameter(listOf(HIERARCHY_ROOT, "contact")))
        assertEquals("company,contact", hierarchyParameter(listOf("company", "contact")))
    }
}
