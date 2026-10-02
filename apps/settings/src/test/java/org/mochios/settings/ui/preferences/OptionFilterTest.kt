// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class OptionFilterTest {

    private val languages = listOf(
        "auto" to "Automatic",
        "de" to "Deutsch",
        "es" to "Español (España)",
        "fr" to "Français",
        "ru" to "Русский",
    )

    private fun values(query: String) = optionFilter(languages, query).map { it.first }

    @Test
    fun `a blank query lists everything`() {
        assertEquals(languages.map { it.first }, values("  "))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf("de"), values("DEUT"))
    }

    @Test
    fun `matching ignores accents`() {
        assertEquals(listOf("es"), values("espanol"))
        assertEquals(listOf("fr"), values("francais"))
    }

    @Test
    fun `an accented query finds the plain label`() {
        assertEquals(listOf("de"), values("déutsch"))
    }

    @Test
    fun `matching finds text inside a label`() {
        assertEquals(listOf("es"), values("españa"))
    }

    @Test
    fun `non-Latin labels match`() {
        assertEquals(listOf("ru"), values("русс"))
    }

    @Test
    fun `nothing matches an unknown query`() {
        assertEquals(emptyList<String>(), values("klingon"))
    }
}
