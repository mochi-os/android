// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseTest {
    @Test
    fun generatesTenWordsFromTheSharedList() {
        val words = Passphrase.generate().split(" ")
        assertEquals(Passphrase.WORDS, words.size)
        assertTrue(words.all { it in Passphrase.LIST })
        assertEquals(248, Passphrase.LIST.size)
    }

    @Test
    fun twoPhrasesDiffer() {
        assertNotEquals(Passphrase.generate(), Passphrase.generate())
    }

    @Test
    fun floorCountsCodePoints() {
        assertFalse(Passphrase.long("a".repeat(11)))
        assertTrue(Passphrase.long("a".repeat(12)))
        // Twelve code points that are 36 UTF-8 bytes and 12 UTF-16 units.
        assertTrue(Passphrase.long("日本語日本語日本語日本語"))
        // Six code points that are 12 UTF-16 units: not long enough.
        assertFalse(Passphrase.long("😀".repeat(6)))
        assertTrue(Passphrase.long(Passphrase.generate()))
    }
}
