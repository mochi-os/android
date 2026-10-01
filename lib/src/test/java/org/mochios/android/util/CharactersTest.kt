// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CharactersTest {

    @Test
    fun `text within the limit is kept whole`() {
        assertEquals("Work", characters("Work", 100))
    }

    @Test
    fun `text past the limit is cut to it`() {
        assertEquals("a".repeat(100), characters("a".repeat(150), 100))
    }

    @Test
    fun `letters of any script count one each, as the server counts them`() {
        val cyrillic = "ж".repeat(100)
        assertEquals(cyrillic, characters(cyrillic, 100))
    }

    @Test
    fun `an emoji counts as one character and is never split`() {
        val text = "a".repeat(99) + "📅" + "b"
        val cut = characters(text, 100)
        assertEquals("a".repeat(99) + "📅", cut)
        assertEquals(100, cut.codePointCount(0, cut.length))
    }
}
