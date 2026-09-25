// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectObject

class ObjectTitleTest {
    private val ticket = ProjectClass(id = "ticket", name = "Ticket", title = "title")
    private val classes = listOf(ticket)

    // An object as objects/list sends it: an opaque id, no readable id.
    private fun listed(number: Int, title: String = "") = ProjectObject(
        id = "01a0cf7164eb719c89ecd8c6d44e779d",
        objectClass = "ticket",
        number = number,
        values = mapOf("title" to title),
    )

    @Test
    fun `the class's title value names the object`() {
        assertEquals("Login fails on Safari", objectTitle(listed(12, "Login fails on Safari"), classes, "MD"))
    }

    @Test
    fun `an object from the list with no title reads as prefix and number, never as its id`() {
        val title = objectTitle(listed(7), classes, "MD")
        assertEquals("MD-7", title)
        assertFalse(title.contains("01a0cf71"))
    }

    @Test
    fun `the server's readable id wins over one rebuilt from the prefix`() {
        val fetched = listed(7).copy(readable = "MD-7")
        assertEquals("MD-7", objectTitle(fetched, classes, "OTHER"))
    }

    @Test
    fun `a project with no prefix numbers the object`() {
        assertEquals("#7", objectTitle(listed(7), classes, ""))
    }

    @Test
    fun `a class with no title field falls through to the number`() {
        val untitled = listOf(ProjectClass(id = "ticket", name = "Ticket"))
        assertEquals("MD-7", objectTitle(listed(7, "ignored"), untitled, "MD"))
    }
}
