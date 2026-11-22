// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** An avatar's initials take whole characters, never half a surrogate pair. */
class InitialsTest {

    @Test
    fun `two words beginning outside the Basic Multilingual Plane give both whole`() {
        assertEquals("😀🎉", initialsOf("😀Smile 🎉Party"))
        assertEquals("😀S", initialsOf("😀 smile"))
        assertEquals("𠀋A", initialsOf("𠀋家 ada"))
    }

    @Test
    fun `letters are upper-cased and blank names show a question mark`() {
        assertEquals("AL", initialsOf("ada lovelace"))
        assertEquals("A", initialsOf("ada"))
        assertEquals("?", initialsOf("  "))
    }
}
