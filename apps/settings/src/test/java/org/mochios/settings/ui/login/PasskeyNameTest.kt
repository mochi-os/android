// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasskeyNameTest {

    /** The fallback is the reader's translation, never a hard-coded English word. */
    @Test
    fun `a blank name registers under the translated fallback`() {
        assertEquals("Clé d'accès", passkeyRegisterName("   ", "Clé d'accès"))
    }

    @Test
    fun `a given name registers trimmed`() {
        assertEquals("Laptop", passkeyRegisterName("  Laptop ", "Passkey"))
    }

    /** The server refuses a name over 255 code points. */
    @Test
    fun `a long name is cut to the server's maximum`() {
        assertEquals(PASSKEY_NAME_MAXIMUM, passkeyNameLimit("a".repeat(400)).length)
    }

    @Test
    fun `the cut never splits a surrogate pair`() {
        val cut = passkeyNameLimit("🔑".repeat(300))
        assertEquals(PASSKEY_NAME_MAXIMUM, cut.codePointCount(0, cut.length))
        assertEquals(PASSKEY_NAME_MAXIMUM * 2, cut.length)
    }

    @Test
    fun `a rename to blank sends nothing`() {
        assertNull(passkeyRename("   ", "Laptop"))
    }

    @Test
    fun `a rename to the current name sends nothing`() {
        assertNull(passkeyRename(" Laptop ", "Laptop"))
    }

    @Test
    fun `a rename to a new name sends it trimmed`() {
        assertEquals("Phone", passkeyRename(" Phone ", "Laptop"))
    }
}
