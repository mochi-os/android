// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.board

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.Project
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.model.ProjectView

class BoardObjectsTest {
    private fun obj(id: String, parent: String = "", cls: String = "task") =
        ProjectObject(id = id, parent = parent, objectClass = cls)

    @Test
    fun `children of an object in the set are not top level`() {
        val objects = listOf(obj("a"), obj("b", parent = "a"), obj("c"))
        assertEquals(listOf("a", "c"), topLevelObjects(objects, ProjectView()).map { it.id })
    }

    @Test
    fun `a parent outside the set counts as none`() {
        val objects = listOf(obj("a", parent = "elsewhere"))
        assertEquals(listOf("a"), topLevelObjects(objects, ProjectView()).map { it.id })
    }

    @Test
    fun `the view's classes narrow the board`() {
        val objects = listOf(obj("a", cls = "task"), obj("b", cls = "bug"))
        assertEquals(listOf("b"), topLevelObjects(objects, ProjectView(classes = listOf("bug"))).map { it.id })
    }

    /**
     * Duc's dticket1: the Ticket class ranks first, and the options arrive
     * keyed alphabetically, Task before Ticket, as the server's JSON has them.
     * "Revise" was added on the web and "Review" on Android, each to the class
     * that client took for the board.
     */
    private val details = ProjectDetails(
        project = Project(id = "dticket1"),
        classes = listOf(ProjectClass(id = "ticket", rank = 0), ProjectClass(id = "task", rank = 1)),
        options = linkedMapOf(
            "task" to mapOf(
                "status" to options("todo", "progress", "done", "review"),
                "effort" to options("small", "large"),
            ),
            "ticket" to mapOf("status" to options("open", "progress", "resolved", "closed", "revise")),
        ),
    )
    private val board = ProjectView(id = "tickets", columns = "status", classes = listOf("ticket", "task"))

    private fun options(vararg ids: String) = ids.mapIndexed { rank, id -> FieldOption(id = id, rank = rank) }

    @Test
    fun `a board's columns are its first class's options, not the first the options map holds`() {
        assertEquals("ticket", boardClass(details, board))
        assertEquals(
            listOf("open", "progress", "resolved", "closed", "revise"),
            boardOptions(details, board, "status").map { it.id },
        )
    }

    @Test
    fun `a board whose view lists no classes takes the project's first class by rank`() {
        // Handed over out of rank order, so the rank has to be what decides.
        val shuffled = details.copy(classes = details.classes.reversed())
        assertEquals("ticket", boardClass(shuffled, ProjectView(columns = "status")))
    }

    @Test
    fun `a board on a field only another class has options for has no columns`() {
        // As on the web, which looks no further than the board class.
        assertEquals(emptyList<FieldOption>(), boardOptions(details, board, "effort"))
    }

    @Test
    fun `every option of a field is each class's, in class order, one per id`() {
        assertEquals(
            listOf("open", "progress", "resolved", "closed", "revise", "todo", "done", "review"),
            // Handed over out of rank order, so the rank has to be what decides.
            allOptions(details.copy(classes = details.classes.reversed()), "status").map { it.id },
        )
    }
}
