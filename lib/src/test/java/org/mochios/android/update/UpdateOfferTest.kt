// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Whether a staged APK is offered on resume. The installer used to be launched
 * for it unasked; now the offer is a dialog, and a declined version stays
 * quiet until a newer one is staged.
 */
class UpdateOfferTest {
    private fun offer(pending: String?, current: String?, promptedFor: String = "", force: Boolean = false) =
        UpdateInstaller.offer(pending, current, promptedFor, force)

    @Test
    fun `nothing staged offers nothing`() {
        assertNull(offer(null, "1.4"))
        assertNull(offer("", "1.4"))
    }

    @Test
    fun `a staged version no newer than the running one offers nothing`() {
        assertNull(offer("1.4", "1.4"))
        assertNull(offer("1.3", "1.4"))
    }

    @Test
    fun `a newer staged version is offered`() {
        assertEquals("1.5", offer("1.5", "1.4"))
        // An unknown running version never blocks the offer.
        assertEquals("1.5", offer("1.5", null))
    }

    @Test
    fun `a declined version is not offered again`() {
        assertNull(offer("1.5", "1.4", promptedFor = "1.5"))
    }

    @Test
    fun `a newer stage than the declined one is offered`() {
        assertEquals("1.6", offer("1.6", "1.4", promptedFor = "1.5"))
    }

    @Test
    fun `the About dialog's explicit ask overrides a decline`() {
        assertEquals("1.5", offer("1.5", "1.4", promptedFor = "1.5", force = true))
    }
}
