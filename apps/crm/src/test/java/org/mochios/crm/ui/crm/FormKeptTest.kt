// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.crm.CrmServer
import org.mochios.crm.R
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.ui.crmlist.CreateCrmScreen
import org.mochios.crm.ui.crmlist.CreateCrmViewModel
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The new-item and new-CRM forms keep what was typed and picked when their
 * screen is built again, as a rotation, a theme change or a font size change
 * builds it, rather than opening empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class FormKeptTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer

    /** Whether the screen is shown; off and on again is the screen built anew. */
    private var shown by mutableStateOf(true)

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("p1/-/information", """{"crm": {"id": "p1", "access": "owner"},
            "classes": [{"id": "card", "name": "Card", "title": "title"}, {"id": "note", "name": "Note", "title": "title"}],
            "fields": {"card": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1},
                {"id": "status", "name": "Status", "fieldtype": "text", "rank": 2}],
                "note": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1},
                {"id": "body", "name": "Body", "fieldtype": "text", "rank": 2}]},
            "options": {}, "views": [], "hierarchy": {"card": [""], "note": [""]}}""")
        server.data("p1/-/objects", """{"objects": []}""")
        server.data("p1/-/people", """{"people": []}""")
        server.data("-/create", """{"id": "c9", "fingerprint": "fp9"}""")
    }

    @After
    fun end() = server.stop()

    private fun rebuild() {
        rule.runOnUiThread { shown = false }
        rule.waitForIdle()
        rule.runOnUiThread { shown = true }
        rule.waitForIdle()
    }

    private fun offered(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun type(label: String, text: String) {
        rule.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(text)
        rule.waitForIdle()
    }

    @Test
    fun `the new item's values stay when its screen is built again`() {
        val model = CreateObjectViewModel(SavedStateHandle(mapOf("crmId" to "p1")), server.repository(), ActiveViewStore(context))
        until("the form") { !model.uiState.value.isLoading }
        rule.setContent { if (shown) CreateObjectScreen(onBack = {}, onCreated = {}, viewModel = model) }
        rule.waitForIdle()
        type("Status", "Typed")
        rebuild()
        assertTrue("the value typed", offered("Typed"))
    }

    @Test
    fun `the class picked for the new item stays when its screen is built again`() {
        val model = CreateObjectViewModel(SavedStateHandle(mapOf("crmId" to "p1")), server.repository(), ActiveViewStore(context))
        until("the form") { !model.uiState.value.isLoading }
        rule.setContent { if (shown) CreateObjectScreen(onBack = {}, onCreated = {}, viewModel = model) }
        rule.waitForIdle()
        rule.onNodeWithText("Card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Note").performClick()
        rule.waitForIdle()
        type("Body", "Kept")
        rebuild()
        assertTrue("the class picked", offered("Body"))
        assertTrue("its value typed", offered("Kept"))
    }

    @Test
    fun `the new CRM's name and switches stay when its screen is built again`() {
        val model = CreateCrmViewModel(server.repository())
        // The listing switch, then who may view, in the order the form shows them.
        val search = { rule.onAllNodes(isToggleable())[0] }
        val view = { rule.onAllNodes(isToggleable())[1] }
        rule.setContent { if (shown) CreateCrmScreen(onBack = {}, onCreated = {}, viewModel = model) }
        rule.waitForIdle()
        search().performClick()
        view().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("Leads")
        rule.waitForIdle()
        rebuild()
        assertTrue(offered("Leads"))
        search().assertIsOn()
        view().assertIsOn()
        rule.onNodeWithText(context.getString(R.string.crm_create_action)).performClick()
        until("the create") { server.of("-/create").isNotEmpty() }
        val body = server.of("-/create").single().body
        assertTrue(body, body.contains("privacy=public") && body.contains("readable=true") && body.contains("name=Leads"))
    }
}
