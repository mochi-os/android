// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Languages by their own names: Latin script first, each part by letter, accents and case aside. */
class LanguageCompareTest {

    @Test
    fun `Latin names come first, each under its letter, then the other scripts`() {
        val names = listOf("Русский", "Zulu", "العربية", "íslenska", "Čeština", "English", "dansk")
        assertEquals(
            listOf("Čeština", "dansk", "English", "íslenska", "Zulu", "Русский", "العربية").take(5),
            names.sortedWith(LanguageCompare).take(5),
        )
        assertEquals(setOf("Русский", "العربية"), names.sortedWith(LanguageCompare).drop(5).toSet())
    }

    @Test
    fun `a name is bucketed by its first letter's script`() {
        assertEquals(0, scriptBucket("Čeština"))
        assertEquals(0, scriptBucket("(English)"))
        assertEquals(1, scriptBucket("Русский"))
        assertEquals(1, scriptBucket("العربية"))
    }
}
