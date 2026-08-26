// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.projects.model.FieldOption

/**
 * compareFieldValues orders custom-field values for the object list. Before
 * this existed the comparator was `lowercase().compareTo()` on every type, so
 * numbers sorted as text, dates sorted as text, enumerated values sorted by
 * their opaque option id, and accents landed after "z".
 */
class SortFieldValuesTest {

    private fun compare(
        a: String,
        b: String,
        fieldtype: String? = null,
        aOption: FieldOption? = null,
        bOption: FieldOption? = null,
        multiplier: Int = 1,
    ) = compareFieldValues(a, b, fieldtype, aOption, bOption, multiplier)

    @Test
    fun `a number field compares numerically, not as text`() {
        // The whole point: as text, "10" precedes "9".
        assertTrue(compare("9", "10", fieldtype = "number") < 0)
        assertTrue(compare("10", "9", fieldtype = "number") > 0)
        assertEquals(0, compare("7", "7", fieldtype = "number"))
    }

    @Test
    fun `a number field falls back to text when a value is not a number`() {
        // Neither parses, so the text comparator runs — and it is the natural
        // one: as plain text "item 10" would precede "Item 2".
        assertTrue(compare("Item 2", "item 10", fieldtype = "number") < 0)
    }

    @Test
    fun `a date field compares by its epoch key, not as text`() {
        // Epoch seconds: as text "1000" precedes "999", by date it does not.
        assertTrue(compare("999", "1000", fieldtype = "date") < 0)
        assertTrue(compare("1000", "999", fieldtype = "date") > 0)
        // ISO dates parse too. (They happen to sort the same way as text,
        // which is why the epoch pair above is what proves the parsing runs.)
        assertTrue(compare("2026-01-09", "2026-02-01", fieldtype = "date") < 0)
    }

    @Test
    fun `an enumerated field compares on the designer's rank, not the option id`() {
        // Ids are opaque and their alphabetical order is meaningless: "zeta"
        // is rank 1 and must precede "alpha" at rank 2.
        val first = FieldOption(id = "zeta", name = "Backlog", rank = 1)
        val second = FieldOption(id = "alpha", name = "Done", rank = 2)
        assertTrue(compare("zeta", "alpha", fieldtype = "enumerated", aOption = first, bOption = second) < 0)
    }

    @Test
    fun `an enumerated field falls back to the option name when one is unresolved`() {
        val known = FieldOption(id = "x", name = "Alpha", rank = 9)
        assertTrue(compare("x", "Beta", fieldtype = "enumerated", aOption = known, bOption = null) < 0)
    }

    @Test
    fun `text compares accent- and case-blind and numeric-aware`() {
        assertEquals(0, compare("Cafe", "cafe"))
        assertEquals(0, compare("café", "cafe"))
        // Numeric-aware: "Sprint 2" precedes "Sprint 10".
        assertTrue(compare("Sprint 2", "Sprint 10") < 0)
    }

    @Test
    fun `blank values sink to the bottom in both directions`() {
        // The blank branch returns before the multiplier, so descending does
        // not float the empties to the top.
        assertTrue(compare("", "value") > 0)
        assertTrue(compare("value", "") < 0)
        assertTrue(compare("", "value", multiplier = -1) > 0)
        assertTrue(compare("value", "", multiplier = -1) < 0)
        assertEquals(0, compare("", ""))
    }

    @Test
    fun `the multiplier reverses a non-blank comparison`() {
        assertTrue(compare("9", "10", fieldtype = "number", multiplier = -1) > 0)
        assertTrue(compare("apple", "banana", multiplier = -1) > 0)
    }
}
