// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.tree

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.CrmServer
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.model.Crm
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmObject
import org.mochios.crm.settle
import org.mochios.crm.ui.`object`.ObjectDetailSheet
import org.mochios.crm.ui.`object`.ObjectDetailViewModel
import org.mochios.crm.ui.crm.CrmViewModel
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * Deleting an object deletes everything under it on the server, so the tree
 * row's Delete and the object sheet's ask first, naming the object and how
 * many objects go with it, as the web does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ObjectDeleteTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket

    // Acme with a contact under it and a deal under the contact; Solo alone.
    private val objects = """[
        {"id": "acme", "class": "company", "parent": "", "rank": "a0", "values": {"name": "Acme"}},
        {"id": "ann", "class": "contact", "parent": "acme", "rank": "a1", "values": {"name": "Ann"}},
        {"id": "deal", "class": "contact", "parent": "ann", "rank": "a2", "values": {"name": "Deal"}},
        {"id": "solo", "class": "company", "parent": "", "rank": "a3", "values": {"name": "Solo"}}
    ]"""

    private val details = CrmDetails(
        crm = Crm(id = "c1", name = "Sales", access = "owner"),
        classes = listOf(CrmClass(id = "company", name = "Company", title = "name"), CrmClass(id = "contact", name = "Contact", title = "name")),
        hierarchy = mapOf("company" to listOf(""), "contact" to listOf("", "company", "contact")),
    )

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "owner"},
            "classes": [{"id": "company", "name": "Company", "title": "name"}, {"id": "contact", "name": "Contact", "title": "name"}],
            "fields": {}, "options": {}, "views": [{"id": "v1", "name": "List", "viewtype": "list"}],
            "hierarchy": {"company": [""], "contact": ["", "company", "contact"]}}""")
        server.data("c1/-/objects", """{"objects": $objects, "watched": []}""")
        server.data("c1/-/people", """{"people": []}""")
        server.data("c1/-/objects/acme", """{"object": {"id": "acme", "crm": "c1", "class": "company", "parent": ""}, "values": {"name": "Acme"}}""")
        socket = server.socket()
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.stop()
    }

    private fun offered(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun tree(): CrmViewModel {
        val model = CrmViewModel(
            SavedStateHandle(mapOf("crmId" to "c1")),
            server.repository(),
            socket,
            server.session,
            ActiveViewStore(context),
            CollapsedColumnsStore(context),
        )
        until("the tree") { model.uiState.value.objects.size == 4 }
        rule.setContent {
            val state by model.uiState.collectAsState()
            TreeView(
                objects = state.objects,
                view = model.getActiveView(),
                viewModel = model,
                crmDetails = state.crmDetails,
                people = emptyList(),
                allObjects = state.objects,
                onObjectClick = {},
            )
        }
        rule.waitForIdle()
        return model
    }

    /** Opens the row menu of the [index]th row and taps Delete. */
    private fun deleteRow(index: Int) {
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[index].performClick()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `a tree row's Delete names the object and what goes with it, and waits for the confirmation`() {
        tree()
        deleteRow(0)
        assertTrue(offered("Are you sure you want to delete \"Acme\" and the 2 items under it? This action cannot be undone."))
        settle(200)
        assertEquals("nothing is deleted before the confirmation", emptyList<Any>(), server.of("c1/-/objects/acme/delete"))
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        until("the delete") { server.of("c1/-/objects/acme/delete").isNotEmpty() }
    }

    @Test
    fun `a row with nothing under it is named alone, and Cancel deletes nothing`() {
        tree()
        deleteRow(3)
        assertTrue(offered("Are you sure you want to delete \"Solo\"? This action cannot be undone."))
        rule.onNodeWithText(context.getString(MochiR.string.common_cancel)).performClick()
        rule.waitForIdle()
        settle(300)
        assertEquals(emptyList<Any>(), server.of("c1/-/objects/solo/delete"))
    }

    @Test
    fun `the object sheet's Delete asks the same`() {
        var deleted = false
        val model = ObjectDetailViewModel(server.repository(), server.session, socket)
        rule.setContent {
            ObjectDetailSheet(
                crmId = "c1",
                objectId = "acme",
                crmDetails = details,
                initialObject = CrmObject(id = "acme", crm = "c1", objectClass = "company", values = mapOf("name" to "Acme")),
                onDismiss = {},
                onDeleteObject = { deleted = true },
                viewModel = model,
            )
        }
        until("the sheet's list") { model.uiState.value.siblingObjects.size == 4 }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_more_options)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        rule.waitForIdle()
        assertTrue(offered("Are you sure you want to delete \"Acme\" and the 2 items under it? This action cannot be undone."))
        assertFalse(deleted)
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        rule.waitForIdle()
        assertTrue(deleted)
    }
}
