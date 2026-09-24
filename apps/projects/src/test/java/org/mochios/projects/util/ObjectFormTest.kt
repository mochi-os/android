// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.ProjectField

/**
 * The create form's values follow the web dialog: a class takes only the
 * values it can hold, a board column's preset lands, and a required
 * enumerated field opens on its first option.
 */
class ObjectFormTest {
    private val title = ProjectField(id = "title", name = "Title", fieldtype = "text")
    private val status = ProjectField(id = "status", name = "Status", fieldtype = "enumerated", flags = "required")
    private val priority = ProjectField(id = "priority", name = "Priority", fieldtype = "enumerated")
    private val owner = ProjectField(id = "owner", name = "Owner", fieldtype = "user", flags = "required")
    private val fields = listOf(title, status, priority)
    private val options = mapOf(
        // Ranked out of list order, so the seed has to look at the rank.
        "status" to listOf(FieldOption(id = "done", name = "Done", rank = 2), FieldOption(id = "todo", name = "To do", rank = 1)),
        "priority" to listOf(FieldOption(id = "high", name = "High", rank = 1)),
    )

    @Test
    fun `a required enumerated field left blank opens on its first option`() {
        val values = formValues(fields, options, emptyMap(), emptyMap())
        assertEquals("todo", values["status"])
        // Not required: stays blank for the user to choose.
        assertFalse("priority" in values)
    }

    @Test
    fun `a board column's preset lands and is not overridden by the seed`() {
        val values = formValues(fields, options, emptyMap(), mapOf("status" to "done"))
        assertEquals("done", values["status"])
    }

    @Test
    fun `an enumerated value that is not one of the class's options is dropped`() {
        // A mixed-class board hands over another class's option id.
        val values = formValues(fields, options, mapOf("status" to "elsewhere"), mapOf("priority" to "nosuch"))
        assertEquals("todo", values["status"])
        assertFalse("priority" in values)
    }

    @Test
    fun `values the class takes survive a class change, values it lacks do not`() {
        val previous = mapOf("title" to "Login fails", "estimate" to "3")
        val values = formValues(fields, options, previous, emptyMap())
        assertEquals("Login fails", values["title"])
        assertFalse("estimate" in values)
    }

    @Test
    fun `a board's column field is seeded with its first option when no caller named one`() {
        val values = formValues(fields, options, emptyMap(), emptyMap(), column = "priority")
        assertEquals("high", values["priority"])
        // A column field this class does not have seeds nothing.
        assertFalse("lane" in formValues(fields, options, emptyMap(), emptyMap(), column = "lane"))
    }

    @Test
    fun `create waits for every required field, not for the optional ones`() {
        assertFalse(requiredFilled(listOf(title, status, owner), mapOf("status" to "todo")))
        assertTrue(requiredFilled(listOf(title, status, owner), mapOf("status" to "todo", "owner" to "abc")))
        assertTrue(requiredFilled(listOf(title, priority), emptyMap()))
    }

    @Test
    fun `a required enumerated field with no options is named as unsatisfiable`() {
        val unfillable = unsatisfiable(listOf(status, owner, priority), emptyMap())
        assertEquals(listOf("status"), unfillable.map { field -> field.id })
        assertTrue(unsatisfiable(listOf(status), options).isEmpty())
    }
}
