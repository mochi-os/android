// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiscardGuardTest {

    @Test
    fun `an action with nothing unsaved happens at once`() {
        val guard = DiscardGuard()
        var done = 0
        guard.request(dirty = false) { done++ }
        assertEquals(1, done)
        assertNull(guard.pending)
    }

    /** Leaving an editor, or switching what it shows, used to drop its unsaved text without asking. */
    @Test
    fun `an action with unsaved edits waits for an answer`() {
        val guard = DiscardGuard()
        var done = 0
        guard.request(dirty = true) { done++ }
        assertEquals(0, done)
        assertNotNull(guard.pending)
    }

    @Test
    fun `discarding runs the held action`() {
        val guard = DiscardGuard()
        var done = 0
        guard.request(dirty = true) { done++ }
        guard.discard()
        assertEquals(1, done)
        assertNull(guard.pending)
    }

    @Test
    fun `keeping the edits drops the held action`() {
        val guard = DiscardGuard()
        var done = 0
        guard.request(dirty = true) { done++ }
        guard.keep()
        guard.discard()
        assertEquals(0, done)
        assertNull(guard.pending)
    }
}
