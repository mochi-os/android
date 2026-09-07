// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.util

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
        assertEquals(",task", hierarchyParameter(listOf(HIERARCHY_ROOT, "task")))
        assertEquals("epic,task", hierarchyParameter(listOf("epic", "task")))
    }
}
