// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The text fallback of a sorted view must order like web's naturalCompare:
// numeric-aware and accent-insensitive, not a bare case-folded compareTo.
class TextCompareTest {
    @Test
    fun `numbers inside text order numerically`() {
        assertTrue(textCompare("Sprint 2", "Sprint 10") < 0)
    }

    @Test
    fun `accents do not push a name to the end`() {
        assertTrue(textCompare("café", "cage") < 0)
    }

    @Test
    fun `case is ignored`() {
        assertEquals(0, textCompare("Acme", "ACME"))
    }
}
