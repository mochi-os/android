// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fingerprint is derived from an entity id as core derives it. */
class FingerprintTest {

    private val id = "12C83B6Phg9Ap8bK58uqrzWKBYRXAo9pSzsHzSJnEsAnCnRpdob"

    @Test
    fun `an id's fingerprint is core's`() {
        // core's fingerprint() of the same id, utilities.go.
        assertEquals("6MFgfL93a", fingerprint(id))
    }

    @Test
    fun `a fingerprint is its own, not hashed again`() {
        assertEquals("6MFgfL93a", fingerprint("6MFgfL93a"))
    }

    @Test
    fun `a blank id has no fingerprint`() {
        assertEquals("", fingerprint(""))
        assertEquals("", fingerprint("  "))
    }

    @Test
    fun `a fingerprint is told from an id`() {
        assertTrue(isFingerprint("6MFgfL93a"))
        assertFalse(isFingerprint(id))
        // Nine characters, but 0, 1, I, O and l are not core's letters.
        assertFalse(isFingerprint("6MFgfL930"))
        assertFalse(isFingerprint("6MFgfL93l"))
    }
}
