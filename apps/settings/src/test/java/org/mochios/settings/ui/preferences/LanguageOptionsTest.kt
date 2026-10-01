// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageOptionsTest {

    private fun values(tags: List<String>, current: String): List<String> =
        languageOptions(tags = tags, current = current, defaultLabel = "Auto").map { it.first }

    /** The server skips a blank value, so a blank Auto left a chosen language in place. */
    @Test
    fun `the automatic choice saves as auto`() {
        assertEquals("auto" to "Auto", languageOptions(listOf("en", "fr"), "fr", "Auto").first())
    }

    /** The web stores "auto"; it is the automatic choice, not a language to list. */
    @Test
    fun `a stored auto is not listed as a language`() {
        assertEquals(3, values(listOf("en", "fr"), "auto").size)
    }

    @Test
    fun `a stored language the server does not list stays`() {
        assertTrue("de" in values(listOf("en", "fr"), "de"))
    }

    @Test
    fun `a blank stored value adds nothing`() {
        assertEquals(3, values(listOf("en", "fr"), "").size)
    }
}
