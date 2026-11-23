// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.newpost

import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.util.languageName

/** The order a post's languages are offered in: by letter, not by code point. */
class PostLanguagesTest {

    private val names = postLanguageChoices().map { it.second }

    /** Asserts the language [first] is offered ahead of [second], each by its tag. */
    private fun before(first: String, second: String) {
        val a = names.indexOf(languageName(first))
        val b = names.indexOf(languageName(second))
        assertTrue("$first and $second are offered", a >= 0 && b >= 0)
        assertTrue("${languageName(first)} comes before ${languageName(second)} in $names", a < b)
    }

    @Test
    fun `an accented name sits under its letter`() {
        // THE POINT: by code point "Čeština" and "Íslenska" fell after "Polski".
        before("cs", "da")
        before("is", "it")
        before("is", "pl")
    }

    @Test
    fun `names in other scripts follow the Latin ones`() {
        before("pl", "ru")
        before("pl", "ar")
    }
}
