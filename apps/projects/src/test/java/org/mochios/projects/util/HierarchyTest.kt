// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectObject

class HierarchyTest {
    // The tickets template: a task can only sit under a ticket.
    private val ticket = ProjectClass(id = "ticket", name = "Ticket")
    private val task = ProjectClass(id = "task", name = "Task")
    private val hierarchy = mapOf("ticket" to listOf(HIERARCHY_ROOT), "task" to listOf("ticket"))

    private fun ticketObject(id: String, number: Int, status: String = "") =
        ProjectObject(id = id, objectClass = "ticket", number = number, values = mapOf("status" to status))

    @Test
    fun `no parents at all is spelt with the server's sentinel`() {
        assertEquals("_none_", hierarchyParameter(emptyList()))
    }

    @Test
    fun `root only is the empty entry`() {
        assertEquals("", hierarchyParameter(listOf(HIERARCHY_ROOT)))
    }

    @Test
    fun `parents and root are joined`() {
        assertEquals(",task", hierarchyParameter(listOf(HIERARCHY_ROOT, "task")))
        assertEquals("epic,task", hierarchyParameter(listOf("epic", "task")))
    }

    @Test
    fun `a class that cannot sit at the top level needs a parent`() {
        assertTrue(parentRequired(hierarchy, "task"))
        assertFalse(parentRequired(hierarchy, "ticket"))
    }

    @Test
    fun `a class with no entries needs a parent`() {
        assertTrue(parentRequired(hierarchy, "missing"))
    }

    @Test
    fun `a class needing a parent is not creatable until one exists`() {
        assertEquals(listOf(ticket), creatableClasses(listOf(ticket, task), hierarchy, emptyList()))
    }

    @Test
    fun `a class needing a parent is creatable once one exists`() {
        val objects = listOf(ticketObject("a", 1))
        assertEquals(listOf(ticket, task), creatableClasses(listOf(ticket, task), hierarchy, objects))
    }

    @Test
    fun `a class with no entries is never creatable`() {
        val orphan = ProjectClass(id = "orphan", name = "Orphan")
        val objects = listOf(ticketObject("a", 1))
        assertEquals(listOf(ticket), creatableClasses(listOf(ticket, orphan), hierarchy, objects))
    }

    @Test
    fun `adding a child opens on a class that takes the parent`() {
        assertEquals("task", initialClass(listOf(ticket, task), hierarchy, "ticket", emptyList()))
    }

    @Test
    fun `a view class that cannot be created is passed over`() {
        assertEquals("ticket", initialClass(listOf(ticket), hierarchy, null, listOf("task", "ticket")))
    }

    @Test
    fun `the view's first creatable class is preferred`() {
        assertEquals("task", initialClass(listOf(ticket, task), hierarchy, null, listOf("task", "ticket")))
    }

    @Test
    fun `nothing creatable opens on no class`() {
        assertEquals("", initialClass(emptyList(), hierarchy, null, listOf("task")))
    }

    @Test
    fun `the default parent is the one in the preset column`() {
        val candidates = listOf(ticketObject("a", 1, "open"), ticketObject("b", 2, "done"))
        assertEquals("b", defaultParent(candidates, mapOf("status" to "done"))?.id)
    }

    @Test
    fun `the default parent falls back to the first candidate`() {
        val candidates = listOf(ticketObject("a", 1, "open"), ticketObject("b", 2, "done"))
        assertEquals("a", defaultParent(candidates, mapOf("status" to "blocked"))?.id)
        assertEquals("a", defaultParent(candidates, emptyMap())?.id)
    }

    @Test
    fun `no candidates means no default parent`() {
        assertNull(defaultParent(emptyList(), emptyMap()))
    }

    // Reparenting, in the tickets template: t1 and t2 are tickets, a and b
    // tasks under t1.
    private val tree = listOf(
        ticketObject("t1", 1),
        ticketObject("t2", 2),
        ProjectObject(id = "a", objectClass = "task", number = 3, parent = "t1"),
        ProjectObject(id = "b", objectClass = "task", number = 4, parent = "t1"),
    )

    @Test
    fun `a parent is allowed only for the classes the hierarchy names`() {
        assertTrue(parentAllowed(hierarchy, "task", "ticket"))
        assertFalse(parentAllowed(hierarchy, "task", "task"))
        assertFalse(parentAllowed(hierarchy, "task", HIERARCHY_ROOT))
        assertTrue(parentAllowed(hierarchy, "ticket", HIERARCHY_ROOT))
    }

    @Test
    fun `a task may move to another ticket`() {
        assertTrue(reparentAllowed(tree[2], "t2", tree, hierarchy))
    }

    @Test
    fun `a task may not move under another task`() {
        assertFalse(reparentAllowed(tree[2], "b", tree, hierarchy))
    }

    @Test
    fun `a task may not move to the top level`() {
        assertFalse(reparentAllowed(tree[2], "", tree, hierarchy))
        assertTrue(reparentAllowed(tree[0], "", tree, hierarchy))
    }

    @Test
    fun `an object may not move under itself or below itself`() {
        val nested = mapOf("ticket" to listOf(HIERARCHY_ROOT, "ticket"))
        val chain = listOf(
            ticketObject("t1", 1),
            ProjectObject(id = "t2", objectClass = "ticket", number = 2, parent = "t1"),
        )
        assertFalse(reparentAllowed(chain[0], "t1", chain, nested))
        assertFalse(reparentAllowed(chain[0], "t2", chain, nested))
        assertTrue(reparentAllowed(chain[1], "", chain, nested))
    }

    @Test
    fun `an unknown parent is refused`() {
        assertFalse(reparentAllowed(tree[2], "missing", tree, hierarchy))
    }

    @Test
    fun `targets are the allowed classes, less the object and its subtree`() {
        assertEquals(listOf("t1", "t2"), parentTargets(tree[2], tree, hierarchy).map { obj -> obj.id })
        val nested = mapOf("ticket" to listOf(HIERARCHY_ROOT, "ticket"))
        val chain = listOf(
            ticketObject("t1", 1),
            ProjectObject(id = "t2", objectClass = "ticket", number = 2, parent = "t1"),
            ticketObject("t3", 3),
        )
        assertEquals(listOf("t3"), parentTargets(chain[0], chain, nested).map { obj -> obj.id })
    }
}
