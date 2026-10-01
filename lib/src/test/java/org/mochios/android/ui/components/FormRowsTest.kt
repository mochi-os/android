// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormRowsTest {

    private data class Field(val id: String, val type: String)

    private val title = Field("title", "text")
    private val description = Field("description", "text")
    private val status = Field("status", "enumerated")
    private val category = Field("category", "enumerated")
    private val owner = Field("owner", "user")
    private val due = Field("due", "date")

    private fun rows(fields: List<Field>, pairs: Boolean = true): List<FormRow<Field>> =
        formRows(fields, title = { it.id == "title" }, short = { shortField(it.type) }, pairs = pairs)

    @Test
    fun `a ticket's form is its title, its description, then its short fields two to a row`() {
        assertEquals(
            listOf(
                FormRow.Heading(title),
                FormRow.Wide(description),
                FormRow.Paired(status, category),
                FormRow.Paired(owner, due),
            ),
            rows(listOf(title, description, status, category, owner, due)),
        )
    }

    @Test
    fun `the title leads as the heading wherever the class ranks it`() {
        assertEquals(
            listOf(FormRow.Heading(title), FormRow.Paired(status, owner)),
            rows(listOf(status, title, owner)),
        )
    }

    @Test
    fun `a class with no title has no heading`() {
        assertEquals(listOf(FormRow.Wide(description), FormRow.Wide(status)), rows(listOf(description, status)))
    }

    @Test
    fun `a short field shares a row only with a short field directly after it`() {
        assertEquals(
            listOf(FormRow.Wide(status), FormRow.Wide(description), FormRow.Wide(owner)),
            rows(listOf(status, description, owner)),
        )
    }

    @Test
    fun `an odd short field out takes a row to itself`() {
        assertEquals(
            listOf(FormRow.Paired(status, category), FormRow.Wide(owner)),
            rows(listOf(status, category, owner)),
        )
    }

    @Test
    fun `with no room for pairs every field takes a row to itself`() {
        assertEquals(
            listOf(FormRow.Heading(title), FormRow.Wide(status), FormRow.Wide(category)),
            rows(listOf(title, status, category), pairs = false),
        )
    }

    @Test
    fun `choices, people, dates, numbers and ticks are short and text is not`() {
        for (type in listOf("enumerated", "user", "date", "number", "checkbox")) {
            assertTrue(type, shortField(type))
        }
        for (type in listOf("text", "checklist", "")) {
            assertFalse(type, shortField(type))
        }
    }

    @Test
    fun `a phone has room for pairs at the default text size and not at a large one`() {
        // A 384dp phone less the form's 16dp side padding.
        assertTrue(formPairs(352.dp, 1f))
        assertFalse(formPairs(352.dp, 1.3f))
        assertFalse(formPairs(280.dp, 1f))
    }

    @Test
    fun `a line break in a one-line value becomes a space`() {
        assertEquals("one two three four", oneLine("one\ntwo\r\nthree\rfour"))
        assertEquals("untouched", oneLine("untouched"))
    }
}
