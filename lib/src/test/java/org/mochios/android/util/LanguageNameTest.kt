// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One helper names languages for the preferences, the system documents and the forums composer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LanguageNameTest {

    /** The system documents' copy had lost its accents: "Espanol (Espana)". */
    @Test
    fun `a language Mochi words its own way is named as the web names it`() {
        assertEquals("Español (España)", languageName("es"))
        assertEquals("Español (latinoamericano)", languageName("es-419"))
        assertEquals("English (international)", languageName("en"))
        assertEquals("English (USA)", languageName("EN-US"))
    }

    @Test
    fun `any other language is named in itself`() {
        assertEquals("Français", languageName("fr"))
        assertEquals("Deutsch", languageName("de"))
    }

    /** The system documents' copy sorted by lower-cased code point, which put "Íslenska" after "Italiano". */
    @Test
    fun `the choices list Latin-script names first, each part by letter, each tag once`() {
        val names = languageChoices(listOf("ru", "da", "cs", "ar", "da", "is", "it")).map { it.second }
        assertEquals(6, names.size)
        assertTrue(names.indexOf(languageName("cs")) < names.indexOf(languageName("da")))
        assertTrue(names.indexOf(languageName("is")) < names.indexOf(languageName("it")))
        assertTrue(names.indexOf(languageName("it")) < names.indexOf(languageName("ru")))
        assertTrue(names.indexOf(languageName("it")) < names.indexOf(languageName("ar")))
    }
}
