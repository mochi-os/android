// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.board

import org.junit.Assert.assertEquals
import org.junit.Test
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
}
