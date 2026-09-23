// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmObject

class HierarchyTest {
    // The CRM template, with contact also allowed at the top level as a
    // custom design might.
    private val company = CrmClass(id = "company", name = "Company")
    private val contact = CrmClass(id = "contact", name = "Contact")
    private val deal = CrmClass(id = "deal", name = "Deal")
    private val hierarchy = mapOf(
        "company" to listOf(HIERARCHY_ROOT),
        "contact" to listOf(HIERARCHY_ROOT, "company"),
        "deal" to listOf("company", "contact"),
    )

    private fun crmObject(id: String, cls: String, parent: String = "") =
        CrmObject(id = id, objectClass = cls, parent = parent)

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
        assertEquals(",contact", hierarchyParameter(listOf(HIERARCHY_ROOT, "contact")))
        assertEquals("company,contact", hierarchyParameter(listOf("company", "contact")))
    }

    @Test
    fun `a class that may sit at the top level needs no parent, even with parent classes`() {
        assertFalse(parentRequired(hierarchy, "contact"))
        assertTrue(parentRequired(hierarchy, "deal"))
        assertTrue(parentRequired(hierarchy, "missing"))
    }

    @Test
    fun `a class that may sit at the top level is creatable with no parent objects`() {
        assertEquals(listOf(company, contact), creatableClasses(listOf(company, contact, deal), hierarchy, emptyList()))
    }

    @Test
    fun `a class needing a parent is creatable once one exists`() {
        val objects = listOf(crmObject("c1", "company"))
        assertEquals(listOf(company, contact, deal), creatableClasses(listOf(company, contact, deal), hierarchy, objects))
    }

    @Test
    fun `a class with no entries is never creatable`() {
        val orphan = CrmClass(id = "orphan", name = "Orphan")
        assertEquals(listOf(company), creatableClasses(listOf(company, orphan), hierarchy, listOf(crmObject("c1", "company"))))
    }

    @Test
    fun `a class that may sit at the top level opens on no parent`() {
        val candidates = listOf(crmObject("c1", "company"))
        assertNull(parentChoice(null, candidates, required = false))
    }

    @Test
    fun `a class needing a parent opens on the first candidate`() {
        val candidates = listOf(crmObject("c1", "company"), crmObject("c2", "company"))
        assertEquals("c1", parentChoice(null, candidates, required = true))
    }

    @Test
    fun `a chosen parent is kept while it is a candidate`() {
        val candidates = listOf(crmObject("c1", "company"), crmObject("c2", "company"))
        assertEquals("c2", parentChoice("c2", candidates, required = true))
        assertEquals("c2", parentChoice("c2", candidates, required = false))
        assertEquals("c1", parentChoice("gone", candidates, required = true))
        assertNull(parentChoice("gone", candidates, required = false))
    }

    private val tree = listOf(
        crmObject("c1", "company"),
        crmObject("c2", "company"),
        crmObject("p1", "contact", parent = "c1"),
        crmObject("d1", "deal", parent = "p1"),
    )

    @Test
    fun `a deal may move to a contact or a company but not the top level`() {
        assertTrue(reparentAllowed(tree[3], "c2", tree, hierarchy))
        assertTrue(reparentAllowed(tree[3], "p1", tree, hierarchy))
        assertFalse(reparentAllowed(tree[3], "", tree, hierarchy))
        assertTrue(reparentAllowed(tree[2], "", tree, hierarchy))
    }

    @Test
    fun `a company may not move under a contact`() {
        assertFalse(reparentAllowed(tree[1], "p1", tree, hierarchy))
    }

    @Test
    fun `an unknown parent is refused`() {
        assertFalse(reparentAllowed(tree[3], "missing", tree, hierarchy))
    }

    @Test
    fun `a contact may not move under its own deal`() {
        val nested = hierarchy + ("contact" to listOf(HIERARCHY_ROOT, "company", "deal", "contact"))
        assertFalse(reparentAllowed(tree[2], "d1", tree, nested))
        assertFalse(reparentAllowed(tree[2], "p1", tree, nested))
    }

    @Test
    fun `targets are the allowed classes, less the object and its subtree`() {
        assertEquals(listOf("c1", "c2", "p1"), parentTargets(tree[3], tree, hierarchy).map { obj -> obj.id })
        assertEquals(emptyList<String>(), parentTargets(tree[1], tree, hierarchy).map { obj -> obj.id })
        val nested = hierarchy + ("contact" to listOf(HIERARCHY_ROOT, "company", "deal", "contact"))
        assertEquals(listOf("c1", "c2"), parentTargets(tree[2], tree, nested).map { obj -> obj.id })
    }
}
