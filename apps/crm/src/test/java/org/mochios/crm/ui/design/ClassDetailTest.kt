// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.crm.CrmServer
import org.mochios.crm.R
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * A class's parents go as the web sends them, so "top level" alone survives
 * a parent being unticked and can be ticked back; a change the server
 * refuses is said over the loaded class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class ClassDetailTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer

    private val hierarchy = "c1/-/classes/contact/hierarchy/set"

    @Before
    fun begin() {
        server = CrmServer(context)
    }

    @After
    fun end() = server.stop()

    private fun show(parents: String) {
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "owner"},
            "classes": [{"id": "company", "name": "Company", "title": ""}, {"id": "contact", "name": "Contact", "title": ""}],
            "fields": {}, "options": {}, "views": [], "hierarchy": {"company": [""], "contact": $parents}}""")
        val model = ClassDetailViewModel(SavedStateHandle(mapOf("crmId" to "c1", "classId" to "contact")), server.repository())
        rule.setContent { ClassDetailScreen(onBack = {}, onAddField = {}, onFieldClick = {}, viewModel = model) }
        until("the class") { model.uiState.value.crmDetails != null }
        rule.waitForIdle()
    }

    private fun chip(name: String) {
        rule.onNode(hasText(name) and isSelectable()).performClick()
    }

    private fun sent(): String {
        until("the hierarchy") { server.of(hierarchy).isNotEmpty() }
        val set = server.of(hierarchy).last()
        assertTrue(set.type, set.type.startsWith("application/json"))
        return set.json().get("parents").asString
    }

    @Test
    fun `unticking the only parent class leaves the top level, not nothing`() {
        show("""["", "company"]""")
        chip("Company")
        assertEquals("", sent())
    }

    @Test
    fun `the top level can be ticked back`() {
        show("""["company"]""")
        chip(context.getString(R.string.crm_tree_root_level))
        assertEquals("company,", sent())
    }

    @Test
    fun `no parent at all is spelt with the server's sentinel`() {
        show("""[""]""")
        chip(context.getString(R.string.crm_tree_root_level))
        assertEquals("_none_", sent())
    }

    @Test
    fun `a delete the server refuses is said over the class`() {
        show("""[""]""")
        server.refuse("c1/-/classes/contact/delete", "Class in use")
        rule.onNodeWithText(context.getString(R.string.crm_class_delete)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        until("the message") { rule.onAllNodesWithText("Class in use").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText(context.getString(R.string.crm_class_parents)).fetchSemanticsNodes().isNotEmpty())
    }
}
