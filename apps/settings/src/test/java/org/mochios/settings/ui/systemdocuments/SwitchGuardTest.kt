// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemdocuments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SwitchGuardTest {

    @Test
    fun `a switch with nothing unsaved happens at once`() {
        val guard = SwitchGuard()
        var switched = 0
        guard.request(dirty = false) { switched++ }
        assertEquals(1, switched)
        assertNull(guard.pending)
    }

    /** A tab or language switch used to drop the editor's unsaved text without asking. */
    @Test
    fun `a switch with unsaved edits waits for an answer`() {
        val guard = SwitchGuard()
        var switched = 0
        guard.request(dirty = true) { switched++ }
        assertEquals(0, switched)
        assertNotNull(guard.pending)
    }

    @Test
    fun `discarding makes the held switch`() {
        val guard = SwitchGuard()
        var switched = 0
        guard.request(dirty = true) { switched++ }
        guard.discard()
        assertEquals(1, switched)
        assertNull(guard.pending)
    }

    @Test
    fun `keeping the edits drops the held switch`() {
        val guard = SwitchGuard()
        var switched = 0
        guard.request(dirty = true) { switched++ }
        guard.keep()
        guard.discard()
        assertEquals(0, switched)
        assertNull(guard.pending)
    }
}
