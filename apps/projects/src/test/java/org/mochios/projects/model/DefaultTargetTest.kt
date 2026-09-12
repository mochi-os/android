// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The new merge request's target is seeded from the branch the server flags as
 * the repository's default, and from its name when an older repositories
 * release flags none. These pin both halves, since a seed that silently
 * resolves to null opens the dialog with no target chosen.
 */
class DefaultTargetTest {

    private fun branches(vararg names: String) = names.map { name -> Branch(name = name) }

    @Test
    fun `the flagged branch wins, whatever it is called`() {
        val branches = listOf(
            Branch(name = "main"),
            Branch(name = "release", isDefault = true),
        )
        assertEquals("release", branches.defaultTarget()?.name)
    }

    @Test
    fun `main wins wherever it sits in the list`() {
        assertEquals("main", branches("dev", "main", "master").defaultTarget()?.name)
    }

    @Test
    fun `master is the fallback when there is no main`() {
        assertEquals("master", branches("dev", "master").defaultTarget()?.name)
    }

    @Test
    fun `otherwise the first branch is offered`() {
        assertEquals("dung/feature", branches("dung/feature", "dev").defaultTarget()?.name)
    }

    @Test
    fun `an empty list seeds nothing`() {
        assertNull(emptyList<Branch>().defaultTarget())
    }
}
