// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Two callers derive an address from what the user typed - a wiki page from
 * its title, a project from its name - and a change here moves both.
 */
class SlugTest {

    @Test
    fun `a title becomes a page address`() {
        assertEquals("my-great-page", slugify("My Great Page"))
    }

    /** Runs of anything unusable collapse to one hyphen, and the ends stay clean. */
    @Test
    fun `punctuation collapses and the ends are trimmed`() {
        assertEquals("spaced", slugify("  --spaced-- "))
        assertEquals("a-b", slugify("a  ///  b"))
        assertEquals("", slugify("!!!"))
    }

    /** The example the projects create form documents, held to character for character. */
    @Test
    fun `a prefix is capped without ending on a hyphen`() {
        assertEquals("android-project-test", slugify("Android project Testing Name", 20))
        // The cut would land on the separator, so it is trimmed back off.
        assertEquals("one-two", slugify("one two three", 8))
    }

    /** An accent belongs to its letter, so the word survives rather than splitting. */
    @Test
    fun `accents fold to the letter underneath`() {
        assertEquals("cafe-unicode", slugify("Café Ünïcode"))
        assertEquals("deja-vu", slugify("déjà vu"))
        assertEquals("cafe-", slugifyPartial("Café "))
    }

    /** A letter whose mark is part of the glyph has no ASCII to fall back to. */
    @Test
    fun `a letter NFKD cannot split still drops out`() {
        assertEquals("rsted", slugify("Ørsted"))
    }

    /**
     * While typing, a trailing hyphen is the separator the user is part way
     * through - trimming it every keystroke makes "my-page" unreachable.
     */
    @Test
    fun `a partial slug keeps the hyphen being typed`() {
        assertEquals("my-", slugifyPartial("my "))
        assertEquals("my-page", slugifyPartial("my page"))
        assertEquals("page", slugifyPartial("  page"))
    }
}
