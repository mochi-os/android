// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The server's `language` preference reaches the app as the web stores it.
 * "auto" and an ill-formed tag used to be pinned as the app's locale, and the
 * same tag was re-applied on every refresh over a language the user had
 * picked for the app in the system settings.
 */
class LanguageTagTest {

    @Test
    fun `the web's automatic setting means the device's language`() {
        assertNull(languageTag("auto"))
        assertNull(languageTag("AUTO"))
    }

    @Test
    fun `no preference means the device's language`() {
        assertNull(languageTag(null))
        assertNull(languageTag(""))
        assertNull(languageTag("  "))
    }

    @Test
    fun `a well-formed tag is kept as given`() {
        assertEquals("vi", languageTag("vi"))
        assertEquals("pt-BR", languageTag("pt-BR"))
        assertEquals("zh-Hant", languageTag("zh-Hant"))
    }

    @Test
    fun `an ill-formed tag is not pinned as the root locale`() {
        assertNull(languageTag("en_US"))
    }

    @Test
    fun `a first tag and a changed tag are applied`() {
        assertEquals(LanguageUpdate("vi", apply = true), languageUpdate(null, "vi"))
        assertEquals(LanguageUpdate("en", apply = true), languageUpdate("vi", "en"))
        assertEquals(LanguageUpdate(null, apply = true), languageUpdate("vi", "auto"))
    }

    @Test
    fun `the same tag again is stored but not re-applied`() {
        assertEquals(LanguageUpdate("vi", apply = false), languageUpdate("vi", "vi"))
        assertEquals(LanguageUpdate(null, apply = false), languageUpdate(null, "auto"))
    }

    @Test
    fun `a raw automatic stored by an older build is released`() {
        assertEquals(LanguageUpdate(null, apply = true), languageUpdate("auto", "auto"))
    }
}
